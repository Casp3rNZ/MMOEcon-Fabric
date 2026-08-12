package com.casp3rnz.mmoecon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Parses Minecraft's standard legacy formatting codes (the {@code &} / {@code §}
 * system) into a {@link Component} suitable for use as an item name or lore line.
 *
 * Server owners write codes with the ampersand form, e.g. {@code &6&lOres},
 * which is the convention used by most server plugins/configs. Both {@code &}
 * and the section sign {@code §} are accepted.
 *
 * Two important behaviours for shop display:
 *   - If no style is supplied, the text is rendered as normal white,
 *     not the provider's default.
 *   - The base style always sets italic = false, so names/lore applied to an
 *     ItemStack via CUSTOM_NAME / LORE do not render in the default italic that
 *     Minecraft uses for custom-named items.
 *
 * Supported codes (case-insensitive):
 *   Colors:  0-9, a-f
 *   Formats: k (obfuscated), l (bold), m (strikethrough), n (underline),
 *            o (italic), r (reset)
 */
public final class TextStyleParser {

    private static final char LEGACY_CHAR = '§'; // §

    /**
     * Parse a legacy-coded string into a Component with italic disabled by
     * default. Unstyled text becomes plain white, non-italic.
     *
     * @param text the raw string, possibly containing {@code &} / {@code §} codes
     * @return a Component ready to apply to an ItemStack
     */
    public static Component parse(String text) {
        MutableComponent result = Component.empty();
        // Base style: white + non-italic so unstyled custom names look "normal".
        Style currentStyle = Style.EMPTY.withColor(ChatFormatting.WHITE).withItalic(false);

        StringBuilder buffer = new StringBuilder();
        Style segmentStyle = currentStyle;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            boolean isCode = (c == '&' || c == LEGACY_CHAR) && i + 1 < text.length();
            if (!isCode) {
                buffer.append(c);
                continue;
            }

            char code = Character.toLowerCase(text.charAt(i + 1));
            ChatFormatting fmt = byCode(code);
            if (fmt == null) {
                // Not a recognised code — treat the marker as a literal character.
                buffer.append(c);
                continue;
            }

            // Flush the text accumulated under the previous style.
            if (!buffer.isEmpty()) {
                result.append(Component.literal(buffer.toString()).withStyle(segmentStyle));
                buffer.setLength(0);
            }

            currentStyle = applyCode(currentStyle, fmt);
            segmentStyle = currentStyle;
            i++; // consume the code character
        }

        if (!buffer.isEmpty()) {
            result.append(Component.literal(buffer.toString()).withStyle(segmentStyle));
        }

        return result;
    }

    /**
     * Applies a single formatting code to a style. A color code resets any
     * previously applied formatting (matching vanilla behaviour), while {@code r}
     * resets fully back to the white/non-italic base.
     */
    private static Style applyCode(Style style, ChatFormatting fmt) {
        if (fmt == ChatFormatting.RESET) {
            return Style.EMPTY.withColor(ChatFormatting.WHITE).withItalic(false);
        }
        if (fmt.isColor()) {
            // A color in vanilla clears formatting; keep italic disabled as our base.
            return Style.EMPTY.withColor(fmt).withItalic(false);
        }
        // Formatting code
        return switch (fmt) {
            case OBFUSCATED   -> style.withObfuscated(true);
            case BOLD         -> style.withBold(true);
            case STRIKETHROUGH-> style.withStrikethrough(true);
            case UNDERLINE    -> style.withUnderlined(true);
            case ITALIC       -> style.withItalic(true);
            default           -> style;
        };
    }

    private static ChatFormatting byCode(char code) {
        for (ChatFormatting fmt : ChatFormatting.values()) {
            if (fmt.getChar() == code) return fmt;
        }
        return null;
    }

    private TextStyleParser() {}
}
