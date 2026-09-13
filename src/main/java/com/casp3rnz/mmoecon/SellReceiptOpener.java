package com.casp3rnz.mmoecon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/**
 * Opens the read-only sell-receipt GUI for a player, showing the given receipt.
 * Mirrors {@link ShopOpener}.
 */
public final class SellReceiptOpener {

    public static void open(ServerPlayer player, SellReceipt receipt) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Receipt of last Sale");
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player p) {
                return new SellReceiptMenu(syncId, playerInventory, receipt);
            }
        });
    }

    private SellReceiptOpener() {}
}
