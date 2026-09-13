package com.casp3rnz.mmoecon;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles the sell wand flow on right-click of a block.
 *
 * Ported from NeoForge's {@code PlayerInteractEvent.RightClickBlock} to Fabric's
 * {@link net.fabricmc.fabric.api.event.player.UseBlockCallback}. The container is
 * read through Fabric's Transfer API ({@link Storage}&lt;{@link ItemVariant}&gt;)
 * instead of NeoForge's {@code IItemHandler} capability:
 *  * The Transfer API path (via {@code ItemStorage.SIDED}) covers drawer/storage
 *    mods and most modern modded storage, and expresses slots holding more than
 *    a single stack — which vanilla Container cannot.
 *  * The Container fallback (wrapped with {@link InventoryStorage#of}) covers
 *    minecraft:chest, barrel, trapped_chest, shulker_box and older modded chests,
 *    including double-chest pairing.
 *
 * Flow:
 *   1st right-click: scan container, build preview, store PendingSale
 *   2nd right-click on same block within the window: execute sale
 *   right-click a different block, or timeout: clear pending, start fresh
 */
public final class SellWandListener {

    /**
     * Registered from {@link MMOEcon} onto {@code UseBlockCallback.EVENT}.
     *
     * Returns {@link InteractionResult#SUCCESS} to consume the interaction (so the
     * container GUI doesn't also open) whenever the player is holding a wand, and
     * {@link InteractionResult#PASS} otherwise to let vanilla behaviour continue.
     */
    public static InteractionResult onUseBlock(Player p, Level level, InteractionHand hand, BlockHitResult hit) {
        // Server-side only; the wand's effects are all server-authoritative.
        if (!(p instanceof ServerPlayer player)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        // Only act on the main hand to avoid firing twice (main + off hand) per click.
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        ItemStack held = player.getItemInHand(hand);
        if (!SellWand.isWand(held)) return InteractionResult.PASS;

        BlockPos pos = hit.getBlockPos();

        // From here we've committed to handling the click — consume it either way.
        Storage<ItemVariant> storage = getItemStorage(serverLevel, pos);
        if (storage == null) {
            player.sendSystemMessage(Messages.error("That isn't a container you can sell from."));
            SellWand.clearPending(player.getUUID());
            return InteractionResult.SUCCESS;
        }

        UUID uuid = player.getUUID();

        // Second click: confirm
        if (SellWand.hasPending(uuid)) {
            SellWand.PendingSale pending = SellWand.getPending(uuid);

            // Must be the same block — clicking a different chest resets
            if (!pending.blockPos().equals(pos)) {
                SellWand.clearPending(uuid);
                previewContainer(player, storage, pos);
                return InteractionResult.SUCCESS;
            }

            // Execute the sale
            executeSale(player, storage, pending);
            SellWand.clearPending(uuid);
            return InteractionResult.SUCCESS;
        }

        // First click: preview
        previewContainer(player, storage, pos);
        return InteractionResult.SUCCESS;
    }

    // Preview
    private static void previewContainer(ServerPlayer player, Storage<ItemVariant> storage, BlockPos pos) {
        SaleResult preview = calculateSale(storage);

        if (preview.totalItems == 0) {
            player.sendSystemMessage(Messages.error("Nothing in there can be sold."));
            return;
        }

        // Store pending sale
        long now = player.serverLevel().getServer().getTickCount();
        SellWand.setPending(player.getUUID(), new SellWand.PendingSale(
                pos, preview.totalEarned, preview.totalItems, now));

        // Send preview message. The window is derived from the timeout constant so
        // the two can't drift apart if it's ever retuned.
        long seconds = SellWand.CONFIRM_TIMEOUT_TICKS / 20L;
        player.sendSystemMessage(Messages.body(
                "Found " + Messages.item(preview.totalItems + " sellable items")
                        + " worth " + Messages.money(preview.totalEarned) + "."));
        player.sendSystemMessage(Messages.body(
                "Right-click again within " + Messages.item(seconds + "s") + " to confirm."));

        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.5f, 1.2f);
    }

    // Execute Sale

    private static void executeSale(ServerPlayer player, Storage<ItemVariant> storage, SellWand.PendingSale pending) {
        // Recalculate at execution time in case contents changed between clicks
        SaleResult planned = calculateSale(storage);

        if (planned.totalItems == 0) {
            player.sendSystemMessage(Messages.error("The container's contents changed, nothing was sold."));
            return;
        }

        // Pay for what actually came out, not what the scan predicted
        SaleResult actual = removeItems(storage, planned.slots);

        if (actual.totalItems == 0) {
            player.sendSystemMessage(Messages.error("Nothing could be removed from that container."));
            return;
        }

        PlayerBalanceManager.addBalance(player.getUUID(), actual.totalEarned);

        SellReceiptStore.put(player.getUUID(), buildReceipt(actual));

        player.sendSystemMessage(Messages.body(
                "Sold " + Messages.item(actual.totalItems + " items")
                        + " for " + Messages.money(actual.totalEarned) + ".")
                .copy().append(SellCommand.receiptButton()));

        TransactionLogger.log(player.getName().getString()
                + " used sell wand at " + pending.blockPos()
                + " — sold " + actual.totalItems + " items for $"
                + ShopMenu.formatMoney(actual.totalEarned));

        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.0f);
    }

    // Helpers

    /**
     * Scans a container and returns a SaleResult with total value and the priced
     * resources found. Does NOT modify the container: extraction is simulated
     * inside a transaction that is always aborted.
     *
     * Only what the storage will actually hand over is counted. Drawer mods keep a
     * locked/"protected" stack that can't be extracted; simulating the extract (as
     * opposed to reading the raw amount) avoids paying the player for items the
     * sale can't remove.
     */
    private static SaleResult calculateSale(Storage<ItemVariant> storage) {
        long totalEarned = 0L;
        int totalItems = 0;
        List<SlotSale> slots = new ArrayList<>();

        try (Transaction outer = Transaction.openOuter()) {
            for (StorageView<ItemVariant> view : storage.nonEmptyViews()) {
                ItemVariant variant = view.getResource();
                if (variant.isBlank()) continue;

                ItemStack representative = variant.toStack();
                boolean isWand = SellWand.isWand(representative);

                // Skip enchanted / renamed / damaged items.
                // Only clean, full-durability stock items may be sold.
                // The wand is exempt (it's priced via "sell_wand").
                if (!SellFilter.isSellable(representative, isWand)) continue;

                ShopItemManager.ShopItem shopItem = isWand
                        ? ShopItemManager.findSpecial("sell_wand")
                        : ShopItemManager.findItem(BuiltInRegistries.ITEM.getKey(variant.getItem()).toString());
                if (shopItem == null || !shopItem.canSell()) continue;

                long available = view.getAmount();
                if (available <= 0) continue;

                // Simulate the extract in a nested transaction so this scan never
                // mutates the container.
                long extractable;
                try (Transaction sim = outer.openNested()) {
                    extractable = storage.extract(variant, available, sim);
                    // sim aborts on close (never committed)
                }
                if (extractable <= 0) continue;

                long unitPrice = shopItem.sellPrice();
                // Extractable is bounded by a stack of a real item, well within int range.
                int count = (int) Math.min(extractable, Integer.MAX_VALUE);
                long earned = Money.multiply(unitPrice, count);
                totalEarned += earned;
                totalItems += count;
                slots.add(new SlotSale(variant, count, unitPrice, earned));
            }
            // outer aborts on close — calculateSale is strictly read-only.
        }

        return new SaleResult(totalEarned, totalItems, slots);
    }

    /**
     * Resolves the inventory at the given position as a Transfer API Storage.
     * Order matters: the sided ItemStorage is queried first, because mods that
     * expose it (and drawer mods that expose only it) model their real contents
     * there — a vanilla Container view of a drawer would under-report a slot
     * holding thousands of items.
     * Falls back to wrapping the vanilla Container (which keeps double-chest
     * pairing working). Returns null if the block has no inventory at all.
     */
    private static Storage<ItemVariant> getItemStorage(ServerLevel level, BlockPos pos) {
        // Null side = the block's general/unsided inventory view.
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, null);
        if (storage != null) return storage;

        Container container = getContainer(level, pos);
        return container == null ? null : InventoryStorage.of(container, null);
    }

    /**
     * Returns the vanilla container at the given position, automatically combining
     * both halves if the block is a large (double) chest or trapped chest.
     * Falls back to the raw BlockEntity inventory for any other container.
     * Returns null if the block has no inventory.
     */
    private static Container getContainer(ServerLevel level, BlockPos pos) {

        BlockEntity be = level.getBlockEntity(pos);
        switch (be) {
            case null -> {
                return null;
            }

            // Handle double chests by checking for a neighbour chest and combining
            case ChestBlockEntity chest -> {
                BlockState state = level.getBlockState(pos);

                ChestType chestType = state.getValue(ChestBlock.TYPE);
                if (chestType != ChestType.SINGLE) {
                    Direction facing = state.getValue(ChestBlock.FACING);
                    Direction partnerDir = chestType == ChestType.RIGHT
                            ? facing.getCounterClockWise()
                            : facing.getClockWise();

                    BlockPos partnerPos = pos.relative(partnerDir);
                    BlockState partnerState = level.getBlockState(partnerPos);
                    BlockEntity partnerBe = level.getBlockEntity(partnerPos);

                    // Verify same block type AND opposite chest half — rules out adjacent unrelated chests
                    ChestType partnerType =
                            partnerState.hasProperty(ChestBlock.TYPE)
                                    ? partnerState.getValue(ChestBlock.TYPE)
                                    : ChestType.SINGLE;

                    boolean trulyPaired = partnerBe instanceof ChestBlockEntity
                            && partnerState.getBlock() == state.getBlock()
                            && partnerType != chestType  // opposite halves
                            && partnerType != ChestType.SINGLE;

                    if (trulyPaired) {
                        ChestBlockEntity partnerChest = (ChestBlockEntity) partnerBe;
                        if (chestType == ChestType.RIGHT) {
                            return new CompoundContainer(chest, partnerChest);
                        } else {
                            return new CompoundContainer(partnerChest, chest);
                        }
                    }
                }

                return chest;
            }
            case Container container -> {
                return container;
            }
            default -> {
            }
        }

        return null;
    }

    /**
     * Extracts the priced resources and returns what was actually removed.
     * Runs in a single committed transaction. If a storage hands back less than it
     * promised during the simulation, the shortfall is simply dropped from the
     * payout instead of trusting the earlier estimate — the player is paid for the
     * exact amount that left the container.
     */
    private static SaleResult removeItems(Storage<ItemVariant> storage, List<SlotSale> slots) {
        long earned = 0L;
        int removed = 0;
        List<SlotSale> actual = new ArrayList<>();

        try (Transaction tx = Transaction.openOuter()) {
            for (SlotSale slot : slots) {
                long taken = storage.extract(slot.variant(), slot.quantity(), tx);
                if (taken <= 0) continue;

                int count = (int) Math.min(taken, Integer.MAX_VALUE);
                long slotEarned = Money.multiply(slot.unitPrice(), count);

                earned += slotEarned;
                removed += count;
                actual.add(new SlotSale(slot.variant(), count, slot.unitPrice(), slotEarned));
            }
            tx.commit(); // apply the extractions for real
        }

        return new SaleResult(earned, removed, actual);
    }

    /**
     * Turns the executed sale's removed resources into a receipt. Each SlotSale
     * carries the ItemVariant and the exact amount that left the container, so the
     * real stacks (with components — enchantments, custom names, damage and all)
     * can be reconstructed for the receipt GUI.
     * variant.toStack() yields a single-count stack; the count is set explicitly
     * because it may exceed a natural stack size and the receipt builder re-splits
     * merged totals into natural stacks itself.
     */
    private static SellReceipt buildReceipt(SaleResult sale) {
        List<SellReceiptBuilder.Entry> entries = new ArrayList<>();
        for (SlotSale slot : sale.slots()) {
            ItemStack stack = slot.variant().toStack();
            stack.setCount(slot.quantity());
            entries.add(new SellReceiptBuilder.Entry(stack, slot.unitPrice()));
        }
        return SellReceiptBuilder.build(entries);
    }

    // Internal records

    private record SlotSale(ItemVariant variant, int quantity, long unitPrice, long earned) {}

    private record SaleResult(long totalEarned, int totalItems, List<SlotSale> slots) {}

    private SellWandListener() {}

}
