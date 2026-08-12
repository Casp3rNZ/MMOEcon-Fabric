package com.casp3rnz.mmoecon;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Central resolver for the mod's on-disk paths.
 * On NeoForge these lived behind {@code FMLPaths.CONFIGDIR}; Fabric exposes the
 * same directory via {@link FabricLoader#getConfigDir()}. Routing every file
 * path through here keeps the rest of the mod loader-agnostic and guarantees the
 * data layout stays identical to the NeoForge build:
 *   <server>/config/mmoecon/…
 * so balances, the shop JSON and the transaction log are cross-loader compatible.
 */
public final class ModPaths {

    /** The mod's own subfolder under the instance config directory: config/mmoecon/. */
    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(MMOEcon.MOD_ID);
    }

    /** Resolve a file beneath config/mmoecon/. */
    public static Path config(String fileName) {
        return configDir().resolve(fileName);
    }

    private ModPaths() {}
}
