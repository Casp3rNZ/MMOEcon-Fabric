package com.casp3rnz.mmoecon;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * A single auction listing: one seller offering {@code quantity} of one item type
 * for a fixed total price, bought by a single buyer.
 *
 * <p>The item is stored as {@code unitStack}:
 * A template that carries the item and all its data components (enchantments, custom name, NBT), plus a separate {@code quantity}.
 * It is <em>not</em> stored as one big {@link ItemStack} with an over-max quantity: vanilla's {@code ItemStack.CODEC}
 * bounds the encoded quantity to [1, 99], so a count-2304 stack would be corrupted on the first save/load.
 * Keeping the quantity out of the ItemStack lets a listing hold up to {@code maxAuctionListingSize}.
 */
public record AuctionListing(
        UUID id,
        UUID seller,
        String sellerName,
        ItemStack unitStack,
        int quantity,
        long price,
        long createdAtEpochMillis) {

    /**
     * A fresh listing with a random id and the current wall-clock time.
     */
    public static AuctionListing create(UUID seller, String sellerName, ItemStack unitStack, int quantity, long price) {
        ItemStack template = unitStack.copy();
        template.setCount(1);
        return new AuctionListing(UUID.randomUUID(), seller, sellerName, template, quantity, price,
                System.currentTimeMillis());
    }

    public boolean isSeller(UUID uuid) {
        return seller.equals(uuid);
    }

    /** The item's display name. */
    public Component displayName() {
        return unitStack.getHoverName();
    }

    /** The item's max natural stack size. */
    public int maxStackSize() {
        return unitStack.getMaxStackSize();
    }
}
