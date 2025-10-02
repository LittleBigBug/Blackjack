package com.vortex.blackjack;

import com.vortex.blackjack.commands.BlackjackCommand;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.economy.EconomyProvider;
import com.vortex.blackjack.economy.VaultEconomyProvider;
import com.vortex.blackjack.integration.BlackjackPlaceholderExpansion;
import com.vortex.blackjack.listener.ChatListener;
import com.vortex.blackjack.listener.InteractListener;
import com.vortex.blackjack.listener.PlayerListener;
import com.vortex.blackjack.model.PlayerStats;
import com.vortex.blackjack.table.BetManager;
import com.vortex.blackjack.table.TableManager;
import com.vortex.blackjack.util.AsyncUtils;
import com.vortex.blackjack.util.ChatUtils;
import com.vortex.blackjack.util.GenericUtils;
import com.vortex.blackjack.util.VersionChecker;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main Blackjack plugin class
 */
public class BlackjackPlugin extends JavaPlugin  {
    
    // Core managers - each handles a specific responsibility
    private ConfigManager configManager;
    private TableManager tableManager;
    private ChatUtils chatUtils;
    private AsyncUtils asyncUtils;
    private EconomyProvider economyProvider;
    private BetManager betManager;

    // GSit integration
    private boolean gSitEnabled = false;
    
    // PlaceholderAPI integration
    private BlackjackPlaceholderExpansion placeholderExpansion;
    
    // Version checker
    private VersionChecker versionChecker;
    
    // Player data - thread-safe collections
    private final Map<UUID, PlayerStats> playerStats = new ConcurrentHashMap<>();
    
    // Files
    private File statsFile;
    
