package com.vortex.blackjack.config;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Centralized configuration management with validation and caching
 */
public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration messagesConfig;
    
    // Cached values for performance
    private int minBet;
    private int maxBet;
    private long betCooldown;
    private double maxJoinDistance;
    private int maxPlayers;
    private Material tableMaterial;
    private Material chairMaterial;
    private boolean soundsEnabled;
    private boolean particlesEnabled;
    private boolean hitSoft17;
    
    public ConfigManager(JavaPlugin plugin, FileConfiguration config, FileConfiguration messagesConfig) {
        this.plugin = plugin;
        this.config = config;
        this.messagesConfig = messagesConfig;
        loadAndValidateConfig();
    }
    
    // Backward compatibility constructor
    public ConfigManager(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.messagesConfig = config; // Use main config for messages if no separate messages config
        loadAndValidateConfig();
    }
    
    /**
     * Migrates and validates configuration files by adding missing keys and removing obsolete ones
     */
    public boolean migrateConfiguration() {
        boolean configChanged = false;
        boolean messagesChanged = false;
        
        // Migrate main config
        configChanged = migrateMainConfig();
        
        // Migrate messages config  
        messagesChanged = migrateMessagesConfig();
        
        return configChanged || messagesChanged;
    }
    
    private boolean migrateMainConfig() {
        boolean changed = false;
        int addedKeys = 0;
        int removedKeys = 0;
        
        try {
            // Load the latest config from GitHub repository
            String githubConfigUrl = "https://raw.githubusercontent.com/DefectiveVortex/Blackjack/main/src/main/resources/config.yml";
            org.bukkit.configuration.file.YamlConfiguration defaultConfig = loadConfigFromUrl(githubConfigUrl);
            
            if (defaultConfig == null) {
                plugin.getLogger().warning("Failed to load default config from GitHub, falling back to JAR resources");
                // Fallback to JAR resources
                java.io.InputStream defaultConfigStream = getClass().getClassLoader().getResourceAsStream("config.yml");
                if (defaultConfigStream == null) {
                    plugin.getLogger().warning("No default config.yml found in resources");
                    return false;
                }
                defaultConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(defaultConfigStream, java.nio.charset.StandardCharsets.UTF_8)
                );
                defaultConfigStream.close();
            }
            
            // Get all keys from default config
            java.util.Set<String> defaultKeys = getAllKeys(defaultConfig);
            java.util.Set<String> currentKeys = getAllKeys(config);
            
            // Protected keys that should never be removed (runtime data)
            java.util.Set<String> protectedKeys = new java.util.HashSet<>();
            protectedKeys.add("tables");
            protectedKeys.add("tables.world");
            
            // Add missing keys from default config
            for (String key : defaultKeys) {
                if (!config.contains(key)) {
                    Object value = defaultConfig.get(key);
                    config.set(key, value);
                    addedKeys++;
                    changed = true;
                    plugin.getLogger().info("Added missing config key: " + key);
                }
            }
            
            // Remove obsolete keys (keys that exist in current but not in default)
            // BUT preserve protected keys that contain runtime data
            for (String key : currentKeys) {
                if (!defaultKeys.contains(key)) {
                    // Check if this key or any parent key is protected
                    boolean isProtected = false;
                    for (String protectedKey : protectedKeys) {
                        if (key.equals(protectedKey) || key.startsWith(protectedKey + ".")) {
                            isProtected = true;
                            break;
                        }
                    }
                    
                    if (!isProtected) {
                        config.set(key, null);
                        removedKeys++;
                        changed = true;
                        plugin.getLogger().info("Removed obsolete config key: " + key);
                    } else {
                        plugin.getLogger().info("Preserved protected key: " + key);
                    }
                }
            }
            
            if (changed) {
                plugin.getLogger().info("Config migration: Added " + addedKeys + " keys, removed " + removedKeys + " keys");
            } else {
                plugin.getLogger().info("Config migration: No changes needed");
            }
            
        } catch (Exception e) {
            // If something goes wrong, log it but don't fail the migration
            plugin.getLogger().severe("Error during config migration: " + e.getMessage());
            e.printStackTrace();
        }
        
        return changed;
    }
    
    private boolean migrateMessagesConfig() {
        boolean changed = false;
        int addedKeys = 0;
        int removedKeys = 0;
        
        try {
            // Load the latest messages from GitHub repository
            String githubMessagesUrl = "https://raw.githubusercontent.com/DefectiveVortex/Blackjack/main/src/main/resources/messages.yml";
            org.bukkit.configuration.file.YamlConfiguration defaultMessages = loadConfigFromUrl(githubMessagesUrl);
            
            if (defaultMessages == null) {
                plugin.getLogger().warning("Failed to load default messages from GitHub, falling back to JAR resources");
                // Fallback to JAR resources
                java.io.InputStream defaultMessagesStream = getClass().getClassLoader().getResourceAsStream("messages.yml");
                if (defaultMessagesStream == null) {
                    plugin.getLogger().warning("No default messages.yml found in resources");
                    return false;
                }
                defaultMessages = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(defaultMessagesStream, java.nio.charset.StandardCharsets.UTF_8)
                );
                defaultMessagesStream.close();
            }
            
            // Get all keys from default messages
            java.util.Set<String> defaultKeys = getAllKeys(defaultMessages);
            java.util.Set<String> currentKeys = getAllKeys(messagesConfig);
            
            // Add missing keys from default messages
            for (String key : defaultKeys) {
                if (!messagesConfig.contains(key)) {
                    Object value = defaultMessages.get(key);
                    messagesConfig.set(key, value);
                    addedKeys++;
                    changed = true;
                    plugin.getLogger().info("Added missing message key: " + key);
                }
            }
            
            // Remove obsolete keys (keys that exist in current but not in default)
            for (String key : currentKeys) {
                if (!defaultKeys.contains(key)) {
                    messagesConfig.set(key, null);
                    removedKeys++;
                    changed = true;
                    plugin.getLogger().info("Removed obsolete message key: " + key);
                }
            }
            
            if (changed) {
                plugin.getLogger().info("Messages migration: Added " + addedKeys + " keys, removed " + removedKeys + " keys");
            } else {
                plugin.getLogger().info("Messages migration: No changes needed");
            }
            
        } catch (Exception e) {
            // If something goes wrong, log it but don't fail the migration
            plugin.getLogger().severe("Error during messages migration: " + e.getMessage());
            e.printStackTrace();
        }
        
        return changed;
    }
    
    /**
     * Loads a YAML configuration from a URL (like GitHub raw file)
     */
    private org.bukkit.configuration.file.YamlConfiguration loadConfigFromUrl(String url) {
        try {
            java.net.URI githubUri = java.net.URI.create(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) githubUri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // 5 second timeout
            connection.setReadTimeout(10000);   // 10 second read timeout
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                try (java.io.InputStream inputStream = connection.getInputStream();
                     java.io.InputStreamReader reader = new java.io.InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8)) {
                    
                    return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);
                }
            } else {
                plugin.getLogger().warning("Failed to fetch config from GitHub: HTTP " + responseCode);
                return null;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error fetching config from GitHub: " + e.getMessage());
            return null;
        }
    }

    /**
     * Recursively gets all keys from a configuration file
     */
    private java.util.Set<String> getAllKeys(org.bukkit.configuration.file.FileConfiguration config) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        getAllKeysRecursive(config, "", keys);
        return keys;
    }
    
    /**
     * Helper method to recursively collect all keys from a configuration section
     */
    private void getAllKeysRecursive(org.bukkit.configuration.ConfigurationSection section, String prefix, java.util.Set<String> keys) {
        for (String key : section.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            
            if (section.isConfigurationSection(key)) {
                // If it's a section, recurse into it
                getAllKeysRecursive(section.getConfigurationSection(key), fullKey, keys);
            } else {
                // If it's a value, add the key
                keys.add(fullKey);
            }
        }
    }
    
    private void loadAndValidateConfig() {
        // Betting settings
        minBet = Math.max(1, config.getInt("betting.min-bet", 10));
        maxBet = Math.max(minBet, config.getInt("betting.max-bet", 10000));
        betCooldown = Math.max(0, config.getLong("betting.cooldown-ms", 2000L));
        
        // Table settings
        maxJoinDistance = Math.max(1.0, config.getDouble("table.max-join-distance", 10.0));
        maxPlayers = Math.max(1, Math.min(8, config.getInt("table.max-players", 4)));
        
        // Materials with fallbacks
        try {
            tableMaterial = Material.valueOf(config.getString("table.table-material", "GREEN_TERRACOTTA"));
        } catch (IllegalArgumentException e) {
            tableMaterial = Material.GREEN_TERRACOTTA;
        }
        
        try {
            chairMaterial = Material.valueOf(config.getString("table.chair-material", "DARK_OAK_STAIRS"));
        } catch (IllegalArgumentException e) {
            chairMaterial = Material.DARK_OAK_STAIRS;
        }
        
        // Audio/visual settings
        soundsEnabled = config.getBoolean("sounds.enabled", true);
        particlesEnabled = config.getBoolean("particles.enabled", true);
        
        // Game rules
        hitSoft17 = config.getBoolean("game.hit-soft-17", false);
    }
    
    // Getters
    public int getMinBet() { return minBet; }
    public int getMaxBet() { return maxBet; }
    public long getBetCooldown() { return betCooldown; }
    public double getMaxJoinDistance() { return maxJoinDistance; }
    public int getMaxPlayers() { return maxPlayers; }
    public Material getTableMaterial() { return tableMaterial; }
    public Material getChairMaterial() { return chairMaterial; }
    public boolean areSoundsEnabled() { return soundsEnabled; }
    public boolean areParticlesEnabled() { return particlesEnabled; }
    public boolean shouldHitSoft17() { return hitSoft17; }
    
    // Quick bet settings
    public java.util.List<Integer> getSmallBets() {
        return config.getIntegerList("betting.quick-bets.small");
    }
    
    public java.util.List<Integer> getMediumBets() {
        return config.getIntegerList("betting.quick-bets.medium");
    }
    
    public java.util.List<Integer> getLargeBets() {
        return config.getIntegerList("betting.quick-bets.large");
    }
    
    // Display settings
    public float getCardScale() {
        return (float) config.getDouble("display.card.scale", 0.35);
    }
    
    public double getCardSpacing() {
        return config.getDouble("display.card.spacing", 0.25);
    }
    
    public double getPlayerCardHeight() {
        return config.getDouble("display.card.player.height", 1.05);
    }
    
    public double getDealerCardHeight() {
        return config.getDouble("display.card.dealer.height", 1.2);
    }
    
    /**
     * Safely get a Sound enum from a string name with fallback
     */
    private Sound getSoundFromString(String soundName, Sound fallback) {
        if (soundName == null || soundName.trim().isEmpty()) {
            return fallback;
        }
        
        try {
            // Use Registry API for Minecraft 1.21.3+
            NamespacedKey key = NamespacedKey.minecraft(soundName.toLowerCase().replace("_", ""));
            Sound sound = Registry.SOUNDS.get(key);
            if (sound != null) {
                return sound;
            }
            
            // Fallback: try direct match with the enum constant
            if (soundName.equals("BLOCK_WOODEN_BUTTON_CLICK_ON")) return Sound.BLOCK_WOODEN_BUTTON_CLICK_ON;
            if (soundName.equals("ENTITY_PLAYER_LEVELUP")) return Sound.ENTITY_PLAYER_LEVELUP;
            if (soundName.equals("ENTITY_VILLAGER_NO")) return Sound.ENTITY_VILLAGER_NO;
            if (soundName.equals("BLOCK_NOTE_BLOCK_PLING")) return Sound.BLOCK_NOTE_BLOCK_PLING;
            
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
    
    // Sound configuration
    public Sound getCardDealSound() {
        String soundName = config.getString("sounds.card-deal.sound", "BLOCK_WOODEN_BUTTON_CLICK_ON");
        return getSoundFromString(soundName, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON);
    }
    
    public float getCardDealVolume() {
        return (float) config.getDouble("sounds.card-deal.volume", 1.0);
    }
    
    public float getCardDealPitch() {
        return (float) config.getDouble("sounds.card-deal.pitch", 1.2);
    }
    
    public Sound getWinSound() {
        String soundName = config.getString("sounds.win.sound", "ENTITY_PLAYER_LEVELUP");
        return getSoundFromString(soundName, Sound.ENTITY_PLAYER_LEVELUP);
    }
    
    public Sound getLoseSound() {
        String soundName = config.getString("sounds.lose.sound", "ENTITY_VILLAGER_NO");
        return getSoundFromString(soundName, Sound.ENTITY_VILLAGER_NO);
    }
    
    public Sound getPushSound() {
        String soundName = config.getString("sounds.push.sound", "BLOCK_NOTE_BLOCK_PLING");
        return getSoundFromString(soundName, Sound.BLOCK_NOTE_BLOCK_PLING);
    }
    
    // Particle configuration
    public Particle getWinParticle() {
        try {
            return Particle.valueOf(config.getString("particles.win.type", "HAPPY_VILLAGER"));
        } catch (IllegalArgumentException e) {
            return Particle.HAPPY_VILLAGER;
        }
    }
    
    public Particle getLoseParticle() {
        try {
            return Particle.valueOf(config.getString("particles.lose.type", "ANGRY_VILLAGER"));
        } catch (IllegalArgumentException e) {
            return Particle.ANGRY_VILLAGER;
        }
    }
    
    // Message handling
    public String getMessage(String path) {
        String message;
        // Try messages config first, then fall back to main config with "messages." prefix
        if (messagesConfig.contains(path)) {
            message = messagesConfig.getString(path);
        } else {
            message = config.getString("messages." + path);
        }
        
        if (message == null) {
            message = "&cMessage not found: " + path;
        }
        
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String formatMessage(String path, Object... args) {
        String message = getMessage(path);
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 < args.length) {
                message = message.replace("%" + args[i] + "%", String.valueOf(args[i + 1]));
            }
        }
        return message;
    }

    public void reload(FileConfiguration newConfig, FileConfiguration newMessagesConfig) {
        if (newConfig != null) {
            this.config = newConfig;
        }
        if (newMessagesConfig != null) {
            this.messagesConfig = newMessagesConfig;
        }
        loadAndValidateConfig();
    }
    
    // Backward compatibility reload method
    public void reload(FileConfiguration newConfig) {
        reload(newConfig, newConfig);
    }
    
    // Performance settings
    public int getStatsSaveInterval() {
        return config.getInt("performance.stats-save-interval", 3);
    }
    
    // Game settings
    public boolean shouldRefundOnLeave() {
        return config.getBoolean("game-settings.refund-on-leave", true);
    }
    
    // Getters for configuration access (for saving during migration)
    public FileConfiguration getConfig() {
        return config;
    }
    
    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }
}
