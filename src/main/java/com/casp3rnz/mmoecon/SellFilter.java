package com.casp3rnz.mmoecon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Shared rule deciding whether a stack is a "plain" item eligible for selling in a GUI shop.
 * A stack is rejected regardless of whether its item has a shop sellPrice if it:
 *   - has any enchantment,
 *   - has a custom name, or
 *   - is not at 100% durability.
 * The intent is that players can only sell clean, stock items, anything a player
 * has invested in or worn down should never be sold for its flat shop price.
 * The Sell Wand is deliberately exempt: it is an enchanted, custom-named blaze rod,
 * and it is priced through its own "sell_wand" shop entry rather than the blaze_rod entry.
 * Callers pass isWand so the wand stays sellable.
 */
public final class SellFilter {

    /**
     * @param stack  the stack being considered
     * @param isWand whether this stack is the Sell Wand (exempt from the checks)
     * @return true if the stack may be sold, false if it should be skipped/refused
     */
    public static boolean isSellable(ItemStack stack, boolean isWand) {
        // Safeguard
        if (stack.isEmpty()) return false;
        // Wand check
        if (isWand) return true;
        // Std item meta check
        if (isEnchanted(stack)) return false;
        if (hasCustomName(stack)) return false;
        if (isDamaged(stack)) return false;
        return true;
    }

    /** True if the stack has any (non-empty) enchantment. */
    private static boolean isEnchanted(ItemStack stack) {
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) return true;
        // Stored enchantments live on enchanted books.
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        return stored != null && !stored.isEmpty();
    }

    /** True if the stack has been given a custom name (anvil/rename tag). */
    private static boolean hasCustomName(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_NAME);
    }

    /** True if the stack is a damageable item below 100% durability. */
    private static boolean isDamaged(ItemStack stack) {
        return stack.isDamageableItem() && stack.getDamageValue() > 0;
    }

    private SellFilter() {}
}
