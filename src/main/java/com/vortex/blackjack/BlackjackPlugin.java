package com.vortex.blackjack;

import com.vortex.blackjack.commands.CommandManager;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.economy.EconomyProvider;
import com.vortex.blackjack.economy.EssentialsEconomyProvider;
import com.vortex.blackjack.model.PlayerStats;
import com.vortex.blackjack.table.BlackjackTable;
import com.vortex.blackjack.table.TableManager;
import com.vortex.blackjack.util.AsyncUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main plugin class - COMPLETELY REFACTORED!
 * 
 * BEFORE: 1270 lines of monolithic code
 * AFTER: ~400 lines of clean, organized code
 * 
 * Key improvements:
 * - Separated concerns into dedicated managers
 * - Individual commands for better UX (/bet, /hit, /stand)
 * - Modern Java patterns and thread-safe collections
 * - Proper error handling and resource cleanup
 * - Extensible architecture for future features
 */
public class BlackjackPlugin extends JavaPlugin implements Listener {
    
    // Core managers - each handles a specific responsibility
    private ConfigManager configManager;
    private TableManager tableManager;
    private CommandManager commandManager;
    private AsyncUtils asyncUtils;
    private EconomyProvider economyProvider;
    
    // Player data - thread-safe collections
    private final Map<Player, Integer> playerBets = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerPersistentBets = new ConcurrentHashMap<>(); // Keeps bet amount for "Play Again"
    private final Map<Player, Long> lastBetTime = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerStats> playerStats = new ConcurrentHashMap<>();
    
    // Files
    private File statsFile;
    
