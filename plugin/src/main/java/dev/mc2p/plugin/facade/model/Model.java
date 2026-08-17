package dev.mc2p.plugin.facade.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure data model shared by the ServerFacade interface and tool handlers. */
public final class Model {

    private Model() {}

    public record Tps(double oneMin, double fiveMin, double fifteenMin) {}

    public record Status(
            String serverId,
            String minecraftVersion,
            String paperVersion,
            String pluginVersion,
            Tps tps,
            long tick,
            long uptimeSeconds,
            int online,
            int max,
            boolean whitelistEnabled,
            boolean onlineMode,
            List<String> worlds,
            List<String> plugins,
            long heapUsedBytes,
            long heapMaxBytes,
            String restartStrategy,
            boolean restartAvailable) {

        public Map<String, Object> toMap() {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("serverId", serverId);
            m.put("minecraftVersion", minecraftVersion);
            m.put("paperVersion", paperVersion);
            m.put("pluginVersion", pluginVersion);
            m.put("tps", Map.of("1m", tps.oneMin(), "5m", tps.fiveMin(), "15m", tps.fifteenMin()));
            m.put("tick", tick);
            m.put("uptimeSeconds", uptimeSeconds);
            m.put("online", online);
            m.put("max", max);
            m.put("whitelist", whitelistEnabled);
            m.put("onlineMode", onlineMode);
            m.put("worlds", worlds);
            m.put("plugins", plugins);
            m.put("heapUsedBytes", heapUsedBytes);
            m.put("heapMaxBytes", heapMaxBytes);
            m.put("restartStrategy", restartStrategy);
            m.put("restartAvailable", restartAvailable);
            return m;
        }
    }

    public record WorldInfo(String name, String dimension, int[] spawn, int loadedChunks) {

        public Map<String, Object> toMap() {
            return Map.of(
                    "name", name,
                    "dimension", dimension,
                    "spawn", Map.of("x", spawn[0], "y", spawn[1], "z", spawn[2]),
                    "loadedChunks", loadedChunks);
        }
    }

    public record PluginInfo(String name, String version, boolean enabled) {

        public Map<String, Object> toMap() {
            return Map.of("name", name, "version", version == null ? "" : version, "enabled", enabled);
        }
    }

    public record PlayerInfo(
            UUID uuid,
            String name,
            int ping,
            String gamemode,
            double health,
            int food,
            int level,
            double x,
            double y,
            double z,
            String world) {

        public Map<String, Object> toMap() {
            return Map.of(
                    "uuid", uuid.toString(),
                    "name", name,
                    "ping", ping,
                    "gamemode", gamemode,
                    "health", health,
                    "food", food,
                    "level", level,
                    "location", Map.of("x", x, "y", y, "z", z, "world", world));
        }
    }

    public record PlayerDetails(PlayerInfo base, List<String> effects, boolean isOp) {

        public Map<String, Object> toMap() {
            Map<String, Object> result = new java.util.LinkedHashMap<>(base.toMap());
            result.put("effects", effects);
            result.put("isOp", isOp);
            return result;
        }
    }

    public record StatsInfo(Map<String, Object> stats) {

        public Map<String, Object> toMap() {
            return stats;
        }
    }

    public record BlockInfo(
            String world,
            int x,
            int y,
            int z,
            String material,
            String blockData,
            String biome,
            int light,
            int skyLight,
            boolean chunkLoaded) {

        public Map<String, Object> toMap() {
            return Map.of(
                    "world", world,
                    "position", Map.of("x", x, "y", y, "z", z),
                    "material", material,
                    "blockData", blockData,
                    "biome", biome,
                    "light", light,
                    "skyLight", skyLight,
                    "chunkLoaded", chunkLoaded);
        }
    }

    public record EntityInfo(
            UUID uuid, String type, double x, double y, double z, String world, double health, String name) {

        public Map<String, Object> toMap() {
            return Map.of(
                    "uuid",
                    uuid.toString(),
                    "type",
                    type,
                    "position",
                    Map.of("x", x, "y", y, "z", z),
                    "world",
                    world,
                    "health",
                    health,
                    "name",
                    name == null ? "" : name);
        }
    }

    public record EntityDetails(EntityInfo base, List<String> passengers, String vehicle) {

        public Map<String, Object> toMap() {
            Map<String, Object> result = new java.util.LinkedHashMap<>(base.toMap());
            result.put("passengers", passengers);
            result.put("vehicle", vehicle == null ? "" : vehicle);
            return result;
        }
    }

    public record CommandResult(boolean ok, String output, String error) {

        public Map<String, Object> toMap() {
            return Map.of("ok", ok, "output", output == null ? "" : output, "error", error == null ? "" : error);
        }
    }
}
