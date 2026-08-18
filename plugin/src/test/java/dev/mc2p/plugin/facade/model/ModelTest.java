package dev.mc2p.plugin.facade.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelTest {

    @Test
    void tpsAccessors() {
        Model.Tps tps = new Model.Tps(1.0, 2.0, 3.0);
        assertEquals(1.0, tps.oneMin());
        assertEquals(2.0, tps.fiveMin());
        assertEquals(3.0, tps.fifteenMin());
    }

    @Test
    void statusToMap() {
        Model.Status status = new Model.Status(
                "main-01",
                "1.21.4",
                "1.21.4-121",
                "1.0.0",
                new Model.Tps(20.0, 19.5, 19.0),
                12345L,
                3600L,
                3,
                10,
                true,
                false,
                List.of("world", "world_nether"),
                List.of("essentials", "mc2p"),
                1024L,
                2048L,
                "auto",
                true);
        Map<String, Object> m = status.toMap();
        assertEquals("main-01", m.get("serverId"));
        assertEquals("1.21.4", m.get("minecraftVersion"));
        assertEquals("1.21.4-121", m.get("paperVersion"));
        assertEquals("1.0.0", m.get("pluginVersion"));
        assertEquals(Map.of("1m", 20.0, "5m", 19.5, "15m", 19.0), m.get("tps"));
        assertEquals(12345L, m.get("tick"));
        assertEquals(3600L, m.get("uptimeSeconds"));
        assertEquals(3, m.get("online"));
        assertEquals(10, m.get("max"));
        assertEquals(true, m.get("whitelist"));
        assertEquals(false, m.get("onlineMode"));
        assertEquals(List.of("world", "world_nether"), m.get("worlds"));
        assertEquals(List.of("essentials", "mc2p"), m.get("plugins"));
        assertEquals(1024L, m.get("heapUsedBytes"));
        assertEquals(2048L, m.get("heapMaxBytes"));
        assertEquals("auto", m.get("restartStrategy"));
        assertEquals(true, m.get("restartAvailable"));
    }

    @Test
    void worldInfoToMap() {
        Model.WorldInfo w = new Model.WorldInfo("world", "overworld", new int[] {1, 64, -2}, 42);
        Map<String, Object> m = w.toMap();
        assertEquals("world", m.get("name"));
        assertEquals("overworld", m.get("dimension"));
        assertEquals(Map.of("x", 1, "y", 64, "z", -2), m.get("spawn"));
        assertEquals(42, m.get("loadedChunks"));
    }

    @Test
    void pluginInfoNullVersionBecomesEmpty() {
        Model.PluginInfo p = new Model.PluginInfo("mc2p", null, true);
        assertEquals(Map.of("name", "mc2p", "version", "", "enabled", true), p.toMap());
        assertEquals("mc2p", p.name());
        assertEquals(true, p.enabled());
    }

    @Test
    void playerInfoToMap() {
        UUID uuid = UUID.randomUUID();
        Model.PlayerInfo p =
                new Model.PlayerInfo(uuid, "alice", 42, "survival", 20.0, 20, 100, 1.5, 64.0, -3.25, "world");
        Map<String, Object> m = p.toMap();
        assertEquals(uuid.toString(), m.get("uuid"));
        assertEquals("alice", m.get("name"));
        assertEquals(42, m.get("ping"));
        assertEquals("survival", m.get("gamemode"));
        assertEquals(20.0, m.get("health"));
        assertEquals(20, m.get("food"));
        assertEquals(100, m.get("level"));
        assertEquals(Map.of("x", 1.5, "y", 64.0, "z", -3.25, "world", "world"), m.get("location"));
    }

    @Test
    void playerDetailsToMapExtendsBase() {
        Model.PlayerInfo base = new Model.PlayerInfo(
                UUID.randomUUID(), "alice", 42, "survival", 20.0, 20, 100, 1.5, 64.0, -3.25, "world");
        Model.PlayerDetails d = new Model.PlayerDetails(base, List.of("speed"), true);
        Map<String, Object> m = d.toMap();
        assertEquals("alice", m.get("name"));
        assertEquals(List.of("speed"), m.get("effects"));
        assertEquals(true, m.get("isOp"));
    }

    @Test
    void statsInfoReturnsSameMap() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("walk", 10);
        Model.StatsInfo s = new Model.StatsInfo(stats);
        assertSame(stats, s.toMap());
    }

    @Test
    void blockInfoToMap() {
        Model.BlockInfo b =
                new Model.BlockInfo("world", 10, 64, -20, "stone", "minecraft:stone", "plains", 15, 15, true);
        Map<String, Object> m = b.toMap();
        assertEquals("world", m.get("world"));
        assertEquals(Map.of("x", 10, "y", 64, "z", -20), m.get("position"));
        assertEquals("stone", m.get("material"));
        assertEquals("minecraft:stone", m.get("blockData"));
        assertEquals("plains", m.get("biome"));
        assertEquals(15, m.get("light"));
        assertEquals(15, m.get("skyLight"));
        assertEquals(true, m.get("chunkLoaded"));
    }

    @Test
    void entityInfoNullNameBecomesEmpty() {
        UUID uuid = UUID.randomUUID();
        Model.EntityInfo e = new Model.EntityInfo(uuid, "zombie", 1.0, 2.0, 3.0, "world", 10.0, null);
        Map<String, Object> m = e.toMap();
        assertEquals(uuid.toString(), m.get("uuid"));
        assertEquals("zombie", m.get("type"));
        assertEquals(Map.of("x", 1.0, "y", 2.0, "z", 3.0), m.get("position"));
        assertEquals("world", m.get("world"));
        assertEquals(10.0, m.get("health"));
        assertEquals("", m.get("name"));
    }

    @Test
    void entityDetailsNullVehicleBecomesEmpty() {
        Model.EntityInfo base =
                new Model.EntityInfo(UUID.randomUUID(), "zombie", 1.0, 2.0, 3.0, "world", 10.0, "zombie");
        Model.EntityDetails d = new Model.EntityDetails(base, List.of(), null);
        Map<String, Object> m = d.toMap();
        assertEquals("zombie", m.get("type"));
        assertEquals(List.of(), m.get("passengers"));
        assertEquals("", m.get("vehicle"));
    }

    @Test
    void commandResultNullsBecomeEmpty() {
        Model.CommandResult r = new Model.CommandResult(true, null, null);
        Map<String, Object> m = r.toMap();
        assertEquals(true, m.get("ok"));
        assertEquals("", m.get("output"));
        assertEquals("", m.get("error"));
    }

    @Test
    void commandResultWithValues() {
        Model.CommandResult r = new Model.CommandResult(false, "out", "bad");
        Map<String, Object> m = r.toMap();
        assertEquals(false, m.get("ok"));
        assertEquals("out", m.get("output"));
        assertEquals("bad", m.get("error"));
    }
}
