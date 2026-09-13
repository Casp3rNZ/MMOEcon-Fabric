package com.casp3rnz.mmoecon;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A snapshot of a single completed sale, used to render the "what did I sell?"
 * GUI opened from the view receipt chat button.
 * Lines can be individually refunded from the receipt GUI.
 * Refunded lines are tracked on the receipt itself rather than on the menu,
 * so the state survives closing and reopening the GUI, and a player can't
 * reopen a receipt to refund the same stack twice.
 */
public record SellReceipt(List<Line> lines, long total, int itemCount, long createdAtEpochMs,
                          Set<Integer> refundedLineIndices) {

    public SellReceipt(List<Line> lines, long total, int itemCount, long createdAtEpochMs) {
        this(lines, total, itemCount, createdAtEpochMs, ConcurrentHashMap.newKeySet());
    }

    /** True if the line at this index has already been refunded. */
    public boolean isRefunded(int lineIndex) {
        return refundedLineIndices.contains(lineIndex);
    }

    /** Marks the line at this index as refunded. Returns false if it already was. */
    public boolean markRefunded(int lineIndex) {
        return refundedLineIndices.add(lineIndex);
    }

    /** Total value of all lines that have NOT been refunded, in cents. */
    public long remainingTotal() {
        long remaining = 0L;
        for (int i = 0; i < lines.size(); i++) {
            if (!isRefunded(i)) remaining += lines.get(i).lineTotal();
        }
        return remaining;
    }

    /** Count of items across all lines that have NOT been refunded. */
    public int remainingItemCount() {
        int remaining = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (!isRefunded(i)) remaining += lines.get(i).stack().getCount();
        }
        return remaining;
    }

    /**
     * One display stack within a sale.
     *
     * @param stack     a copy of the sold item, its count set to how many this
     *                  stack shows (already clamped to the item's max stack size)
     * @param unitPrice sell price per item, in cents
     * @param lineTotal stack.getCount() * unitPrice, in cents
     */
    public record Line(ItemStack stack, long unitPrice, long lineTotal) {}
}
