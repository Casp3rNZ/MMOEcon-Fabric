package com.casp3rnz.mmoecon;

import net.minecraft.network.chat.Component;

/**
 * Builds the mod's player-facing chat messages so they share one look.
 * The scheme is deliberately small — players should be able to tell what kind of
 * message they got from the colour alone, without reading it:
 *   success  green    something worked, money went up
 *   error    red      it didn't work, nothing changed
 *   info     yellow   neutral state (balances, prompts)
 * Money and item names are highlighted inside a sentence so the numbers are
 * scannable in a busy chat.
 * Every message carries the same dark-grey prefix so mod output is
 * distinguishable from other mods and vanilla server messages.
 */
public final class Messages {

    private static final String PREFIX = "§8[§6MMO Econ§8]§r ";
    // Colours
    private static final String SUCCESS   = "§a";
    private static final String ERROR     = "§c";
    private static final String INFO      = "§e";
    private static final String BODY      = "§7";
    private static final String HIGHLIGHT = "§f";
    private static final String MONEY     = "§a";

    public static Component success(String text) {
        return Component.literal(PREFIX + SUCCESS + text);
    }

    public static Component error(String text) {
        return Component.literal(PREFIX + ERROR + text);
    }

    public static Component info(String text) {
        return Component.literal(PREFIX + INFO + text);
    }

    /** Body text with no leading colour of its own — for sentences built via money()/item(). */
    public static Component body(String text) {
        return Component.literal(PREFIX + BODY + text);
    }

    /** "$1,234.50" in money green, returning to body grey afterwards. */
    public static String money(long cents) {
        return MONEY + "$" + Money.format(cents) + BODY;
    }

    /** An item name or count, highlighted white, returning to body grey afterwards. */
    public static String item(String name) {
        return HIGHLIGHT + name + BODY;
    }

    private Messages() {}
}
