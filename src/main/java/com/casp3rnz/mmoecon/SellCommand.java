package com.casp3rnz.mmoecon;


import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Logic for /sell hand and /sell inv.
 * Called from EconomyCommands — not a command class itself, just the implementations.
 */
public final class SellCommand {

    static int sellHand(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Messages.error("You are not holding anything."));
            return 0;
        }

        ShopItemManager.ShopItem shopItem = SellWand.isWand(held)
                ? ShopItemManager.findSpecial("sell_wand")
                : ShopItemManager.findItem(BuiltInRegistries.ITEM.getKey(held.getItem()).toString());

        if (shopItem == null || !shopItem.canSell()) {
            player.sendSystemMessage(Messages.error(
                    held.getHoverName().getString() + " can't be sold here."));
            return 0;
        }

        int qty   = held.getCount();
        long total = Money.multiply(shopItem.sellPrice(), qty);

        player.getInventory().removeItem(player.getInventory().selected, qty);
        PlayerBalanceManager.addBalance(player.getUUID(), total);

        String itemName = held.getHoverName().getString();
        player.sendSystemMessage(Messages.body(
                "Sold " + Messages.item(qty + "x " + itemName)
                        + " for " + Messages.money(total) + "."));
        TransactionLogger.log(player.getName().getString() + " sold " + qty + " " + itemName
                + " for $" + ShopMenu.formatMoney(total));

        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.0f);
        return 1;
    }

    static int sellInventory(ServerPlayer player) {
        long totalEarned = 0L;
        int  totalSold   = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            // Skip armour and offhand slots
            if (isArmorOrOffhand(player, stack)) continue;

            ShopItemManager.ShopItem shopItem = SellWand.isWand(stack)
                    ? ShopItemManager.findSpecial("sell_wand")
                    : ShopItemManager.findItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            if (shopItem == null || !shopItem.canSell()) continue;

            int qty      = stack.getCount();
            long earned  = Money.multiply(shopItem.sellPrice(), qty);
            totalEarned += earned;
            totalSold   += qty;

            String itemName = stack.getHoverName().getString();
            player.getInventory().setItem(i, ItemStack.EMPTY);
            TransactionLogger.log(player.getName().getString() + " sold " + qty + " " + itemName
                    + " for $" + ShopMenu.formatMoney(earned));
        }

        if (totalSold > 0) {
            PlayerBalanceManager.addBalance(player.getUUID(), totalEarned);
            player.sendSystemMessage(Messages.body(
                    "Sold " + Messages.item(totalSold + " items")
                            + " for " + Messages.money(totalEarned) + "."));
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.0f);
        } else {
            player.sendSystemMessage(Messages.error("Nothing in your inventory can be sold here."));
        }

        return 1;
    }

    private static boolean isArmorOrOffhand(ServerPlayer player, ItemStack stack) {
        for (ItemStack armour : player.getInventory().armor) {
            if (armour == stack) return true;
        }
        return player.getInventory().offhand.getFirst() == stack;
    }

    private SellCommand() {}
}
