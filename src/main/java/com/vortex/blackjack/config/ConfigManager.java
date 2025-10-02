package com.vortex.blackjack.config;

import com.vortex.blackjack.BlackjackPlugin;
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

    private final BlackjackPlugin plugin;
    
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
    
    public ConfigManager(BlackjackPlugin plugin) {
        this.plugin = plugin;
        loadAndValidateConfig();
    }
    
    private void loadAndValidateConfig() {
        FileConfiguration config = this.plugin.getConfig();

        // Betting settings
        minBet = Math.max(1, config.getInt("betting.min-bet", 10));
        maxBet = Math.max(minBet, config.getInt("betting.max-bet", 10000));
        betCooldown = Math.max(0, config.getLong("betting.cooldown-ms", 2000L));
        
        // Table settings
        maxJoinDistance = Math.max(1.0, config.getDouble("table.max-join-distance", 10.0));
        maxPlayers = Math.max(1, Math.min(8, config.getInt("table.max-players", 5)));
        
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
    
    // Auto-leave settings
    public int getAutoLeaveTimeoutSeconds() {
        return Math.max(10, this.plugin.getConfig().getInt("game.auto-leave-timeout-seconds", 30));
    }
    
    // Quick bet settings
    public java.util.List<Integer> getSmallBets() {
        return this.plugin.getConfig().getIntegerList("betting.quick-bets.small");
    }
    
    public java.util.List<Integer> getMediumBets() {
        return this.plugin.getConfig().getIntegerList("betting.quick-bets.medium");
    }
    
    public java.util.List<Integer> getLargeBets() {
        return this.plugin.getConfig().getIntegerList("betting.quick-bets.large");
    }
    
    // Display settings
    public float getCardScale() {
        return (float) this.plugin.getConfig().getDouble("display.card.scale", 0.35);
    }
    
    public double getCardSpacing() {
        return this.plugin.getConfig().getDouble("display.card.spacing", 0.25);
    }
    
    public double getPlayerCardHeight() {
        return this.plugin.getConfig().getDouble("display.card.player.height", 1.05);
    }
    
    public double getDealerCardHeight() {
        return this.plugin.getConfig().getDouble("display.card.dealer.height", 1.2);
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
        String soundName = this.plugin.getConfig().getString("sounds.card-deal.sound", "BLOCK_WOODEN_BUTTON_CLICK_ON");
        return getSoundFromString(soundName, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON);
    }
    
    public float getCardDealVolume() {
        return (float) this.plugin.getConfig().getDouble("sounds.card-deal.volume", 1.0);
    }
    
    public float getCardDealPitch() {
        return (float) this.plugin.getConfig().getDouble("sounds.card-deal.pitch", 1.2);
    }
    
    public Sound getWinSound() {
        String soundName = this.plugin.getConfig().getString("sounds.win.sound", "ENTITY_PLAYER_LEVELUP");
        return getSoundFromString(soundName, Sound.ENTITY_PLAYER_LEVELUP);
    }
    
    public Sound getLoseSound() {
        String soundName = this.plugin.getConfig().getString("sounds.lose.sound", "ENTITY_VILLAGER_NO");
        return getSoundFromString(soundName, Sound.ENTITY_VILLAGER_NO);
    }
    
    public Sound getPushSound() {
        String soundName = this.plugin.getConfig().getString("sounds.push.sound", "BLOCK_NOTE_BLOCK_PLING");
        return getSoundFromString(soundName, Sound.BLOCK_NOTE_BLOCK_PLING);
    }
    
    // Particle configuration
    public Particle getWinParticle() {
        try {
            return Particle.valueOf(this.plugin.getConfig().getString("particles.win.type", "HAPPY_VILLAGER"));
        } catch (IllegalArgumentException e) {
            return Particle.HAPPY_VILLAGER;
        }
    }
    
    public Particle getLoseParticle() {
        try {
            return Particle.valueOf(this.plugin.getConfig().getString("particles.lose.type", "ANGRY_VILLAGER"));
        } catch (IllegalArgumentException e) {
            return Particle.ANGRY_VILLAGER;
        }
    }
    
    // Message handling
    public String getMessage(String path) {
        FileConfiguration messagesConfig = this.plugin.getMessagesConfig();
        String message;

        // Try messages config first, then fall back to main config with "messages." prefix
        if (messagesConfig.contains(path)) message = messagesConfig.getString(path);
        else message = this.plugin.getMessagesConfig().getString("messages." + path);
        
        if (message == null) message = "&cMessage not found: " + path;
        
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

    public void reload() {
        loadAndValidateConfig();
    }
    
    // Performance settings
    public int getStatsSaveInterval() {
        return this.plugin.getConfig().getInt("performance.stats-save-interval", 3);
    }
    
    // Game settings
    public boolean shouldRefundOnLeave() {
        return this.plugin.getConfig().getBoolean("game-settings.refund-on-leave", true);
    }
    
    // Button configuration methods
    public String getButtonText(String buttonName) {
        return ChatColor.translateAlternateColorCodes('&',
                this.plugin.getMessagesConfig().getString("buttons." + buttonName + ".text", "&7[" + buttonName.toUpperCase() + "]"));
    }
    
    public String getButtonCommand(String buttonName) {
        return this.plugin.getMessagesConfig().getString("buttons." + buttonName + ".command", "/" + buttonName);
    }
    
    public String getButtonHover(String buttonName) {
        return ChatColor.translateAlternateColorCodes('&',
                this.plugin.getMessagesConfig().getString("buttons." + buttonName + ".hover", "Click to " + buttonName));
    }
    
    public String getBetColorByAmount(int amount) {
        FileConfiguration messagesConfig = this.plugin.getMessagesConfig();

        if (amount >= 5000)
            return ChatColor.translateAlternateColorCodes('&', messagesConfig.getString("buttons.huge-bet-color", "&d"));
        else if (amount >= 1000)
            return ChatColor.translateAlternateColorCodes('&', messagesConfig.getString("buttons.large-bet-color", "&c"));
        else if (amount >= 100)
            return ChatColor.translateAlternateColorCodes('&', messagesConfig.getString("buttons.medium-bet-color", "&e"));
        else
            return ChatColor.translateAlternateColorCodes('&', messagesConfig.getString("buttons.small-bet-color", "&a"));
    }
    
    public String getGameActionPrompt() {
        return ChatColor.translateAlternateColorCodes('&',
                this.plugin.getMessagesConfig().getString("game-action-prompt", "&7Your turn: "));
    }
    
    public String getGameActionSeparator() {
        return ChatColor.translateAlternateColorCodes('&',
                this.plugin.getMessagesConfig().getString("game-action-separator", "&7 | "));
    }
    
    public String getPostGamePrompt() {
        return ChatColor.translateAlternateColorCodes('&',
                this.plugin.getMessagesConfig().getString("post-game-prompt", "&7Choose: "));
    }
    
    // Betting category labels
    public String getBettingCategoryLabel(String category) {
        return ChatColor.translateAlternateColorCodes('&',
                this.plugin.getMessagesConfig().getString("betting-category-" + category, "&7" + category.substring(0, 1).toUpperCase() + category.substring(1) + ": "));
    }

    public boolean getShouldNotifyUpdates() {
        return this.plugin.getConfig().getBoolean("notify-updates", true);
    }

}
