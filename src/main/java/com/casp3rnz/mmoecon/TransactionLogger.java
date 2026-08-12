package com.casp3rnz.mmoecon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Logs buy/sell transactions to config/mmoecon/transactions.log.
 * Uses java.util.logging (separate from the SLF4J LOGGER) so transaction history
 * lives in its own file rather than being mixed into the server log.
 * The logger is initialised lazily on first use so the config dir is guaranteed ready.
 */
public final class TransactionLogger {

    private static final Path LOG_PATH = ModPaths.config("transactions.log");

    private static Logger logger;

    private static Logger getLogger() {
        if (logger == null) {
            logger = Logger.getLogger("mmoecon.transactions");
            logger.setUseParentHandlers(false); // don't also write to console
            try {
                Files.createDirectories(LOG_PATH.getParent());
                FileHandler handler = new FileHandler(LOG_PATH.toString(), /* append */ true);
                handler.setFormatter(new SimpleFormatter());
                logger.addHandler(handler);
            } catch (IOException e) {
                // Fall back to using the mod logger if file handler fails
                MMOEcon.LOGGER.error("Could not initialise transaction log: {}", e.getMessage());
            }
        }
        return logger;
    }

    public static void log(String message) {
        getLogger().info(message);
    }

    private TransactionLogger() {}
}
