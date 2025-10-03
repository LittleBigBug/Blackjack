package com.vortex.blackjack;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.EventManager;
import com.vortex.blackjack.commands.BlackjackCommand;
import com.vortex.blackjack.config.Config;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.economy.EconomyProvider;
import com.vortex.blackjack.economy.VaultEconomyProvider;
import com.vortex.blackjack.integration.BlackjackPlaceholderExpansion;
import com.vortex.blackjack.listener.*;
import com.vortex.blackjack.model.PlayerStats;
import com.vortex.blackjack.table.BetManager;
import com.vortex.blackjack.table.TableManager;
import com.vortex.blackjack.util.AsyncUtils;
import com.vortex.blackjack.util.ChatUtils;
import com.vortex.blackjack.util.GenericUtils;
import com.vortex.blackjack.util.VersionChecker;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
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
    
    // PlaceholderAPI integration
    private BlackjackPlaceholderExpansion placeholderExpansion;
    
    // Version checker
    private VersionChecker versionChecker;
    
    // Player data - thread-safe collections
    private final Map<UUID, PlayerStats> playerStats = new ConcurrentHashMap<>();

    public final NamespacedKey chipNameKey = new NamespacedKey(this, "chipName");
    public final NamespacedKey chipPriceKey = new NamespacedKey(this, "chipPrice");

    // Files
    private File statsFile;

    private Config guiConfig;
    private Config chipsConfig;
    private Config messagesConfig;
    private Config tablesData;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.guiConfig = new Config(this, "gui.yml");
        this.chipsConfig = new Config(this, "chips.yml");
        this.messagesConfig = new Config(this, "messages.yml");
        this.tablesData = new Config(this, "data/tables.yml");

        configManager = new ConfigManager(this);
        
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
        
        // Check for PlaceholderAPI integration
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new BlackjackPlaceholderExpansion(this);
            placeholderExpansion.register();
            getLogger().info("PlaceholderAPI found! Extensive placeholder support enabled.");
        } else
            getLogger().info("PlaceholderAPI not found. Placeholder support disabled.");
        
        // Initialize files
        statsFile = new File(getDataFolder(), "stats.yml");

        // Register protocol events
        PacketEventsAPI<?> packetApi = PacketEvents.getAPI();
        packetApi.init();

        EventManager eventManager = packetApi.getEventManager();

        eventManager.registerListener(new PlayerInputPacketListener(this));

        // Register Bukkit events
        PluginManager pluginManager = getServer().getPluginManager();

        pluginManager.registerEvents(new ChatListener(this), this);
        pluginManager.registerEvents(new InventoryListener(this), this);
        pluginManager.registerEvents(new InteractListener(this), this);
        pluginManager.registerEvents(new PlayerListener(this), this);
        
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

        PacketEvents.getAPI().terminate();

        // Save player stats
        savePlayerStats();

        getLogger().info("Blackjack plugin disabled!");
    }

    private EconomyProvider initializeEconomyProvider() {
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) return null;

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

    public Config getGuiConfig() { return guiConfig; }
    public Config getChipsConfig() { return chipsConfig; }
    public Config getMessagesConfig() { return messagesConfig; }
    public Config getTablesData() { return tablesData; }

    // Player data getters
    public Map<UUID, PlayerStats> getPlayerStats() { return playerStats; }
    public File getStatsFile() { return statsFile; }
    
    public VersionChecker getVersionChecker() { return versionChecker; }

    public NamespacedKey getChipNameKey() { return chipNameKey; }
    public NamespacedKey getChipPriceKey() { return chipPriceKey; }
}