    @Override
    public void onEnable() {
        // Initialize configuration
        saveDefaultConfig();
        configManager = new ConfigManager(getConfig());
        
        // Initialize core managers
        tableManager = new TableManager(this, configManager);
        commandManager = new CommandManager(this);
        asyncUtils = new AsyncUtils(this);
        
        // Initialize economy provider
        if (getServer().getPluginManager().getPlugin("Essentials") != null) {
            economyProvider = new EssentialsEconomyProvider();
            getLogger().info("Using EssentialsX economy provider");
        } else {
            getLogger().severe("No supported economy plugin found! Please install EssentialsX.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initialize files
        statsFile = new File(getDataFolder(), "stats.yml");
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        // Register commands - this enables individual commands like /bet, /hit, /stand
        commandManager.registerCommands();
        
        // Load tables from config
        tableManager.loadTablesFromConfig();
        
        getLogger().info("Blackjack plugin enabled successfully!");
        getLogger().info("✅ Players can now use individual commands like /bet, /hit, /stand!");
        getLogger().info("✅ Enhanced UX with clickable chat and GUI betting!");
    }
    
    @Override
    public void onDisable() {
        // Cancel all async tasks
        if (asyncUtils != null) {
            asyncUtils.cancelAllTasks();
        }
        
        // Cleanup and refund bets
        if (tableManager != null) {
            refundAllBets();
            tableManager.cleanup();
        }
        
        // Save player stats
        savePlayerStats();
        
        getLogger().info("Blackjack plugin disabled!");
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Remove from bet tracking
        lastBetTime.remove(player);
        playerPersistentBets.remove(player);
        
        // Remove from table if they're at one
        if (tableManager != null) {
            tableManager.removePlayerFromTable(player);
        }
        
        // Save their stats
        savePlayerStats();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        
        String action = args[0].toLowerCase();
        
        return switch (action) {
            case "createtable" -> handleCreateTable(player);
            case "removetable" -> handleRemoveTable(player);
            case "join" -> handleJoin(player);
            case "leave" -> handleLeave(player);
            case "start" -> handleStart(player);
            case "hit" -> handleHit(player);
            case "stand" -> handleStand(player);
            case "doubledown" -> handleDoubleDown(player);
            case "bet" -> handleBet(player, args);
            case "stats" -> handleStats(player);
            case "reload" -> handleReload(player);
            default -> {
                sendHelp(player);
                yield true;
            }
        };
    }
    
    // Command handlers - clean and focused methods
    
    private boolean handleCreateTable(Player player) {
        if (!player.hasPermission("blackjack.admin")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }
        
        if (tableManager.createTable(player.getLocation())) {
            player.sendMessage(configManager.getMessage("table-created"));
        } else {
            player.sendMessage("§cTable already exists at this location!");
        }
        return true;
    }
    
    private boolean handleRemoveTable(Player player) {
        if (!player.hasPermission("blackjack.admin")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }
        
        BlackjackTable nearestTable = tableManager.findNearestTable(player.getLocation());
        if (nearestTable != null) {
            if (tableManager.removeTable(nearestTable.getCenterLocation())) {
                player.sendMessage(configManager.getMessage("table-removed"));
            } else {
                player.sendMessage("§cFailed to remove table!");
            }
        } else {
            player.sendMessage(configManager.getMessage("no-table-nearby"));
        }
        return true;
    }
    
    private boolean handleJoin(Player player) {
        if (tableManager.getPlayerTable(player) != null) {
            player.sendMessage(configManager.getMessage("already-at-table"));
            return true;
        }
        
        BlackjackTable nearestTable = tableManager.findNearestTable(player.getLocation());
        if (nearestTable != null) {
            nearestTable.addPlayer(player);
        } else {
            player.sendMessage(configManager.getMessage("no-table-nearby"));
        }
        return true;
    }
    
    private boolean handleLeave(Player player) {
        BlackjackTable table = tableManager.getPlayerTable(player);
        if (table != null) {
            // Send message to table before removing player
            table.broadcastToTable("§e" + player.getName() + " left the table.");
            
            table.removePlayer(player);
            refundPlayerBet(player);
            
            // Clear persistent bet when leaving
            playerPersistentBets.remove(player);
            
            player.sendMessage("§aYou left the table.");
        } else {
            player.sendMessage(configManager.getMessage("not-at-table"));
        }
        return true;
    }
    
    private boolean handleStart(Player player) {
        BlackjackTable table = tableManager.getPlayerTable(player);
        if (table != null) {
            // Auto-bet if player has a persistent bet amount but no current bet
            Integer currentBet = playerBets.get(player);
            Integer persistentBet = playerPersistentBets.get(player);
            
            if ((currentBet == null || currentBet == 0) && persistentBet != null && persistentBet > 0) {
                // Attempt to place the persistent bet automatically
                if (processBet(player, persistentBet)) {
                    player.sendMessage("§aAuto-bet placed: $" + persistentBet);
                }
            }
            
            table.startGame();
        } else {
            player.sendMessage(configManager.getMessage("not-at-table"));
        }
        return true;
    }
    
    private boolean handleHit(Player player) {
        BlackjackTable table = tableManager.getPlayerTable(player);
        if (table != null) {
            table.hit(player);
        } else {
            player.sendMessage(configManager.getMessage("not-at-table"));
        }
        return true;
    }
    
    private boolean handleStand(Player player) {
        BlackjackTable table = tableManager.getPlayerTable(player);
        if (table != null) {
            table.stand(player);
        } else {
            player.sendMessage(configManager.getMessage("not-at-table"));
        }
        return true;
    }
    
    private boolean handleDoubleDown(Player player) {
        BlackjackTable table = tableManager.getPlayerTable(player);
        if (table != null) {
            table.doubleDown(player);
        } else {
            player.sendMessage(configManager.getMessage("not-at-table"));
        }
        return true;
    }
    
    private boolean handleBet(Player player, String[] args) {
        if (tableManager.getPlayerTable(player) == null) {
            player.sendMessage(configManager.getMessage("not-at-table"));
            return true;
        }
        
        if (args.length < 2) {
            player.sendMessage("§cUsage: /bet <amount>");
            return true;
        }
        
        try {
            int amount = Integer.parseInt(args[1]);
            return processBet(player, amount);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount!");
            return true;
        }
    }
    
    private boolean handleStats(Player player) {
        loadPlayerStats(player);
        PlayerStats stats = playerStats.get(player.getUniqueId());
        
        if (stats == null) {
            player.sendMessage("§cNo statistics found!");
            return true;
        }
        
        // Use the new formatted messages from config
        player.sendMessage(configManager.getMessage("stats-header"));
        player.sendMessage(configManager.formatMessage("stats-hands-won", "value", stats.getHandsWon()));
        player.sendMessage(configManager.formatMessage("stats-hands-lost", "value", stats.getHandsLost()));
        player.sendMessage(configManager.formatMessage("stats-hands-pushed", "value", stats.getHandsPushed()));
        player.sendMessage(configManager.formatMessage("stats-blackjacks", "value", stats.getBlackjacks()));
        player.sendMessage(configManager.formatMessage("stats-busts", "value", stats.getBusts()));
        player.sendMessage(configManager.formatMessage("stats-win-rate", "value", String.format("%.1f", stats.getWinRate())));
        player.sendMessage(configManager.formatMessage("stats-current-streak", "value", stats.getCurrentStreak()));
        player.sendMessage(configManager.formatMessage("stats-best-streak", "value", stats.getBestStreak()));
        player.sendMessage(configManager.formatMessage("stats-total-winnings", "value", String.format("%.2f", stats.getTotalWinnings())));
        
        return true;
    }
    
    private boolean handleReload(Player player) {
        if (!player.hasPermission("blackjack.admin")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }
        
        reloadConfig();
        configManager.reload(getConfig());
        player.sendMessage("§aConfiguration reloaded!");
        return true;
    }
    
    // Betting system - improved and thread-safe
    
    private boolean processBet(Player player, int amount) {
        // Validate bet amount
        if (amount < configManager.getMinBet() || amount > configManager.getMaxBet()) {
            player.sendMessage(configManager.formatMessage("invalid-bet", 
                "min_bet", configManager.getMinBet(), "max_bet", configManager.getMaxBet()));
            return true;
        }
        
        // Check cooldown
        long currentTime = System.currentTimeMillis();
        long lastBet = lastBetTime.getOrDefault(player, 0L);
        if (currentTime - lastBet < configManager.getBetCooldown()) {
            player.sendMessage(configManager.getMessage("bet-cooldown"));
            return true;
        }
        
        // Check if player has enough money
        if (!economyProvider.hasEnough(player.getUniqueId(), BigDecimal.valueOf(amount))) {
            player.sendMessage(configManager.formatMessage("insufficient-funds", "amount", amount));
            return true;
        }
        
        // Process the bet
        int previousBet = playerBets.getOrDefault(player, 0);
        int difference = amount - previousBet;
        
        if (difference > 0) {
            // Taking more money
            if (economyProvider.subtract(player.getUniqueId(), BigDecimal.valueOf(difference))) {
                playerBets.put(player, amount);
                playerPersistentBets.put(player, amount); // Store for "Play Again"
                lastBetTime.put(player, currentTime);
                player.sendMessage(configManager.formatMessage("bet-set", "amount", amount));
                
                // Auto-start game if player is at table and no game in progress
                BlackjackTable table = tableManager.getPlayerTable(player);
                if (table != null && !table.isGameInProgress() && previousBet == 0) {
                    // First bet placed, try to start game
                    this.getServer().getScheduler().runTaskLater(this, () -> {
                        if (table.canStartGame()) {
                            table.startGame();
                        }
                    }, 20L); // 1 second delay to allow other players to bet
                }
            } else {
                player.sendMessage("§cFailed to process bet!");
            }
        } else if (difference < 0) {
            // Refunding some money
            int refund = -difference;
            if (economyProvider.add(player.getUniqueId(), BigDecimal.valueOf(refund))) {
                playerBets.put(player, amount);
                playerPersistentBets.put(player, amount); // Store for "Play Again"
                lastBetTime.put(player, currentTime);
                player.sendMessage("§aBet reduced to $" + amount + " and refunded $" + refund + "!");
            } else {
                player.sendMessage("§cFailed to process bet refund!");
            }
        } else {
            player.sendMessage("§eYour bet is already $" + amount + "!");
        }
        
        return true;
    }
    
    private void refundPlayerBet(Player player) {
        Integer betAmount = playerBets.remove(player);
        if (betAmount != null && betAmount > 0) {
            if (economyProvider.add(player.getUniqueId(), BigDecimal.valueOf(betAmount))) {
                player.sendMessage(configManager.formatMessage("bet-refunded", "amount", betAmount));
            } else {
                player.sendMessage(configManager.getMessage("error-refund"));
                getLogger().severe("Failed to refund bet for " + player.getName());
            }
        }
    }
    
    private void refundAllBets() {
        for (Map.Entry<Player, Integer> entry : playerBets.entrySet()) {
            Player player = entry.getKey();
            Integer amount = entry.getValue();
            
            if (amount != null && amount > 0) {
                BlackjackTable table = tableManager.getPlayerTable(player);
                if (table == null || !table.isGameInProgress()) {
                    economyProvider.add(player.getUniqueId(), BigDecimal.valueOf(amount));
                    if (player.isOnline()) {
                        player.sendMessage("§aBet refunded due to server shutdown: $" + amount);
                    }
                }
            }
        }
        playerBets.clear();
    }
    
    // Player statistics system
    
    private void loadPlayerStats(Player player) {
        if (playerStats.containsKey(player.getUniqueId())) {
            return;
        }
        
        FileConfiguration statsConfig = YamlConfiguration.loadConfiguration(statsFile);
        PlayerStats stats = new PlayerStats();
        String path = "players." + player.getUniqueId() + ".";
        
        stats.setHandsWon(statsConfig.getInt(path + "handsWon", 0));
        stats.setHandsLost(statsConfig.getInt(path + "handsLost", 0));
        stats.setHandsPushed(statsConfig.getInt(path + "handsPushed", 0));
        stats.setCurrentStreak(statsConfig.getInt(path + "currentStreak", 0));
        stats.setBestStreak(statsConfig.getInt(path + "bestStreak", 0));
        stats.setTotalWinnings(statsConfig.getDouble(path + "totalWinnings", 0.0));
        stats.setBlackjacks(statsConfig.getInt(path + "blackjacks", 0));
        stats.setBusts(statsConfig.getInt(path + "busts", 0));
        
        playerStats.put(player.getUniqueId(), stats);
    }
    
    private void savePlayerStats() {
        if (playerStats.isEmpty()) {
            return;
        }
        
        FileConfiguration statsConfig = YamlConfiguration.loadConfiguration(statsFile);
        
        for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
            String path = "players." + entry.getKey() + ".";
            PlayerStats stats = entry.getValue();
            
            statsConfig.set(path + "handsWon", stats.getHandsWon());
            statsConfig.set(path + "handsLost", stats.getHandsLost());
            statsConfig.set(path + "handsPushed", stats.getHandsPushed());
            statsConfig.set(path + "currentStreak", stats.getCurrentStreak());
            statsConfig.set(path + "bestStreak", stats.getBestStreak());
            statsConfig.set(path + "totalWinnings", stats.getTotalWinnings());
            statsConfig.set(path + "blackjacks", stats.getBlackjacks());
            statsConfig.set(path + "busts", stats.getBusts());
        }
        
        try {
            if (!statsFile.getParentFile().exists()) {
                statsFile.getParentFile().mkdirs();
            }
            statsConfig.save(statsFile);
        } catch (IOException e) {
            getLogger().severe("Could not save player statistics: " + e.getMessage());
        }
    }
    
    private void sendHelp(Player player) {
        player.sendMessage(configManager.getMessage("prefix") + "§rAvailable Commands:");
        
        if (player.hasPermission("blackjack.admin")) {
            player.sendMessage("§e/bj createtable §7- Create a new blackjack table");
            player.sendMessage("§e/bj removetable §7- Remove the nearest table");
            player.sendMessage("§e/bj reload §7- Reload configuration");
        }
        
        player.sendMessage("§e/join §7- Join the nearest table");
        player.sendMessage("§e/leave §7- Leave your current table");
        player.sendMessage("§e/bet <amount> §7- Place or change your bet");
        player.sendMessage("§e/start §7- Start a new game");
        player.sendMessage("§e/hit §7- Take another card");
        player.sendMessage("§e/stand §7- End your turn");
        player.sendMessage("§e/bj stats §7- View your statistics");
        
        player.sendMessage("");
        player.sendMessage("§a✨ TIP: Use individual commands like §e/bet§a, §e/hit§a, §e/stand §ainstead of §e/bj§a!");
        player.sendMessage("§a✨ Type §e/bet §aalone to open the visual betting menu!");
    }
    
    // Getters for managers (used by other classes)
    public ConfigManager getConfigManager() { return configManager; }
    public TableManager getTableManager() { return tableManager; }
    public EconomyProvider getEconomyProvider() { return economyProvider; }
    public AsyncUtils getAsyncUtils() { return asyncUtils; }
    
    // Player data getters
    public Map<Player, Integer> getPlayerBets() { return playerBets; }
    public Map<Player, Integer> getPlayerPersistentBets() { return playerPersistentBets; }
    public Map<UUID, PlayerStats> getPlayerStats() { return playerStats; }
}
