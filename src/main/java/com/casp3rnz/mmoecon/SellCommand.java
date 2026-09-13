package com.casp3rnz.mmoecon;


import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Logic for /sell hand, /sell inv and /sell receipt.
 * Called from EconomyCommands — not a command class itself, just the implementations.
 */
public final class SellCommand {

    static int sellHand(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Messages.error("You are not holding anything."));
            return 0;
        }

        boolean isWand = SellWand.isWand(held);

        // Reject enchanted / renamed / damaged items outright — only clean, full-durability
        // stock items may be sold. The wand is exempt (it's an enchanted, renamed blaze rod).
        if (!SellFilter.isSellable(held, isWand)) {
            player.sendSystemMessage(Messages.error(
                    held.getHoverName().getString()
                            + " can't be sold — it's enchanted, renamed, or damaged."));
            return 0;
        }

        ShopItemManager.ShopItem shopItem = isWand
                ? ShopItemManager.findSpecial("sell_wand")
                : ShopItemManager.findItem(BuiltInRegistries.ITEM.getKey(held.getItem()).toString());

        if (shopItem == null || !shopItem.canSell()) {
            player.sendSystemMessage(Messages.error(
                    held.getHoverName().getString() + " can't be sold here."));
            return 0;
        }

        int qty   = held.getCount();
        long total = Money.multiply(shopItem.sellPrice(), qty);

        String itemName = held.getHoverName().getString();
        // Snapshot the sold stack (with all its components) before removing it.
        ItemStack soldStack = held.copy();

        player.getInventory().removeItem(player.getInventory().selected, qty);
        PlayerBalanceManager.addBalance(player.getUUID(), total);

        SellReceiptStore.put(player.getUUID(), SellReceiptBuilder.build(
                List.of(new SellReceiptBuilder.Entry(soldStack, shopItem.sellPrice()))));

        player.sendSystemMessage(Messages.body(
                "Sold " + Messages.item(qty + "x " + itemName)
                        + " for " + Messages.money(total) + ".")
                .copy().append(receiptButton()));
        TransactionLogger.log(player.getName().getString() + " sold " + qty + " " + itemName
                + " for $" + ShopMenu.formatMoney(total));

        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.0f);
        return 1;
    }

    static int sellInventory(ServerPlayer player) {
        long totalEarned = 0L;
        int  totalSold   = 0;
        // Each sold slot's stack, in inventory order. The receipt builder merges
        // same-item-same-components entries and re-splits into natural stacks.
        List<SellReceiptBuilder.Entry> entries = new ArrayList<>();

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            // Skip armour and offhand slots
            if (isArmorOrOffhand(player, stack)) continue;

            boolean isWand = SellWand.isWand(stack);

            // Skip enchanted / renamed / damaged items — only clean, full-durability
            // stock items may be sold. The wand is exempt (it's priced via "sell_wand").
            if (!SellFilter.isSellable(stack, isWand)) continue;

            ShopItemManager.ShopItem shopItem = isWand
                    ? ShopItemManager.findSpecial("sell_wand")
                    : ShopItemManager.findItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            if (shopItem == null || !shopItem.canSell()) continue;

            int qty      = stack.getCount();
            long earned  = Money.multiply(shopItem.sellPrice(), qty);
            totalEarned += earned;
            totalSold   += qty;

            String itemName = stack.getHoverName().getString();
            // Snapshot the stack (with components) before clearing the slot.
            entries.add(new SellReceiptBuilder.Entry(stack.copy(), shopItem.sellPrice()));

            player.getInventory().setItem(i, ItemStack.EMPTY);
            TransactionLogger.log(player.getName().getString() + " sold " + qty + " " + itemName
                    + " for $" + ShopMenu.formatMoney(earned));
        }

        if (totalSold > 0) {
            PlayerBalanceManager.addBalance(player.getUUID(), totalEarned);
            SellReceiptStore.put(player.getUUID(), SellReceiptBuilder.build(entries));
            player.sendSystemMessage(Messages.body(
                    "Sold " + Messages.item(totalSold + " items")
                            + " for " + Messages.money(totalEarned) + ".")
                    .copy().append(receiptButton()));
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.0f);
        } else {
            player.sendSystemMessage(Messages.error("Nothing in your inventory can be sold here."));
        }

        return 1;
    }

    /** Opens the read-only receipt GUI for the player's most recent sale */
    static int showReceipt(ServerPlayer player) {
        SellReceipt receipt = SellReceiptStore.get(player.getUUID());
        if (receipt == null || receipt.lines().isEmpty()) {
            player.sendSystemMessage(Messages.info("You have no recent sale to view."));
            return 0;
        }
        SellReceiptOpener.open(player, receipt);
        return 1;
    }

    /** The click to view button appended to sale confirmations. */
    static Component receiptButton() {
        return Component.literal(" ").append(
                Messages.button("[View Receipt]", "/sell receipt", "View a receipt of your last sale"));
    }

    private static boolean isArmorOrOffhand(ServerPlayer player, ItemStack stack) {
        for (ItemStack armour : player.getInventory().armor) {
            if (armour == stack) return true;
        }
        return player.getInventory().offhand.getFirst() == stack;
    }

    private SellCommand() {}
}
