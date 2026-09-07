package com.casp3rnz.mmoecon;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of active auction listings, persisted to
 * config/mmoecon/auctions.dat.
 *
 * <p>Design mirrors {@link PlayerBalanceManager}: a concurrent map is the live
 * source of truth, a {@code dirty} flag drives a periodic flush plus a final
 * flush on shutdown, and writes go through a temp file + atomic move so a crash
 * mid-save can't corrupt the store.
 *
 * <p>Listed items are held in escrow here (the real {@link ItemStack} is removed
 * from the seller's inventory when they list).
 * A listing only ever leaves the store in one of two ways: bought or delisted,
 * and both hand the stack to exactly one player, so items can be neither duplicated nor lost.
 *
 * <p>Persistence uses NBT (via {@link ItemStack#save}) rather than the binary
 * format {@code balances.dat} uses. The file layout is identical to the NeoForge
 * build, so auctions.dat is cross-loader compatible.
 *
 * <p>This is the Fabric port of the NeoForge manager: where NeoForge subscribed
 * to server lifecycle events with {@code @SubscribeEvent} and read a stored
 * server ref, here the lifecycle entry points ({@link #load()},
 * {@link #flushTick(MinecraftServer)}, {@link #saveIfDirty()}) are called from
 * {@link MMOEcon}, and the running server is read from {@link ModServer}.
 */
public final class AuctionHouseManager {

    private static final Path AUCTION_FILE = ModPaths.config("auctions.dat");

    /** Insertion order preserved so the browse view is stable (newest last). */
    private static final ConcurrentHashMap<UUID, AuctionListing> listings = new ConcurrentHashMap<>();

    private static volatile boolean dirty = false;
    private static final long FLUSH_INTERVAL_TICKS = 1200L; // ~60s, matches balances

    // Results

    /** Outcome of a buy attempt, so the menu can show the right message. */
    public enum BuyResult { SUCCESS, GONE, OWN_LISTING, INSUFFICIENT_FUNDS, NO_ROOM }

    /** Outcome of a list attempt, so the /ah sell command can show the right message. */
    public enum ListResult { SUCCESS, AT_LISTING_CAP }

    // Public API

    /** All active listings, newest first, as a snapshot safe to page through. */
    public static List<AuctionListing> getActiveListings() {
        List<AuctionListing> all = new ArrayList<>(listings.values());
        all.sort((a, b) -> Long.compare(b.createdAtEpochMillis(), a.createdAtEpochMillis()));
        return all;
    }

    /** Listings owned by the given player, newest first. */
    public static List<AuctionListing> getListingsBy(UUID seller) {
        List<AuctionListing> mine = new ArrayList<>();
        for (AuctionListing listing : listings.values()) {
            if (listing.isSeller(seller)) mine.add(listing);
        }
        mine.sort((a, b) -> Long.compare(b.createdAtEpochMillis(), a.createdAtEpochMillis()));
        return mine;
    }

    public static AuctionListing get(UUID id) {
        return listings.get(id);
    }

    /** Number of active listings owned by the given player. */
    public static int countListingsBy(UUID seller) {
        int count = 0;
        for (AuctionListing listing : listings.values()) {
            if (listing.isSeller(seller)) count++;
        }
        return count;
    }

    /** True if the player is below the per-player active-listing cap. */
    public static boolean canList(UUID seller) {
        return countListingsBy(seller) < Config.MAX_AUCTION_QUANTITY.get();
    }

    public static int activeCount() {
        return listings.size();
    }

    /**
     * Creates a new listing of {@code quantity} of {@code unitStack}'s item type.
     * The caller is responsible for having already removed the items from the
     * seller's inventory — this stores exactly what it's handed.
     *
     * <p>Refuses (returning {@link ListResult#AT_LISTING_CAP}) if the seller is
     * already at the per-player cap. Callers should check {@link #canList} first so
     * they don't strip items from the inventory only to be refused here; this
     * re-check is the authoritative guard.
     */
    public static ListResult list(UUID seller, String sellerName, ItemStack unitStack, int quantity, long price) {
        if (!canList(seller)) return ListResult.AT_LISTING_CAP;

        AuctionListing listing = AuctionListing.create(seller, sellerName, unitStack, quantity, price);
        listings.put(listing.id(), listing);
        dirty = true;
        TransactionLogger.log(sellerName + " listed " + quantity + "x "
                + listing.displayName().getString() + " for $" + Money.format(price));
        return ListResult.SUCCESS;
    }

    /**
     * Attempts to buy the listing for {@code buyer}.
     * On success the buyer is charged, the seller credited,
     * the created stack is given to the buyer, and the listing removed.
     * Any failure leaves money, item and listing untouched.
     */
    public static BuyResult buy(ServerPlayer buyer, UUID listingId) {
        AuctionListing listing = listings.get(listingId);
        if (listing == null) return BuyResult.GONE;
        if (listing.isSeller(buyer.getUUID())) return BuyResult.OWN_LISTING;

        long price = listing.price();
        if (!PlayerBalanceManager.hasFunds(buyer.getUUID(), price)) {
            return BuyResult.INSUFFICIENT_FUNDS;
        }
        if (!hasRoomFor(buyer, listing)) {
            return BuyResult.NO_ROOM;
        }

        // Claim the listing first so two near-simultaneous buys can't both win.
        // remove() is atomic on the concurrent map; whoever gets the non-null
        // value owns the sale, the loser sees GONE.
        AuctionListing claimed = listings.remove(listingId);
        if (claimed == null) return BuyResult.GONE;
        dirty = true;

        PlayerBalanceManager.subtractBalance(buyer.getUUID(), price);
        PlayerBalanceManager.addBalance(listing.seller(), price);
        giveOrDrop(buyer, listing);

        String desc = listing.quantity() + "x " + listing.displayName().getString();
        TransactionLogger.log(buyer.getName().getString() + " bought " + desc
                + " from " + listing.sellerName() + " for $" + Money.format(price));

        // Let an online seller know their item sold.
        MinecraftServer server = ModServer.get();
        if (server != null) {
            ServerPlayer sellerOnline = server.getPlayerList().getPlayer(listing.seller());
            if (sellerOnline != null) {
                sellerOnline.sendSystemMessage(Messages.body(
                        Messages.item(buyer.getName().getString()) + " bought your "
                                + Messages.item(desc) + " for " + Messages.money(price) + "."));
            }
        }
        return BuyResult.SUCCESS;
    }

    /**
     * Removes the caller's own listing and returns the escrowed stack to them.
     * Returns false if the listing no longer exists or isn't theirs (e.g. it sold
     * between opening the menu and clicking).
     * The stack is only ever handed back to the verified owner.
     */
    public static boolean delist(ServerPlayer seller, UUID listingId) {
        AuctionListing listing = listings.get(listingId);
        if (listing == null || !listing.isSeller(seller.getUUID())) return false;

        AuctionListing claimed = listings.remove(listingId);
        if (claimed == null) return false;
        dirty = true;

        giveOrDrop(seller, claimed);
        TransactionLogger.log(seller.getName().getString() + " delisted "
                + claimed.quantity() + "x " + claimed.displayName().getString());
        return true;
    }

    // Inventory helpers

    /**
     * True if the buyer's inventory can hold the listing's full quantity, counting
     * both empty slots and room left in matching partial stacks. A listing can be
     * far larger than one stack (up to maxAuctionListingSize), so this measures
     * total capacity, not whether it fits in a single slot.
     */
    private static boolean hasRoomFor(ServerPlayer player, AuctionListing listing) {
        int needed = listing.quantity();
        int maxStack = listing.maxStackSize();
        ItemStack template = listing.unitStack();
        for (ItemStack slot : player.getInventory().items) {
            if (slot.isEmpty()) {
                needed -= maxStack;
            } else if (ItemStack.isSameItemSameComponents(slot, template)) {
                needed -= (maxStack - slot.getCount());
            }
            if (needed <= 0) return true;
        }
        return needed <= 0;
    }

    /**
     * Gives the listing's full quantity to the player, split into natural-sized
     * stacks, dropping any remainder that doesn't fit at their feet. Buys are
     * pre-checked with {@link #hasRoomFor}, but the drop fallback ensures a bought
     * or reclaimed item can never simply vanish.
     */
    private static void giveOrDrop(ServerPlayer player, AuctionListing listing) {
        int remaining = listing.quantity();
        int maxStack = listing.maxStackSize();
        while (remaining > 0) {
            int give = Math.min(remaining, maxStack);
            ItemStack chunk = listing.unitStack().copy();
            chunk.setCount(give);
            remaining -= give;

            boolean added = player.getInventory().add(chunk);
            if (!added || !chunk.isEmpty()) {
                player.drop(chunk, false);
            }
        }
    }

    // Persistence

    private static void load() {
        listings.clear();
        if (!Files.exists(AUCTION_FILE)) {
            MMOEcon.LOGGER.info("No auction file found, starting fresh.");
            return;
        }
        MinecraftServer server = ModServer.get();
        if (server == null) return;
        HolderLookup.Provider registries = server.registryAccess();
        try {
            CompoundTag root = NbtIo.readCompressed(AUCTION_FILE, NbtAccounter.unlimitedHeap());
            ListTag entries = root.getList("listings", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                AuctionListing listing = fromNbt(entry, registries);
                if (listing != null) listings.put(listing.id(), listing);
            }
            MMOEcon.LOGGER.info("Loaded {} auction listing(s).", listings.size());
        } catch (IOException e) {
            MMOEcon.LOGGER.error("Failed to load auction file from {}: {}", AUCTION_FILE, e.getMessage());
        }
    }

    private static void save() {
        MinecraftServer server = ModServer.get();
        if (server == null) return;
        // When the AH is disabled we never load(), so listings is empty — writing
        // it out would clobber a populated file (and the escrowed items with it).
        // Guard explicitly rather than relying on dirty never being set.
        if (!Config.ENABLE_AUCTION_HOUSE.get()) return;
        HolderLookup.Provider registries = server.registryAccess();
        try {
            Files.createDirectories(AUCTION_FILE.getParent());

            ListTag entries = new ListTag();
            for (AuctionListing listing : listings.values()) {
                entries.add(toNbt(listing, registries));
            }
            CompoundTag root = new CompoundTag();
            root.put("listings", entries);

            Path temp = AUCTION_FILE.resolveSibling(AUCTION_FILE.getFileName() + ".tmp");
            NbtIo.writeCompressed(root, temp);
            Files.move(temp, AUCTION_FILE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            dirty = false;
        } catch (IOException e) {
            MMOEcon.LOGGER.error("Failed to save auction file to {}: {}", AUCTION_FILE, e.getMessage());
        }
    }

    private static CompoundTag toNbt(AuctionListing listing, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", listing.id());
        tag.putUUID("seller", listing.seller());
        tag.putString("sellerName", listing.sellerName());
        tag.putLong("price", listing.price());
        tag.putInt("quantity", listing.quantity());
        tag.putLong("createdAt", listing.createdAtEpochMillis());
        // The template's count is always 1, so save() stays within the codec's
        // valid [1, 99] count range; the true total lives in "quantity" above.
        tag.put("item", listing.unitStack().save(registries));
        return tag;
    }

    private static AuctionListing fromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        try {
            UUID id = tag.getUUID("id");
            UUID seller = tag.getUUID("seller");
            String sellerName = tag.getString("sellerName");
            long price = tag.getLong("price");
            long createdAt = tag.getLong("createdAt");
            ItemStack unit = ItemStack.parse(registries, tag.getCompound("item")).orElse(ItemStack.EMPTY);
            // An item whose mod was removed since it was listed parses to empty;
            // dropping it here is better than serving an air listing nobody can buy.
            if (unit.isEmpty()) {
                MMOEcon.LOGGER.warn("Dropping auction listing {} whose item could not be loaded.", id);
                return null;
            }
            // Older files (before oversized listings) stored the count inside the
            // item and had no "quantity" field; fall back to that parsed count
            // before we normalize the template to a single item.
            int quantity = tag.contains("quantity")
                    ? Math.max(1, tag.getInt("quantity"))
                    : Math.max(1, unit.getCount());
            unit.setCount(1);
            return new AuctionListing(id, seller, sellerName, unit, quantity, price, createdAt);
        } catch (Exception e) {
            MMOEcon.LOGGER.warn("Skipping malformed auction listing: {}", e.getMessage());
            return null;
        }
    }

    // Lifecycle entry points (called from MMOEcon)

    /** Loads listings from disk. Called on server start, only if the AH is enabled. */
    public static void load(MinecraftServer server) {
        if (Config.ENABLE_AUCTION_HOUSE.get()) {
            load();
        }
    }

    /** Throttled periodic flush; call once per server tick. */
    public static void flushTick(MinecraftServer server) {
        if (dirty && server.getTickCount() % FLUSH_INTERVAL_TICKS == 0) {
            save();
        }
    }

    /** Force a save if there are unsaved changes; call on server stop. */
    public static void saveIfDirty() {
        if (dirty) save();
    }

    private AuctionHouseManager() {}
}
