package com.cozycraftpve;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class LobbyManager implements Listener {
    
    private final JavaPlugin plugin;
    private final Set<Player> frozenPlayers = new HashSet<>();
    private boolean gameStarted = false;
    private final List<Location> lobbySpawns = new ArrayList<>();
    private final Set<String> excludedPlayers = new HashSet<>();
    private World lobbyWorld;
    private EquipmentSet equipment; // Holds armor and tools

    public LobbyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reloadLobbyConfig() {
        loadConfig();
        plugin.getLogger().info("Lobby config reloaded.");
    }
    
    // Load lobby settings from config_lobby.yml
    private void loadConfig() {
        File lobbyConfigFile = new File(plugin.getDataFolder(), "config_lobby.yml");
        if (!lobbyConfigFile.exists()) {
            plugin.saveResource("config_lobby.yml", false);
            plugin.getLogger().info("Saved default config_lobby.yml");
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(lobbyConfigFile);
        
        // Read the lobby world from config (default to "world")
        String lobbyWorldName = config.getString("lobby.world", "world");
        lobbyWorld = Bukkit.getWorld(lobbyWorldName);
        if (lobbyWorld == null) {
            plugin.getLogger().warning("Lobby world " + lobbyWorldName + " not found! Defaulting to first available world.");
            lobbyWorld = Bukkit.getWorlds().get(0);
        }
        
        // Load lobby spawn locations from config under "lobby.spawns"
        ConfigurationSection spawnsSection = config.getConfigurationSection("lobby.spawns");
        if (spawnsSection != null) {
            for (String key : spawnsSection.getKeys(false)) {
                double x = spawnsSection.getDouble(key + ".x");
                double y = spawnsSection.getDouble(key + ".y");
                double z = spawnsSection.getDouble(key + ".z");
                String worldName = spawnsSection.getString(key + ".world", lobbyWorld.getName());
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    lobbySpawns.add(new Location(world, x, y, z));
                } else {
                    plugin.getLogger().warning("World " + worldName + " not found for lobby spawn " + key);
                }
            }
        } else {
            plugin.getLogger().warning("No lobby spawns defined in config under lobby.spawns.");
        }
        
        // Load excluded players (by name) from config under "lobby.exclude"
        List<String> excluded = config.getStringList("lobby.exclude");
        if (excluded != null) {
            excludedPlayers.addAll(excluded);
        }
        
        // Load equipment from config under "lobby.equipment"
        if (config.isConfigurationSection("lobby.equipment")) {
            equipment = new EquipmentSet();
            String helmetStr = config.getString("lobby.equipment.helmet");
            String chestplateStr = config.getString("lobby.equipment.chestplate");
            String leggingsStr = config.getString("lobby.equipment.leggings");
            String bootsStr = config.getString("lobby.equipment.boots");
            String swordStr = config.getString("lobby.equipment.sword");
            String bowStr = config.getString("lobby.equipment.bow");
            int arrowsCount = config.getInt("lobby.equipment.arrows", 0);
            
            equipment.helmet = createItem(helmetStr, 1);
            equipment.chestplate = createItem(chestplateStr, 1);
            equipment.leggings = createItem(leggingsStr, 1);
            equipment.boots = createItem(bootsStr, 1);
            equipment.sword = createItem(swordStr, 1);
            equipment.bow = createItem(bowStr, 1);
            if (arrowsCount > 0) {
                equipment.arrows = new ItemStack(Material.ARROW, arrowsCount);
            }
        }
    }
    
    // Utility to create an item from a material string.
    private ItemStack createItem(String materialStr, int amount) {
        if (materialStr == null || materialStr.isEmpty()) return null;
        Material mat = Material.getMaterial(materialStr.toUpperCase());
        if (mat != null) {
            return new ItemStack(mat, amount);
        }
        return null;
    }
    
    // Called by the /startgame command: Teleport eligible players to lobby and freeze them.
    public void startGame() {
        int index = 0;
        for (Player player : lobbyWorld.getPlayers()) {
            if (excludedPlayers.contains(player.getName())) continue;
            if (!lobbySpawns.isEmpty()) {
                Location spawn = lobbySpawns.get(index % lobbySpawns.size());
                player.teleport(spawn);
                frozenPlayers.add(player);
                // Freeze players by setting their game mode to Adventure.
                player.setGameMode(GameMode.ADVENTURE);
                index++;
            }
        }
        Bukkit.broadcastMessage("§eGame starting! Players have been sent to the lobby.");
    }

    // Called by /stopgame command to reset players to lobby.
    public void stopGame() {
        for (Player player : lobbyWorld.getPlayers()) {
            if (excludedPlayers.contains(player.getName())) continue;
            player.getInventory().clear();
            if (!lobbySpawns.isEmpty()) {
                player.teleport(lobbySpawns.get(0));
            }
            player.setGameMode(GameMode.ADVENTURE);
        }
        resetGame();
        Bukkit.broadcastMessage("§eGame has been stopped. Players returned to the lobby.");
    }
    
    // Called by the /startwaves command to clear inventories, give equipment, unfreeze players, and start the game.
    public void unfreezePlayers() {
        for (Player player : lobbyWorld.getPlayers()) {
            if (excludedPlayers.contains(player.getName())) continue;
            // Clear inventory and give equipment.
            player.getInventory().clear();
            if (equipment != null) {
                if (equipment.helmet != null) player.getInventory().setHelmet(equipment.helmet);
                if (equipment.chestplate != null) player.getInventory().setChestplate(equipment.chestplate);
                if (equipment.leggings != null) player.getInventory().setLeggings(equipment.leggings);
                if (equipment.boots != null) player.getInventory().setBoots(equipment.boots);
                if (equipment.sword != null) player.getInventory().addItem(equipment.sword);
                if (equipment.bow != null) player.getInventory().addItem(equipment.bow);
                if (equipment.arrows != null) player.getInventory().addItem(equipment.arrows);
            }
            // Set game mode to Survival so they can move.
            player.setGameMode(GameMode.SURVIVAL);
        }
        frozenPlayers.clear();
        gameStarted = true;
        Bukkit.broadcastMessage("§eWaves have started! You may now move.");
    }
    
    // When a player dies during the game, force them into Spectator mode.
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!excludedPlayers.contains(player.getName()) && gameStarted()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage("§eYou are now in Spectator mode until the next game starts.");
            }, 1L);
        }
    }
    
    // Prevent frozen players from moving.
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            if (!event.getFrom().getBlock().equals(event.getTo().getBlock())) {
                event.setTo(event.getFrom());
            }
        }
    }
    
    // When a player joins the lobby world, do nothing special—players won't be teleported or frozen until /startgame is called.
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Optionally, you can add a message indicating that a game is in progress or not.
        // Here, we let players join normally.
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        frozenPlayers.remove(event.getPlayer());
    }
    
    // Check if the game has started (i.e. players have been unfrozen).
    public boolean gameStarted() {
        return gameStarted;
    }
    
    // Reset lobby state when the game ends.
    public void resetGame() {
        gameStarted = false;
        frozenPlayers.clear();
    }
    
    // Inner class to hold equipment data.
    private class EquipmentSet {
        ItemStack helmet;
        ItemStack chestplate;
        ItemStack leggings;
        ItemStack boots;
        ItemStack sword;
        ItemStack bow;
        ItemStack arrows;
    }
}
