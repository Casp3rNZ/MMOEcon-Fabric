package com.casp3rnz.mmoecon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/**
 * Opens the auction house GUI for a player. Mirrors {@link ShopOpener}.
 */
public final class AuctionHouseOpener {

    public static void open(ServerPlayer player) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Auction House");
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player p) {
                return new AuctionHouseMenu(syncId, playerInventory, new PlayerAuctionHouseSession());
            }
        });
    }

    private AuctionHouseOpener() {}
}
