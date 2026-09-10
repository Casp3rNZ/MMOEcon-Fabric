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
        JsonObject root = new JsonObject();
        JsonArray categories = new JsonArray();

        categories.add(buildCategory(
                "&2Building Blocks",
                "minecraft:grass_block",
                new String[][]{
                        { "minecraft:dirt", "1", "0.4" },
                        { "minecraft:coarse_dirt", "1", "0.4" },
                        { "minecraft:rooted_dirt", "1.5", "0.6" },
                        { "minecraft:grass_block", "2", "0.8" },
                        { "minecraft:cherry_leaves", "1", "0.4" },
                        { "minecraft:podzol", "2", "0.8" },
                        { "minecraft:mycelium", "2.5", "1" },
                        { "minecraft:mud", "1.5", "0.6" },
                        { "minecraft:packed_mud", "2", "0.8" },
                        { "minecraft:muddy_mangrove_roots", "2.5", "1" },
                        { "minecraft:mangrove_roots", "4", "1.6" },
                        { "minecraft:dirt_path", "2", "0.8" },
                        { "minecraft:farmland", "2", "0.8" },
                        { "minecraft:clay", "3", "1.2" },
                        { "minecraft:gravel", "1.5", "0.6" },
                        { "minecraft:sand", "2", "0.8" },
                        { "minecraft:red_sand", "2", "0.8" },
                        { "minecraft:oak_log", "5", "2" },
                        { "minecraft:spruce_log", "5", "2" },
                        { "minecraft:birch_log", "5", "2" },
                        { "minecraft:jungle_log", "5", "2" },
                        { "minecraft:acacia_log", "5", "2" },
                        { "minecraft:dark_oak_log", "5", "2" },
                        { "minecraft:mangrove_log", "5", "2" },
                        { "minecraft:cherry_log", "5", "2" },
                        { "minecraft:crimson_stem", "5", "2" },
                        { "minecraft:warped_stem", "5", "2" },
                        { "minecraft:oak_leaves", "1", "0.4" },
                        { "minecraft:spruce_leaves", "1", "0.4" },
                        { "minecraft:birch_leaves", "1", "0.4" },
                        { "minecraft:jungle_leaves", "1", "0.4" },
                        { "minecraft:acacia_leaves", "1", "0.4" },
                        { "minecraft:dark_oak_leaves", "1", "0.4" },
                        { "minecraft:mangrove_leaves", "1", "0.4" },
                        { "minecraft:snow_block", "2", "0.8" },
                        { "minecraft:ice", "3", "1.2" },
                        { "minecraft:packed_ice", "5", "2" },
                        { "minecraft:blue_ice", "8", "3.2" },
                        { "minecraft:moss_block", "3", "1.2" },
                        { "minecraft:moss_carpet", "2", "0.8" },
                        { "minecraft:magma_block", "4", "1.6" },
                        { "minecraft:obsidian", "20", "8" },
                        { "minecraft:crying_obsidian", "40", "16" },
                        { "minecraft:crimson_nylium", "4", "1.6" },
                        { "minecraft:warped_nylium", "4", "1.6" },
                        { "minecraft:nether_wart_block", "5", "2" },
                        { "minecraft:warped_wart_block", "5", "2" },
                        { "minecraft:shroomlight", "5", "2" },
                        { "minecraft:glass", "3", "1.2" },
                        { "minecraft:glass_pane", "2", "0.8" },
                        { "minecraft:stone", "3", "1.2" },
                        { "minecraft:cobblestone", "2", "0.8" },
                        { "minecraft:mossy_cobblestone", "3", "1.2" },
                        { "minecraft:smooth_stone", "4", "1.6" },
                        { "minecraft:stone_bricks", "4", "1.6" },
                        { "minecraft:mossy_stone_bricks", "5", "2" },
                        { "minecraft:cracked_stone_bricks", "4", "1.6" },
                        { "minecraft:chiseled_stone_bricks", "5", "2" },
                        { "minecraft:granite", "3", "1.2" },
                        { "minecraft:polished_granite", "4", "1.6" },
                        { "minecraft:diorite", "3", "1.2" },
                        { "minecraft:polished_diorite", "4", "1.6" },
                        { "minecraft:andesite", "3", "1.2" },
                        { "minecraft:polished_andesite", "4", "1.6" },
                        { "minecraft:deepslate", "3.5", "1.4" },
                        { "minecraft:cobbled_deepslate", "3", "1.2" },
                        { "minecraft:polished_deepslate", "4", "1.6" },
                        { "minecraft:deepslate_bricks", "5", "2" },
                        { "minecraft:cracked_deepslate_bricks", "5", "2" },
                        { "minecraft:deepslate_tiles", "5", "2" },
                        { "minecraft:cracked_deepslate_tiles", "5", "2" },
                        { "minecraft:chiseled_deepslate", "5", "2" },
                        { "minecraft:tuff", "3", "1.2" },
                        { "minecraft:polished_tuff", "4", "1.6" },
                        { "minecraft:tuff_bricks", "5", "2" },
                        { "minecraft:chiseled_tuff", "5", "2" },
                        { "minecraft:chiseled_tuff_bricks", "6", "2.4" },
                        { "minecraft:calcite", "4", "1.6" },
                        { "minecraft:dripstone_block", "4", "1.6" },
                        { "minecraft:smooth_basalt", "4", "1.6" },
                        { "minecraft:basalt", "3", "1.2" },
                        { "minecraft:polished_basalt", "4", "1.6" },
                        { "minecraft:bricks", "5", "2" },
                        { "minecraft:mud_bricks", "4", "1.6" },
                        { "minecraft:prismarine", "6", "2.4" },
                        { "minecraft:prismarine_bricks", "7", "2.8" },
                        { "minecraft:dark_prismarine", "8", "3.2" },
                        { "minecraft:sea_lantern", "12", "4.8" },
                        { "minecraft:sandstone", "3", "1.2" },
                        { "minecraft:smooth_sandstone", "4", "1.6" },
                        { "minecraft:cut_sandstone", "4", "1.6" },
                        { "minecraft:chiseled_sandstone", "4", "1.6" },
                        { "minecraft:red_sandstone", "3", "1.2" },
                        { "minecraft:smooth_red_sandstone", "4", "1.6" },
                        { "minecraft:cut_red_sandstone", "4", "1.6" },
                        { "minecraft:chiseled_red_sandstone", "4", "1.6" },
                        { "minecraft:quartz_block", "16", "6.4" },
                        { "minecraft:smooth_quartz", "16", "6.4" },
                        { "minecraft:chiseled_quartz_block", "16", "6.4" },
                        { "minecraft:quartz_bricks", "16", "6.4" },
                        { "minecraft:quartz_pillar", "16", "6.4" },
                        { "minecraft:amethyst_block", "24", "9.6" },
                        { "minecraft:cut_copper", "7", "2.8" },
                        { "minecraft:exposed_copper", "7", "2.8" },
                        { "minecraft:weathered_copper", "7", "2.8" },
                        { "minecraft:oxidized_copper", "7", "2.8" },
                        { "minecraft:chiseled_copper", "8", "3.2" },
                        { "minecraft:copper_grate", "8", "3.2" },
                        { "minecraft:copper_bulb", "10", "4" },
                        { "minecraft:bone_block", "6", "2.4" },
                        { "minecraft:hay_block", "9", "3.6" },
                        { "minecraft:dried_kelp_block", "6", "2.4" },
                        { "minecraft:slime_block", "12", "4.8" },
                        { "minecraft:honey_block", "12", "4.8" },
                        { "minecraft:honeycomb_block", "12", "4.8" },
                        { "minecraft:terracotta", "4", "1.6" },
                        { "minecraft:glowstone", "8", "3.2" },
                        { "minecraft:nether_bricks", "4", "1.6" },
                        { "minecraft:red_nether_bricks", "5", "2" },
                        { "minecraft:ochre_froglight", "12", "4.8" },
                        { "minecraft:verdant_froglight", "12", "4.8" },
                        { "minecraft:pearlescent_froglight", "12", "4.8" },
                        { "minecraft:netherrack", "2", "0.8" },
                        { "minecraft:soul_sand", "4", "1.6" },
                        { "minecraft:soul_soil", "4", "1.6" },
                        { "minecraft:blackstone", "3", "1.2" },
                        { "minecraft:polished_blackstone", "4", "1.6" },
                        { "minecraft:polished_blackstone_bricks", "5", "2" },
                        { "minecraft:cracked_polished_blackstone_bricks", "5", "2" },
                        { "minecraft:chiseled_polished_blackstone", "5", "2" },
                        { "minecraft:gilded_blackstone", "15", "6" },
                        { "minecraft:end_stone", "4", "1.6" },
                        { "minecraft:end_stone_bricks", "5", "2" },
                        { "minecraft:purpur_block", "6", "2.4" },
                        { "minecraft:purpur_pillar", "6", "2.4" },
                        { "minecraft:chorus_plant", "4", "1.6" },
                        { "minecraft:chorus_flower", "4", "1.6" },
                        { "minecraft:sculk", "4", "1.6" },
                        { "minecraft:sponge", "12", "4.8" },
                }
        ));

        categories.add(buildCategory(
                "&6Ores & Minerals",
                "minecraft:diamond",
                new String[][]{
                        { "minecraft:ancient_debris", "200", "80" },
                        { "minecraft:coal", "3", "1.2" },
                        { "minecraft:charcoal", "3", "1.2" },
                        { "minecraft:raw_iron", "8", "3.2" },
                        { "minecraft:raw_copper", "6", "2.4" },
                        { "minecraft:raw_gold", "18", "7.2" },
                        { "minecraft:iron_ingot", "10", "4" },
                        { "minecraft:copper_ingot", "7", "2.8" },
                        { "minecraft:gold_ingot", "20", "8" },
                        { "minecraft:iron_nugget", "1.5", "0.6" },
                        { "minecraft:gold_nugget", "3", "1.2" },
                        { "minecraft:netherite_scrap", "120", "48" },
                        { "minecraft:netherite_ingot", "500", "200" },
                        { "minecraft:redstone", "2", "0.8" },
                        { "minecraft:lapis_lazuli", "3", "1.2" },
                        { "minecraft:diamond", "75", "30" },
                        { "minecraft:emerald", "50", "20" },
                        { "minecraft:quartz", "4", "1.6" },
                        { "minecraft:amethyst_shard", "6", "2.4" },
                        { "minecraft:flint", "2", "0.8" },
                        { "minecraft:glowstone_dust", "2", "0.8" },
                }
        ));

        categories.add(buildCategory(
                "&dWool & Dyes",
                "minecraft:pink_stained_glass",
                new String[][]{
                        { "minecraft:white_wool", "4", "1.6" },
                        { "minecraft:orange_wool", "4", "1.6" },
                        { "minecraft:magenta_wool", "4", "1.6" },
                        { "minecraft:light_blue_wool", "4", "1.6" },
                        { "minecraft:yellow_wool", "4", "1.6" },
                        { "minecraft:lime_wool", "4", "1.6" },
                        { "minecraft:pink_wool", "4", "1.6" },
                        { "minecraft:gray_wool", "4", "1.6" },
                        { "minecraft:light_gray_wool", "4", "1.6" },
                        { "minecraft:cyan_wool", "4", "1.6" },
                        { "minecraft:purple_wool", "4", "1.6" },
                        { "minecraft:blue_wool", "4", "1.6" },
                        { "minecraft:brown_wool", "4", "1.6" },
                        { "minecraft:green_wool", "4", "1.6" },
                        { "minecraft:red_wool", "4", "1.6" },
                        { "minecraft:black_wool", "4", "1.6" },
                        { "minecraft:white_dye", "3", "1.2" },
                        { "minecraft:orange_dye", "3", "1.2" },
                        { "minecraft:magenta_dye", "3", "1.2" },
                        { "minecraft:light_blue_dye", "3", "1.2" },
                        { "minecraft:yellow_dye", "3", "1.2" },
                        { "minecraft:lime_dye", "3", "1.2" },
                        { "minecraft:pink_dye", "3", "1.2" },
                        { "minecraft:gray_dye", "3", "1.2" },
                        { "minecraft:light_gray_dye", "3", "1.2" },
                        { "minecraft:cyan_dye", "3", "1.2" },
                        { "minecraft:purple_dye", "3", "1.2" },
                        { "minecraft:blue_dye", "3", "1.2" },
                        { "minecraft:brown_dye", "3", "1.2" },
                        { "minecraft:green_dye", "3", "1.2" },
                        { "minecraft:red_dye", "3", "1.2" },
                        { "minecraft:black_dye", "3", "1.2" },
                        { "minecraft:white_bed", "12", "4.8" },
                        { "minecraft:orange_bed", "12", "4.8" },
                        { "minecraft:magenta_bed", "12", "4.8" },
                        { "minecraft:light_blue_bed", "12", "4.8" },
                        { "minecraft:yellow_bed", "12", "4.8" },
                        { "minecraft:lime_bed", "12", "4.8" },
                        { "minecraft:pink_bed", "12", "4.8" },
                        { "minecraft:gray_bed", "12", "4.8" },
                        { "minecraft:light_gray_bed", "12", "4.8" },
                        { "minecraft:cyan_bed", "12", "4.8" },
                        { "minecraft:purple_bed", "12", "4.8" },
                        { "minecraft:blue_bed", "12", "4.8" },
                        { "minecraft:brown_bed", "12", "4.8" },
                        { "minecraft:green_bed", "12", "4.8" },
                        { "minecraft:red_bed", "12", "4.8" },
                        { "minecraft:black_bed", "12", "4.8" },
                        { "minecraft:white_concrete", "4", "1.6" },
                        { "minecraft:orange_concrete", "4", "1.6" },
                        { "minecraft:magenta_concrete", "4", "1.6" },
                        { "minecraft:light_blue_concrete", "4", "1.6" },
                        { "minecraft:yellow_concrete", "4", "1.6" },
                        { "minecraft:lime_concrete", "4", "1.6" },
                        { "minecraft:pink_concrete", "4", "1.6" },
                        { "minecraft:gray_concrete", "4", "1.6" },
                        { "minecraft:light_gray_concrete", "4", "1.6" },
                        { "minecraft:cyan_concrete", "4", "1.6" },
                        { "minecraft:purple_concrete", "4", "1.6" },
                        { "minecraft:blue_concrete", "4", "1.6" },
                        { "minecraft:brown_concrete", "4", "1.6" },
                        { "minecraft:green_concrete", "4", "1.6" },
                        { "minecraft:red_concrete", "4", "1.6" },
                        { "minecraft:black_concrete", "4", "1.6" },
                        { "minecraft:white_concrete_powder", "4", "1.6" },
                        { "minecraft:orange_concrete_powder", "4", "1.6" },
                        { "minecraft:magenta_concrete_powder", "4", "1.6" },
                        { "minecraft:light_blue_concrete_powder", "4", "1.6" },
                        { "minecraft:yellow_concrete_powder", "4", "1.6" },
                        { "minecraft:lime_concrete_powder", "4", "1.6" },
                        { "minecraft:pink_concrete_powder", "4", "1.6" },
                        { "minecraft:gray_concrete_powder", "4", "1.6" },
                        { "minecraft:light_gray_concrete_powder", "4", "1.6" },
                        { "minecraft:cyan_concrete_powder", "4", "1.6" },
                        { "minecraft:purple_concrete_powder", "4", "1.6" },
                        { "minecraft:blue_concrete_powder", "4", "1.6" },
                        { "minecraft:brown_concrete_powder", "4", "1.6" },
                        { "minecraft:green_concrete_powder", "4", "1.6" },
                        { "minecraft:red_concrete_powder", "4", "1.6" },
                        { "minecraft:black_concrete_powder", "4", "1.6" },
                        { "minecraft:white_terracotta", "4", "1.6" },
                        { "minecraft:orange_terracotta", "4", "1.6" },
                        { "minecraft:magenta_terracotta", "4", "1.6" },
                        { "minecraft:light_blue_terracotta", "4", "1.6" },
                        { "minecraft:yellow_terracotta", "4", "1.6" },
                        { "minecraft:lime_terracotta", "4", "1.6" },
                        { "minecraft:pink_terracotta", "4", "1.6" },
                        { "minecraft:gray_terracotta", "4", "1.6" },
                        { "minecraft:light_gray_terracotta", "4", "1.6" },
                        { "minecraft:cyan_terracotta", "4", "1.6" },
                        { "minecraft:purple_terracotta", "4", "1.6" },
                        { "minecraft:blue_terracotta", "4", "1.6" },
                        { "minecraft:brown_terracotta", "4", "1.6" },
                        { "minecraft:green_terracotta", "4", "1.6" },
                        { "minecraft:red_terracotta", "4", "1.6" },
                        { "minecraft:black_terracotta", "4", "1.6" },
                        { "minecraft:white_glazed_terracotta", "6", "2.4" },
                        { "minecraft:orange_glazed_terracotta", "6", "2.4" },
                        { "minecraft:magenta_glazed_terracotta", "6", "2.4" },
                        { "minecraft:light_blue_glazed_terracotta", "6", "2.4" },
                        { "minecraft:yellow_glazed_terracotta", "6", "2.4" },
                        { "minecraft:lime_glazed_terracotta", "6", "2.4" },
                        { "minecraft:pink_glazed_terracotta", "6", "2.4" },
                        { "minecraft:gray_glazed_terracotta", "6", "2.4" },
                        { "minecraft:light_gray_glazed_terracotta", "6", "2.4" },
                        { "minecraft:cyan_glazed_terracotta", "6", "2.4" },
                        { "minecraft:purple_glazed_terracotta", "6", "2.4" },
                        { "minecraft:blue_glazed_terracotta", "6", "2.4" },
                        { "minecraft:brown_glazed_terracotta", "6", "2.4" },
                        { "minecraft:green_glazed_terracotta", "6", "2.4" },
                        { "minecraft:red_glazed_terracotta", "6", "2.4" },
                        { "minecraft:black_glazed_terracotta", "6", "2.4" },
                        { "minecraft:white_stained_glass", "3", "1.2" },
                        { "minecraft:orange_stained_glass", "3", "1.2" },
                        { "minecraft:magenta_stained_glass", "3", "1.2" },
                        { "minecraft:light_blue_stained_glass", "3", "1.2" },
                        { "minecraft:yellow_stained_glass", "3", "1.2" },
                        { "minecraft:lime_stained_glass", "3", "1.2" },
                        { "minecraft:pink_stained_glass", "3", "1.2" },
                        { "minecraft:gray_stained_glass", "3", "1.2" },
                        { "minecraft:light_gray_stained_glass", "3", "1.2" },
                        { "minecraft:cyan_stained_glass", "3", "1.2" },
                        { "minecraft:purple_stained_glass", "3", "1.2" },
                        { "minecraft:blue_stained_glass", "3", "1.2" },
                        { "minecraft:brown_stained_glass", "3", "1.2" },
                        { "minecraft:green_stained_glass", "3", "1.2" },
                        { "minecraft:red_stained_glass", "3", "1.2" },
                        { "minecraft:black_stained_glass", "3", "1.2" },
                        { "minecraft:white_stained_glass_pane", "2", "0.8" },
                        { "minecraft:orange_stained_glass_pane", "2", "0.8" },
                        { "minecraft:magenta_stained_glass_pane", "2", "0.8" },
                        { "minecraft:light_blue_stained_glass_pane", "2", "0.8" },
                        { "minecraft:yellow_stained_glass_pane", "2", "0.8" },
                        { "minecraft:lime_stained_glass_pane", "2", "0.8" },
                        { "minecraft:pink_stained_glass_pane", "2", "0.8" },
                        { "minecraft:gray_stained_glass_pane", "2", "0.8" },
                        { "minecraft:light_gray_stained_glass_pane", "2", "0.8" },
                        { "minecraft:cyan_stained_glass_pane", "2", "0.8" },
                        { "minecraft:purple_stained_glass_pane", "2", "0.8" },
                        { "minecraft:blue_stained_glass_pane", "2", "0.8" },
                        { "minecraft:brown_stained_glass_pane", "2", "0.8" },
                        { "minecraft:green_stained_glass_pane", "2", "0.8" },
                        { "minecraft:red_stained_glass_pane", "2", "0.8" },
                        { "minecraft:black_stained_glass_pane", "2", "0.8" },
                }
        ));

        categories.add(buildCategory(
                "&cRedstone",
                "minecraft:redstone",
                new String[][]{
                        { "minecraft:redstone_torch", "3", "1.2" },
                        { "minecraft:repeater", "6", "2.4" },
                        { "minecraft:comparator", "8", "3.2" },
                        { "minecraft:lever", "2", "0.8" },
                        { "minecraft:tripwire_hook", "3", "1.2" },
                        { "minecraft:daylight_detector", "12", "4.8" },
                        { "minecraft:target", "8", "3.2" },
                        { "minecraft:note_block", "8", "3.2" },
                        { "minecraft:jukebox", "30", "12" },
                        { "minecraft:observer", "12", "4.8" },
                        { "minecraft:piston", "12", "4.8" },
                        { "minecraft:sticky_piston", "16", "6.4" },
                        { "minecraft:dispenser", "20", "8" },
                        { "minecraft:dropper", "12", "4.8" },
                        { "minecraft:hopper", "30", "12" },
                        { "minecraft:crafter", "40", "16" },
                        { "minecraft:iron_trapdoor", "12", "4.8" },
                        { "minecraft:iron_door", "12", "4.8" },
                        { "minecraft:heavy_weighted_pressure_plate", "12", "4.8" },
                        { "minecraft:light_weighted_pressure_plate", "12", "4.8" },
                        { "minecraft:stone_pressure_plate", "4", "1.6" },
                        { "minecraft:polished_blackstone_pressure_plate", "4", "1.6" },
                        { "minecraft:stone_button", "2", "0.8" },
                        { "minecraft:polished_blackstone_button", "2", "0.8" },
                        { "minecraft:tnt", "25", "10" },
                        { "minecraft:rail", "3", "1.2" },
                        { "minecraft:powered_rail", "12", "4.8" },
                        { "minecraft:detector_rail", "12", "4.8" },
                        { "minecraft:activator_rail", "12", "4.8" },
                        { "minecraft:minecart", "20", "8" },
                        { "minecraft:chest_minecart", "30", "12" },
                        { "minecraft:hopper_minecart", "50", "20" },
                        { "minecraft:furnace_minecart", "30", "12" },
                        { "minecraft:tnt_minecart", "45", "18" },
                        { "minecraft:lightning_rod", "12", "4.8" },
                        { "minecraft:redstone_lamp", "10", "4" },
                }
        ));

        categories.add(buildCategory(
                "&3Functional Blocks",
                "minecraft:crafting_table",
                new String[][]{
                        { "minecraft:crafting_table", "8", "3.2" },
                        { "minecraft:furnace", "8", "3.2" },
                        { "minecraft:blast_furnace", "20", "8" },
                        { "minecraft:smoker", "15", "6" },
                        { "minecraft:chest", "8", "3.2" },
                        { "minecraft:barrel", "10", "4" },
                        { "minecraft:ender_chest", "60", "24" },
                        { "minecraft:shulker_box", "100", "40" },
                        { "minecraft:anvil", "100", "40" },
                        { "minecraft:grindstone", "15", "6" },
                        { "minecraft:smithing_table", "15", "6" },
                        { "minecraft:fletching_table", "15", "6" },
                        { "minecraft:cartography_table", "15", "6" },
                        { "minecraft:loom", "15", "6" },
                        { "minecraft:stonecutter", "15", "6" },
                        { "minecraft:composter", "8", "3.2" },
                        { "minecraft:brewing_stand", "25", "10" },
                        { "minecraft:cauldron", "20", "8" },
                        { "minecraft:enchanting_table", "120", "48" },
                        { "minecraft:bookshelf", "18", "7.2" },
                        { "minecraft:chiseled_bookshelf", "18", "7.2" },
                        { "minecraft:lectern", "15", "6" },
                        { "minecraft:bell", "60", "24" },
                        { "minecraft:beacon", "1000", "400" },
                        { "minecraft:conduit", "400", "160" },
                        { "minecraft:lodestone", "80", "32" },
                        { "minecraft:respawn_anchor", "60", "24" },
                        { "minecraft:scaffolding", "3", "1.2" },
                        { "minecraft:ladder", "3", "1.2" },
                        { "minecraft:torch", "1", "0.4" },
                        { "minecraft:soul_torch", "2", "0.8" },
                        { "minecraft:lantern", "6", "2.4" },
                        { "minecraft:soul_lantern", "8", "3.2" },
                        { "minecraft:campfire", "10", "4" },
                        { "minecraft:soul_campfire", "12", "4.8" },
                        { "minecraft:flower_pot", "4", "1.6" },
                        { "minecraft:armor_stand", "12", "4.8" },
                        { "minecraft:item_frame", "6", "2.4" },
                        { "minecraft:glow_item_frame", "8", "3.2" },
                        { "minecraft:painting", "6", "2.4" },
                        { "minecraft:end_crystal", "60", "24" },
                        { "minecraft:decorated_pot", "8", "3.2" },
                }
        ));

        categories.add(buildCategory(
                "&bTools",
                "minecraft:diamond_pickaxe",
                new String[][]{
                        { "minecraft:wooden_pickaxe", "5" },
                        { "minecraft:wooden_axe", "5" },
                        { "minecraft:wooden_shovel", "3" },
                        { "minecraft:wooden_hoe", "4" },
                        { "minecraft:stone_pickaxe", "8" },
                        { "minecraft:stone_axe", "8" },
                        { "minecraft:stone_shovel", "5" },
                        { "minecraft:stone_hoe", "6" },
                        { "minecraft:iron_pickaxe", "40" },
                        { "minecraft:iron_axe", "40" },
                        { "minecraft:iron_shovel", "20" },
                        { "minecraft:iron_hoe", "25" },
                        { "minecraft:golden_pickaxe", "30" },
                        { "minecraft:golden_axe", "30" },
                        { "minecraft:golden_shovel", "15" },
                        { "minecraft:golden_hoe", "20" },
                        { "minecraft:diamond_pickaxe", "200" },
                        { "minecraft:diamond_axe", "200" },
                        { "minecraft:diamond_shovel", "90" },
                        { "minecraft:diamond_hoe", "120" },
                        { "minecraft:netherite_pickaxe", "800" },
                        { "minecraft:netherite_axe", "800" },
                        { "minecraft:netherite_shovel", "550" },
                        { "minecraft:netherite_hoe", "600" },
                        { "minecraft:shears", "15" },
                        { "minecraft:flint_and_steel", "8" },
                        { "minecraft:fishing_rod", "15" },
                        { "minecraft:carrot_on_a_stick", "20" },
                        { "minecraft:warped_fungus_on_a_stick", "25" },
                        { "minecraft:compass", "20" },
                        { "minecraft:recovery_compass", "120" },
                        { "minecraft:clock", "20" },
                        { "minecraft:spyglass", "30" },
                        { "minecraft:lead", "8" },
                        { "minecraft:name_tag", "40" },
                        { "minecraft:bucket", "12" },
                        { "minecraft:water_bucket", "12" },
                        { "minecraft:lava_bucket", "20" },
                        { "minecraft:milk_bucket", "15" },
                        { "minecraft:powder_snow_bucket", "15" },
                        { "minecraft:brush", "12" },
                }
        ));

        categories.add(buildCategory(
                "&4Combat & Armor",
                "minecraft:netherite_sword",
                new String[][]{
                        { "minecraft:wooden_sword", "5" },
                        { "minecraft:stone_sword", "8" },
                        { "minecraft:iron_sword", "40" },
                        { "minecraft:golden_sword", "30" },
                        { "minecraft:diamond_sword", "200" },
                        { "minecraft:netherite_sword", "800" },
                        { "minecraft:bow", "20" },
                        { "minecraft:crossbow", "30" },
                        { "minecraft:arrow", "1", "0.4" },
                        { "minecraft:spectral_arrow", "4", "1.6" },
                        { "minecraft:trident", "300" },
                        { "minecraft:shield", "20" },
                        { "minecraft:mace", "400" },
                        { "minecraft:leather_helmet", "10" },
                        { "minecraft:leather_chestplate", "16" },
                        { "minecraft:leather_leggings", "14" },
                        { "minecraft:leather_boots", "8" },
                        { "minecraft:chainmail_helmet", "40" },
                        { "minecraft:chainmail_chestplate", "64" },
                        { "minecraft:chainmail_leggings", "56" },
                        { "minecraft:chainmail_boots", "32" },
                        { "minecraft:iron_helmet", "50" },
                        { "minecraft:iron_chestplate", "80" },
                        { "minecraft:iron_leggings", "70" },
                        { "minecraft:iron_boots", "40" },
                        { "minecraft:golden_helmet", "35" },
                        { "minecraft:golden_chestplate", "56" },
                        { "minecraft:golden_leggings", "49" },
                        { "minecraft:golden_boots", "28" },
                        { "minecraft:diamond_helmet", "250" },
                        { "minecraft:diamond_chestplate", "400" },
                        { "minecraft:diamond_leggings", "350" },
                        { "minecraft:diamond_boots", "200" },
                        { "minecraft:netherite_helmet", "900" },
                        { "minecraft:netherite_chestplate", "1200" },
                        { "minecraft:netherite_leggings", "1100" },
                        { "minecraft:netherite_boots", "800" },
                        { "minecraft:turtle_helmet", "60" },
                        { "minecraft:elytra", "2000" },
                        { "minecraft:totem_of_undying", "500" },
                        { "minecraft:wolf_armor", "80" },
                        { "minecraft:leather_horse_armor", "20" },
                        { "minecraft:iron_horse_armor", "60" },
                        { "minecraft:golden_horse_armor", "80" },
                        { "minecraft:diamond_horse_armor", "200" },
                        { "minecraft:saddle", "40" },
                }
        ));

        categories.add(buildCategory(
                "&aFood",
                "minecraft:bread",
                new String[][]{
                        { "minecraft:apple", "3", "1.2" },
                        { "minecraft:golden_apple", "30", null },
                        { "minecraft:enchanted_golden_apple", "500", null },
                        { "minecraft:bread", "4", "1.6" },
                        { "minecraft:wheat", "1.5", "0.6" },
                        { "minecraft:carrot", "2", "0.8" },
                        { "minecraft:golden_carrot", "12", "4.8" },
                        { "minecraft:potato", "2", "0.8" },
                        { "minecraft:baked_potato", "4", "1.6" },
                        { "minecraft:poisonous_potato", "1", "0.4" },
                        { "minecraft:beetroot", "2", "0.8" },
                        { "minecraft:beetroot_soup", "6", "2.4" },
                        { "minecraft:mushroom_stew", "6", "2.4" },
                        { "minecraft:rabbit_stew", "10", "4" },
                        { "minecraft:suspicious_stew", "8", "3.2" },
                        { "minecraft:melon_slice", "1", "0.4" },
                        { "minecraft:glistering_melon_slice", "8", "3.2" },
                        { "minecraft:sweet_berries", "2", "0.8" },
                        { "minecraft:glow_berries", "3", "1.2" },
                        { "minecraft:chorus_fruit", "4", "1.6" },
                        { "minecraft:popped_chorus_fruit", "4", "1.6" },
                        { "minecraft:dried_kelp", "2", "0.8" },
                        { "minecraft:cookie", "3", "1.2" },
                        { "minecraft:pumpkin_pie", "8", "3.2" },
                        { "minecraft:cake", "15", "6" },
                        { "minecraft:honey_bottle", "6", "2.4" },
                        { "minecraft:beef", "5", "2" },
                        { "minecraft:cooked_beef", "8", "3.2" },
                        { "minecraft:porkchop", "5", "2" },
                        { "minecraft:cooked_porkchop", "8", "3.2" },
                        { "minecraft:chicken", "4", "1.6" },
                        { "minecraft:cooked_chicken", "7", "2.8" },
                        { "minecraft:mutton", "4", "1.6" },
                        { "minecraft:cooked_mutton", "7", "2.8" },
                        { "minecraft:rabbit", "4", "1.6" },
                        { "minecraft:cooked_rabbit", "7", "2.8" },
                        { "minecraft:cod", "4", "1.6" },
                        { "minecraft:cooked_cod", "7", "2.8" },
                        { "minecraft:salmon", "4", "1.6" },
                        { "minecraft:cooked_salmon", "7", "2.8" },
                        { "minecraft:tropical_fish", "6", "2.4" },
                        { "minecraft:pufferfish", "6", "2.4" },
                        { "minecraft:rotten_flesh", "1", "0.4" },
                        { "minecraft:spider_eye", "2", "0.8" },
                }
        ));

        categories.add(buildCategory(
                "&2Plants & Flowers",
                "minecraft:poppy",
                new String[][]{
                        { "minecraft:dandelion", "2", "0.8" },
                        { "minecraft:poppy", "2", "0.8" },
                        { "minecraft:blue_orchid", "2", "0.8" },
                        { "minecraft:allium", "2", "0.8" },
                        { "minecraft:azure_bluet", "2", "0.8" },
                        { "minecraft:red_tulip", "2", "0.8" },
                        { "minecraft:orange_tulip", "2", "0.8" },
                        { "minecraft:white_tulip", "2", "0.8" },
                        { "minecraft:pink_tulip", "2", "0.8" },
                        { "minecraft:oxeye_daisy", "2", "0.8" },
                        { "minecraft:cornflower", "2", "0.8" },
                        { "minecraft:lily_of_the_valley", "2", "0.8" },
                        { "minecraft:torchflower", "8", "3.2" },
                        { "minecraft:torchflower_seeds", "6", "2.4" },
                        { "minecraft:wither_rose", "10", "4" },
                        { "minecraft:sunflower", "3", "1.2" },
                        { "minecraft:lilac", "3", "1.2" },
                        { "minecraft:rose_bush", "3", "1.2" },
                        { "minecraft:peony", "3", "1.2" },
                        { "minecraft:pitcher_plant", "8", "3.2" },
                        { "minecraft:pitcher_pod", "6", "2.4" },
                        { "minecraft:pink_petals", "2", "0.8" },
                        { "minecraft:spore_blossom", "8", "3.2" },
                        { "minecraft:brown_mushroom", "2", "0.8" },
                        { "minecraft:red_mushroom", "2", "0.8" },
                        { "minecraft:brown_mushroom_block", "3", "1.2" },
                        { "minecraft:red_mushroom_block", "3", "1.2" },
                        { "minecraft:mushroom_stem", "3", "1.2" },
                        { "minecraft:fern", "2", "0.8" },
                        { "minecraft:large_fern", "2", "0.8" },
                        { "minecraft:short_grass", "1", "0.4" },
                        { "minecraft:tall_grass", "1", "0.4" },
                        { "minecraft:dead_bush", "1", "0.4" },
                        { "minecraft:seagrass", "2", "0.8" },
                        { "minecraft:sea_pickle", "4", "1.6" },
                        { "minecraft:kelp", "2", "0.8" },
                        { "minecraft:lily_pad", "3", "1.2" },
                        { "minecraft:vine", "2", "0.8" },
                        { "minecraft:glow_lichen", "3", "1.2" },
                        { "minecraft:hanging_roots", "2", "0.8" },
                        { "minecraft:big_dripleaf", "5", "2" },
                        { "minecraft:small_dripleaf", "4", "1.6" },
                        { "minecraft:azalea", "5", "2" },
                        { "minecraft:flowering_azalea", "6", "2.4" },
                        { "minecraft:azalea_leaves", "2", "0.8" },
                        { "minecraft:flowering_azalea_leaves", "2", "0.8" },
                        { "minecraft:cactus", "3", "1.2" },
                        { "minecraft:sugar_cane", "2", "0.8" },
                        { "minecraft:cocoa_beans", "3", "1.2" },
                }
        ));

        categories.add(buildCategory(
                "&2Seeds",
                "minecraft:wheat_seeds",
                new String[][]{
                        { "minecraft:wheat_seeds", "1", "0.4" },
                        { "minecraft:beetroot_seeds", "1", "0.4" },
                        { "minecraft:melon_seeds", "2", "0.8" },
                        { "minecraft:pumpkin_seeds", "2", "0.8" },
                        { "minecraft:bone_meal", "2", "0.8" },
                        { "minecraft:bone", "3", "1.2" },
                        { "minecraft:egg", "2", "0.8" },
                        { "minecraft:sugar", "2", "0.8" },
                        { "minecraft:nether_wart", "6", "2.4" },
                }
        ));

        categories.add(buildCategory(
                "&5Mob Drops",
                "minecraft:ender_pearl",
                new String[][]{
                        { "minecraft:string", "2", "0.8" },
                        { "minecraft:feather", "2", "0.8" },
                        { "minecraft:leather", "4", "1.6" },
                        { "minecraft:rabbit_hide", "2", "0.8" },
                        { "minecraft:rabbit_foot", "8", "3.2" },
                        { "minecraft:gunpowder", "4", "1.6" },
                        { "minecraft:ender_pearl", "12", "4.8" },
                        { "minecraft:ender_eye", "20", "8" },
                        { "minecraft:blaze_rod", "15", "6" },
                        { "minecraft:blaze_powder", "8", "3.2" },
                        { "minecraft:ghast_tear", "40", "16" },
                        { "minecraft:magma_cream", "8", "3.2" },
                        { "minecraft:slime_ball", "4", "1.6" },
                        { "minecraft:phantom_membrane", "12", "4.8" },
                        { "minecraft:nautilus_shell", "30", "12" },
                        { "minecraft:heart_of_the_sea", "200", null },
                        { "minecraft:prismarine_shard", "4", "1.6" },
                        { "minecraft:prismarine_crystals", "6", "2.4" },
                        { "minecraft:shulker_shell", "40", "16" },
                        { "minecraft:nether_star", "800", null },
                        { "minecraft:dragon_breath", "30", "12" },
                        { "minecraft:wither_skeleton_skull", "120", "48" },
                        { "minecraft:skeleton_skull", "40", "16" },
                        { "minecraft:zombie_head", "40", "16" },
                        { "minecraft:creeper_head", "40", "16" },
                        { "minecraft:piglin_head", "40", "16" },
                        { "minecraft:dragon_head", "200", "80" },
                        { "minecraft:honeycomb", "4", "1.6" },
                        { "minecraft:ink_sac", "3", "1.2" },
                        { "minecraft:glow_ink_sac", "5", "2" },
                        { "minecraft:turtle_scute", "8", "3.2" },
                        { "minecraft:armadillo_scute", "6", "2.4" },
                        { "minecraft:echo_shard", "30", "12" },
                        { "minecraft:breeze_rod", "25", "10" },
                        { "minecraft:wind_charge", "4", "1.6" },
                        { "minecraft:experience_bottle", "15", "6" },
                        { "minecraft:fire_charge", "6", "2.4" },
                        { "minecraft:firework_rocket", "6", "2.4" },
                        { "minecraft:firework_star", "8", "3.2" },
                        { "minecraft:paper", "2", "0.8" },
                        { "minecraft:book", "6", "2.4" },
                        { "minecraft:writable_book", "8", "3.2" },
                        { "minecraft:map", "8", "3.2" },
                        { "minecraft:glass_bottle", "3", "1.2" },
                        { "minecraft:bowl", "2", "0.8" },
                        { "minecraft:bundle", "15", "6" },
                        { "minecraft:goat_horn", "40", "16" },
                        { "minecraft:disc_fragment_5", "20", "8" },
                }
        ));

        categories.add(buildCategory(
                "&fMiscellaneous",
                "minecraft:chest",
                new String[][]{
                        { "minecraft:cobweb", "8", "3.2" },
                        { "minecraft:turtle_egg", "20", "8" },
                        { "minecraft:sniffer_egg", "60", "24" },
                        { "minecraft:frogspawn", "8", "3.2" },
                        { "minecraft:ominous_bottle", "30", "12" },
                        { "minecraft:music_disc_13", "40", null },
                        { "minecraft:music_disc_cat", "40", null },
                        { "minecraft:music_disc_blocks", "40", null },
                        { "minecraft:music_disc_chirp", "40", null },
                        { "minecraft:music_disc_far", "40", null },
                        { "minecraft:music_disc_mall", "40", null },
                        { "minecraft:music_disc_mellohi", "40", null },
                        { "minecraft:music_disc_stal", "40", null },
                        { "minecraft:music_disc_strad", "40", null },
                        { "minecraft:music_disc_ward", "40", null },
                        { "minecraft:music_disc_11", "40", null },
                        { "minecraft:music_disc_wait", "40", null },
                        { "minecraft:music_disc_otherside", "60", null },
                        { "minecraft:music_disc_5", "60", null },
                        { "minecraft:music_disc_pigstep", "80", null },
                        { "minecraft:music_disc_relic", "80", null },
                        { "minecraft:music_disc_creator", "80", null },
                        { "minecraft:music_disc_creator_music_box", "80", null },
                        { "minecraft:music_disc_precipice", "80", null },
                }
        ));

        categories.add(buildCategory(
                "&bSpecial Items",
                "minecraft:emerald",
                new String[][]{
                        { "mmoecon:sell_wand", "35000", null, "sell_wand" },
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

    /**
     * Builds a category JsonObject from a 2D array of rows. A row may be
     * { id }, { id, buyPrice }, { id, buyPrice, sellPrice }, or
     * { id, buyPrice, sellPrice, special }. Any absent or null price is
     * omitted; an absent or null special is omitted. An omitted sellPrice
     * therefore defaults to null (item can't be sold).
     */
    private static JsonObject buildCategory(String name, String representativeItem, String[][] items) {
        JsonObject cat = new JsonObject();
        cat.addProperty("name", name);
        cat.addProperty("representativeItem", representativeItem);

        JsonArray itemsArray = new JsonArray();
        for (String[] entry : items) {
            JsonObject item = new JsonObject();
            item.addProperty("id", entry[0]);
            if (entry.length > 1 && entry[1] != null) item.addProperty("buyPrice",  new java.math.BigDecimal(entry[1]));
            if (entry.length > 2 && entry[2] != null) item.addProperty("sellPrice", new java.math.BigDecimal(entry[2]));
            if (entry.length > 3 && entry[3] != null) item.addProperty("special", entry[3]);
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

    /**
     * Returns the first ShopItem with the special key, or null if none.
     * Used to resolve items by their role rather than registry ID,
     * since special items share an underlying vanilla item (the sell wand is a blaze rod)
     * and must not be priced off that underlying item's entry.
     */
    public static ShopItem findSpecial(String special) {
        for (ShopItem item : getAllItems()) {
            if (special.equals(item.special)) return item;
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
