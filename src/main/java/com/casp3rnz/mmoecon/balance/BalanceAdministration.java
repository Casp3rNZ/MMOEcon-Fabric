package com.casp3rnz.mmoecon.balance;

import com.casp3rnz.mmoecon.core.Money;
import com.casp3rnz.mmoecon.core.TransactionLogger;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Administrator operations over the existing balance store.
 * Callers must authorize the actor before invoking this API.
 * Success updates the live account; the balance manager handles scheduled persistence.
 */
public final class BalanceAdministration {

    public enum Operation { SET, ADD, SUBTRACT }
    public enum Status { SUCCESS, NO_ACCOUNT, INVALID_AMOUNT, INVALID_BALANCE, INSUFFICIENT_FUNDS, OVERFLOW }

    public record Result(Status status, long previousBalance, long balance) {
        public boolean success() { return status == Status.SUCCESS; }
    }

    public static Result get(MinecraftServer server, UUID target) {
        requireServerThread(server);
        if (!PlayerBalanceManager.hasAccount(target)) return new Result(Status.NO_ACCOUNT, 0L, 0L);
        long balance = PlayerBalanceManager.getBalance(target);
        return new Result(Status.SUCCESS, balance, balance);
    }

    public static Result execute(MinecraftServer server, String actor, UUID target,
                                 Operation operation, long amount) {
        Result current = get(server, target);
        if (!current.success()) return current;
        Result result = calculate(current.balance(), operation, amount);
        if (!result.success()) return result;

        switch (operation) {
            case SET -> PlayerBalanceManager.setBalance(target, amount);
            case ADD -> PlayerBalanceManager.addBalance(target, amount);
            case SUBTRACT -> PlayerBalanceManager.subtractBalance(target, amount);
        }
        TransactionLogger.log("Balance admin " + actor + " " + operation + " " + target
                + " amount=$" + Money.formatExact(amount)
                + " before=$" + Money.formatExact(result.previousBalance())
                + " after=$" + Money.formatExact(result.balance()));
        return result;
    }

    /** Pure validation, shared by execution and GUI previews. All values are cents. */
    public static Result calculate(long balance, Operation operation, long amount) {
        if (amount < 0 || (operation != Operation.SET && amount == 0)) {
            return new Result(Status.INVALID_AMOUNT, balance, balance);
        }
        // A set can repair a corrupt negative account; adjustments must not hide it.
        if (balance < 0 && operation != Operation.SET) {
            return new Result(Status.INVALID_BALANCE, balance, balance);
        }
        if (operation == Operation.SUBTRACT && amount > balance) {
            return new Result(Status.INSUFFICIENT_FUNDS, balance, balance);
        }
        try {
            long updated = switch (operation) {
                case SET -> amount;
                case ADD -> Math.addExact(balance, amount);
                case SUBTRACT -> Math.subtractExact(balance, amount);
            };
            return new Result(Status.SUCCESS, balance, updated);
        } catch (ArithmeticException e) {
            return new Result(Status.OVERFLOW, balance, balance);
        }
    }

    public static String failureMessage(Status status) {
        return switch (status) {
            case NO_ACCOUNT -> "That player does not have a stored account.";
            case INVALID_AMOUNT -> "Set requires a non-negative amount; add and subtract require a positive amount.";
            case INVALID_BALANCE -> "That account has a negative balance. Use set to repair it first.";
            case INSUFFICIENT_FUNDS -> "That player does not have enough money. Nothing changed.";
            case OVERFLOW -> "The resulting balance would exceed the currency limit. Nothing changed.";
            case SUCCESS -> throw new IllegalArgumentException("Success is not a failure.");
        };
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Balance administration must run on the server thread.");
        }
    }

    private BalanceAdministration() {}
}
