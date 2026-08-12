package com.casp3rnz.mmoecon;

import com.google.gson.*;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


/**
 *  Loads shop categories and items from config/mmoecon/MMOShop.json.
 *
 * JSON format:
 * {
 *   "categories": [
 *     {
 *       "name": "Blocks",
 *       "representativeItem": "minecraft:stone",
 *       "items": [
 *         { "id": "minecraft:stone", "buyPrice": 1.0, "sellPrice": 0.5 }
 *       ]
 *     }
 *   ]
 * }
 */

public class ShopItemManager {

    private static final Path CONFIG_PATH = ModPaths.config("MMOShop.json");

    private static List<ShopCategory> categories = new ArrayList<>();

    // Public API

    public static void load() {
        List<ShopCategory> loaded = new ArrayList<>();

        if(!Files.exists(CONFIG_PATH)) {
            MMOEcon.LOGGER.warn("Shop config not found at {}. Generating a template...", CONFIG_PATH);
            generateTemplate();
            MMOEcon.LOGGER.info("Template written to {}. Edit it and run /shop reload.", CONFIG_PATH);
            categories = loaded;
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            JsonArray categoriesJson = root.getAsJsonArray("categories");

            for (int i = 0; i < categoriesJson.size(); i++) {
                String name = null;
                String representativeItem = null;
                JsonArray itemsJson = null;

                try {
                    JsonObject categoryJson = categoriesJson.get(i).getAsJsonObject();
                    name = categoryJson.get("name").getAsString();
                    representativeItem = categoryJson.get("representativeItem").getAsString();
                    itemsJson = categoryJson.getAsJsonArray("items");
                } catch (Exception e) {
                    MMOEcon.LOGGER.warn("Skipping malformed category at index {}", i);
                    continue; // skip to next category entirely
                }

                List<ShopItem> items = new ArrayList<>();
                for (int j = 0; j < itemsJson.size(); j++) {
                    try {
                        JsonObject itemJson = itemsJson.get(j).getAsJsonObject();
                        String id = itemJson.get("id").getAsString();
                        Long buyPrice  = itemJson.has("buyPrice")
                                ? Money.fromDouble(itemJson.get("buyPrice").getAsDouble())  : null;
                        Long sellPrice = itemJson.has("sellPrice")
                                ? Money.fromDouble(itemJson.get("sellPrice").getAsDouble()) : null;
                        String specialItem = itemJson.has("special") ? itemJson.get("special").getAsString() : null;
                        items.add(new ShopItem(id, buyPrice, sellPrice, specialItem));
                    } catch (Exception e) {
                        // malformed item — skip silently
                    }
                }

                loaded.add(new ShopCategory(name, representativeItem, items));
                MMOEcon.LOGGER.info("Loaded shop category '{}' with {} items.", name, items.size());
            }

            categories = loaded;
        } catch (IOException | JsonParseException e) {
            MMOEcon.LOGGER.error("Failed to load shop config from {}: {}", CONFIG_PATH, e.getMessage());
        }
    }

    public static void reload() {
        categories = new ArrayList<>();
        load();
    }

    /**
     * Writes a starter MMOShop.json so new users have something to work from.
     * The file is immediately loaded after writing, so the shop is functional
     * on first launch without a restart.
     */
    private static void generateTemplate() {
        // Build the JSON structure manually so we control field order and comments
        // aren't supported in JSON, but the structure itself is self-explanatory.
        JsonObject root = new JsonObject();
        JsonArray categories = new JsonArray();

        categories.add(buildCategory(
                "Blocks",
                "minecraft:grass_block",
                new String[][]{
                        // { id, buyPrice, sellPrice }  — use null to omit a price
                        { "minecraft:grass_block",  "2.0",  "1.0"  },
                        { "minecraft:dirt",         "1.0",  "0.5"  },
                        { "minecraft:stone",        "3.0",  "1.5"  },
                        { "minecraft:cobblestone",  "2.0",  "1.0"  },
                        { "minecraft:sand",         "2.0",  "1.0"  },
                        { "minecraft:gravel",       "1.5",  "0.75" },
                        { "minecraft:oak_log",      "5.0",  "2.5"  },
                }
        ));

        // Category names support Minecraft &/§ color & format codes, e.g.
        // "&6&lOres & Materials" renders bold gold. Omit codes for plain white.
        categories.add(buildCategory(
                "&6Ores & Materials",
                "minecraft:iron_ore",
                new String[][]{
                        { "minecraft:coal",         "3.0",  "1.5"  },
                        { "minecraft:iron_ingot",   "10.0", "5.0"  },
                        { "minecraft:gold_ingot",   "20.0", "10.0" },
                        { "minecraft:diamond",      "75.0", "40.0" },
                        { "minecraft:emerald",      "50.0", "25.0" },
                        { "minecraft:redstone",     "2.0",  "1.0"  },
                        { "minecraft:lapis_lazuli", "3.0",  "1.5"  },
                }
        ));

        categories.add(buildCategory(
                "Food",
                "minecraft:bread",
                new String[][]{
                        { "minecraft:bread",        "4.0",  "2.0"  },
                        { "minecraft:cooked_beef",  "8.0",  "4.0"  },
                        { "minecraft:cooked_porkchop", "7.0", "3.5" },
                        { "minecraft:apple",        "3.0",  "1.5"  },
                        { "minecraft:golden_apple", "30.0", null   }, // buy only
                        { "minecraft:wheat",        "1.5",  "0.75" },
                }
        ));

        root.add("categories", categories);

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
                gson.toJson(root, writer);
            }
        } catch (IOException e) {
            MMOEcon.LOGGER.error("Failed to write shop template: {}", e.getMessage());
        }
    }

    /** Builds a category JsonObject from a 2D array of { id, buyPrice, sellPrice }. */
    private static JsonObject buildCategory(String name, String representativeItem, String[][] items) {
        JsonObject cat = new JsonObject();
        cat.addProperty("name", name);
        cat.addProperty("representativeItem", representativeItem);

        JsonArray itemsArray = new JsonArray();
        for (String[] entry : items) {
            JsonObject item = new JsonObject();
            item.addProperty("id", entry[0]);
            if (entry[1] != null) item.addProperty("buyPrice",  new java.math.BigDecimal(entry[1]));
            if (entry[2] != null) item.addProperty("sellPrice", new java.math.BigDecimal(entry[2]));
            itemsArray.add(item);
        }
        cat.add("items", itemsArray);
        return cat;
    }

    // Queries

    public static List<ShopCategory> getCategories() {
        return List.copyOf(categories);
    }

    public static List<ShopItem> getAllItems() {
        List<ShopItem> all = new ArrayList<>();
        for (ShopCategory cat : categories) all.addAll(cat.items);
        return all;
    }

    /** Returns the ShopItem for the given registry ID, or null is not in the shop. **/
    public static ShopItem findItem(String registryId) {
        for (ShopItem item : getAllItems()) {
            if (item.id.equals(registryId)) return item;
        }
        return null;
    }


    // Data Classes
    /** Prices are in cents (see Money); null means the item can't be traded that way. */
    public record ShopItem(String id, Long buyPrice, Long sellPrice, String special) {
        public boolean canBuy() { return buyPrice != null; }
        public boolean canSell() { return sellPrice != null; }
        public boolean isSpecial() { return special != null; }
    }

    public record ShopCategory(String name, String representativeItem, List<ShopItem> items) {}


}
