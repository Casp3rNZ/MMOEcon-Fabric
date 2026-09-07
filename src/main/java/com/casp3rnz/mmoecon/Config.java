package com.casp3rnz.mmoecon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON-backed server config, written to config/mmoecon/config.json.
 * NeoForge supplied {@code ModConfigSpec} (a TOML system) for free; Fabric has no
 * built-in equivalent, so this hand-rolls the same set of options over Gson —
 * reusing the JSON approach the shop config already uses, with no extra
 * dependencies.
 * The public API deliberately mirrors the old NeoForge {@code ModConfigSpec.*Value}
 * shape: each option is a {@link Value} exposing {@code .get()}. That keeps every
 * call site ({@code Config.ENABLE_GUI_SHOP.get()}, {@code Config.PLAYTIME_INTERVAL.get()}
 * …) identical to the NeoForge build, so the game-logic classes port unchanged.
 * Values are loaded once from disk in {@link #load()} (called on server start,
 * before balances or the shop load). A missing file is created from defaults; any
 * unreadable or partial file falls back to defaults field-by-field and is left on
 * disk untouched.
 */
public final class Config {

    /** Minimal stand-in for NeoForge's {@code ModConfigSpec.ConfigValue}: a read-only holder. */
    public static final class Value<T> {
        private T value;
        private Value(T initial) { this.value = initial; }
        public T get() { return value; }
        private void set(T v) { this.value = v; }
    }

    // ── Options (defaults match the NeoForge ModConfigSpec definitions) ──────────

    // economy
    public static final Value<Boolean> ENABLE_PLAYTIME_REWARDS = new Value<>(true);
    public static final Value<Double>  PLAYTIME_REWARD          = new Value<>(200.0);
    public static final Value<Long>    PLAYTIME_INTERVAL        = new Value<>(36000L);
    public static final Value<Double>  STARTING_AMOUNT          = new Value<>(1000.0);

    // shop
    public static final Value<Boolean> ENABLE_GUI_SHOP          = new Value<>(true);
    public static final Value<Integer> MAX_TRANSACTION_QUANTITY = new Value<>(2304);

    // auction house
    public static final Value<Boolean> ENABLE_AUCTION_HOUSE     = new Value<>(true);
    public static final Value<Integer> MAX_AUCTION_QUANTITY     = new Value<>(10);
    public static final Value<Integer> MAX_AUCTION_LISTING_SIZE = new Value<>(2304);

    private static final Path CONFIG_PATH = ModPaths.config("config.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Load config from disk, creating it from defaults if absent. Safe to call
     * once at server start. Individual missing/invalid keys fall back to their
     * default rather than aborting the whole load.
     */
    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            MMOEcon.LOGGER.info("No config found at {}. Writing defaults.", CONFIG_PATH);
            save();
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                MMOEcon.LOGGER.warn("Config at {} was empty; using defaults.", CONFIG_PATH);
                return;
            }

            JsonObject economy = root.has("economy") ? root.getAsJsonObject("economy") : new JsonObject();
            JsonObject shop     = root.has("shop")     ? root.getAsJsonObject("shop")     : new JsonObject();
            JsonObject auction  = root.has("auction house") ? root.getAsJsonObject("auction house") : new JsonObject();

            ENABLE_PLAYTIME_REWARDS.set(getBool(economy, "enablePlaytimeRewards", ENABLE_PLAYTIME_REWARDS.get()));
            PLAYTIME_REWARD.set(getDouble(economy, "playtimeReward", PLAYTIME_REWARD.get(), 0.0, Double.MAX_VALUE));
            PLAYTIME_INTERVAL.set(getLong(economy, "playtimeInterval", PLAYTIME_INTERVAL.get(), 1L, Long.MAX_VALUE));
            STARTING_AMOUNT.set(getDouble(economy, "startingAmount", STARTING_AMOUNT.get(), 0.0, Double.MAX_VALUE));

