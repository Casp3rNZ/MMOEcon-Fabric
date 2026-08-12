package com.casp3rnz.mmoecon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/**
 *  Opens the shop GUI for a player.
 */
public final class ShopOpener {

    public static void open(ServerPlayer player) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Shop");
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player p) {
                PlayerShopSession session = new PlayerShopSession();
                return new ShopMenu(syncId, playerInventory, session);
            }
        });
    }

    private ShopOpener() {}
}
