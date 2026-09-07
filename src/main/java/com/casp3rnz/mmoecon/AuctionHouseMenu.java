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
import java.util.UUID;

/**
 * Server-side container menu for the auction house GUI.
 * The player-to-player counterpart to {@link ShopMenu}.
 */
public final class AuctionHouseMenu extends AbstractContainerMenu {

    private static final int CONTENT_SLOTS = 45;

    // Nav bar slots (bottom row of the 54-slot chest)
    private static final int NAV_MINE      = 45; // browse → my listings toggle
    private static final int NAV_BROWSE    = 45; // my listings → browse toggle (same slot, different view)
    private static final int NAV_PREV_PAGE = 48;
    private static final int NAV_INFO      = 49; // balance / listing-count icon
    private static final int NAV_NEXT_PAGE = 50;

    // Confirm-screen slots (within the 54-slot chest)
    private static final int CONFIRM_ITEM    = 13; // the listing's item, centred
    private static final int CONFIRM_ACCEPT  = 29; // green pane
    private static final int CONFIRM_CANCEL  = 33; // red pane

    private final Container menuInventory = new SimpleContainer(54);
    private final PlayerAuctionHouseSession session;
    private final ServerPlayer player;

    public AuctionHouseMenu(int syncId, Inventory playerInventory, PlayerAuctionHouseSession session) {
        super(MenuType.GENERIC_9x6, syncId);
        this.session = session;
        this.player  = (ServerPlayer) playerInventory.player;

        // Auction inventory slots (top 54) — display only, no pickup/placement.
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9;
                this.addSlot(new Slot(menuInventory, index, 8 + col * 18, 18 + row * 18) {
                    @Override public boolean mayPickup(Player p) { return false; }
                    @Override public boolean mayPlace(ItemStack s) { return false; }
                });
            }
        }

        // Player inventory + hotbar (unchanged from the shop layout).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }

        populateView();
    }

    // AbstractContainerMenu overrides

    @Override
    public boolean stillValid(Player player) {
        return true; // no proximity requirement, like the shop
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // disable shift-click
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Only clicks in the top (menu) container matter; ignore player-inventory clicks.
        if (slotId < 0 || slotId >= menuInventory.getContainerSize()) return;
        playClickSound();
        routeClick(slotId, button);
    }

    // Click routing

    private void routeClick(int slot, int button) {
        switch (session.view) {
            case ITEM_LIST           -> handleBrowseClick(slot);
            case OWN_ITEM_LIST       -> handleMineClick(slot);
            case CONFIRM_SALE        -> handleConfirmSaleClick(slot);
            case CONFIRM_DELIST_ITEM -> handleConfirmDelistClick(slot);
        }
    }

    // View: ITEM_LIST (browse & buy)

    private void handleBrowseClick(int slot) {
        List<AuctionListing> listings = AuctionHouseManager.getActiveListings();

        switch (slot) {
            case NAV_MINE -> { session.resetTo(AuctionHouseViews.OWN_ITEM_LIST); populateView(); return; }
            case NAV_PREV_PAGE -> { if (session.currentPage > 0) { session.currentPage--; populateView(); } return; }
            case NAV_NEXT_PAGE -> {
                if ((session.currentPage + 1) * CONTENT_SLOTS < listings.size()) {
                    session.currentPage++; populateView();
                }
                return;
            }
            default -> { /* fall through to content-area handling */ }
        }
        if (slot >= CONTENT_SLOTS) return;

        int index = slot + session.currentPage * CONTENT_SLOTS;
        if (index >= listings.size()) return;

        session.pendingListingId = listings.get(index).id();
        session.navigateTo(AuctionHouseViews.CONFIRM_SALE);
        populateView();
    }

    // View: OWN_ITEM_LIST (manage own listings)

    private void handleMineClick(int slot) {
        List<AuctionListing> mine = AuctionHouseManager.getListingsBy(player.getUUID());

        switch (slot) {
            case NAV_BROWSE -> { session.resetTo(AuctionHouseViews.ITEM_LIST); populateView(); return; }
            case NAV_PREV_PAGE -> { if (session.currentPage > 0) { session.currentPage--; populateView(); } return; }
            case NAV_NEXT_PAGE -> {
                if ((session.currentPage + 1) * CONTENT_SLOTS < mine.size()) {
                    session.currentPage++; populateView();
                }
                return;
            }
            default -> { /* content area below */ }
        }
        if (slot >= CONTENT_SLOTS) return;

        int index = slot + session.currentPage * CONTENT_SLOTS;
        if (index >= mine.size()) return;

        session.pendingListingId = mine.get(index).id();
        session.navigateTo(AuctionHouseViews.CONFIRM_DELIST_ITEM);
        populateView();
    }

    // View: CONFIRM_SALE (buy)

    private void handleConfirmSaleClick(int slot) {
        if (slot == CONFIRM_CANCEL) {
            session.navigateTo(AuctionHouseViews.ITEM_LIST);
            populateView();
            return;
        }
        if (slot != CONFIRM_ACCEPT) return;

        AuctionHouseManager.BuyResult result = AuctionHouseManager.buy(player, session.pendingListingId);
        switch (result) {
            case SUCCESS -> {
                player.sendSystemMessage(Messages.success("Purchase complete."));
                playTransactionSound();
            }
            case GONE -> player.sendSystemMessage(Messages.error("That listing is no longer available."));
            case OWN_LISTING -> player.sendSystemMessage(Messages.error("You can't buy your own listing."));
            case INSUFFICIENT_FUNDS -> player.sendSystemMessage(Messages.error("You can't afford that."));
            case NO_ROOM -> player.sendSystemMessage(Messages.error("You don't have room for that."));
        }

        session.pendingListingId = null;
        session.navigateTo(AuctionHouseViews.ITEM_LIST);
        populateView();
    }

    // View: CONFIRM_DELIST_ITEM (reclaim own listing)

    private void handleConfirmDelistClick(int slot) {
        if (slot == CONFIRM_CANCEL) {
            session.navigateTo(AuctionHouseViews.OWN_ITEM_LIST);
            populateView();
            return;
        }
        if (slot != CONFIRM_ACCEPT) return;

        boolean ok = AuctionHouseManager.delist(player, session.pendingListingId);
        if (ok) {
            player.sendSystemMessage(Messages.success("Listing removed, item returned to you."));
            playTransactionSound();
        } else {
            player.sendSystemMessage(Messages.error("That listing has been purchased."));
        }

        session.pendingListingId = null;
        session.navigateTo(AuctionHouseViews.OWN_ITEM_LIST);
        populateView();
    }

    // View population

    private void populateView() {
        clearMenuInventory();
        switch (session.view) {
            case ITEM_LIST           -> populateBrowse();
            case OWN_ITEM_LIST       -> populateMine();
            case CONFIRM_SALE        -> populateConfirm(false);
            case CONFIRM_DELIST_ITEM -> populateConfirm(true);
        }
        broadcastChanges();
    }

    private void populateBrowse() {
        List<AuctionListing> listings = AuctionHouseManager.getActiveListings();
        int pageStart = session.currentPage * CONTENT_SLOTS;

        for (int i = 0; i < CONTENT_SLOTS; i++) {
            int index = pageStart + i;
            if (index >= listings.size()) break;
            menuInventory.setItem(i, listingDisplay(listings.get(index), false));
        }

        boolean empty = listings.isEmpty();
        if (empty) {
            menuInventory.setItem(22, namedStack(Items.COBWEB, "§7No listings yet"));
        }

        // Nav bar
        menuInventory.setItem(NAV_MINE, namedStack(Items.CHEST, "§eMy Listings →"));
        setPager(listings.size());
        setInfoIcon();
    }

    private void populateMine() {
        List<AuctionListing> mine = AuctionHouseManager.getListingsBy(player.getUUID());
        int pageStart = session.currentPage * CONTENT_SLOTS;

        for (int i = 0; i < CONTENT_SLOTS; i++) {
            int index = pageStart + i;
            if (index >= mine.size()) break;
            menuInventory.setItem(i, listingDisplay(mine.get(index), true));
        }

        if (mine.isEmpty()) {
            menuInventory.setItem(22, namedStack(Items.COBWEB,
                    "§7You have no listings. use §f/ah sell <price> [quantity]"));
        }

        menuInventory.setItem(NAV_BROWSE, namedStack(Items.COMPASS, "§e← Browse All"));
        setPager(mine.size());
        setInfoIcon();
    }

    private void populateConfirm(boolean delist) {
        AuctionListing listing = session.pendingListingId == null
                ? null : AuctionHouseManager.get(session.pendingListingId);

        // The listing can vanish between selecting it and this screen rendering
        // (someone else bought it). Bail back to the list rather than showing air.
        if (listing == null) {
            session.navigateTo(delist ? AuctionHouseViews.OWN_ITEM_LIST : AuctionHouseViews.ITEM_LIST);
            populateView();
            return;
        }

        menuInventory.setItem(CONFIRM_ITEM, listingDisplay(listing, delist));

        if (delist) {
            menuInventory.setItem(CONFIRM_ACCEPT, namedStack(Items.LIME_STAINED_GLASS_PANE,
                    "§aReclaim item"));
            menuInventory.setItem(CONFIRM_CANCEL, namedStack(Items.RED_STAINED_GLASS_PANE,
                    "§cCancel"));
        } else {
            menuInventory.setItem(CONFIRM_ACCEPT, namedStack(Items.LIME_STAINED_GLASS_PANE,
                    "§aBuy for $" + Money.format(listing.price())));
            menuInventory.setItem(CONFIRM_CANCEL, namedStack(Items.RED_STAINED_GLASS_PANE,
                    "§cCancel"));
        }
        setInfoIcon();
    }

    // Display helpers

    /**
     * A display copy of the listing's item, annotated with price/quantity/seller lore.
     */
    private ItemStack listingDisplay(AuctionListing listing, boolean owned) {
        ItemStack stack = listing.unitStack().copy();
        stack.setCount(Math.min(listing.quantity(), listing.maxStackSize()));

        List<Component> lore = new ArrayList<>();
        lore.add(line("§7Price: §a$" + Money.format(listing.price())));
        lore.add(line("§7Quantity: §f" + listing.quantity()));
        if (owned) {
            lore.add(line("§7Your listing"));
            lore.add(line("§eClick to reclaim"));
        } else {
            lore.add(line("§7Seller: §f" + listing.sellerName()));
            lore.add(line("§aClick to buy"));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private void setPager(int total) {
        boolean hasPrev = session.currentPage > 0;
        boolean hasNext = (session.currentPage + 1) * CONTENT_SLOTS < total;
        if (hasPrev) menuInventory.setItem(NAV_PREV_PAGE, namedStack(Items.ARROW, "§e← Previous page"));
        if (hasNext) menuInventory.setItem(NAV_NEXT_PAGE, namedStack(Items.ARROW, "§eNext page →"));
    }

    private void setInfoIcon() {
        long balance = PlayerBalanceManager.getBalance(player.getUUID());
        ItemStack info = new ItemStack(Items.LANTERN);
        info.set(DataComponents.CUSTOM_NAME,
                Component.literal("§6Auction House").withStyle(s -> s.withItalic(false)));
        List<Component> lore = new ArrayList<>();
        lore.add(line("§7Your Balance: §a$" + Money.format(balance)));
        lore.add(line("§7Active listings: §f" + AuctionHouseManager.activeCount()));
        info.set(DataComponents.LORE, new ItemLore(lore));
        menuInventory.setItem(NAV_INFO, info);
    }

    private void clearMenuInventory() {
        for (int i = 0; i < menuInventory.getContainerSize(); i++) {
            menuInventory.setItem(i, ItemStack.EMPTY);
        }
    }

    // Utility

    private static Component line(String text) {
        return Component.literal(text).withStyle(s -> s.withItalic(false));
    }

    private static ItemStack namedStack(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false)));
        return stack;
    }

    private void playClickSound() {
        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f,
                0.5f + player.getRandom().nextFloat());
    }

    private void playTransactionSound() {
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.0f);
    }
}
