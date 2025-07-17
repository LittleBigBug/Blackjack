package com.vortex.blackjack.table;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages blackjack tables - creation, removal, and lookup
 */
public class TableManager {
    private final BlackjackPlugin plugin;
    private final ConfigManager configManager;
    private final Map<Location, BlackjackTable> tables = new ConcurrentHashMap<>();
    private final Map<Player, BlackjackTable> playerTables = new ConcurrentHashMap<>();
    
    public TableManager(BlackjackPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }
    
    /**
     * Load tables from configuration on startup
     */
    public void loadTablesFromConfig() {
        if (!plugin.getConfig().contains("tables")) {
            return;
        }
        
        for (String worldName : plugin.getConfig().getConfigurationSection("tables").getKeys(false)) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World not found: " + worldName);
                continue;
            }
            
            ConfigurationSection worldSection = plugin.getConfig().getConfigurationSection("tables." + worldName);
            for (String locString : worldSection.getKeys(false)) {
                try {
                    String[] parts = locString.split("_");
                    if (parts.length != 3) continue;
                    
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    
                    Location loc = new Location(world, x, y, z);
                    createTable(loc, false); // Don't save to config again
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid table location format: " + locString);
                }
            }
        }
        
        plugin.getLogger().info("Loaded " + tables.size() + " blackjack tables");
    }
    
    /**
     * Create a new blackjack table at the specified location
     */
    public boolean createTable(Location centerLoc) {
        return createTable(centerLoc, true);
    }
    
    private boolean createTable(Location centerLoc, boolean saveToConfig) {
        // Check if table already exists at this location
        for (Location loc : tables.keySet()) {
            if (loc.equals(centerLoc)) {
                return false; // Table already exists
            }
        }
        
        World world = centerLoc.getWorld();
        if (world == null) return false;
        
        int centerX = centerLoc.getBlockX();
        int centerY = centerLoc.getBlockY();
        int centerZ = centerLoc.getBlockZ();
        
        // Ensure chunk is loaded
        world.getChunkAt(centerLoc).load();
        
        Material tableMaterial = configManager.getTableMaterial();
        Material chairMaterial = configManager.getChairMaterial();
        
        // Create table blocks (3x3 hollow square)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) { // Don't place block in center
                    Location blockLoc = new Location(world, centerX + x, centerY, centerZ + z);
                    blockLoc.getBlock().setType(tableMaterial);
                }
            }
        }
        
        // Place chairs at cardinal directions
        placeStair(world, centerX + 2, centerY, centerZ, BlockFace.EAST, chairMaterial);
        placeStair(world, centerX - 2, centerY, centerZ, BlockFace.WEST, chairMaterial);
        placeStair(world, centerX, centerY, centerZ + 2, BlockFace.SOUTH, chairMaterial);
        placeStair(world, centerX, centerY, centerZ - 2, BlockFace.NORTH, chairMaterial);
        
        // Save to config if requested
        if (saveToConfig) {
            String tablePath = "tables." + world.getName() + "." + centerX + "_" + centerY + "_" + centerZ;
            plugin.getConfig().set(tablePath, true);
            plugin.saveConfig();
        }
        
        // Create table object
        BlackjackTable table = new BlackjackTable(plugin, this, configManager, centerLoc);
        tables.put(centerLoc, table);
        
        return true;
    }
    
    private void placeStair(World world, int x, int y, int z, BlockFace facing, Material material) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(material);
        if (block.getBlockData() instanceof Stairs stairs) {
            stairs.setFacing(facing);
            block.setBlockData(stairs);
        }
    }
    
    /**
     * Remove a table at the specified location
     */
    public boolean removeTable(Location tableLoc) {
        BlackjackTable table = tables.remove(tableLoc);
        if (table == null) return false;
        
        // Remove all players from the table
        table.removeAllPlayers();
        table.cleanup();
        
        // Remove from config
        String worldName = tableLoc.getWorld().getName();
        int centerX = tableLoc.getBlockX();
        int centerY = tableLoc.getBlockY();
        int centerZ = tableLoc.getBlockZ();
        
        if (plugin.getConfig().contains("tables." + worldName)) {
            ConfigurationSection tablesSection = plugin.getConfig().getConfigurationSection("tables." + worldName);
            String key = centerX + "_" + centerY + "_" + centerZ;
            tablesSection.set(key, null);
            plugin.saveConfig();
        }
        
        return true;
    }
    
    /**
     * Find the nearest table to a player's location
     */
    public BlackjackTable findNearestTable(Location playerLoc) {
        double maxDistance = configManager.getMaxJoinDistance();
        double closestDistance = maxDistance;
        BlackjackTable closestTable = null;
        
        for (Map.Entry<Location, BlackjackTable> entry : tables.entrySet()) {
            Location tableLoc = entry.getKey();
            if (!tableLoc.getWorld().equals(playerLoc.getWorld())) {
                continue;
            }
            
            double distance = tableLoc.distance(playerLoc);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestTable = entry.getValue();
            }
        }
        
        return closestTable;
    }
    
    /**
     * Get the table a player is currently at
     */
    public BlackjackTable getPlayerTable(Player player) {
        return playerTables.get(player);
    }
    
    /**
     * Set which table a player is at
     */
    public void setPlayerTable(Player player, BlackjackTable table) {
        if (table == null) {
            playerTables.remove(player);
        } else {
            playerTables.put(player, table);
        }
    }
    
    /**
     * Remove player from any table they're at
     */
    public void removePlayerFromTable(Player player) {
        BlackjackTable table = playerTables.remove(player);
        if (table != null) {
            table.removePlayer(player);
        }
    }
    
    /**
     * Get all tables
     */
    public Map<Location, BlackjackTable> getAllTables() {
        return tables;
    }
    
    /**
     * Cleanup all tables
     */
    public void cleanup() {
        for (BlackjackTable table : tables.values()) {
            table.cleanup();
        }
        tables.clear();
        playerTables.clear();
    }
}
