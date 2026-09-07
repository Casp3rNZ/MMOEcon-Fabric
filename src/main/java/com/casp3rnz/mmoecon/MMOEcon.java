package com.casp3rnz.mmoecon;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entrypoint for MMO Econ.
 *
 * This is the Fabric port of the NeoForge {@code @Mod}-annotated main class. Where
 * NeoForge registered listener classes on {@code NeoForge.EVENT_BUS} with
 * {@code @SubscribeEvent}, Fabric registers explicit callbacks here, each
 * delegating into the same game-logic classes as the NeoForge build.
 *
 * Startup ordering is made explicit (NeoForge guaranteed SERVER config was loaded
 * before {@code ServerAboutToStartEvent}; here we load config first ourselves):
 *   1. Config.load()          — options available to everything below
 *   2. PlayerBalanceManager.load()
 *   3. ShopItemManager.load() — only if the GUI shop is enabled
 */
public class MMOEcon implements ModInitializer {

    public static final String MOD_ID = "mmoecon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // ── Server lifecycle: capture server ref, load data, save on stop ────────
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ModServer.set(server);

            Config.load();
            PlayerBalanceManager.load();
            if (Config.ENABLE_GUI_SHOP.get()) {
                ShopItemManager.load();
            }
            AuctionHouseManager.load(server);
            LOGGER.info("MMOEcon server starting — config loaded.");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PlayerBalanceManager.saveIfDirty();
            AuctionHouseManager.saveIfDirty();
        });

        // Clear the cached server reference once it is fully stopped.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ModServer.set(null));

        // ── Player join: ensure a starting balance exists ───────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PlayerBalanceManager.onPlayerJoin(handler.getPlayer().getUUID()));

        // ── Server tick: playtime rewards + throttled balance flush ─────────────
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PlaytimeRewardListener.tick(server);
            PlayerBalanceManager.flushTick(server);
            AuctionHouseManager.flushTick(server);
        });

        // ── Commands ────────────────────────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EconomyCommands.register(dispatcher));

        // ── Sell wand: right-click a container to sell its contents ─────────────
        UseBlockCallback.EVENT.register(SellWandListener::onUseBlock);

        LOGGER.info("MMO Econ initialised.");
    }
}
