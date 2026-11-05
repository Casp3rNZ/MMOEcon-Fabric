package com.casp3rnz.mmoecon.balance.gui;

import com.casp3rnz.mmoecon.core.Messages;
import com.casp3rnz.mmoecon.core.Permissions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public final class BalanceAdminOpener {

    public static boolean allowed(ServerPlayer player) {
        var source = player.createCommandSourceStack();
        return Permissions.check(source, Permissions.BAL, 0)
                && Permissions.check(source, Permissions.BALANCE_ADMIN, Permissions.OP_LEVEL);
    }

    public static void open(ServerPlayer player) {
        if (!allowed(player)) {
            player.sendSystemMessage(Messages.error("You do not have permission to manage balances."));
            return;
        }
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("§6Balance Administration");
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player p) {
                return new BalanceAdminMenu(syncId, inventory, new PlayerBalanceAdminSession());
            }
        });
    }

    private BalanceAdminOpener() {}
}
