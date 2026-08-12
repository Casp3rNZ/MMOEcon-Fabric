package com.casp3rnz.mmoecon;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

/**
 * Holds the running {@link MinecraftServer} instance.
 *
 * NeoForge offered {@code ServerLifecycleHooks.getCurrentServer()} as a global
 * accessor; Fabric has no equivalent, so the server reference is captured from
 * the {@code ServerLifecycleEvents.SERVER_STARTING}/{@code SERVER_STOPPED}
 * callbacks (wired up in {@link MMOEcon}) and read back here.
 *
 * The field is only written on the server thread during lifecycle events and
 * read from game logic on the same thread, but it is marked volatile so a stale
 * reference can never be observed if a read ever happens off-thread.
 */
public final class ModServer {

    @Nullable
    private static volatile MinecraftServer current;

    static void set(@Nullable MinecraftServer server) {
        current = server;
    }

    /** The running server, or null before start / after stop. */
    @Nullable
    public static MinecraftServer get() {
        return current;
    }

    private ModServer() {}
}
