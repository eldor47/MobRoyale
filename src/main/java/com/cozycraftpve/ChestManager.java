package com.cozycraftpve;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class ChestManager implements Listener {

    private final JavaPlugin plugin;
    private File chestConfigFile;
    private FileConfiguration chestConfig;
    private final Set<Location> rolledChests = new HashSet<>();

    private World chestWorld;
    // Map tier name (common, uncommon, etc.) to Tier object.
    private final Map<String, Tier> tiers = new HashMap<>();
    // Tier chances (weights) defined in config.
    private final Map<String, Integer> tierChances = new HashMap<>();

    public ChestManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadChestConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void loadChestConfig() {
        chestConfigFile = new File(plugin.getDataFolder(), "config_chest.yml");
        if (!chestConfigFile.exists()) {
            plugin.saveResource("config_chest.yml", false);
            plugin.getLogger().info("Saved default config_chest.yml");
        }
        chestConfig = YamlConfiguration.loadConfiguration(chestConfigFile);

        // Load the designated chest world.
        String worldName = chestConfig.getString("chest.world", "world");
        chestWorld = Bukkit.getWorld(worldName);
        if (chestWorld == null) {
            plugin.getLogger().warning("Chest world " + worldName + " not found! Defaulting to first available world.");
            chestWorld = Bukkit.getWorlds().get(0);
        }

        // Load tier chances.
        tierChances.clear();
        ConfigurationSection chanceSection = chestConfig.getConfigurationSection("chest.tier-chances");
        if (chanceSection != null) {
            for (String key : chanceSection.getKeys(false)) {
                int chance = chanceSection.getInt(key);
                tierChances.put(key.toLowerCase(), chance);
            }
        }

        // Load tiers.
        tiers.clear();
        ConfigurationSection tiersSection = chestConfig.getConfigurationSection("chest.tiers");
        if (tiersSection != null) {
            for (String key : tiersSection.getKeys(false)) {
                ConfigurationSection tierSec = tiersSection.getConfigurationSection(key);
                int minItems = tierSec.getInt("min-items", 1);
                int maxItems = tierSec.getInt("max-items", 3);
                List<LootEntry> lootList = new ArrayList<>();
                List<Map<?, ?>> lootSection = tierSec.getMapList("loot");
                for (Map<?, ?> lootData : lootSection) {
                    String itemString = (String) lootData.get("item");
                    int weight = Integer.parseInt(lootData.get("weight").toString());
                    int amount = Integer.parseInt(lootData.get("amount").toString());
                    Map<String, Integer> enchantments = null;
                    if (lootData.containsKey("enchantments")) {
                        enchantments = new HashMap<>();
                        Map<?, ?> enchants = (Map<?, ?>) lootData.get("enchantments");
                        for (Object enchantKey : enchants.keySet()) {
                            String enchName = enchantKey.toString();
                            int level = Integer.parseInt(enchants.get(enchantKey).toString());
                            enchantments.put(enchName, level);
                        }
                    }
        
                    lootList.add(new LootEntry(itemString, weight, amount, enchantments));
                }
                Tier tier = new Tier(key.toLowerCase(), minItems, maxItems, lootList);
                tiers.put(key.toLowerCase(), tier);
            }
        }
        plugin.getLogger().info("Chest config reloaded. Loaded tiers: " + tiers.keySet());
    }

    // Reload the entire chest configuration (for /reloadchestconfig)
    public void reloadChestConfig() {
        loadChestConfig();
    }

    // Reload loot (in this system, loot is part of the chest config)
    public void reloadLoot() {
        loadChestConfig();  // Reloads tiers and loot settings
        rolledChests.clear();
        clearCurrentLootChests();
        plugin.getLogger().info("Loot configuration reloaded and rolled chests cleared.");
    }

    // Randomly choose a tier based on configured tier chances.
    private Tier chooseTier() {
        int totalWeight = 0;
        for (int weight : tierChances.values()) {
            totalWeight += weight;
        }
        int random = new Random().nextInt(totalWeight);
        for (Map.Entry<String, Integer> entry : tierChances.entrySet()) {
            if (random < entry.getValue()) {
                Tier tier = tiers.get(entry.getKey());
                if (tier != null) return tier;
            }
            random -= entry.getValue();
        }
        // Fallback
        return tiers.values().iterator().next();
    }

    // Randomly select a loot entry using weighted random.
    private LootEntry getWeightedRandomLoot(List<LootEntry> lootList) {
        int totalWeight = 0;
        for (LootEntry entry : lootList) {
            totalWeight += entry.weight;
        }
        int random = new Random().nextInt(totalWeight);
        for (LootEntry entry : lootList) {
            if (random < entry.weight) {
                return entry;
            }
            random -= entry.weight;
        }
        return null;
    }
    
    public void clearCurrentLootChests() {
        if (chestWorld == null) {
            plugin.getLogger().warning("Chest world is null; cannot clear loot chests.");
            return;
        }
        int cleared = 0;
        for (org.bukkit.Chunk chunk : chestWorld.getLoadedChunks()) {
            for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
                if (state instanceof org.bukkit.block.Chest) {
                    org.bukkit.block.Chest chest = (org.bukkit.block.Chest) state;
                    String customName = chest.getCustomName();
                    if (customName != null && customName.trim().equalsIgnoreCase("chest_loot")) {
                        // Clear the chest inventory.
                        chest.getBlockInventory().clear();
                        // Force a chunk refresh so clients see the empty chest.
                        int chunkX = chest.getLocation().getChunk().getX();
                        int chunkZ = chest.getLocation().getChunk().getZ();
                        chest.getWorld().refreshChunk(chunkX, chunkZ);
                        cleared++;
                        plugin.getLogger().info("Cleared loot chest at " + chest.getLocation());
                    }
                }
            }
        }
        plugin.getLogger().info("Total loot chests cleared: " + cleared);
    }
    

    // Fills the given chest with loot.
    private void fillChest(Chest chest) {
        plugin.getLogger().info("Filling chest at " + chest.getLocation());
        
        // Choose a tier based on weighted chance.
        Tier tier = chooseTier();
        if (tier == null) {
            plugin.getLogger().warning("No tier selected, aborting loot fill.");
            return;
        }
        plugin.getLogger().info("Chosen tier: " + tier.name);
        
        // Determine number of items to fill (between minItems and maxItems).
        int count = tier.minItems;
        if (tier.maxItems > tier.minItems) {
            count = tier.minItems + new Random().nextInt(tier.maxItems - tier.minItems + 1);
        }
        plugin.getLogger().info("Filling chest with " + count + " items.");
        
        // Get the chest inventory and clear it.
        org.bukkit.inventory.Inventory inv = chest.getBlockInventory();
        inv.clear();
        
        // Create a list of slot indices and shuffle them for random placement.
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            slots.add(i);
        }
        Collections.shuffle(slots);
        
        // Fill the chest with loot.
        for (int i = 0; i < count && i < slots.size(); i++) {
            LootEntry loot = getWeightedRandomLoot(tier.lootList);
            if (loot != null) {
                plugin.getLogger().info("Selected loot: " + loot.itemString + " (amount " + loot.amount + ")");
                ItemStack drop = parseItemStack(loot.itemString, loot.amount);
                if (drop != null) {
                    // If there are enchantments configured for this loot entry, apply them.
                    if (loot.enchantments != null) {
                        drop = applyEnchantments(drop, loot.enchantments);
                    }
                    inv.setItem(slots.get(i), drop);
                    plugin.getLogger().info("Placed " + drop.getType() + " in slot " + slots.get(i));
                } else {
                    plugin.getLogger().warning("parseItemStack returned null for: " + loot.itemString);
                }
            } else {
                plugin.getLogger().warning("No loot entry selected for tier " + tier.name);
            }
        }
        
        // Force an inventory update.
        ItemStack[] newContents = inv.getContents();
        inv.setContents(newContents);
        
        // Set the chest's custom name.
        chest.setCustomName("chest_loot");
        
        // Force a chunk refresh.
        int chunkX = chest.getLocation().getChunk().getX();
        int chunkZ = chest.getLocation().getChunk().getZ();
        chest.getWorld().refreshChunk(chunkX, chunkZ);
        
        plugin.getLogger().info("Finished filling chest at " + chest.getLocation());
    }

    private ItemStack applyEnchantments(ItemStack item, Map<String, Integer> enchantments) {
        for (Map.Entry<String, Integer> enchantEntry : enchantments.entrySet()) {
            // Using the modern API: convert the enchantment name to a NamespacedKey.
            NamespacedKey key = NamespacedKey.minecraft(enchantEntry.getKey().toLowerCase());
            Enchantment enchant = Enchantment.getByKey(key);
            if (enchant != null) {
                item.addUnsafeEnchantment(enchant, enchantEntry.getValue());
            } else {
                plugin.getLogger().warning("Invalid enchantment key: " + enchantEntry.getKey());
            }
        }
        return item;
    }
    
    
    
    // When a player opens a chest, if it's a loot chest, fill it with loot.
    @EventHandler
    public void onChestOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        Object holder = event.getInventory().getHolder();
        Chest chest = null;
        if (holder instanceof Chest) {
            chest = (Chest) holder;
        } else if (holder instanceof org.bukkit.block.DoubleChest) {
            // If it's a double chest, use one side (adjust as needed).
            chest = (Chest) ((org.bukkit.block.DoubleChest) holder).getLeftSide();
        }
        if (chest == null) return;
        
        // Only process chests that have the custom name "chest_loot"
        if (chest.getCustomName() != null && chest.getCustomName().equalsIgnoreCase("chest_loot")) {
            // If this chest has already been rolled, do nothing.
            if (rolledChests.contains(chest.getLocation())) {
                plugin.getLogger().info("Chest at " + chest.getLocation() + " has already been rolled; skipping re-roll.");
                return;
            }
            // Otherwise, if the chest is empty, fill it and mark it as rolled.
            if (isChestEmpty(chest)) {
                plugin.getLogger().info("Chest at " + chest.getLocation() + " is empty. Filling with loot...");
                fillChest(chest);
                rolledChests.add(chest.getLocation());
                plugin.getLogger().info("Chest at " + chest.getLocation() + " marked as rolled.");
            } else {
                plugin.getLogger().info("Chest at " + chest.getLocation() + " is not empty.");
            }
        }
    }
    
    
    

    private boolean isChestEmpty(Chest chest) {
        for (ItemStack item : chest.getBlockInventory().getContents()) {
            if (item != null) return false;
        }
        return true;
    }

    // Helper: parse an ItemStack from a string with NBT-like data.
    public static ItemStack parseItemStack(String itemString, int amount) {
        if (itemString.contains("{")) {
            int braceIndex = itemString.indexOf("{");
            String base = itemString.substring(0, braceIndex).trim().toUpperCase();
            String dataPart = itemString.substring(braceIndex + 1, itemString.length() - 1).trim(); // e.g., Potion:regeneration2
            Material mat = Material.getMaterial(base);
            if (mat == null) {
                Bukkit.getLogger().warning("Invalid material in chest loot config: " + base);
                return null;
            }
            ItemStack item = new ItemStack(mat, amount);
            if (mat == Material.SPLASH_POTION && dataPart.startsWith("Potion:")) {
                String potionInfo = dataPart.substring("Potion:".length()).toLowerCase();
                boolean upgraded = false;
                if (potionInfo.endsWith("2")) {
                    upgraded = true;
                    potionInfo = potionInfo.substring(0, potionInfo.length() - 1);
                } else if (potionInfo.endsWith("1")) {
                    potionInfo = potionInfo.substring(0, potionInfo.length() - 1);
                }
                PotionType potionType = null;
                switch (potionInfo) {
                    case "regeneration":
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
                        Bukkit.getLogger().warning("Unknown potion type in chest loot config: " + potionInfo);
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
            Material material = Material.getMaterial(itemString.toUpperCase());
            if (material == null) {
                Bukkit.getLogger().warning("Invalid material in chest loot config: " + itemString);
                return null;
            }
            return new ItemStack(material, amount);
        }
    }
    

    // Inner classes
    private static class LootEntry {
        String itemString;
        int weight;
        int amount;
        Map<String, Integer> enchantments; // New: key = enchantment name, value = level
    
        public LootEntry(String itemString, int weight, int amount, Map<String, Integer> enchantments) {
            this.itemString = itemString;
            this.weight = weight;
            this.amount = amount;
            this.enchantments = enchantments;
        }
    }
    

    private static class Tier {
        String name;
        int minItems;
        int maxItems;
        List<LootEntry> lootList;
        public Tier(String name, int minItems, int maxItems, List<LootEntry> lootList) {
            this.name = name;
            this.minItems = minItems;
            this.maxItems = maxItems;
            this.lootList = lootList;
        }
    }
}