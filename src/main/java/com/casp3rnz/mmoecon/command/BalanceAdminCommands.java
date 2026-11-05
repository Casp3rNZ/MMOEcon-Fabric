package com.casp3rnz.mmoecon.command;

import com.casp3rnz.mmoecon.balance.BalanceAdministration;
import com.casp3rnz.mmoecon.balance.BalanceTargets;
import com.casp3rnz.mmoecon.balance.gui.BalanceAdminOpener;
import com.casp3rnz.mmoecon.core.Messages;
import com.casp3rnz.mmoecon.core.Money;
import com.casp3rnz.mmoecon.core.Permissions;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;

/** Administrator command adapters; money operations live in BalanceAdministration. */
public final class BalanceAdminCommands {

    private static final SuggestionProvider<CommandSourceStack> ACCOUNTS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    BalanceTargets.accounts(ctx.getSource().getServer()).stream()
                            .filter(BalanceTargets.Target::hasUsername)
                            .map(BalanceTargets.Target::name).distinct(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("bal")
                .requires(src -> Permissions.check(src, Permissions.BAL, 0))
                .then(Commands.literal("get")
                        .requires(BalanceAdminCommands::allowed)
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests(ACCOUNTS)
                                .executes(ctx -> get(ctx.getSource(), StringArgumentType.getString(ctx, "target")))))
                .then(Commands.literal("admin")
                        .requires(BalanceAdminCommands::allowed)
                        .executes(ctx -> {
                            BalanceAdminOpener.open(ctx.getSource().getPlayerOrException());
                            return Command.SINGLE_SUCCESS;
                        }));

        root.then(adjustment("set", BalanceAdministration.Operation.SET));
        root.then(adjustment("add", BalanceAdministration.Operation.ADD));
        root.then(adjustment("subtract", BalanceAdministration.Operation.SUBTRACT));
        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adjustment(
            String name, BalanceAdministration.Operation operation) {
        return Commands.literal(name)
                .requires(BalanceAdminCommands::allowed)
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(ACCOUNTS)
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(ctx -> adjust(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "target"), operation,
                                        StringArgumentType.getString(ctx, "amount")))));
    }

    private static int get(CommandSourceStack src, String input) {
        if (!allowed(src)) return 0;
        BalanceTargets.Target target = resolve(src, input);
        if (target == null) return 0;
        BalanceAdministration.Result result = BalanceAdministration.get(src.getServer(), target.uuid());
        if (!result.success()) return failure(src, result);
        src.sendSuccess(() -> Messages.body(target.name() + " balance: §a$"
                + Money.formatExact(result.balance())), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int adjust(CommandSourceStack src, String input,
                              BalanceAdministration.Operation operation, String amountText) {
        if (!allowed(src)) return 0;
        final long amount;
        try {
            amount = Money.parseExact(amountText);
        } catch (IllegalArgumentException | ArithmeticException e) {
            src.sendFailure(Messages.error("Use a non-negative dollar amount with at most two decimal places,"
                    + " no greater than $" + Money.formatExact(Long.MAX_VALUE) + "."));
            return 0;
        }
        BalanceTargets.Target target = resolve(src, input);
        if (target == null) return 0;
        String actor = src.getTextName() + (src.getPlayer() == null ? "" : " (" + src.getPlayer().getUUID() + ")");
        BalanceAdministration.Result result = BalanceAdministration.execute(
                src.getServer(), actor, target.uuid(), operation, amount);
        if (!result.success()) return failure(src, result);
        src.sendSuccess(() -> Messages.body(target.name() + " balance: §a$"
                + Money.formatExact(result.previousBalance()) + "§7 -> §a$" + Money.formatExact(result.balance())
                + "§7."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static BalanceTargets.Target resolve(CommandSourceStack src, String input) {
        BalanceTargets.Target target = BalanceTargets.resolve(src.getServer(), input);
        if (target == null) {
            src.sendFailure(Messages.error("No unique stored account found for " + input
                    + ". Use an online username or a locally cached offline username."));
        }
        return target;
    }

    private static int failure(CommandSourceStack src, BalanceAdministration.Result result) {
        src.sendFailure(Messages.error(BalanceAdministration.failureMessage(result.status())));
        return 0;
    }

    private static boolean allowed(CommandSourceStack src) {
        return Permissions.check(src, Permissions.BALANCE_ADMIN, Permissions.OP_LEVEL);
    }

    private BalanceAdminCommands() {}
}
