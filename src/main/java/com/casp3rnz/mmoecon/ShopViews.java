package com.casp3rnz.mmoecon;

/**
 * Represents which "page" of the shop GUI the player is currently viewing.
 * The ShopMenu routes slot click logic entirely based on this value, so each
 * case in the click handler is self-contained and easy to follow.
 */
public enum ShopViews {
    /** Top-level category list. One icon per category, up to 45 slots. */
    CATEGORY_LIST,

    /** Item list for the currently selected category. Paginated. */
    ITEM_LIST,

    /** Quantity picker before confirming a buy or sell. */
    QUANTITY_PICKER
}
