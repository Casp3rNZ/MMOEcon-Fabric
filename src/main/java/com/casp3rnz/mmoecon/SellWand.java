package com.casp3rnz.mmoecon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;

/**
 * Represents the Sell Wand — an NBT-tagged blaze rod that lets players
 * Right-click a chest to sell all its sellable contents.
 * Two-click confirm flow:
 *   1st click: calculates total, stores a PendingSale, sends summary to player
 *   2nd click (within CONFIRM_TIMEOUT_TICKS): executes the sale
 *   Any other action or timeout: pending sale is cleared
 * The wand is identified by a custom NBT tag "mmoecon_sell_wand:true" written
 * into the item's CustomData component. This avoids registering a new Item
 * class, keeping the mod fully server-side.
 */

public class SellWand {
    public static final String WAND_TAG = "mmoecon_sell_wand";
    public static final long CONFIRM_TIMEOUT_TICKS = 300L;

    /**
     * Per-player pending sale. Populated on first wand click, consumed or
     * expired on second click. Stored as server tick time so we can time out.
     * @param blockPos   The exact block position clicked on the first click.
     *                   Second click must be the same block to confirm.
     * @param totalValue The calculated sell value of the container contents, in cents.
     * @param totalItems Number of individual items that will be sold.
     * @param createdAt  Server tick time when this pending sale was created.
     */
    public record PendingSale(
            BlockPos blockPos,
            long totalValue,
            int totalItems,
            long createdAt
    ) {
        public boolean isExpired() {
            var server = ModServer.get();
            if (server == null) return true;
            return server.getTickCount() - createdAt > CONFIRM_TIMEOUT_TICKS;
        }
    }

    private static final Map<UUID, PendingSale> pendingSales = new ConcurrentHashMap<>();

    // Wand ItemStack Factory

    public static ItemStack create() {
        ItemStack wand = new ItemStack(Items.BLAZE_ROD);

        // Custom name
        wand.set(DataComponents.CUSTOM_NAME,
                Component.literal("§6§lSell Wand")
                        .withStyle(s -> s.withItalic(false)));

        // Lore
        List<Component> lore = List.of(
                Component.literal("§7Right-click a chest to sell")
                        .withStyle(s -> s.withItalic(false)),
                Component.literal("§7all sellable items inside.")
                        .withStyle(s -> s.withItalic(false)),
                Component.literal("")
                        .withStyle(s -> s.withItalic(false)),
                Component.literal("§eClick once to preview, again to confirm.")
                        .withStyle(s -> s.withItalic(false))
        );
        wand.set(DataComponents.LORE, new ItemLore(lore, lore));

        // Add enchantment glint — empty map gives visual only, no actual enchantment
        wand.set(DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);

        // NBT tag to identify this as a sell wand
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(WAND_TAG, true);
        wand.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        return wand;
    }

    // Detection

    /** Returns true is the given ItemStack is a sell wand  */
    public static boolean isWand(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.BLAZE_ROD)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if(data == null) return false;
        return data.copyTag().getBoolean(WAND_TAG);
    }

    // Pending Sale Management
    public static void setPending(UUID playerUUID, PendingSale sale) {
        pendingSales.put(playerUUID, sale);
    }

    public static PendingSale getPending(UUID playerUUID) {
        return pendingSales.get(playerUUID);
    }

    public static void clearPending(UUID playerUUID) {
        pendingSales.remove(playerUUID);
    }

    public static boolean hasPending(UUID playerUUID) {
        PendingSale sale = pendingSales.get(playerUUID);
        if (sale == null) return false;
        if (sale.isExpired()) {
            pendingSales.remove(playerUUID);
            return false;
        }
        return true;
    }

    private SellWand() {}
}
