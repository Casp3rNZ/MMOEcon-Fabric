package com.casp3rnz.mmoecon;

/**
 * Holds all per-player shop session state.
 */
public class PlayerShopSession {

    // Current View
    public ShopViews view = ShopViews.CATEGORY_LIST;
    public ShopItemManager.ShopCategory currentCategory = null;
    public int currentPage = 0;
    public ShopItemManager.ShopItem pendingShopItem = null;
    public boolean isSelling = false;
    public int quantity = 1;

    public void navigateTo(ShopViews newView) { this.view = newView; }

    public void resetQuantity() { this.quantity = 1; }

    public void adjustQuantity(int delta, int max) {
        quantity = Math.clamp(quantity + delta, 1, max);
    }

    /** Re-clamps the current quantity, e.g. after the player's capacity changed. */
    public void clampQuantity(int max) {
        quantity = Math.clamp(quantity, 1, max);
    }

}
