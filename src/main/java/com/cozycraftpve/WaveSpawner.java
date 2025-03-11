package com.cozycraftpve;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class WaveSpawner {

    private final JavaPlugin plugin;
    private Location spawnPoint;
    private double radius;
    private List<Wave> waves;
    private int currentWaveIndex = 0;
    private BukkitTask currentSpawnTask;
    private BukkitTask waveEndTask;
    private BukkitTask waveCountdownTask; // Periodic countdown during wave

    public WaveSpawner(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    @SuppressWarnings("unchecked")
    private void loadConfig() {
        // Ensure config_spawn.yml exists in the plugin data folder.
        File configFile = new File(plugin.getDataFolder(), "config_spawn.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config_spawn.yml", false);
            plugin.getLogger().info("Saved default config_spawn.yml");
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Read spawn point
        String worldName = config.getString("spawner.spawnPoint.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().severe("World " + worldName + " not found!");
            return;
        }
        double x = config.getDouble("spawner.spawnPoint.x", 0);
        double y = config.getDouble("spawner.spawnPoint.y", 64);
        double z = config.getDouble("spawner.spawnPoint.z", 0);
        spawnPoint = new Location(world, x, y, z);
        plugin.getLogger().info("Spawn point loaded: " + spawnPoint.toString() + " in world: " + worldName);

        // Read radius
        radius = config.getDouble("spawner.radius", 10);
        plugin.getLogger().info("Spawn radius loaded: " + radius);

        // Load waves
        waves = new ArrayList<>();
        List<Map<?, ?>> wavesList = config.getMapList("spawner.waves");
        if (wavesList == null || wavesList.isEmpty()) {
            plugin.getLogger().warning("No waves defined in config_spawn.yml");
            return;
        }
        for (Map<?, ?> map : wavesList) {
            String name = (String) map.get("name");
            int duration = (int) map.get("duration");           // in seconds
            int spawnInterval = (int) map.get("spawnInterval");   // in seconds
            Map<String, Object> mobs = (Map<String, Object>) map.get("mobs");
            Wave wave = new Wave(name, duration, spawnInterval, mobs);
            waves.add(wave);
            plugin.getLogger().info("Loaded wave: " + name + " | Duration: " + duration +
                    "s, Interval: " + spawnInterval + "s, Mobs: " + mobs.toString());
        }
    }

    public void reload() {
        loadConfig();
        plugin.getLogger().info("WaveSpawner configuration reloaded.");
    }

    // Starts the entire wave sequence with an initial countdown.
    public void startWaves() {
        if (waves == null || waves.isEmpty()) {
            plugin.getLogger().warning("No waves configured.");
            return;
        }
        currentWaveIndex = 0;
        // Countdown before the first wave starts.
        Bukkit.broadcastMessage("§6GAME HAS STARTED GO!");
        startCountdown(15, "First wave starting in", () -> startWave(waves.get(currentWaveIndex)));
    }

    // Stops any active wave tasks.
    public void stopWaves() {
        if (currentSpawnTask != null) {
            currentSpawnTask.cancel();
            currentSpawnTask = null;
        }
        if (waveEndTask != null) {
            waveEndTask.cancel();
            waveEndTask = null;
        }
        if (waveCountdownTask != null) {
            waveCountdownTask.cancel();
            waveCountdownTask = null;
        }
        killAllHostileMobs();
        plugin.getLogger().info("Wave spawning stopped.");
    }

    // Starts an individual wave.
    private void startWave(Wave wave) {
        Bukkit.broadcastMessage("§6Wave " + wave.name + " is starting now! Duration: " + wave.duration + " seconds.");
        // Start periodic countdown for this wave.
        final long waveStartTime = System.currentTimeMillis();
        final long waveDurationMillis = wave.duration * 1000L;
        if (waveCountdownTask != null) waveCountdownTask.cancel();
        waveCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long elapsed = System.currentTimeMillis() - waveStartTime;
            long remaining = (waveDurationMillis - elapsed) / 1000;
            if (remaining < 0) remaining = 0;
            Bukkit.broadcastMessage("§e" + wave.name + " - " + remaining + " seconds remaining.");
        }, 0L, 300L); // every 15 seconds (300 ticks)

        long waveDurationTicks = wave.duration * 20L;
        long spawnIntervalTicks = wave.spawnInterval * 20L;

        // Schedule mob spawning repeatedly.
        currentSpawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            plugin.getLogger().info("Spawning mobs for wave: " + wave.name);
            for (Map.Entry<String, Object> entry : wave.mobs.entrySet()) {
                String mobName = entry.getKey().toUpperCase();
                int count;
                try {
                    count = Integer.parseInt(entry.getValue().toString());
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("Invalid count for mob: " + mobName);
                    continue;
                }
                EntityType type;
                try {
                    type = EntityType.valueOf(mobName);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid mob type in config: " + mobName);
                    continue;
                }
                for (int i = 0; i < count; i++) {
                    Location loc = getRandomLocationInRadius(spawnPoint, radius);
                    spawnMob(type, loc);
                }
            }
        }, 0L, spawnIntervalTicks);

        // Schedule wave end.
        waveEndTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (currentSpawnTask != null) {
                currentSpawnTask.cancel();
                currentSpawnTask = null;
            }
            if (waveCountdownTask != null) {
                waveCountdownTask.cancel();
                waveCountdownTask = null;
            }
            // Kill all hostile mobs spawned during this wave.
            killAllHostileMobs();
            Bukkit.broadcastMessage("§6" + wave.name + " ended.");
            
            // Reload chest loot and announce it.
            Bukkit.broadcastMessage("§eReloading chest loot...");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "reloadloot");
            
            currentWaveIndex++;
            if (currentWaveIndex < waves.size()) {
                // Announce and countdown for the next wave.
                Bukkit.broadcastMessage("§6Next wave will start in 10 seconds.");
                startCountdown(10, "Next wave starting in", () -> startWave(waves.get(currentWaveIndex)));
            } else {
                // Final wave complete.
                killAllHostileMobs();
                Bukkit.broadcastMessage("§6All waves completed. Game finished!");
            }
        }, waveDurationTicks);


    }

    private void killAllHostileMobs() {
        World world = spawnPoint.getWorld();
        if (world != null) {
            world.getEntities().stream()
                .filter(entity ->
                    (entity instanceof org.bukkit.entity.Monster) ||
                    (entity.getType() == EntityType.MAGMA_CUBE) ||
                    (entity.getType() == EntityType.PHANTOM))
                .forEach(entity -> entity.remove());
            plugin.getLogger().info("All hostile mobs in world " + world.getName() + " have been removed.");
        } else {
            plugin.getLogger().warning("Cannot kill hostile mobs, spawnPoint's world is null.");
        }
    }

    // Spawns a mob of the given type at the given location and tags it.
    private void spawnMob(EntityType type, Location location) {
        World world = location.getWorld();
        if (world != null) {
            org.bukkit.entity.Entity spawned = world.spawnEntity(location, type);
            spawned.setMetadata("waveSpawner", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        } else {
            plugin.getLogger().warning("Attempted to spawn mob in a null world at location: " + location.toString());
        }
    }

    // Calculates a random location within the radius from the center.
    private Location getRandomLocationInRadius(Location center, double radius) {
        double angle = Math.random() * 2 * Math.PI;
        double distance = Math.random() * radius;
        double xOffset = Math.cos(angle) * distance;
        double zOffset = Math.sin(angle) * distance;
        Location randomLoc = center.clone().add(xOffset, 0, zOffset);
        return randomLoc;
    }

    // Helper method to start a countdown in chat.
    private void startCountdown(int seconds, String messagePrefix, Runnable callback) {
        final int[] counter = {seconds};
        final BukkitTask[] countdownTask = new BukkitTask[1];
        countdownTask[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int count = counter[0];
            @Override
            public void run() {
                if (count > 0) {
                    Bukkit.broadcastMessage("§e" + messagePrefix + " " + count + "...");
                    count--;
                } else {
                    Bukkit.broadcastMessage("§e" + messagePrefix + " Go!");
                    callback.run();
                    countdownTask[0].cancel();
                }
            }
        }, 0L, 20L); // Every second
    }

    // Inner class representing a wave.
    private static class Wave {
        String name;
        int duration;      // in seconds
        int spawnInterval; // in seconds
        Map<String, Object> mobs; // mob type to count mapping

        public Wave(String name, int duration, int spawnInterval, Map<String, Object> mobs) {
            this.name = name;
            this.duration = duration;
            this.spawnInterval = spawnInterval;
            this.mobs = mobs;
        }
    }
}
