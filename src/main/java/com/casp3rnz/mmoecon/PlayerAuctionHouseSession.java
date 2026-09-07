package com.casp3rnz.mmoecon;

import java.util.UUID;

/**
 * Holds all per-player auction house session state — the mirror of
 * {@link PlayerShopSession} for the auction GUI.
 *
 * <p>A pending action targets a listing by its {@code id} rather than by a stored
 * {@link AuctionListing} reference, so a confirm screen always re-resolves the
 * live listing at click time: if it sold or was delisted while the player sat on
 * the confirm view, the action fails cleanly instead of acting on stale data.
 */
public class PlayerAuctionHouseSession {

    /** Which screen the player is on. */
    public AuctionHouseViews view = AuctionHouseViews.ITEM_LIST;

    /** Current page within the active list view. */
    public int currentPage = 0;

    /** The listing a confirm screen is about, addressed by id (see class doc). */
    public UUID pendingListingId = null;

    public void navigateTo(AuctionHouseViews newView) {
        this.view = newView;
    }

    /** Resets paging/selection when switching between the browse and "mine" tabs. */
    public void resetTo(AuctionHouseViews newView) {
        this.view = newView;
        this.currentPage = 0;
        this.pendingListingId = null;
    }
}
