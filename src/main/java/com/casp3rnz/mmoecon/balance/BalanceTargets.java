package com.casp3rnz.mmoecon.balance;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Resolves stored accounts locally, without querying external profile services. */
public final class BalanceTargets {

    public record Target(UUID uuid, String name) {
        public boolean hasUsername() { return !name.equalsIgnoreCase(uuid.toString()); }
    }

    public static List<Target> accounts(MinecraftServer server) {
        List<Target> targets = new ArrayList<>();
        for (UUID uuid : PlayerBalanceManager.getBalances().keySet()) {
            targets.add(describe(server, uuid));
        }
        targets.sort(Comparator.comparing(Target::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Target::uuid));
        return targets;
    }

    public static Target resolve(MinecraftServer server, String input) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(input);
        if (online != null && PlayerBalanceManager.hasAccount(online.getUUID())) {
            return describe(server, online.getUUID());
        }
        Target match = null;
        for (Target target : accounts(server)) {
            if (target.hasUsername() && target.name().equalsIgnoreCase(input)) {
                if (match != null) return null; // Refuse ambiguous cached names.
                match = target;
            }
        }
        return match;
    }

    public static Target describe(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return new Target(uuid, online.getGameProfile().getName());
        var cache = server.getProfileCache();
        String name = cache == null ? uuid.toString()
                : cache.get(uuid).map(profile -> profile.getName()).orElse(uuid.toString());
        return new Target(uuid, name);
    }

    private BalanceTargets() {}
}
