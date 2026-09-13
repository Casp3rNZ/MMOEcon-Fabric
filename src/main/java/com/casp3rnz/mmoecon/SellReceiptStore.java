package com.casp3rnz.mmoecon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds each player's most recent sale receipt so the click to view chat
 * button has something to open. In-memory and last-sale-only: a new sale
 * overwrites the previous one, and everything is lost on server restart.
 */
public final class SellReceiptStore {

    private static final Map<UUID, SellReceipt> receipts = new ConcurrentHashMap<>();

    /** Records the player's latest sale, replacing any previous receipt. */
    public static void put(UUID playerUUID, SellReceipt receipt) {
        receipts.put(playerUUID, receipt);
    }

    /** The player's latest sale receipt, or null if they have none this session. */
    public static SellReceipt get(UUID playerUUID) {
        return receipts.get(playerUUID);
    }

    private SellReceiptStore() {}
}
