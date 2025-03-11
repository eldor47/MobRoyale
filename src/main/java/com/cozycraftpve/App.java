package com.cozycraftpve;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public class App extends JavaPlugin implements Listener {

    private final Map<UUID, Integer> playerScores = new HashMap<>();
    private String trackedWorld = "world"; // Default tracked world
    private File sessionFolder; // Directory for past sessions
    private File scoreFile;
    private FileConfiguration scoreConfig;
    private WaveSpawner waveSpawner;
    private LobbyManager lobbyManager;
    private ChestManager chestManager;
    // Loot configuration for mobs spawned by WaveSpawner
    private Map<EntityType, List<LootEntry>> lootMap = new HashMap<>();
    private final Map<UUID, Integer> playerDeaths = new HashMap<>();

    // Define point values for each mob type (for scoring kills)
    private static final Map<EntityType, Integer> mobPoints = new HashMap<>();
    static {
        mobPoints.put(EntityType.SPIDER, 1);
        mobPoints.put(EntityType.ZOMBIE, 2);
        mobPoints.put(EntityType.CAVE_SPIDER, 10);
        mobPoints.put(EntityType.HUSK, 3);
        mobPoints.put(EntityType.BREEZE, 12);
        mobPoints.put(EntityType.BOGGED, 15);
        mobPoints.put(EntityType.BLAZE, 20);
        mobPoints.put(EntityType.MAGMA_CUBE, 2);
        mobPoints.put(EntityType.PIGLIN_BRUTE, 25);
        mobPoints.put(EntityType.PIGLIN, 25);
        mobPoints.put(EntityType.ZOMBIFIED_PIGLIN, 25);
        mobPoints.put(EntityType.VINDICATOR, 15);
        mobPoints.put(EntityType.WITHER_SKELETON, 20);
        mobPoints.put(EntityType.WITCH, 10);
        mobPoints.put(EntityType.PHANTOM, 12);
        mobPoints.put(EntityType.VEX, 25);
        mobPoints.put(EntityType.ILLUSIONER, 15);
        mobPoints.put(EntityType.SKELETON, 5);
        mobPoints.put(EntityType.RAVAGER, 45);
        mobPoints.put(EntityType.CREEPER, 7);
        mobPoints.put(EntityType.ENDERMAN, 15);
        mobPoints.put(EntityType.WITHER, 500);
        mobPoints.put(EntityType.ENDER_DRAGON, 500);
        mobPoints.put(EntityType.WARDEN, 1000);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        trackedWorld = getConfig().getString("tracked-world", "world");
        waveSpawner = new WaveSpawner(this);
        lobbyManager = new LobbyManager(this);
        chestManager = new ChestManager(this);
        loadLootConfig();

        // Create session folder
        sessionFolder = new File(getDataFolder(), "sessions");
        if (!sessionFolder.exists()) sessionFolder.mkdirs();

        loadScores();
        Bukkit.getPluginManager().registerEvents(this, this);
        registerCommands();

        getLogger().info("MobKillTracker Plugin Enabled! Tracking world: " + trackedWorld);
    }

    @Override
    public void onDisable() {
        saveScores();
        getLogger().info("MobKillTracker Plugin Disabled!");
    }

    private void registerCommands() {
        getCommand("checkkills").setExecutor(this::onCommand);
        getCommand("leaderboard").setExecutor(this::onCommand);
        getCommand("startsession").setExecutor(this::onCommand);
        getCommand("viewsession").setExecutor(this::onCommand);
        getCommand("listsessions").setExecutor(this::onCommand);
        getCommand("startwaves").setExecutor(this::onCommand);
        getCommand("startgame").setExecutor(this::onCommand);
        getCommand("stopwaves").setExecutor(this::onCommand);
        getCommand("reloadloot").setExecutor(this::onCommand);
        getCommand("reloadchestconfig").setExecutor(this::onCommand);
    }

    // Load loot configuration from config_spawn.yml for mobs spawned by WaveSpawner.
    private void loadLootConfig() {
        File configFile = new File(getDataFolder(), "config_spawn.yml");
        if (!configFile.exists()) {
            getLogger().warning("config_spawn.yml not found for loot configuration.");
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        if (config.contains("spawner.loot")) {
            for (String mobKey : config.getConfigurationSection("spawner.loot").getKeys(false)) {
                EntityType type;
                try {
                    type = EntityType.valueOf(mobKey.toUpperCase());
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid mob type in loot config: " + mobKey);
                    continue;
                }
                List<Map<?, ?>> lootList = config.getMapList("spawner.loot." + mobKey);
                List<LootEntry> entries = new ArrayList<>();
                for (Map<?, ?> lootData : lootList) {
                    String item = (String) lootData.get("item");
                    int weight = Integer.parseInt(lootData.get("weight").toString());
                    int amount = lootData.containsKey("amount") ? Integer.parseInt(lootData.get("amount").toString()) : 1;
                    Map<String, Integer> enchantments = null;
                    if (lootData.containsKey("enchantments")) {
                        enchantments = new HashMap<>();
                        Map<?, ?> enchants = (Map<?, ?>) lootData.get("enchantments");
                        for (Object key : enchants.keySet()) {
                            String enchName = key.toString();
                            int level = Integer.parseInt(enchants.get(key).toString());
                            enchantments.put(enchName, level);
                        }
                    }
                    entries.add(new LootEntry(item, weight, amount, enchantments));
                }
                lootMap.put(type, entries);
                getLogger().info("Loaded loot for " + mobKey + ": " + entries.size() + " entries.");
            }
        }
    }
    

    // LootEntry inner class representing a loot option.
    private static class LootEntry {
        String itemName;
        int weight;
        int amount;
        Map<String, Integer> enchantments; // key: enchantment name, value: level
    
        public LootEntry(String itemName, int weight, int amount, Map<String, Integer> enchantments) {
            this.itemName = itemName;
            this.weight = weight;
            this.amount = amount;
            this.enchantments = enchantments;
        }
    }
    

    // Helper method to select a LootEntry based on weighted random selection.
    private LootEntry getWeightedRandom(List<LootEntry> entries) {
        int totalWeight = entries.stream().mapToInt(e -> e.weight).sum();
        int random = (int)(Math.random() * totalWeight);
        for (LootEntry entry : entries) {
            if (random < entry.weight) {
                return entry;
            }
            random -= entry.weight;
        }
        return null;
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        // Process only if a player killed the mob.
        if (!(event.getEntity().getKiller() instanceof Player)) return;
        // Only apply custom loot to mobs spawned by WaveSpawner.
        if (!event.getEntity().hasMetadata("waveSpawner")) return;
    
        Player player = event.getEntity().getKiller();
        World world = player.getWorld();
        if (!world.getName().equalsIgnoreCase(trackedWorld)) return;
    
        EntityType entityType = event.getEntity().getType();
        UUID playerUUID = player.getUniqueId();
    
        int points = mobPoints.getOrDefault(entityType, 1);
        playerScores.put(playerUUID, playerScores.getOrDefault(playerUUID, 0) + points);
        player.sendMessage("§aYou earned §b" + points + "§a points! Total: §e" + playerScores.get(playerUUID));
        saveScores();
        updateLeaderboard();
    
        // Apply custom loot if configured for this mob type.
        if (lootMap.containsKey(entityType)) {
            List<LootEntry> entries = lootMap.get(entityType);
            LootEntry selected = getWeightedRandom(entries);
            if (selected != null) {
                event.getDrops().clear();
                // If the item string contains potion data, use parseItemStack, else use a simple method.
                ItemStack drop;
                if (selected.itemName.toLowerCase().contains("{")) {
                    drop = parseItemStack(selected.itemName, selected.amount);
                } else {
                    Material material = Material.getMaterial(selected.itemName.toUpperCase());
                    if (material == null) {
                        getLogger().warning("Invalid material for loot: " + selected.itemName);
                        return;
                    }
                    drop = new ItemStack(material, selected.amount);
                }
                
                // Apply enchantments if defined in the loot entry.
                if (selected.enchantments != null && drop != null) {
                    for (Map.Entry<String, Integer> enchantEntry : selected.enchantments.entrySet()) {
                        // Use the modern API: convert the enchantment name into a NamespacedKey.
                        NamespacedKey key = NamespacedKey.minecraft(enchantEntry.getKey().toLowerCase());
                        org.bukkit.enchantments.Enchantment enchant = org.bukkit.enchantments.Enchantment.getByKey(key);
                        if (enchant != null) {
                            drop.addUnsafeEnchantment(enchant, enchantEntry.getValue());
                        } else {
                            getLogger().warning("Invalid enchantment key: " + enchantEntry.getKey());
                        }
                    }
                }
                
                if (drop != null) {
                    event.getDrops().add(drop);
                    //player.sendMessage("§6Custom loot dropped: " + selected.itemName.toUpperCase());
                } else {
                    getLogger().warning("Failed to create custom loot for: " + selected.itemName);
                }
            }
        }
        
    }

    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        int deaths = playerDeaths.getOrDefault(uuid, 0) + 1;
        playerDeaths.put(uuid, deaths);
        // Optionally send a message:
        player.sendMessage("§cYou died! Total deaths: " + deaths);
        // Update the leaderboard so it reflects the new death count.
        updateLeaderboard();
    }
        

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "checkkills":
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    int score = playerScores.getOrDefault(player.getUniqueId(), 0);
                    player.sendMessage("§eYour total mob kill points: §b" + score);
                }
                return true;

            case "startsession":
                if (args.length != 1) {
                    sender.sendMessage("§cUsage: /startsession <session_name>");
                    return true;
                }
                startNewSession(args[0]);
                sender.sendMessage("§aNew scoring session started: " + args[0]);
                return true;

            case "viewsession":
                if (args.length != 1) {
                    sender.sendMessage("§cUsage: /viewsession <session_name>");
                    return true;
                }
                viewSession(sender, args[0]);
                return true;

            case "listsessions":
                listSessions(sender);
                return true;

            case "leaderboard":
                showLeaderboard(sender, playerScores);
                updateLeaderboard();
                return true;

            case "stopwaves":
                waveSpawner.stopWaves();
                sender.sendMessage("Wave spawning stopped.");
                return true;
                
            case "reloadwaves":
                // Reload the main configuration file
                reloadConfig();
                // Reload WaveSpawner configuration
                waveSpawner.reload();
                // Reload loot configuration if needed
                loadLootConfig();
                sender.sendMessage("Wave configuration reloaded.");
                return true;

            case "startgame":
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "heal @a");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "clearlag");
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    lobbyManager.startGame();
                    chestManager.reloadLoot();
                    Bukkit.broadcastMessage("§5Loot has been reloaded!");
                    Bukkit.broadcastMessage("§aGame is commencing shortly...");
                }, 300L);
                return true;

            case "startwaves":
                lobbyManager.unfreezePlayers();
                waveSpawner.startWaves();
                sender.sendMessage("Wave spawning started.");
                return true;

            case "stopgame":
                // Stop game: reset lobby and stop waves.
                waveSpawner.stopWaves();
                lobbyManager.stopGame();
                sender.sendMessage("Force stopping game.");
                return true;

            case "reloadloot":
                chestManager.reloadLoot();
                sender.sendMessage("Loot reloaded.");
                return true;

            case "reloadchestconfig":
                chestManager.reloadChestConfig();
                sender.sendMessage("Chest configuration reloaded.");
                return true;
            
            case "reloadlobby":
                lobbyManager.reloadLobbyConfig();
                sender.sendMessage("Lobby config reloaded.");
                return true;

            case "resetleaderboard":
                resetLeaderboard();
                sender.sendMessage("Leaderboard has been reset.");
                return true;
            
        }
        return false;
    }

    private void saveScores() {
        if (scoreFile == null) {
            scoreFile = new File(getDataFolder(), "scores.yml");
        }

        scoreConfig = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : playerScores.entrySet()) {
            scoreConfig.set(entry.getKey().toString(), entry.getValue());
        }

        try {
            scoreConfig.save(scoreFile);
        } catch (IOException e) {
            getLogger().severe("Could not save scores.yml!");
            e.printStackTrace();
        }
    }

    private void loadScores() {
        scoreFile = new File(getDataFolder(), "scores.yml");

        if (!scoreFile.exists()) {
            try {
                scoreFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create scores.yml!");
                e.printStackTrace();
            }
        }

        scoreConfig = YamlConfiguration.loadConfiguration(scoreFile);
        playerScores.clear();

        for (String key : scoreConfig.getKeys(false)) {
            playerScores.put(UUID.fromString(key), scoreConfig.getInt(key));
        }
    }

    private synchronized void startNewSession(String sessionName) {
        File sessionFile = new File(new File(getDataFolder(), "sessions"), sessionName + ".yml");

        YamlConfiguration sessionData = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : playerScores.entrySet()) {
            sessionData.set(entry.getKey().toString(), entry.getValue());
        }

        try {
            sessionData.save(sessionFile);
            getLogger().info("Session '" + sessionName + "' saved successfully.");
        } catch (Exception e) {
            getLogger().severe("Failed to save session: " + sessionName);
            e.printStackTrace();
            return;
        }

        playerScores.clear();
        saveScores();
        getLogger().info("Active scores reset after starting session '" + sessionName + "'.");
    }

    private void viewSession(CommandSender sender, String sessionName) {
        File sessionFile = new File(new File(getDataFolder(), "sessions"), sessionName + ".yml");

        if (!sessionFile.exists()) {
            sender.sendMessage("§cSession '" + sessionName + "' does not exist.");
            return;
        }

        YamlConfiguration sessionData = YamlConfiguration.loadConfiguration(sessionFile);
        Map<UUID, Integer> sessionScores = new HashMap<>();

        for (String key : sessionData.getKeys(false)) {
            sessionScores.put(UUID.fromString(key), sessionData.getInt(key));
        }

        sender.sendMessage("§6§lLeaderboard - Session: " + sessionName);
        showLeaderboard(sender, sessionScores);
    }

    private void listSessions(CommandSender sender) {
        File sessionsDir = new File(getDataFolder(), "sessions");
        File[] sessionFiles = sessionsDir.listFiles();

        if (sessionFiles == null || sessionFiles.length == 0) {
            sender.sendMessage("§cNo past sessions found.");
            return;
        }

        sender.sendMessage("§6§lPast Sessions:");
        for (File file : sessionFiles) {
            sender.sendMessage("§e- " + file.getName().replace(".yml", ""));
        }
    }

    private void showLeaderboard(CommandSender sender, Map<UUID, Integer> scores) {
        List<Map.Entry<UUID, Integer>> sortedScores = new ArrayList<>(scores.entrySet());
        sortedScores.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        sender.sendMessage("§6§lLeaderboard:");
        for (Map.Entry<UUID, Integer> entry : sortedScores) {
            String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            sender.sendMessage("§a" + playerName + " §f- §b" + entry.getValue() + " points");
        }
    }

    private void updateLeaderboard() {
        // Get the scoreboard manager and create a new scoreboard.
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("leaderboard", "dummy", "Leaderboard");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        // For each player in your kill scores, display both kills and deaths.
        // (Assuming playerScores stores kill counts.)
        for (UUID uuid : playerScores.keySet()) {
            int kills = playerScores.getOrDefault(uuid, 0);
            int deaths = playerDeaths.getOrDefault(uuid, 0);
            OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
            String name = off.getName();
            if (name == null) {
                name = uuid.toString().substring(0, 8);
            }
            String line ="§5" +  name + " | §4☠: §c" + deaths + "§5 | §a";
            Score score = objective.getScore(line);
            // You can use kills for ordering or a composite value.
            score.setScore(kills);
        }
        
        // Set this scoreboard for all online players.
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(board);
        }
    }

    private void resetLeaderboard() {
        // Clear in-memory maps.
        playerScores.clear();
        // Optionally, clear playerDeaths if you're tracking deaths as well.
        // playerDeaths.clear();
    
        // Create a new (empty) configuration and save it to scores.yml.
        scoreConfig = new YamlConfiguration();
        try {
            scoreConfig.save(scoreFile);
            getLogger().info("Leaderboard reset; scores.yml cleared.");
        } catch (IOException e) {
            getLogger().severe("Could not save scores.yml while resetting leaderboard!");
            e.printStackTrace();
        }
    }
    
    
    private ItemStack parseItemStack(String itemString, int amount) {
        // Check if extra data is provided (i.e. contains a '{...}' block)
        if (itemString.contains("{")) {
            int braceIndex = itemString.indexOf("{");
            String baseMaterialName = itemString.substring(0, braceIndex).trim().toUpperCase();
            String dataPart = itemString.substring(braceIndex + 1, itemString.length() - 1).trim(); // Remove { and }
            
            Material mat = Material.getMaterial(baseMaterialName);
            if (mat == null) {
                getLogger().warning("Invalid material: " + baseMaterialName);
                return null;
            }
            
            ItemStack item = new ItemStack(mat, amount);
            // If it's a splash potion and the extra data starts with "Potion:"
            if (mat == Material.SPLASH_POTION && dataPart.startsWith("Potion:")) {
                String potionInfo = dataPart.substring("Potion:".length()).toLowerCase(); // e.g. "regeneration2"
                boolean upgraded = false;
                // Determine level: if ends with '2', mark as upgraded (level II)
                if (potionInfo.endsWith("2")) {
                    upgraded = true;
                    potionInfo = potionInfo.substring(0, potionInfo.length() - 1);
                } else if (potionInfo.endsWith("1")) {
                    potionInfo = potionInfo.substring(0, potionInfo.length() - 1);
                }
                
                PotionType potionType = null;
                switch (potionInfo) {
                    case "regeneration":
                        // Note: Vanilla doesn't usually have splash regeneration, but you can map it as you like.
                        potionType = PotionType.REGENERATION;
                        break;
                    case "swiftness":
                        potionType = PotionType.SWIFTNESS;
                        break;
                    case "strength":
                        potionType = PotionType.STRENGTH;
                        break;
                    case "healing":
                        potionType = PotionType.HEALING;
                        break;
                    default:
                        getLogger().warning("Unknown potion type: " + potionInfo);
                        break;
                }
                if (potionType != null) {
                    PotionMeta meta = (PotionMeta) item.getItemMeta();
                    if (meta != null) {
                        meta.setBasePotionData(new PotionData(potionType, false, upgraded));
                        item.setItemMeta(meta);
                    }
                }
            }
            return item;
        } else {
            // No extra data; simply create the ItemStack.
            Material material = Material.getMaterial(itemString.toUpperCase());
            if (material == null) {
                getLogger().warning("Invalid material: " + itemString);
                return null;
            }
            return new ItemStack(material, amount);
        }
    }
    
    

}
