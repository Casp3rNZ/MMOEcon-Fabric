package com.casp3rnz.mmoecon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * 6-row chest GUI that shows the items from a single {@link SellReceipt}, and lets
 * the player refund an individual sold stack.
 * Opened by the click to view chat button (via /sell receipt).
 *
 * Two view modes share the same underlying container:
 *   VIEWING  — paged list of sold stacks + nav bar. Clicking a line opens the
 *              confirm screen for that line.
 *   CONFIRM  — a single-line "refund this stack?" prompt with confirm / cancel
 *              buttons and the item shown in the centre.
 *
 * A refund gives the stack back (dropping any overflow at the player's feet) and
 * subtracts the line's payout from the player's balance. It is refused if the
 * player can no longer afford to give the money back. Refunded lines are tracked
 * on the receipt itself, so reopening the GUI can't refund the same stack twice.
 */
public final class SellReceiptMenu extends AbstractContainerMenu {

    private enum Mode { VIEWING, CONFIRM }

    private static final int PAGE_SIZE = 45;

    // VIEWING nav-bar slots (bottom row of the 54-slot display).
    private static final int NAV_CLOSE     = 45;
    private static final int NAV_PREV_PAGE = 48;
    private static final int NAV_SUMMARY   = 49;
    private static final int NAV_NEXT_PAGE = 50;

    // CONFIRM screen slots.
    private static final int CONFIRM_ITEM   = 22; // centre of the grid
    private static final int CONFIRM_YES    = 33;
    private static final int CONFIRM_NO      = 29;

    private final Container display = new SimpleContainer(54);
    private final ServerPlayer player;
    private final SellReceipt receipt;
    private int page = 0;

    private Mode mode = Mode.VIEWING;
    /** Absolute receipt-line index being confirmed; valid only in CONFIRM mode. */
    private int confirmLineIndex = -1;

    public SellReceiptMenu(int syncId, Inventory playerInventory, SellReceipt receipt) {
        super(MenuType.GENERIC_9x6, syncId);
        this.player = (ServerPlayer) playerInventory.player;
        this.receipt = receipt;

        // Display slots (top 54) — locked, players can't take from a receipt.
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9;
                this.addSlot(new Slot(display, index, 8 + col * 18, 18 + row * 18) {
                    @Override public boolean mayPickup(Player p) { return false; }
                    @Override public boolean mayPlace(ItemStack s) { return false; }
                });
            }
        }

        // Player inventory + hotbar (standard positions).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }

        populate();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // disable shift-click
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Only react to clicks in the display area; ignore the player's own inventory.
        if (slotId < 0 || slotId >= display.getContainerSize()) return;

        playClickSound();

        if (mode == Mode.CONFIRM) {
            handleConfirmClick(slotId);
            return;
        }

        // VIEWING mode.
        switch (slotId) {
            case NAV_CLOSE -> this.player.closeContainer();
            case NAV_PREV_PAGE -> {
                if (page > 0) { page--; populate(); }
            }
            case NAV_NEXT_PAGE -> {
                if (page < maxPage()) { page++; populate(); }
            }
            case NAV_SUMMARY -> { /* summary tile is informational only */ }
            default -> {
                // A line-icon slot (0..PAGE_SIZE-1). Map to an absolute line index.
                int lineIndex = page * PAGE_SIZE + slotId;
                if (slotId < PAGE_SIZE && lineIndex < receipt.lines().size()
                        && !receipt.isRefunded(lineIndex)) {
                    openConfirm(lineIndex);
                }
            }
        }
    }

    // Confirm screen

    private void handleConfirmClick(int slotId) {
        switch (slotId) {
            case CONFIRM_YES -> doRefund(confirmLineIndex);
            case CONFIRM_NO, NAV_CLOSE -> backToViewing();
            default -> { /* other slots on the confirm screen are inert */ }
        }
    }

    private void openConfirm(int lineIndex) {
        this.mode = Mode.CONFIRM;
        this.confirmLineIndex = lineIndex;
        populateConfirm();
    }

    private void backToViewing() {
        this.mode = Mode.VIEWING;
        this.confirmLineIndex = -1;
        populate();
    }

    /**
     * Performs the refund for a line: refuses if the line is already refunded or
     * the player can't afford to return the money, otherwise subtracts the payout,
     * gives the stack back (dropping overflow at the player's feet) and marks the
     * line refunded. Always returns the player to the VIEWING screen.
     */
    private void doRefund(int lineIndex) {
        if (lineIndex < 0 || lineIndex >= receipt.lines().size() || receipt.isRefunded(lineIndex)) {
            backToViewing();
            return;
        }

        SellReceipt.Line line = receipt.lines().get(lineIndex);
        long refundCost = line.lineTotal();

        // "Refuse if too poor": the player must currently hold at least the payout.
        // Messages.money() ends in body grey, so re-assert error red (§c) after each.
        if (!PlayerBalanceManager.hasFunds(player.getUUID(), refundCost)) {
            long balance = PlayerBalanceManager.getBalance(player.getUUID());
            player.sendSystemMessage(Messages.error(
                    "You can't afford to refund that. It would cost "
                            + Messages.money(refundCost) + "§c and you have "
                            + Messages.money(balance) + "§c."));
            player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1.0f);
            backToViewing();
            return;
        }

        // Claim the line first so a double-click can't refund it twice.
        if (!receipt.markRefunded(lineIndex)) {
            backToViewing();
            return;
        }

        PlayerBalanceManager.subtractBalance(player.getUUID(), refundCost);

        // Give the item back; drop whatever doesn't fit at the player's feet.
        ItemStack refundStack = line.stack().copy();
        boolean fullyAdded = player.getInventory().add(refundStack);
        if (!fullyAdded && !refundStack.isEmpty()) {
            // add() mutates refundStack down to the leftover count on partial success.
            player.drop(refundStack, false);
        }

        String itemName = line.stack().getHoverName().getString();
        int qty = line.stack().getCount();

        player.sendSystemMessage(Messages.body(
                "Refunded " + Messages.item(qty + "x " + itemName)
                        + " for " + Messages.money(refundCost) + "."));
        TransactionLogger.log(player.getName().getString() + " refunded " + qty + " " + itemName
                + " for $" + ShopMenu.formatMoney(refundCost));

        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 0.8f);

        backToViewing();
    }

    // View population

    private void populate() {
        clearDisplay();

        List<SellReceipt.Line> lines = receipt.lines();
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int lineIndex = start + i;
            if (lineIndex >= lines.size()) break;
            display.setItem(i, buildLineIcon(lineIndex));
        }

        // Nav bar
        display.setItem(NAV_CLOSE, namedStack(Items.BARRIER, "§cClose"));
        if (page > 0) {
            display.setItem(NAV_PREV_PAGE, namedStack(Items.ARROW, "§e← Previous page"));
        }
        if (page < maxPage()) {
            display.setItem(NAV_NEXT_PAGE, namedStack(Items.ARROW, "§eNext page →"));
        }
        display.setItem(NAV_SUMMARY, buildSummary());

        broadcastChanges();
    }

    /** Lays out the single-line "refund this stack?" confirmation screen. */
    private void populateConfirm() {
        clearDisplay();

        SellReceipt.Line line = receipt.lines().get(confirmLineIndex);

        // The stack in question, with a "refund value" note.
        ItemStack item = line.stack().copy();
        ItemLore existing = item.get(DataComponents.LORE);
        List<Component> lore = new ArrayList<>(existing == null ? List.of() : existing.lines());
        lore.add(Component.literal("§7Refund value: §a$" + Money.format(line.lineTotal()))
                .withStyle(s -> s.withItalic(false)));
        item.set(DataComponents.LORE, new ItemLore(lore));
        display.setItem(CONFIRM_ITEM, item);

        // Confirm / cancel buttons.
        ItemStack yes = namedStack(Items.LIME_WOOL, "§a§lRefund this stack");
        yes.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("§7Returns the item and takes back")
                        .withStyle(s -> s.withItalic(false)),
                Component.literal("§7§a$" + Money.format(line.lineTotal()) + "§7 from your balance.")
                        .withStyle(s -> s.withItalic(false)))));
        display.setItem(CONFIRM_YES, yes);

        display.setItem(CONFIRM_NO, namedStack(Items.RED_WOOL, "§c§lCancel"));

        broadcastChanges();
    }

    /**
     * The sold stack itself, shown as-is — real item, count, enchantment glint,
     * custom name and any existing lore are all preserved. Price lines are
     * appended below whatever lore the item already carries, so a renamed or
     * enchanted item still reads correctly. Refunded lines render as a greyed-out
     * barrier so the receipt still reads as a record of the whole sale.
     */
    private ItemStack buildLineIcon(int lineIndex) {
        SellReceipt.Line line = receipt.lines().get(lineIndex);

        if (receipt.isRefunded(lineIndex)) {
            ItemStack refunded = namedStack(Items.BARRIER,
                    "§7§m" + line.stack().getHoverName().getString());
            refunded.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("§cRefunded").withStyle(s -> s.withItalic(false)))));
            return refunded;
        }

        ItemStack stack = line.stack().copy();

        // Append price lore beneath the item's own lore, if any.
        ItemLore existing = stack.get(DataComponents.LORE);
        List<Component> lore = new ArrayList<>(existing == null ? List.of() : existing.lines());
        lore.add(Component.literal("§7Each: §a$" + Money.format(line.unitPrice()))
                .withStyle(s -> s.withItalic(false)));
        lore.add(Component.literal("§7Total: §a$" + Money.format(line.lineTotal()))
                .withStyle(s -> s.withItalic(false)));
        lore.add(Component.literal("")
                .withStyle(s -> s.withItalic(false)));
        lore.add(Component.literal("§eClick to refund this stack.")
                .withStyle(s -> s.withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(lore));

        return stack;
    }

    /** Grand-total tile shown in the middle of the nav bar. */
    private ItemStack buildSummary() {
        ItemStack stack = new ItemStack(Items.GOLD_INGOT);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("§6§lReceipt of last Sale")
                        .withStyle(s -> s.withItalic(false)));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("§7Items sold: §f" + receipt.itemCount())
                .withStyle(s -> s.withItalic(false)));
        lore.add(Component.literal("§7Total earned: §a$" + Money.format(receipt.total()))
                .withStyle(s -> s.withItalic(false)));

        // Show what's left after any refunds, but only once something has been refunded.
        if (receipt.remainingItemCount() != receipt.itemCount()) {
            lore.add(Component.literal("§7After refunds: §f" + receipt.remainingItemCount()
                            + " §7items, §a$" + Money.format(receipt.remainingTotal()))
                    .withStyle(s -> s.withItalic(false)));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));

        return stack;
    }

    // Helpers

    private int maxPage() {
        return Math.max(0, (receipt.lines().size() - 1) / PAGE_SIZE);
    }

    private void clearDisplay() {
        for (int i = 0; i < display.getContainerSize(); i++) {
            display.setItem(i, ItemStack.EMPTY);
        }
    }

    private static ItemStack namedStack(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(name).withStyle(s -> s.withItalic(false)));
        return stack;
    }

    private void playClickSound() {
        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f,
                0.5f + player.getRandom().nextFloat());
    }
}
