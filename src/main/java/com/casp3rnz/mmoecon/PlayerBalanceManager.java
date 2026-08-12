package com.casp3rnz.mmoecon;

import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  Manages per-player balances, persisted to config/mmoecon/balances.dat.
 *  The file format is identical to the NeoForge build, so balances.dat is
 *  cross-loader compatible: a world can move between the NeoForge and Fabric
 *  versions of the mod without losing balances.
 *  Lifecycle hooks are driven from {@link MMOEcon} (Fabric event callbacks) rather
 *  than annotations:
 *    - {@link #load()}       on SERVER_STARTING / SERVER_STARTED
 *    - {@link #onPlayerJoin(UUID)} on player connect
 *    - {@link #flushTick(MinecraftServer)} each server tick (throttled)
 *    - {@link #saveIfDirty()} on SERVER_STOPPING
 */
public class PlayerBalanceManager {

    private static final Path BALANCE_FILE = ModPaths.config("balances.dat");

    /**
     * Marks a file as using the versioned (v2+) format.
     * The v1 format had no header and began directly with the player count, so any first int that
     * isn't this magic identifies a legacy file.
     * The value is deliberately far larger than any plausible player count
     * (~1.3 billion), so the two formats can never be confused.
     */
    private static final int MAGIC = 0x4D4D4F45; // "MMOE"

    /** v1: float balances, no header.  v2: long cents, magic + version header. */
    private static final int FORMAT_VERSION = 2;

    /** Balances in cents. See Money for why this is never floating point. */
    private static final Map<UUID, Long> balances = new ConcurrentHashMap<>();

    private static volatile boolean dirty = false;
    private static final long FLUSH_INTERVAL_TICKS = 1200L; // ~60s

    // Init

    /**
     * Loads balances from disk. Called on server start, before any player can join.
     */
    public static void load() {
        balances.clear();
        if(!Files.exists(BALANCE_FILE)) {
            MMOEcon.LOGGER.info("No balance file found, starting fresh.");
            return;
        }
        try (DataInputStream in = new DataInputStream(new FileInputStream(BALANCE_FILE.toFile()))) {
            int first = in.readInt();

            if (first == MAGIC) {
                int version = in.readInt();
                if (version > FORMAT_VERSION) {
                    throw new IOException("balance file is format v" + version
                            + " but this build only understands up to v" + FORMAT_VERSION
                            + " - update the mod or restore a backup");
                }
                readCents(in);
            } else {
                // Legacy v1: `first` was the entry count, already consumed.
                readLegacyFloats(in, first);
                MMOEcon.LOGGER.info("Migrated {} balance(s) from the legacy float format to cents.",
                        balances.size());
                // Persist immediately so the upgrade isn't lost if the server
                // crashes before the next scheduled flush.
                dirty = true;
                saveBalances();
            }
        } catch (IOException e) {
            MMOEcon.LOGGER.error("Failed to load balance file from {}: {}", BALANCE_FILE, e.getMessage());
        }
    }

    /** v2 payload: count, then (uuid, cents) pairs. */
    private static void readCents(DataInputStream in) throws IOException {
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            UUID uuid = UUID.fromString(in.readUTF());
            balances.put(uuid, in.readLong());
        }
    }

    /**
     * v1 payload: (uuid, float) pairs; the count was read by the caller.
     * float to double widening is exact, so no balance changes during migration.
     */
    private static void readLegacyFloats(DataInputStream in, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            UUID uuid = UUID.fromString(in.readUTF());
            balances.put(uuid, Money.fromDouble(in.readFloat()));
        }
    }

    private static void saveBalances() {
        try {
            Files.createDirectories(BALANCE_FILE.getParent());

            // Write to a temp file and move into place so an interrupted save
            // can't leave a half-written balances.dat behind.
            Path temp = BALANCE_FILE.resolveSibling(BALANCE_FILE.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(temp.toFile()))) {
                out.writeInt(MAGIC);
                out.writeInt(FORMAT_VERSION);
                out.writeInt(balances.size());
                for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
                    out.writeUTF(entry.getKey().toString());
                    out.writeLong(entry.getValue());
                }
            }
            Files.move(temp, BALANCE_FILE,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            dirty = false;
        } catch (IOException e) {
            MMOEcon.LOGGER.error("Failed to save balance file to {}: {}", BALANCE_FILE, e.getMessage());
        }
    }

    // Lifecycle entry points (called from MMOEcon)

    /**
     * Creates a starting balance for a player who has never played here.
     * Config is loaded before players can join, so STARTING_AMOUNT is available.
     */
    public static void onPlayerJoin(UUID uuid) {
        if (!balances.containsKey(uuid)) {
            long startAmount = Money.fromDouble(Config.STARTING_AMOUNT.get());
            balances.put(uuid, startAmount);
            MMOEcon.LOGGER.info("New player {}: starting balance ${}", uuid, Money.format(startAmount));
            dirty = true;
        }
    }

    /** Throttled periodic flush; call once per server tick. */
    public static void flushTick(MinecraftServer server) {
        if (dirty && server.getTickCount() % FLUSH_INTERVAL_TICKS == 0) {
            saveBalances();
        }
    }

    /** Force a save if there are unsaved changes; call on server stop. */
    public static void saveIfDirty() {
        if (dirty) saveBalances();
    }

    // Public Balance API, all amounts are in cents (see Money)

    /**
     * True if this UUID has a stored balance. An account is created when a player
     * first joins, so this is false for anyone who has never played here.
     */
    public static boolean hasAccount(UUID uuid) {
        return balances.containsKey(uuid);
    }

    public static long getBalance(UUID uuid) {
        return balances.getOrDefault(uuid, Money.fromDouble(Config.STARTING_AMOUNT.get()));
    }

    public static void setBalance(UUID uuid, long cents) {
        balances.put(uuid, Math.max(Money.ZERO, cents));
        dirty = true;
    }

    public static void addBalance(UUID uuid, long cents) {
        setBalance(uuid, getBalance(uuid) + cents);
    }

    /**
     * Subtract amount from balance. Does NOT deduct if the player lacks
     * sufficient funds. Callers should check first with hasFunds.
     */
    public static void subtractBalance(UUID uuid, long cents) {
        if(!hasFunds(uuid, cents)) return;
        setBalance(uuid, getBalance(uuid) - cents);
    }

    public static boolean hasFunds(UUID uuid, long cents) {
        return getBalance(uuid) >= cents;
    }

    /**
     * Moves money between two players atomically with respect to callers: either
     * both sides change or neither does. Returns false if the sender is short.
     *
     * Both parties must already have an account. Crediting an arbitrary UUID would
     * let anyone script transfers to random UUIDs and grow balances.dat without
     * bound with entries for players who have never joined.
     */
    public static boolean transfer(UUID from, UUID to, long cents) {
        if (cents < 0) return false;
        synchronized (balances) {
            if (!hasAccount(from) || !hasAccount(to)) return false;
            if (!hasFunds(from, cents)) return false;
            setBalance(from, getBalance(from) - cents);
            setBalance(to, getBalance(to) + cents);
        }
        return true;
    }

    // Read-only view of balance map, in cents (for baltop)
    public static Map<UUID, Long> getBalances() { return Collections.unmodifiableMap(balances); }

}
