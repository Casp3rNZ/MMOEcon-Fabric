package com.casp3rnz.mmoecon;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Awards all online players a money reward every PLAYTIME_INTERVAL ticks.
 *
 * Driven from {@link MMOEcon}'s server-tick callback rather than a NeoForge
 * {@code @SubscribeEvent} — the logic is otherwise unchanged.
 */
public final class PlaytimeRewardListener {

    /** Call once per server tick. */
    public static void tick(MinecraftServer server) {
        if (!Config.ENABLE_PLAYTIME_REWARDS.get()) return;

        long interval = Config.PLAYTIME_INTERVAL.get();

        if (server.getTickCount() % interval != 0) return;

        // Skip the very first tick (tickCount == 0 at world load would fire immediately)
        if (server.getTickCount() == 0) return;

        long reward = Money.fromDouble(Config.PLAYTIME_REWARD.get());

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerBalanceManager.addBalance(player.getUUID(), reward);
            player.sendSystemMessage(Messages.body(
                    "You earned " + Messages.money(reward) + " for playing."));
        }
    }

    private PlaytimeRewardListener() {}
}
