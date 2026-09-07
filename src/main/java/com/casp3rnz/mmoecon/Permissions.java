package com.casp3rnz.mmoecon;

import net.minecraft.commands.CommandSourceStack;

/**
 * Central permission definitions for MMOEcon.
 *
 * <p>This is the Fabric counterpart to the NeoForge {@code Permissions} class.
 * NeoForge has a first-party {@code PermissionAPI} with registered
 * {@code PermissionNode}s; Fabric's equivalent is Lucko's
 * <a href="https://github.com/lucko/fabric-permissions-api">fabric-permissions-api</a>,
 * which LuckPerms (and other permission mods) hook into automatically and which
 * falls back to vanilla OP levels when none is installed. Nodes there are plain
 * strings — there is nothing to register — so this class is just the node
 * catalogue plus a small {@link #check} helper.
 *
 * <p>Nodes (all boolean), matching the NeoForge build one-for-one:
 * <pre>
 *   mmoecon.command.bal       — /bal, /money, /bal top       (default: everyone)
 *   mmoecon.command.pay       — /pay                         (default: everyone)
 *   mmoecon.command.shop      — /shop                        (default: everyone)
 *   mmoecon.command.sell      — /sell hand | inv             (default: everyone)
 *   mmoecon.command.ah        — /ah, /auction, /auctionhouse (default: everyone)
 *   mmoecon.admin.reload      — /shop reload                 (default: OP, level 2)
 *   mmoecon.admin.sellwand    — /sellwand give               (default: OP, level 2)
 * </pre>
 */
public final class Permissions {

    /** Vanilla OP level required by admin commands when no permission mod is present. */
    public static final int OP_LEVEL = 2;

    // Player-facing commands: available to everyone by default.
    public static final String BAL      = node("command.bal");
    public static final String PAY      = node("command.pay");
    public static final String SHOP     = node("command.shop");
    public static final String SELL     = node("command.sell");
    public static final String AH       = node("command.ah");

    // Admin commands: OP by default.
    public static final String RELOAD   = node("admin.reload");
    public static final String SELLWAND = node("admin.sellwand");

    /**
     * Predicate for Brigadier's {@code .requires(...)}. Resolves the node through
     * fabric-permissions-api, so a permission mod's grant is honoured; when no
     * permission mod is installed the API falls back to the vanilla permission
     * level, so {@code opLevel} is the default the node resolves to. Passing an
     * {@code opLevel} of 0 means "everyone" (any source passes level 0).
     *
     * <p>The API's own {@code check(CommandSourceStack, String, int)} already
     * handles non-player sources (console, command blocks, functions) by their
     * permission level, so — unlike the NeoForge helper — no explicit player
     * branch is needed here.
     */
    public static boolean check(CommandSourceStack src, String node, int opLevel) {
        return me.lucko.fabric.api.permissions.v0.Permissions.check(src, node, opLevel);
    }

    private static String node(String path) {
        return MMOEcon.MOD_ID + "." + path;
    }

    private Permissions() {}
}
