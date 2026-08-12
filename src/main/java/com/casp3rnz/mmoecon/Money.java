package com.casp3rnz.mmoecon;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fixed-point money helpers. All balances and transaction amounts in the mod are
 * `long` counts of cents — never float or double.
 * Cents as long are exact for add/subtract/compare and hold ~92 quadrillion
 * dollars.
 * Prices from JSON/config are fractional (e.g. 0.5) and are converted
 * at the boundary with fromDouble; everything downstream stays in cents.
 */
public final class Money {

    public static final long ZERO = 0L;
    private static final int CENTS_PER_UNIT = 100;

    /**
     * Converts a decimal currency amount to cents, half-up.
     * Uses BigDecimal rather than Math.round(value * 100) because the latter
     * inherits the very representation error this class exists to avoid:
     * 8.245 is stored as 8.2449999... and would truncate to 824 instead of 825.
     */
    public static long fromDouble(double amount) {
        return BigDecimal.valueOf(amount)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** Cents back to a decimal amount. For display and config round-tripping only. */
    public static double toDouble(long cents) {
        return (double) cents / CENTS_PER_UNIT;
    }

    /**
     * Multiplies a unit price by a whole quantity. Exact — no rounding, because
     * both operands are integers.
     */
    public static long multiply(long unitCents, int quantity) {
        return unitCents * quantity;
    }

    /**
     * Human-readable amount without a currency symbol. Callers add their own "$".
     * Large values are abbreviated (10K, 1.5M) to match the previous display
     * behaviour; smaller ones render with cents only when non-zero, so whole
     * amounts stay clean ("1,000" rather than "1,000.00").
     */
    public static String format(long cents) {
        boolean negative = cents < 0;
        long abs = Math.abs(cents);

        String rendered;
        if (abs >= 1_000_000L * CENTS_PER_UNIT) {
            rendered = trim(abs / (double) (1_000_000L * CENTS_PER_UNIT)) + "M";
        } else if (abs >= 10_000L * CENTS_PER_UNIT) {
            rendered = trim(abs / (double) (1_000L * CENTS_PER_UNIT)) + "K";
        } else {
            long units = abs / CENTS_PER_UNIT;
            long remainder = abs % CENTS_PER_UNIT;
            rendered = remainder == 0
                    ? String.format("%,d", units)
                    : String.format("%,d.%02d", units, remainder);
        }

        return negative ? "-" + rendered : rendered;
    }

    private static String trim(double value) {
        String formatted = String.format("%.1f", value);
        return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
    }

    private Money() {}
}
