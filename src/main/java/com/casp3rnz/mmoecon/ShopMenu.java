package com.casp3rnz.mmoecon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
     * Server-side container menu for the shop GUI.
     * Layout is always a 6-row chest (54 slots), with the bottom row used as navigation/action bar.
     * The top 45 slots (rows 0-4) are the main content area.
     *  CONTENT AREA  (slots 0-44)   — categories, items, or quantity display
     *  NAV BAR       (slots 45-53)  — back button, page arrows, action buttons
     * Slot routing is dispatched entirely by session.view (a ShopView enum value),
     * This class is purely server-side. ChestMenu / MenuType.GENERIC_9x6 sends the
     * inventory contents to the client automatically — no client mod required.
     **/
    public final class ShopMenu extends AbstractContainerMenu {

        // ── Nav bar slot indices (within the 54-slot inventory) ───────────────────
        private static final int NAV_BACK        = 45;
        private static final int NAV_PREV_PAGE   = 48;
        private static final int NAV_NEXT_PAGE   = 50;

        // Quantity picker action slots
        private static final int QP_SELL_ALL     = 9;   // sell all of this item type
        private static final int QP_DEC_64       = 10;
        private static final int QP_DEC_10       = 11;
        private static final int QP_DEC_1        = 12;
        private static final int QP_INC_1        = 14;
        private static final int QP_INC_10       = 15;
        private static final int QP_INC_64       = 16;
        private static final int QP_CONFIRM      = 26;

        private static final int PERSISTENT_ICON_1 = 49;
        private static final int PERSISTENT_ICON_2 = 52;

        // ── Fields ────────────────────────────────────────────────────────────────
        private final Container shopInventory = new SimpleContainer(54);
        private final PlayerShopSession session;
        private final ServerPlayer player;

        // ── Constructor ───────────────────────────────────────────────────────────

        public ShopMenu(int syncId, Inventory playerInventory, PlayerShopSession session) {
            super(MenuType.GENERIC_9x6, syncId);
            this.session = session;
            this.player  = (ServerPlayer) playerInventory.player;

            // Shop inventory slots (top 54 slots) — players cannot take items
            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 9; col++) {
                    int index = col + row * 9;
                    this.addSlot(new Slot(shopInventory, index, 8 + col * 18, 18 + row * 18) {
                        @Override public boolean mayPickup(Player p) { return false; }
                        @Override public boolean mayPlace(ItemStack s) { return false; }
                    });
                }
            }

            // Player inventory
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
                }
            }
            // Hotbar
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
            }

            populateView();
        }

        // AbstractContainerMenu overrides

        @Override
        public boolean stillValid(Player player) {
            return true; // shop doesn't require proximity to a block
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY; // disable shift-click
        }

        @Override
        public void clicked(int slotId, int button, ClickType clickType, Player player) {
            // Block all item movement — we only care about the click position
            if (slotId < 0 || slotId >= shopInventory.getContainerSize()) return;

            playClickSound();
            routeClick(slotId, button);
        }

        // Click routing

        /**
         * Dispatch the click to the correct handler based on the current view.
         * Each handler is a small, focused method — no more giant switch/if chains.
         */
        private void routeClick(int slot, int button) {
            switch (session.view) {
                case CATEGORY_LIST   -> handleCategoryListClick(slot);
                case ITEM_LIST       -> handleItemListClick(slot, button);
                case QUANTITY_PICKER -> handleQuantityPickerClick(slot);
            }
        }

        // View: CATEGORY_LIST

        private void handleCategoryListClick(int slot) {
            List<ShopItemManager.ShopCategory> cats = ShopItemManager.getCategories();
            if (slot < cats.size()) {
                session.currentCategory = cats.get(slot);
                session.currentPage = 0;
                session.navigateTo(ShopViews.ITEM_LIST);
                populateView();
            }
        }

        // View: ITEM_LIST

        private void handleItemListClick(int slot, int button) {
            if (slot == NAV_BACK) {
                session.navigateTo(ShopViews.CATEGORY_LIST);
                populateView();
                return;
            }

            if (slot == NAV_PREV_PAGE) {
                if (session.currentPage > 0) { session.currentPage--; populateView(); }
                return;
            }

            if (slot == NAV_NEXT_PAGE) {
                int maxPage = maxPage(session.currentCategory.items().size(), 45);
                if (session.currentPage < maxPage) { session.currentPage++; populateView(); }
                return;
            }

            // Content area: find the item at this slot
            int itemIndex = slot + session.currentPage * 45;
            List<ShopItemManager.ShopItem> items = session.currentCategory.items();
            if (itemIndex >= items.size()) return;

            ShopItemManager.ShopItem shopItem = items.get(itemIndex);
            ItemStack displayStack = shopInventory.getItem(slot);
            if (displayStack.isEmpty()) return;

            boolean selling = (button == 1); // right-click = sell, left-click = buy

            if (selling && !shopItem.canSell()) return;
            if (!selling && !shopItem.canBuy()) return;

            session.pendingShopItem = shopItem;
            session.isSelling   = selling;
            session.resetQuantity();
            session.navigateTo(ShopViews.QUANTITY_PICKER);
            populateView();
        }

        // View: QUANTITY_PICKER

        private void handleQuantityPickerClick(int slot) {
            switch (slot) {
                case NAV_BACK -> {
                    session.navigateTo(ShopViews.ITEM_LIST);
                    populateView();
                }
                case QP_INC_1  -> { session.adjustQuantity(+1,  maxQuantity()); populateView(); }
                case QP_INC_10 -> { session.adjustQuantity(+10, maxQuantity()); populateView(); }
                case QP_INC_64 -> { session.adjustQuantity(+64, maxQuantity()); populateView(); }
                case QP_DEC_1  -> { session.adjustQuantity(-1,  maxQuantity()); populateView(); }
                case QP_DEC_10 -> { session.adjustQuantity(-10, maxQuantity()); populateView(); }
                case QP_DEC_64 -> { session.adjustQuantity(-64, maxQuantity()); populateView(); }
                case QP_SELL_ALL -> {
                    if (session.isSelling) {
                        executeSellAll();
                        session.navigateTo(ShopViews.ITEM_LIST);
                        populateView();
                    }
                }
                case QP_CONFIRM -> {
                    executeTransaction();
                    session.navigateTo(ShopViews.ITEM_LIST);
                    populateView();
                }
            }
        }

        // Transaction execution

        private void executeTransaction() {
            ShopItemManager.ShopItem shopItem = session.pendingShopItem;
            if (shopItem == null) return;

            int qty = session.quantity;
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(shopItem.id()));

            if (session.isSelling) {
                // selling special items not supported
                if (shopItem.isSpecial()) return;
                // Verify the player has enough of the item
                int held = countItemInInventory(item);
                if (held < qty) {
                    player.sendSystemMessage(Messages.error(
                            "You only have " + held + "x " + itemName(item) + "."));
                    return;
                }
                long total = Money.multiply(shopItem.sellPrice(), qty);
                removeItemsFromInventory(item, qty);
                PlayerBalanceManager.addBalance(player.getUUID(), total);
                player.sendSystemMessage(Messages.body(
                        "Sold " + Messages.item(qty + "x " + itemName(item))
                                + " for " + Messages.money(total) + "."));
                TransactionLogger.log(player.getName().getString() + " sold " + qty + " " + itemName(item) + " for $" + formatMoney(total));
            } else {
                long total = Money.multiply(shopItem.buyPrice(), qty);
                if (!PlayerBalanceManager.hasFunds(player.getUUID(), total)) {
                    long shortfall = total - PlayerBalanceManager.getBalance(player.getUUID());
                    player.sendSystemMessage(Messages.error(
                            "You need " + Messages.money(shortfall) + "§c more for that."));
                    return;
                }
                boolean special = shopItem.isSpecial();
                if (special ? freeSlots() < qty : !hasInventorySpace(item, qty)) {
                    player.sendSystemMessage(Messages.error("You don't have room for that."));
                    return;
                }
                PlayerBalanceManager.subtractBalance(player.getUUID(), total);
                String specialItemName = null;
                if (special) {
                    for (int i = 0; i < qty; i++) {
                        ItemStack displayStack = createDisplayStack(shopItem);
                        player.getInventory().add(displayStack);
                        specialItemName = displayStack.getItem().getName(displayStack).getString();
                    }
                } else {
                    giveItems(item, qty);
                }
                player.sendSystemMessage(Messages.body(
                        "Bought " + Messages.item(qty + "x "
                                + (specialItemName != null ? specialItemName : itemName(item)))
                                + " for " + Messages.money(total) + "."));
                TransactionLogger.log(player.getName().getString() + " bought " + qty + " " + (specialItemName != null ? specialItemName : itemName(item)) + " for $" + formatMoney(total));
            }

            playTransactionSound();
        }

        private void executeSellAll() {
            ShopItemManager.ShopItem shopItem = session.pendingShopItem;
            if (shopItem == null || !shopItem.canSell()) return;
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(shopItem.id()));
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();

            int qty = countItemInInventory(item);
            if (qty == 0) {
                player.sendSystemMessage(Messages.error("You have no " + itemName(item) + " to sell."));
                return;
            }

            long total = Money.multiply(shopItem.sellPrice(), qty);
            removeItemsFromInventory(item, qty);
            PlayerBalanceManager.addBalance(player.getUUID(), total);
            player.sendSystemMessage(Messages.body(
                    "Sold all " + Messages.item(qty + "x " + itemName(item))
                            + " for " + Messages.money(total) + "."));
            TransactionLogger.log(player.getName().getString() + " sold all " + qty + " " + itemName(item) + " for $" + formatMoney(total));
            playTransactionSound();
        }

        // Inventory helpers

        private int maxQuantity() {
            ShopItemManager.ShopItem shopItem = session.pendingShopItem;
            int configMax = Config.MAX_TRANSACTION_QUANTITY.get();
            if (shopItem == null) return configMax;

            // Special items occupy a whole slot each regardless of their backing
            // item, so they're resolved before any registry lookup.
            if (!session.isSelling && shopItem.isSpecial()) {
                return Math.clamp(freeSlots(), 1, configMax);
            }

            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(shopItem.id()));
            // An id absent from the registry resolves to air, whose 64 max stack
            // size would advertise capacity for an item that can never fit.
            if (item == Items.AIR) return 1;

            int capacity = session.isSelling
                    ? countItemInInventory(item)
                    : availableSpaceFor(item);

            return Math.clamp(capacity, 1, configMax);
        }

        /** Total number of `item` the player's inventory could still accept. */
        private int availableSpaceFor(Item item) {
            int space = 0;
            int maxStack = new ItemStack(item).getMaxStackSize();
            for (ItemStack stack : player.getInventory().items) {
                if (stack.isEmpty()) {
                    space += maxStack;
                } else if (stack.is(item)) {
                    space += maxStack - stack.getCount();
                }
            }
            return space;
        }

        private int countItemInInventory(Item item) {
            int count = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(item)) count += stack.getCount();
            }
            return count;
        }

        private void removeItemsFromInventory(Item item, int qty) {
            int remaining = qty;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(item) && remaining > 0) {
                    int take = Math.min(stack.getCount(), remaining);
                    stack.shrink(take);
                    remaining -= take;
                }
            }
        }

        private boolean hasInventorySpace(Item item, int qty) {
            return availableSpaceFor(item) >= qty;
        }

        private int freeSlots() {
            int free = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.isEmpty()) free++;
            }
            return free;
        }

        private void giveItems(Item item, int qty) {
            int remaining = qty;
            int maxStack = new ItemStack(item).getMaxStackSize();
            while (remaining > 0) {
                int give = Math.min(remaining, maxStack);
                player.getInventory().add(new ItemStack(item, give));
                remaining -= give;
            }
        }

        // View population

        /** Rebuild the shop inventory to match the current session view. */
        private void populateView() {
            clearShopInventory();
            switch (session.view) {
                case CATEGORY_LIST   -> populateCategoryList();
                case ITEM_LIST       -> populateItemList();
                case QUANTITY_PICKER -> populateQuantityPicker();
            }

            // Load toolbar icons
            loadPersistentIcons();

            // Sync to client
            broadcastChanges();
        }

        private void loadPersistentIcons() {
            long balance = PlayerBalanceManager.getBalance(player.getUUID());
            String playerName = player.getName().getString();
            ItemStack lantern = new ItemStack(Items.LANTERN);

            lantern.set(DataComponents.CUSTOM_NAME,
                    Component.literal("§6Welcome, " + playerName + "!")
                            .withStyle(s -> s.withItalic(false)));

            // Lore lines
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7Your Balance: §a$" + formatMoney(balance))
                    .withStyle(s -> s.withItalic(false)));

            lantern.set(DataComponents.LORE,
                    new ItemLore(lore));

            shopInventory.setItem(PERSISTENT_ICON_1, lantern);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME,
                    Component.literal("§bMod Developed by Casp3rNZ!")
                            .withStyle(s -> s.withItalic(false)));
            shopInventory.setItem(PERSISTENT_ICON_2 + 1, paper);
        }

        private void clearShopInventory() {
            for (int i = 0; i < shopInventory.getContainerSize(); i++) {
                shopInventory.setItem(i, ItemStack.EMPTY);
            }
        }

        private void populateCategoryList() {
            List<ShopItemManager.ShopCategory> cats = ShopItemManager.getCategories();
            for (int i = 0; i < Math.min(cats.size(), 45); i++) {
                ShopItemManager.ShopCategory cat = cats.get(i);
                Item repItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(cat.representativeItem()));
                ItemStack icon = new ItemStack(repItem);
                // Parse &/§ color & format codes from the category name. If no style
                // is supplied this yields plain white, non-italic text — applied via
                // CUSTOM_NAME so the name isn't rendered in the default italic.
                icon.set(DataComponents.CUSTOM_NAME, TextStyleParser.parse(cat.name()));
                shopInventory.setItem(i, icon);
            }
        }

        private void populateItemList() {
            if (session.currentCategory == null) return;
            List<ShopItemManager.ShopItem> items = session.currentCategory.items();
            int pageStart = session.currentPage * 45;

            for (int i = 0; i < 45; i++) {
                int itemIndex = pageStart + i;
                if (itemIndex >= items.size()) break;

                ShopItemManager.ShopItem shopItem = items.get(itemIndex);
                ItemStack stack = createDisplayStack(shopItem);
                if(stack.isEmpty()) continue;
                // Keep the vanilla item name but mark it as custom so it renders white (not italic)
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        Component.literal(stack.getHoverName().getString())
                                .withStyle(s -> s.withItalic(false)));

                // Build lore lines and apply via DataComponents.LORE
                List<Component> loreLines = new ArrayList<>();
                if (shopItem.canBuy()) {
                    loreLines.add(Component.literal("§aBuy: $" + formatMoney(shopItem.buyPrice()) + "  (left-click)")
                            .withStyle(s -> s.withItalic(false)));
                }
                if (shopItem.canSell()) {
                    loreLines.add(Component.literal("§6Sell: $" + formatMoney(shopItem.sellPrice()) + "  (right-click)")
                            .withStyle(s -> s.withItalic(false)));
                }
                stack.set(net.minecraft.core.component.DataComponents.LORE,
                        new net.minecraft.world.item.component.ItemLore(loreLines));

                shopInventory.setItem(i, stack);
            }

            // Nav bar
            boolean hasPrev = session.currentPage > 0;
            boolean hasNext = (session.currentPage + 1) * 45 < items.size();

            shopInventory.setItem(NAV_BACK, namedStack(Items.BARRIER, "§c← Back"));
            if (hasPrev) shopInventory.setItem(NAV_PREV_PAGE, namedStack(Items.ARROW, "§e← Previous page"));
            if (hasNext) shopInventory.setItem(NAV_NEXT_PAGE, namedStack(Items.ARROW, "§eNext page →"));
        }

        private void populateQuantityPicker() {
            ShopItemManager.ShopItem shopItem = session.pendingShopItem;
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(shopItem.id()));
            String action    = session.isSelling ? "Selling" : "Buying";
            ItemStack displayStack = createDisplayStack(shopItem);
            // Display item in the centre
            shopInventory.setItem(13, displayStack);
            String itemLabel = displayStack.getItem().getName(displayStack).getString();

            // Quantity display — clamp first so a stale quantity can't survive an
            // inventory change made while the picker was already open.
            int max = maxQuantity();
            session.clampQuantity(max);
            ItemStack qtyStack = namedStack(Items.PAPER, action + " x" + session.quantity);

            List<Component> qtyLore = new ArrayList<>();
            qtyLore.add(Component.literal("§7Max: §f" + max
                            + (session.isSelling ? " §7(you have)" : " §7(you can hold)"))
                    .withStyle(s -> s.withItalic(false)));

            // Prices are optional per direction — a buy-only item has no sellPrice
            // and vice versa, so the total is only shown when one applies.
            Long unitPrice = session.isSelling ? shopItem.sellPrice() : shopItem.buyPrice();
            if (unitPrice != null) {
                qtyLore.add(Component.literal("§7Total: §a$" + formatMoney(Money.multiply(unitPrice, session.quantity)))
                        .withStyle(s -> s.withItalic(false)));
            }

            qtyStack.set(DataComponents.LORE, new ItemLore(qtyLore));
            shopInventory.setItem(22, qtyStack);

            // Increment buttons
            shopInventory.setItem(QP_INC_1,  namedStack(Items.LIME_STAINED_GLASS_PANE,  "§a+1"));
            shopInventory.setItem(QP_INC_10, namedStack(Items.LIME_STAINED_GLASS_PANE,  "§a+10"));
            shopInventory.setItem(QP_INC_64, namedStack(Items.LIME_STAINED_GLASS_PANE,  "§a+64"));

            // Decrement buttons
            shopInventory.setItem(QP_DEC_1,  namedStack(Items.RED_STAINED_GLASS_PANE, "§c-1"));
            shopInventory.setItem(QP_DEC_10, namedStack(Items.RED_STAINED_GLASS_PANE, "§c-10"));
            shopInventory.setItem(QP_DEC_64, namedStack(Items.RED_STAINED_GLASS_PANE, "§c-64"));

            // Confirm
            shopInventory.setItem(QP_CONFIRM, namedStack(Items.EMERALD, "§aConfirm " + action));

            // Sell-all (only visible when selling)
            if (session.isSelling) {
                shopInventory.setItem(QP_SELL_ALL, namedStack(Items.HOPPER, "§6Sell all " + itemLabel));
            }

            // Back
            shopInventory.setItem(NAV_BACK, namedStack(Items.BARRIER, "§c← Back"));
        }

        // Utility

        private static ItemStack namedStack(Item item, String name) {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            return stack;
        }

        /**
         * The item's proper display name, translated and mod-aware — deriving it
         * from the registry path instead would render "create:cogwheel" as
         * "cogwheel" and miss any custom or localised name.
         */
        private static String itemName(Item item) {
            return new ItemStack(item).getHoverName().getString();
        }

        private static int maxPage(int itemCount, int pageSize) {
            return Math.max(0, (itemCount - 1) / pageSize);
        }

        public static String formatMoney(long cents) {
            return Money.format(cents);
        }

        private void playClickSound() {
            player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f,
                    0.5f + player.getRandom().nextFloat());
        }

        private void playTransactionSound() {
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.0f);
        }

        public static ItemStack createDisplayStack(ShopItemManager.ShopItem shopItem) {
            if(shopItem.isSpecial()) {
                switch(shopItem.special()) {
                    case "sell_wand": return SellWand.create();

                }
            }
            Item mc = BuiltInRegistries.ITEM.get(ResourceLocation.parse(shopItem.id()));
            return new ItemStack(mc);
        }
    }