    @Override
    public void onEnable() {
        // Initialize configuration
        saveDefaultConfig();
        
        // Load messages configuration
        File messagesFile = new File(getDataFolder(), "messages.yml");
        FileConfiguration messagesConfig = null;
        
        if (!messagesFile.exists()) {
            // Try to save the resource if it exists in the JAR
            try {
                saveResource("messages.yml", false);
                messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
                getLogger().info("Created messages.yml file");
            } catch (IllegalArgumentException e) {
                // messages.yml doesn't exist in JAR, create a default one
                getLogger().info("Creating default messages.yml file");
                createDefaultMessagesFile(messagesFile);
                messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
            }
        } else {
            messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        }
        
        configManager = new ConfigManager(getConfig(), messagesConfig);
        
        // Initialize core managers
        betManager = new BetManager(this);
        tableManager = new TableManager(this);
        chatUtils = new ChatUtils(this);
        asyncUtils = new AsyncUtils(this);
        versionChecker = new VersionChecker(this);

        // Initialize economy provider - Vault with EssentialsX fallback
        economyProvider = initializeEconomyProvider();
        
        if (economyProvider == null) {
            getLogger().severe("No supported economy plugin found! Please install Vault with an economy plugin (like EssentialsX, EconomyAPI, CMI, HexaEcon, etc.)");
            getLogger().severe("Disabling Blackjack...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Check for GSit integration
        if (getServer().getPluginManager().getPlugin("GSit") != null) {
            gSitEnabled = true;
            getLogger().info("GSit found! Players will automatically sit when joining tables.");
        } else {
            getLogger().info("GSit not found. Players will not automatically sit when joining tables.");
        }
        
        // Check for PlaceholderAPI integration
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new BlackjackPlaceholderExpansion(this);
            placeholderExpansion.register();
            getLogger().info("PlaceholderAPI found! Extensive placeholder support enabled.");
        } else {
            getLogger().info("PlaceholderAPI not found. Placeholder support disabled.");
        }
        
        // Initialize files
        statsFile = new File(getDataFolder(), "stats.yml");
        
        // Register events
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new InteractListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        
        // Register commands
        PluginCommand cmd = this.getCommand("blackjack");
        if (cmd != null) cmd.setExecutor(new BlackjackCommand(this));
        else getLogger().severe("Could not register command /blackjack!");
        
        // Load tables from config
        tableManager.loadTablesFromConfig();
        
        // Start version checking
        versionChecker.checkForUpdates();
        
        // Schedule periodic stats saving - configurable interval
        int statsSaveInterval = configManager.getStatsSaveInterval();
        long ticks = statsSaveInterval * 20L; // Convert seconds to ticks (20 ticks = 1 second)
        asyncUtils.scheduleRepeating("stats-autosave", this::savePlayerStats, ticks, ticks);
        getLogger().info("Stats auto-save scheduled every " + statsSaveInterval + " seconds");
        
        getLogger().info("Blackjack enabled successfully!");
    }
    
    @Override
    public void onDisable() {
        // Unregister PlaceholderAPI expansion
        if (placeholderExpansion != null)
            this.placeholderExpansion.unregister();

        // Cancel all async tasks
        if (asyncUtils != null)
            this.asyncUtils.cancelAllTasks();

        // Cleanup and refund bets
        if (tableManager != null) {
            this.betManager.refundAllBets();
            this.tableManager.cleanup();
        }

        // Save player stats
        savePlayerStats();

        getLogger().info("Blackjack plugin disabled!");
    }
    
    /**
     * Create a default messages.yml file with all the required messages
     */
    private void createDefaultMessagesFile(File messagesFile) {
        try {
            if (!messagesFile.getParentFile().exists()) {
                messagesFile.getParentFile().mkdirs();
            }
            
            FileConfiguration messagesConfig = new YamlConfiguration();
            
            // Add all the default messages
            messagesConfig.set("prefix", "&8[&6Blackjack&8] &r");
            messagesConfig.set("no-permission", "&cYou don't have permission to do that!");
            messagesConfig.set("player-only-command", "&cThis command can only be used by players!");
            
            // Table management
            messagesConfig.set("table-created", "&aBlackjack table created!");
            messagesConfig.set("table-removed", "&cBlackjack table removed!");
            messagesConfig.set("table-already-exists", "&cTable already exists at this location!");
            messagesConfig.set("table-remove-failed", "&cFailed to remove table!");
            messagesConfig.set("no-table-nearby", "&cNo table found nearby!");
            
            // Player table status
            messagesConfig.set("already-at-table", "&cYou are already at a table! Use /leave to leave your current table.");
            messagesConfig.set("not-at-table", "&cYou're not at a table! Use /join near a table to play.");
            messagesConfig.set("auto-left-table", "&eYou moved too far from the table and were automatically removed.");
            messagesConfig.set("left-table", "&aYou left the table.");
            messagesConfig.set("left-table-bet-refunded", "&aYou left the table and your bet of $%amount% has been refunded.");
            messagesConfig.set("left-table-bet-forfeit", "&cYou left the table mid-game and forfeit your bet of $%amount%.");
            
            // Table joining
            messagesConfig.set("table-full", "&cThis table is full!");
            messagesConfig.set("too-far", "&cYou are too far from the table to join!");
            messagesConfig.set("no-seats", "&cNo available seats!");
            messagesConfig.set("join-error", "&cError joining table. Please try again.");
            messagesConfig.set("game-in-progress", "&cCannot perform this action during an active game!");
            
            // Betting
            messagesConfig.set("bet-required", "&cYou must place a bet before the game can start! Use /bet <amount>");
            messagesConfig.set("invalid-bet", "&cInvalid bet amount! Must be between %min_bet% and %max_bet%.");
            messagesConfig.set("insufficient-funds", "&cYou don't have enough money to bet $%amount%!");
            messagesConfig.set("bet-cooldown", "&cPlease wait a moment before changing your bet again.");
            messagesConfig.set("bet-set", "&aYour bet has been set to $%amount%!");
            messagesConfig.set("bet-already-set", "&eYour bet is already $%amount%!");
            messagesConfig.set("bet-refunded", "&aYour bet of $%amount% has been refunded.");
            messagesConfig.set("bet-refunded-shutdown", "&aBet refunded due to server shutdown: $%amount%");
            messagesConfig.set("bet-reduced-refunded", "&aBet reduced to $%amount% and refunded $%refund%!");
            messagesConfig.set("auto-bet-placed", "&aAuto-bet placed: $%amount%");
            messagesConfig.set("invalid-amount", "&cInvalid amount!");
            messagesConfig.set("bet-usage", "&cUsage: /bet <amount>");
            messagesConfig.set("bet-failed", "&cFailed to process bet!");
            messagesConfig.set("bet-refund-failed", "&cFailed to process bet refund!");
            messagesConfig.set("error-refund", "&cAn error occurred while refunding your bet. Contact a staff member!");
            messagesConfig.set("error-payout", "&cAn error occurred processing your payout. Contact a staff member!");
            
            // Quick bet menu
            messagesConfig.set("quick-bet-header", "&6&l=== Quick Bet Menu ===");
            messagesConfig.set("quick-bet-description", "&7Click on an amount to place your bet:");
            messagesConfig.set("quick-bet-border", "&e&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            messagesConfig.set("quick-bet-title", "&6&l                          QUICK BET");
            
            // Game flow
            messagesConfig.set("game-started", "&aGame started! %player%'s turn.");
            messagesConfig.set("your-turn", "&aIt's your turn!");
            messagesConfig.set("hand-value", "&aYour hand value: %value%");
            messagesConfig.set("dealer-card", "&aDealer's visible card: %card% | Value: %value%");
            messagesConfig.set("dealer-value", "&aDealer's final hand value: %value%");
            
            // Game actions
            messagesConfig.set("player-busts", "&c%player% busts!");
            messagesConfig.set("player-stands", "&a%player% stands at %value%!");
            messagesConfig.set("player-wins", "&a%player% wins and gets back $%amount%!");
            messagesConfig.set("player-loses", "&c%player% loses their bet of $%amount%!");
            messagesConfig.set("player-pushes", "&e%player% pushes and gets their bet of $%amount% back!");
            messagesConfig.set("player-blackjack", "&6%player% got BLACKJACK and wins $%amount%!");
            
            // Double down
            messagesConfig.set("double-down-first-two-cards", "&cYou can only double down on your first two cards!");
            messagesConfig.set("double-down-already-used", "&cYou have already doubled down!");
            messagesConfig.set("double-down-insufficient-funds", "&cYou don't have enough money to double down!");
            
            // Hand display
            messagesConfig.set("hand-display", "&bHand: %hand% | %hand_value%");
            messagesConfig.set("dealer-shows", "&aDealer shows: %card% | %value%");
            
            // Statistics
            messagesConfig.set("stats-header", "&6=== Blackjack Statistics ===");
            messagesConfig.set("stats-other-player-header", "&6=== %player%'s Blackjack Statistics ===");
            messagesConfig.set("stats-hands-won", "&eHands Won: &a%value%");
            messagesConfig.set("stats-hands-lost", "&eHands Lost: &c%value%");
            messagesConfig.set("stats-hands-pushed", "&eHands Pushed: &7%value%");
            messagesConfig.set("stats-blackjacks", "&eBlackjacks: &6%value%");
            messagesConfig.set("stats-busts", "&eBusts: &c%value%");
            messagesConfig.set("stats-win-rate", "&eWin Rate: &a%value%%");
            messagesConfig.set("stats-current-streak", "&eCurrent Streak: &b%value%");
            messagesConfig.set("stats-best-streak", "&eBest Streak: &a%value%");
            messagesConfig.set("stats-total-winnings", "&eTotal Winnings: &2$%value%");
            messagesConfig.set("stats-no-permission", "&cYou don't have permission to check other players' statistics!");
            messagesConfig.set("stats-player-not-found", "&cPlayer not found: %player%");
            messagesConfig.set("stats-none-found", "&cNo statistics found!");
            messagesConfig.set("stats-none-found-player", "&cNo statistics found for %player%!");
            
            // Configuration
            messagesConfig.set("config-reloaded", "&aConfiguration reloaded!");
            
            // Help command
            messagesConfig.set("help-header", "&rAvailable Commands:");
            messagesConfig.set("help-admin-create", "&e/bj createtable &7- Create a new blackjack table");
            messagesConfig.set("help-admin-remove", "&e/bj removetable &7- Remove the nearest table");
            messagesConfig.set("help-admin-reload", "&e/bj reload &7- Reload configuration");
            messagesConfig.set("help-join", "&e/join &7- Join the nearest table");
            messagesConfig.set("help-leave", "&e/leave &7- Leave your current table");
            messagesConfig.set("help-bet", "&e/bet <amount> &7- Place or change your bet");
            messagesConfig.set("help-start", "&e/start &7- Start a new game");
            messagesConfig.set("help-hit", "&e/hit &7- Take another card");
            messagesConfig.set("help-stand", "&e/stand &7- End your turn");
            messagesConfig.set("help-stats", "&e/bj stats &7- View your statistics");
            messagesConfig.set("help-stats-others", "&e/bj stats <player> &7- View another player's statistics");
            
            // Table broadcast messages
            messagesConfig.set("player-left-during-turn", "&c%player% %reason% during their turn.");
            messagesConfig.set("player-left-table", "&c%player% %reason%.");
            
            // Game action prompts
            messagesConfig.set("game-action-prompt", "&7Your turn: ");
            messagesConfig.set("game-action-separator", "&7 | ");
            messagesConfig.set("post-game-prompt", "&7Choose: ");
            
            // Betting category labels
            messagesConfig.set("betting-category-small", "&7Small: ");
            messagesConfig.set("betting-category-medium", "&7Medium: ");
            messagesConfig.set("betting-category-large", "&7Large: ");
            
            // Button configuration
            messagesConfig.set("buttons.hit.text", "&a&l[HIT]");
            messagesConfig.set("buttons.hit.command", "/hit");
            messagesConfig.set("buttons.hit.hover", "&eClick to take another card");
            
            messagesConfig.set("buttons.stand.text", "&c&l[STAND]");
            messagesConfig.set("buttons.stand.command", "/stand");
            messagesConfig.set("buttons.stand.hover", "&eClick to end your turn");
            
            messagesConfig.set("buttons.double-down.text", "&6&l[DOUBLE DOWN]");
            messagesConfig.set("buttons.double-down.command", "/doubledown");
            messagesConfig.set("buttons.double-down.hover", "&eClick to double your bet and take one card");
            
            messagesConfig.set("buttons.play-again.text", "&a&l[Play Again]");
            messagesConfig.set("buttons.play-again.command", "/start");
            messagesConfig.set("buttons.play-again.hover", "&eClick to start a new game");
            
            messagesConfig.set("buttons.leave-table.text", "&c&l[Leave Table]");
            messagesConfig.set("buttons.leave-table.command", "/leave");
            messagesConfig.set("buttons.leave-table.hover", "&eClick to leave the table");
            
            messagesConfig.set("buttons.custom-bet.text", "&b&l[CUSTOM BET]");
            messagesConfig.set("buttons.custom-bet.command", "/bet ");
            messagesConfig.set("buttons.custom-bet.hover", "&eClick to enter custom amount");
            
            // Button color configurations
            messagesConfig.set("buttons.small-bet-color", "&a");    // Green for small bets
            messagesConfig.set("buttons.medium-bet-color", "&e");   // Yellow for medium bets
            messagesConfig.set("buttons.large-bet-color", "&c");    // Red for large bets
            messagesConfig.set("buttons.huge-bet-color", "&d");     // Pink for huge bets
            
            messagesConfig.save(messagesFile);
            
        } catch (IOException e) {
            getLogger().severe("Could not create default messages.yml: " + e.getMessage());
        }
    }
    
    /**
     * Initialize economy provider
     */
    private EconomyProvider initializeEconomyProvider() {
        VaultEconomyProvider provider = new VaultEconomyProvider(this);
        
        if (provider.isEnabled()) {
            getLogger().info("Economy: " + provider.getProviderName());
            return provider;
        }
        
        // Try delayed initialization for late-loading economy plugins
        getServer().getScheduler().runTaskLater(this, () -> {
            if (provider.reconnect()) {
                economyProvider = provider;
                getLogger().info("Economy (delayed): " + provider.getProviderName());
            } else {
                getLogger().warning("No economy plugin found! Install Vault + an economy plugin (EssentialsX, HexaEcon, etc.)");
            }
        }, 40L);
        
        return provider; // Return even if not enabled, might work after delay
    }
    
    // Player statistics system
    
    
    private void savePlayerStats() {
        if (playerStats.isEmpty()) {
            return;
        }
        
        FileConfiguration statsConfig = YamlConfiguration.loadConfiguration(statsFile);
        
        for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
            GenericUtils.savePlayerStats(statsConfig, entry.getKey(), entry.getValue());
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
    

    
    // Getters for managers (used by other classes)
    public ConfigManager getConfigManager() { return configManager; }
    public TableManager getTableManager() { return tableManager; }
    public EconomyProvider getEconomyProvider() { return economyProvider; }
    public ChatUtils getChatUtils() { return chatUtils; }
    public AsyncUtils getAsyncUtils() { return asyncUtils; }
    public BetManager getBetManager() { return betManager; }
    
    // Player data getters
    public Map<UUID, PlayerStats> getPlayerStats() { return playerStats; }
    public File getStatsFile() { return statsFile; }
    public boolean isGSitEnabled() { return gSitEnabled; }
    
    public VersionChecker getVersionChecker() { return versionChecker; }
}
