package com.vortex.blackjack.table;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.Config;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages blackjack tables - creation, removal, and lookup
 */
public class TableManager {

    private final BlackjackPlugin plugin;

    private final Map<Integer, BlackjackTable> tables = new ConcurrentHashMap<>();
    private final Map<Player, BlackjackTable> playerTables = new ConcurrentHashMap<>();

    private int nextId = 1;

    public TableManager(BlackjackPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Load tables from configuration on startup
     */
    public void loadTablesFromConfig() {
        int loaded = this.loadTables();
        plugin.getLogger().info("Loaded " + loaded + " blackjack tables");
        if (loaded == 0) plugin.getLogger().warning("No blackjack tables found. Create one with /bj create");
    }

    private int loadTables() {
        FileConfiguration tableStore = this.plugin.getTablesData();
        if (!tableStore.contains("tables")) return 0;

        ConfigurationSection ids = tableStore.getConfigurationSection("tables");
        if (ids == null) return 0;

        int highestId = 0;

        for (String idStr : ids.getKeys(false)) {
            ConfigurationSection tableData = tableStore.getConfigurationSection("tables." + idStr);
            if (tableData == null) continue;

            Location loc = tableData.getLocation("location");
            if (loc == null) continue;

            int id = Integer.parseInt(idStr);
            if (id > highestId) highestId = id;

            String name = tableData.getString("name");

            createTable(id, name, loc, false);
        }

        this.nextId = highestId + 1;
        return tables.size();
    }

    /**
     * Create a new blackjack table at the specified location
     */
    public boolean createTable(Location centerLoc) {
        int id = this.nextId++;
        return createTable(id, "table_" + id, centerLoc, true);
    }

    public boolean createTable(String name, Location centerLoc) {
        return createTable(this.nextId++, name, centerLoc, true);
    }

    private boolean createTable(int id, String name, Location centerLoc, boolean saveToConfig) {
        for (int cid : tables.keySet())
            if (id == cid) return false;

        World world = centerLoc.getWorld();
        if (world == null) return false;

        world.getChunkAt(centerLoc).load();

        BlackjackTable table = new BlackjackTable(plugin, centerLoc);
        tables.put(id, table);

        if (saveToConfig) {
            Config tableStore = this.plugin.getTablesData();
            ConfigurationSection tableData = tableStore.createSection("tables." + id);

            tableData.set("name", name);
            tableData.set("location", table.getTableLocation());
            tableStore.save();
        }

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
        double closestDistance = Math.pow(plugin.getConfigManager().getMaxJoinDistance(), 2);
        BlackjackTable closestTable = null;

        for (BlackjackTable table : tables.values()) {
            Location tableLoc = table.getCenterLocation();
            if (!Objects.equals(tableLoc.getWorld(), playerLoc.getWorld()))
                continue;

            double distance = tableLoc.distanceSquared(playerLoc);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestTable = table;
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
        removePlayerFromTable(player, "left the table");
    }
    
    /**
     * Remove player from any table they're at with custom reason
     */
    public void removePlayerFromTable(Player player, String reason) {
        BlackjackTable table = playerTables.remove(player);
        if (table != null) {
            table.removePlayer(player, reason);
        }
    }
    
    /**
     * Get all tables
     */
    public Map<Integer, BlackjackTable> getAllTables() {
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