            ENABLE_GUI_SHOP.set(getBool(shop, "enableGUIShop", ENABLE_GUI_SHOP.get()));
            MAX_TRANSACTION_QUANTITY.set(getInt(shop, "maxTransactionQuantity", MAX_TRANSACTION_QUANTITY.get(), 1, Integer.MAX_VALUE));

            ENABLE_AUCTION_HOUSE.set(getBool(auction, "enableAuctionHouse", ENABLE_AUCTION_HOUSE.get()));
            MAX_AUCTION_QUANTITY.set(getInt(auction, "maxAuctionQuantity", MAX_AUCTION_QUANTITY.get(), 1, Integer.MAX_VALUE));
            MAX_AUCTION_LISTING_SIZE.set(getInt(auction, "maxAuctionListingSize", MAX_AUCTION_LISTING_SIZE.get(), 1, Integer.MAX_VALUE));

            MMOEcon.LOGGER.info("Loaded config from {}.", CONFIG_PATH);
        } catch (IOException | RuntimeException e) {
            MMOEcon.LOGGER.error("Failed to read config from {}: {} — using defaults.",
                    CONFIG_PATH, e.getMessage());
        }
    }

    /** Write the current values to disk, mirroring NeoForge's TOML sections as JSON objects. */
    public static void save() {
        JsonObject economy = new JsonObject();
        economy.addProperty("enablePlaytimeRewards", ENABLE_PLAYTIME_REWARDS.get());
        economy.addProperty("playtimeReward", PLAYTIME_REWARD.get());
        economy.addProperty("playtimeInterval", PLAYTIME_INTERVAL.get());
        economy.addProperty("startingAmount", STARTING_AMOUNT.get());

        JsonObject shop = new JsonObject();
        shop.addProperty("enableGUIShop", ENABLE_GUI_SHOP.get());
        shop.addProperty("maxTransactionQuantity", MAX_TRANSACTION_QUANTITY.get());

        JsonObject auction = new JsonObject();
        auction.addProperty("enableAuctionHouse", ENABLE_AUCTION_HOUSE.get());
        auction.addProperty("maxAuctionQuantity", MAX_AUCTION_QUANTITY.get());
        auction.addProperty("maxAuctionListingSize", MAX_AUCTION_LISTING_SIZE.get());

        JsonObject root = new JsonObject();
        root.add("economy", economy);
        root.add("shop", shop);
        root.add("auction house", auction);

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            MMOEcon.LOGGER.error("Failed to write config to {}: {}", CONFIG_PATH, e.getMessage());
        }
    }

    // Typed getters with per-key fallback and range clamping

    private static boolean getBool(JsonObject obj, String key, boolean fallback) {
        try {
            return obj.has(key) ? obj.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            warnBadKey(key, fallback);
            return fallback;
        }
    }

    private static double getDouble(JsonObject obj, String key, double fallback, double min, double max) {
        try {
            return obj.has(key) ? clamp(obj.get(key).getAsDouble(), min, max) : fallback;
        } catch (RuntimeException e) {
            warnBadKey(key, fallback);
            return fallback;
        }
    }

    private static long getLong(JsonObject obj, String key, long fallback, long min, long max) {
        try {
            return obj.has(key) ? clamp(obj.get(key).getAsLong(), min, max) : fallback;
        } catch (RuntimeException e) {
            warnBadKey(key, fallback);
            return fallback;
        }
    }

    private static int getInt(JsonObject obj, String key, int fallback, int min, int max) {
        try {
            return obj.has(key) ? clamp(obj.get(key).getAsInt(), min, max) : fallback;
        } catch (RuntimeException e) {
            warnBadKey(key, fallback);
            return fallback;
        }
    }

    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static long   clamp(long v, long min, long max)       { return Math.max(min, Math.min(max, v)); }
    private static int    clamp(int v, int min, int max)          { return Math.max(min, Math.min(max, v)); }

    private static void warnBadKey(String key, Object fallback) {
        MMOEcon.LOGGER.warn("Config key '{}' was invalid; falling back to {}.", key, fallback);
    }

    private Config() {}
}
