package com.vortex.blackjack.config;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Centralized configuration management with validation and caching
 */
public class ConfigManager {
    private FileConfiguration config;
    
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
    
    public ConfigManager(FileConfiguration config) {
        this.config = config;
        loadAndValidateConfig();
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
    
    public double getPlayerCardDistance() {
        return config.getDouble("display.card.player.distance", 1.0);
    }
    
    public double getDealerCardHeight() {
        return config.getDouble("display.card.dealer.height", 1.2);
    }
    
    public double getDealerCardDistance() {
        return config.getDouble("display.card.dealer.distance", 0.75);
    }
    
    // Chat management settings - keep the spam reduction features
    public boolean isCompactMode() {
        return config.getBoolean("chat-management.compact-mode", false);
    }
    
    public boolean useActionBar() {
        return config.getBoolean("chat-management.use-action-bar", false);
    }
    
    public int getMaxRecentMessages() {
        return config.getInt("ux.chat.max-recent-messages", 3);
    }
    
    public String getGameStatusFormat() {
        return config.getString("ux.chat.game-status-format", "compact");
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
        return ChatColor.translateAlternateColorCodes('&', 
            config.getString("messages." + path, "&cMessage not found: " + path));
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
    
    public void reload(FileConfiguration newConfig) {
        if (newConfig != null) {
            this.config = newConfig;
        }
        loadAndValidateConfig();
    }
}
