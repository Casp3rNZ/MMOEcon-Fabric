package com.casp3rnz.mmoecon;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.world.item.ItemStack;

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
        dispatcher.register(Commands.literal("bal")
                .requires(src -> Permissions.check(src, Permissions.BAL, 0))
                .executes(ctx -> showBalance(ctx.getSource())));
        dispatcher.register(Commands.literal("money")
                .requires(src -> Permissions.check(src, Permissions.BAL, 0))
                .executes(ctx -> showBalance(ctx.getSource())));

        // /bal top
        dispatcher.register(Commands.literal("bal")
                .requires(src -> Permissions.check(src, Permissions.BAL, 0))
                .then(Commands.literal("top").executes(ctx -> showBalTop(ctx.getSource()))));

        // /pay <player> <amount>
        dispatcher.register(Commands.literal("pay")
                .requires(src -> Permissions.check(src, Permissions.PAY, 0))
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
                .requires(src -> Permissions.check(src, Permissions.SHOP, 0))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ShopOpener.open(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("reload")
                        .requires(src -> Permissions.check(src, Permissions.RELOAD, Permissions.OP_LEVEL))
                        .executes(ctx -> {
                            ShopItemManager.reload();
                            ctx.getSource().sendSuccess(() -> Messages.success("Shop config reloaded."), true);
                            return Command.SINGLE_SUCCESS;
                        })));

        // /sell hand | inv
        dispatcher.register(Commands.literal("sell")
                .requires(src -> Permissions.check(src, Permissions.SELL, 0))
                .then(Commands.literal("hand").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return SellCommand.sellHand(player);
                }))
                .then(Commands.literal("inv").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return SellCommand.sellInventory(player);
                })));

        // /sellwand give — admin-gated, gives a sell wand to the player
        dispatcher.register(Commands.literal("sellwand")
                .requires(src -> Permissions.check(src, Permissions.SELLWAND, Permissions.OP_LEVEL))
                .then(Commands.literal("give")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            player.getInventory().add(SellWand.create());
                            ctx.getSource().sendSuccess(() -> Messages.success("Given a Sell Wand."), false);
                            return Command.SINGLE_SUCCESS;
                        })));

        // /ah  (aliases: /auction, /auctionhouse)
        //   /ah                        — open the auction house GUI
        //   /ah sell <price>           — list the held stack for that total price
        //   /ah sell <price> <amount>  — list <amount> of the held item (gathered
        //                                from the inventory), for that total price
        var ah = dispatcher.register(Commands.literal("ah")
                .requires(src -> Permissions.check(src, Permissions.AH, 0))
                .executes(ctx -> openAuctionHouse(ctx.getSource()))
                .then(Commands.literal("sell")
                        .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01d))
                                .executes(ctx -> {
                                    long price = Money.fromDouble(DoubleArgumentType.getDouble(ctx, "price"));
                                    // No amount given: list exactly the held stack.
                                    return listHeldItem(ctx.getSource(), price, -1);
                                })
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            long price = Money.fromDouble(DoubleArgumentType.getDouble(ctx, "price"));
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            return listHeldItem(ctx.getSource(), price, amount);
                                        })))));
        dispatcher.register(Commands.literal("auction")
                .requires(src -> Permissions.check(src, Permissions.AH, 0))
                .redirect(ah));
        dispatcher.register(Commands.literal("auctionhouse")
                .requires(src -> Permissions.check(src, Permissions.AH, 0))
                .redirect(ah));
    }

    // Auction house commands

    private static int openAuctionHouse(CommandSourceStack src) throws CommandSyntaxException {
        if (!Config.ENABLE_AUCTION_HOUSE.get()) {
            src.sendFailure(Messages.error("The auction house is disabled on this server."));
            return 0;
        }
        ServerPlayer player = src.getPlayerOrException();
        AuctionHouseOpener.open(player);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Lists items of the held type on the auction house.
     *
     * @param requestedAmount the number to list, gathered from across the
     *        inventory; -1 means "just the held stack". Clamped to what the player
     *        actually has and to the configured per-listing ceiling.
     */
    private static int listHeldItem(CommandSourceStack src, long price, int requestedAmount)
            throws CommandSyntaxException {
        if (!Config.ENABLE_AUCTION_HOUSE.get()) {
            src.sendFailure(Messages.error("The auction house is disabled on this server."));
            return 0;
        }
        ServerPlayer player = src.getPlayerOrException();

        // Refuse before touching the inventory if they're already at their cap, so
        // a full-up player never has items pulled out only to be told "no".
        if (!AuctionHouseManager.canList(player.getUUID())) {
            src.sendFailure(Messages.error(
                    "You already have the maximum of " + Config.MAX_AUCTION_QUANTITY.get()
                            + " active listings."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            src.sendFailure(Messages.error("Hold the item you want to list in your main hand."));
            return 0;
        }

        // The held stack fixes the item type (and its components). "just the held
        // stack" lists exactly what's in hand; an explicit amount gathers matching
        // items from the whole inventory.
        int owned = countMatching(player, held);
        int ceiling = Config.MAX_AUCTION_LISTING_SIZE.get();
        int amount = requestedAmount < 0
                ? held.getCount()
                : Math.min(requestedAmount, owned);
        amount = Math.min(amount, ceiling);

        if (amount <= 0) {
            src.sendFailure(Messages.error("You don't have any of that item to list."));
            return 0;
        }
        if (requestedAmount > ceiling) {
            src.sendFailure(Messages.error("You can only list " + ceiling + " items at once."));
            return 0;
        }

        // Snapshot the item type before removing anything, then take exactly the
        // amount into escrow. Items are only ever in one place: removed here,
        // owned by the listing from now on.
        ItemStack template = held.copy();
        template.setCount(1);
        removeMatching(player, held, amount);

        int listed = amount;
        AuctionHouseManager.ListResult result =
                AuctionHouseManager.list(player.getUUID(), player.getName().getString(), template, listed, price);

        // list() re-checks the cap authoritatively; if it somehow refuses now, hand
        // the items back rather than deleting them.
        if (result != AuctionHouseManager.ListResult.SUCCESS) {
            giveBack(player, template, listed);
            src.sendFailure(Messages.error(
                    "You already have the maximum of " + Config.MAX_AUCTION_QUANTITY.get()
                            + " active listings."));
            return 0;
        }

        src.sendSuccess(() -> Messages.body(
                "Listed " + Messages.item(listed + "x " + template.getHoverName().getString())
                        + " for " + Messages.money(price) + "."), false);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.5f, 1.2f);
        return Command.SINGLE_SUCCESS;
    }

    /** Total count of items matching the template (same item and components). */
    private static int countMatching(ServerPlayer player, ItemStack template) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItemSameComponents(stack, template)) count += stack.getCount();
        }
        return count;
    }

    /** Removes up to {@code amount} matching items from the inventory. */
    private static void removeMatching(ServerPlayer player, ItemStack template, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) break;
            if (ItemStack.isSameItemSameComponents(stack, template)) {
                int take = Math.min(stack.getCount(), remaining);
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    /** Returns items to the player, split into natural stacks, dropping any overflow. */
    private static void giveBack(ServerPlayer player, ItemStack template, int amount) {
        int remaining = amount;
        int maxStack = template.getMaxStackSize();
        while (remaining > 0) {
            int give = Math.min(remaining, maxStack);
            ItemStack chunk = template.copy();
            chunk.setCount(give);
            remaining -= give;
            if (!player.getInventory().add(chunk) || !chunk.isEmpty()) {
                player.drop(chunk, false);
            }
        }
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
