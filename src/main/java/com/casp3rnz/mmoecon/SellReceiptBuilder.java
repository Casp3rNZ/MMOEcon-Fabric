package com.casp3rnz.mmoecon;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the raw stacks a sale removed into a {@link SellReceipt} for the receipt
 * GUI.
 * Two passes:
 *   1. merge - combine entries whose stacks are the same item AND same components
 *      (ItemStack.isSameItemSameComponents), so several partial stacks of plain
 *      cobblestone become one running count.
 *   2. split - re-emit each merged entry as natural max-size stacks, so a merged
 *      count of 3,456 stone becomes 54 display stacks of 64.
 */
public final class SellReceiptBuilder {

    /** One item type sold, at a unit price. The stack's count is the full amount sold. */
    public record Entry(ItemStack soldStack, long unitPrice) {}

    public static SellReceipt build(List<Entry> entries) {
        // Pass 1: merge same-item-same-components entries, preserving first-seen order.
        List<Entry> merged = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.soldStack().isEmpty()) continue;
            Entry existing = null;
            for (Entry m : merged) {
                if (ItemStack.isSameItemSameComponents(m.soldStack(), entry.soldStack())) {
                    existing = m;
                    break;
                }
            }
            if (existing == null) {
                merged.add(new Entry(entry.soldStack().copy(), entry.unitPrice()));
            } else {
                existing.soldStack().grow(entry.soldStack().getCount());
            }
        }

        // Pass 2: split each merged entry into natural max-size display stacks and accumulate the exact totals.
        List<SellReceipt.Line> lines = new ArrayList<>();
        long total = 0L;
        int itemCount = 0;

        for (Entry entry : merged) {
            ItemStack stack = entry.soldStack();
            long unitPrice = entry.unitPrice();

            int remaining = stack.getCount();
            int maxStack = stack.getMaxStackSize();
            itemCount += remaining;
            total += Money.multiply(unitPrice, remaining);

            while (remaining > 0) {
                int chunk = Math.min(remaining, maxStack);
                ItemStack display = stack.copy();
                display.setCount(chunk);
                lines.add(new SellReceipt.Line(display, unitPrice, Money.multiply(unitPrice, chunk)));
                remaining -= chunk;
            }
        }

        return new SellReceipt(lines, total, itemCount, System.currentTimeMillis());
    }

    private SellReceiptBuilder() {}
}
