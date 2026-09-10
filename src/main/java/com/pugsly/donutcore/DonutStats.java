package com.pugsly.donutcore;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DonutStats {
    private static final Map<UUID, Integer> KILLS = new HashMap<>();
    private static final Map<UUID, Integer> DEATHS = new HashMap<>();
    private static final Map<UUID, Long> PLAYTIME = new HashMap<>();
    private static long ticks;

    private DonutStats() {}

    public static void init() {
        ServerPlayerEvents.ALLOW_DEATH.register((player, damageSource, damageAmount) -> {
            DEATHS.merge(player.getUUID(), 1, Integer::sum);
            if (damageSource.getEntity() instanceof ServerPlayer killer && killer != player) {
                KILLS.merge(killer.getUUID(), 1, Integer::sum);
            }
            return true;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ticks++;
            if (ticks % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PLAYTIME.merge(player.getUUID(), 1L, Long::sum);
                updateSidebar(server, player);
            }
        });
    }

    public static int kills(UUID uuid) { return KILLS.getOrDefault(uuid, 0); }
    public static int deaths(UUID uuid) { return DEATHS.getOrDefault(uuid, 0); }
    public static long playtimeSeconds(UUID uuid) { return PLAYTIME.getOrDefault(uuid, 0L); }

    private static void updateSidebar(MinecraftServer server, ServerPlayer player) {
        String money = DonutCore.format(DonutCore.getBalance(player));
        String shards = String.valueOf(DonutCore.getShards(player));
        String kills = String.valueOf(kills(player.getUUID()));
        String deaths = String.valueOf(deaths(player.getUUID()));
        String playtime = formatTime(playtimeSeconds(player.getUUID()));

        run(server, "scoreboard objectives add donut dummy \"DONUT SMP\"");
        run(server, "scoreboard objectives setdisplay sidebar donut");
        run(server, "scoreboard players reset * donut");
        run(server, "scoreboard players set \"Balance: $" + money + "\" donut 5");
        run(server, "scoreboard players set \"Shards: " + shards + "\" donut 4");
        run(server, "scoreboard players set \"Kills: " + kills + "\" donut 3");
        run(server, "scoreboard players set \"Deaths: " + deaths + "\" donut 2");
        run(server, "scoreboard players set \"Playtime: " + playtime + "\" donut 1");
    }

    private static void run(MinecraftServer server, String command) {
        // Minecraft 26.2 uses PermissionSet rather than the old integer permission level.
        // The server's command source already carries the appropriate server permissions.
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private static String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}
