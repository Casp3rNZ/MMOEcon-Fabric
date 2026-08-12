package com.casp3rnz.mmoecon;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.*;

/**
 * Registers all economy commands.
 *
 * Ported from NeoForge's {@code RegisterCommandsEvent} to Fabric's
 * {@code CommandRegistrationCallback}; {@link #register(CommandDispatcher)} is
 * invoked from {@link MMOEcon}. The command bodies are plain vanilla Brigadier
 * and are unchanged from the NeoForge build.
 *
 * Commands:
 *   /bal            — show own balance
 *   /money          — alias for /bal
 *   /bal top        — top 10 richest players
 *   /pay <name> <amount>
 *   /shop           — open shop GUI
 *   /shop reload    — reload shop JSON config
 *   /sell hand      — sell held item
 *   /sell inv       — sell all sellable items in inventory
 *   /sellwand give - (admin only) gives user a sell wand
 */
public final class EconomyCommands {

    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    ctx.getSource().getServer().getPlayerNames(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /bal and /money
        dispatcher.register(Commands.literal("bal").executes(ctx -> showBalance(ctx.getSource())));
        dispatcher.register(Commands.literal("money").executes(ctx -> showBalance(ctx.getSource())));

        // /bal top
        dispatcher.register(Commands.literal("bal")
                .then(Commands.literal("top").executes(ctx -> showBalTop(ctx.getSource()))));

        // /pay <player> <amount>
        dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("target", StringArgumentType.string())
                        .suggests(ONLINE_PLAYERS)
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01d))
                                .executes(ctx -> {
                                    String targetName = StringArgumentType.getString(ctx, "target");
                                    long amount = Money.fromDouble(DoubleArgumentType.getDouble(ctx, "amount"));
                                    return executePay(ctx.getSource(), targetName, amount);
                                }))));

        // /shop
        dispatcher.register(Commands.literal("shop")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ShopOpener.open(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2)) // op-only
                        .executes(ctx -> {
                            ShopItemManager.reload();
                            ctx.getSource().sendSuccess(() -> Messages.success("Shop config reloaded."), true);
                            return Command.SINGLE_SUCCESS;
                        })));

        // /sell hand | inv
        dispatcher.register(Commands.literal("sell")
                .then(Commands.literal("hand").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return SellCommand.sellHand(player);
                }))
                .then(Commands.literal("inv").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return SellCommand.sellInventory(player);
                })));

        // /sellwand give — op-only, gives a sell wand to the player
        dispatcher.register(Commands.literal("sellwand")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("give")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            player.getInventory().add(SellWand.create());
                            ctx.getSource().sendSuccess(() -> Messages.success("Given a Sell Wand."), false);
                            return Command.SINGLE_SUCCESS;
                        })));
    }

    // Command implementations

    private static int showBalance(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        long balance = PlayerBalanceManager.getBalance(player.getUUID());
        src.sendSuccess(() -> Messages.body("Balance: " + Messages.money(balance)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int showBalTop(CommandSourceStack src) {
        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(PlayerBalanceManager.getBalances().entrySet());
        sorted.sort(Map.Entry.<UUID, Long>comparingByValue().reversed());

        if (sorted.isEmpty()) {
            src.sendSuccess(() -> Messages.info("No balances recorded yet."), false);
            return Command.SINGLE_SUCCESS;
        }

        src.sendSuccess(() -> Component.literal("§8§m                    §r §6§lTop Balances §8§m                    "), false);
        MinecraftServer server = src.getServer();

        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Map.Entry<UUID, Long> entry = sorted.get(i);
            Optional<GameProfile> profile = Objects.requireNonNull(server.getProfileCache()).get(entry.getKey());
            String name = profile.map(GameProfile::getName).orElse("unknown");
            final int rank = i + 1;

            // Gold/silver/bronze for the podium, muted grey for the rest, so the
            // top of the list reads at a glance.
            String rankColour = switch (rank) {
                case 1 -> "§6";
                case 2 -> "§f";
                case 3 -> "§c";
                default -> "§8";
            };

            final String display = " " + rankColour + rank + "." + " §7" + name
                    + " §8- " + "§a$" + Money.format(entry.getValue());
            src.sendSuccess(() -> Component.literal(display), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executePay(CommandSourceStack src, String targetName, long amount)
            throws CommandSyntaxException {

        ServerPlayer sender = src.getPlayerOrException();
        ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(targetName);

        if (target == null) {
            src.sendFailure(Messages.error(targetName + " is not online."));
            return 0;
        }
        if (target.equals(sender)) {
            src.sendFailure(Messages.error("You can't pay yourself."));
            return 0;
        }
        // Checked before the transfer so a missing account isn't reported as
        // "not enough money". Both sides are online here, so both should already
        // have accounts — this only trips if one somehow wasn't created on join.
        if (!PlayerBalanceManager.hasAccount(target.getUUID())) {
            src.sendFailure(Messages.error(targetName + " doesn't have an account yet."));
            return 0;
        }
        if (!PlayerBalanceManager.transfer(sender.getUUID(), target.getUUID(), amount)) {
            long shortfall = amount - PlayerBalanceManager.getBalance(sender.getUUID());
            src.sendFailure(Messages.error(
                    "You need $" + Money.format(shortfall) + " more to pay that."));
            return 0;
        }

        src.sendSuccess(() -> Messages.body(
                "Paid " + Messages.money(amount) + " to " + Messages.item(targetName) + "."), false);
        target.sendSystemMessage(Messages.body(
                Messages.item(sender.getName().getString())
                        + " paid you " + Messages.money(amount) + "."));
        // Receiving money should feel like the other transactions in the mod.
        target.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.0f);

        return Command.SINGLE_SUCCESS;
    }

    private EconomyCommands() {}
}
