package com.vortex.blackjack.util;

import com.google.gson.JsonParser;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.model.PlayerStats;
import com.vortex.blackjack.table.BlackjackTable;
import com.vortex.blackjack.table.TableManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.text.DecimalFormat;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Generic utility functions to reduce code duplication across the plugin
 */
public class GenericUtils {

    public final static DecimalFormat decimalFormat = new DecimalFormat("#.##");
    public final static DecimalFormat percentFormat = new DecimalFormat("#.#");

    /**
     * Generic table action handler that reduces code duplication
     * for methods like hit, stand, doubledown, join, leave, start
     */
    public static boolean handleTableAction(Player player, TableManager tableManager, 
                                          ConfigManager configManager, String actionName, 
                                          Consumer<BlackjackTable> tableAction) {
        BlackjackTable table = tableManager.getPlayerTable(player);
        if (table != null) {
            tableAction.accept(table);
        } else {
            player.sendMessage(configManager.getMessage("not-at-table"));
        }
        return true;
    }
    
    /**
     * Generic method for loading player stats from configuration
     */
    public static PlayerStats loadPlayerStats(FileConfiguration statsConfig, UUID playerUUID) {
        String path = "players." + playerUUID + ".";
        PlayerStats stats = new PlayerStats();
        
        stats.setHandsWon(statsConfig.getInt(path + "handsWon", 0));
        stats.setHandsLost(statsConfig.getInt(path + "handsLost", 0));
        stats.setHandsPushed(statsConfig.getInt(path + "handsPushed", 0));
        stats.setCurrentStreak(statsConfig.getInt(path + "currentStreak", 0));
        stats.setBestStreak(statsConfig.getInt(path + "bestStreak", 0));
        stats.setTotalWinnings(statsConfig.getDouble(path + "totalWinnings", 0.0));
        stats.setTotalLosses(statsConfig.getDouble(path + "totalLosses", 0.0));
        stats.setBlackjacks(statsConfig.getInt(path + "blackjacks", 0));
        stats.setBusts(statsConfig.getInt(path + "busts", 0));
        
        return stats;
    }
    
    /**
     * Generic method for saving player stats to configuration
     */
    public static void savePlayerStats(FileConfiguration statsConfig, UUID playerUUID, PlayerStats stats) {
        String path = "players." + playerUUID + ".";
        
        statsConfig.set(path + "handsWon", stats.getHandsWon());
        statsConfig.set(path + "handsLost", stats.getHandsLost());
        statsConfig.set(path + "handsPushed", stats.getHandsPushed());
        statsConfig.set(path + "currentStreak", stats.getCurrentStreak());
        statsConfig.set(path + "bestStreak", stats.getBestStreak());
        statsConfig.set(path + "totalWinnings", stats.getTotalWinnings());
        statsConfig.set(path + "totalLosses", stats.getTotalLosses());
        statsConfig.set(path + "blackjacks", stats.getBlackjacks());
        statsConfig.set(path + "busts", stats.getBusts());
    }
    
    /**
     * Generic method for creating clickable chat buttons
     */
    public static TextComponent createClickableButton(String text, String command, String hoverText) {
        TextComponent button = new TextComponent(org.bukkit.ChatColor.translateAlternateColorCodes('&', text));
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));

        TextComponent hover = new TextComponent(org.bukkit.ChatColor.translateAlternateColorCodes('&', hoverText));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)));

        return button;
    }
    
    /**
     * Generic method for creating suggestion buttons (for chat input)
     */
    public static TextComponent createSuggestionButton(String text, String command, String hoverText) {
        TextComponent button = new TextComponent(org.bukkit.ChatColor.translateAlternateColorCodes('&', text));
        button.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));
        // Note: Hover events removed due to API compatibility
        return button;
    }
    
    /**
     * Generic method for creating betting button rows
     */
    public static TextComponent createBettingButtonRow(String categoryLabel, List<Integer> amounts, 
                                                      ConfigManager configManager) {
        TextComponent row = new TextComponent(categoryLabel);
        
        for (int i = 0; i < amounts.size(); i++) {
            if (i > 0) row.addExtra(" ");
            
            int amount = amounts.get(i);
            String buttonText = configManager.getBetColorByAmount(amount) + "$" + amount;
            String command = "/bet " + amount;
            String hoverText = "§eClick to bet $" + amount;
            
            TextComponent button = createClickableButton(buttonText, command, hoverText);
            row.addExtra(button);
        }
        
        return row;
    }
    
    /**
     * Generic method for sending formatted stat messages to a player
     */
    public static void sendStatsToPlayer(Player player, PlayerStats stats, ConfigManager configManager, 
                                       String targetPlayerName, boolean isOwnStats) {
        // Send header
        if (isOwnStats) {
            player.sendMessage(configManager.getMessage("stats-header"));
        } else {
            player.sendMessage(configManager.formatMessage("stats-other-player-header", "player", targetPlayerName));
        }
        
        // Send individual stats
        player.sendMessage(configManager.formatMessage("stats-hands-won", "value", stats.getHandsWon()));
        player.sendMessage(configManager.formatMessage("stats-hands-lost", "value", stats.getHandsLost()));
        player.sendMessage(configManager.formatMessage("stats-hands-pushed", "value", stats.getHandsPushed()));
        player.sendMessage(configManager.formatMessage("stats-blackjacks", "value", stats.getBlackjacks()));
        player.sendMessage(configManager.formatMessage("stats-busts", "value", stats.getBusts()));
        player.sendMessage(configManager.formatMessage("stats-win-rate", "value", String.format("%.1f", stats.getWinRate())));
        player.sendMessage(configManager.formatMessage("stats-current-streak", "value", stats.getCurrentStreak()));
        player.sendMessage(configManager.formatMessage("stats-best-streak", "value", stats.getBestStreak()));
        player.sendMessage(configManager.formatMessage("stats-total-winnings", "value", String.format("%.2f", stats.getTotalWinnings())));
        player.sendMessage(configManager.formatMessage("stats-total-losses", "value", String.format("%.2f", stats.getTotalLosses())));
        player.sendMessage(configManager.formatMessage("stats-total-net", "value", String.format("%.2f", stats.getTotalNet())));
    }
    
    /**
     * Generic method for validating and parsing integer arguments
     */
    public static Integer parseIntegerArgument(String arg, Player player, ConfigManager configManager, 
                                             String errorMessageKey) {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            player.sendMessage(configManager.getMessage(errorMessageKey));
            return null;
        }
    }
    
    /**
     * Generic method for permission checking with automatic error message
     */
    public static boolean checkPermission(Player player, String permission, ConfigManager configManager) {
        if (!player.hasPermission(permission)) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return false;
        }
        return true;
    }

    /**
     * Validates if CommandSender is a player and sends appropriate error message if not.
     * Returns the Player instance if valid, null otherwise.
     */
    public static Player validatePlayer(CommandSender sender, ConfigManager configManager) {
        if (!(sender instanceof Player)) {
            // Use config message if available, fallback to hardcoded
            String message = configManager != null ? 
                configManager.getMessage("player-only-command") : 
                "§cThis command can only be used by players!";
            sender.sendMessage(message);
            return null;
        }
        return (Player) sender;
    }

    public static String getURLFromTexture(String texture) {
        // String decoded = new String(Base64.getDecoder().decode(texture));
        // return new URL(decoded.substring("{\"textures\":{\"SKIN\":{\"url\":\"".length(), decoded.length() - "\"}}}".length()));

        // Decode B64.
        String decoded = new String(Base64.getDecoder().decode(texture));

        // Get url from json.
        return JsonParser.parseString(decoded).getAsJsonObject()
                .getAsJsonObject("textures")
                .getAsJsonObject("SKIN")
                .get("url")
                .getAsString();
    }

    public static void applySkin(SkullMeta meta, String texture, boolean isUrl) {
        applySkin(meta, UUID.randomUUID(), texture, isUrl);
    }

    public static void applySkin(SkullMeta meta, UUID uuid, String texture, boolean isUrl) {
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(uuid, "");

            PlayerTextures textures = profile.getTextures();
            String url = isUrl ? "http://textures.minecraft.net/texture/" + texture : getURLFromTexture(texture);
            textures.setSkin(new URI(url).toURL());

            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    /**
     * Format money values with appropriate suffixes
     */
    public static String formatMoney(double amount) {
        if (amount >= 1_000_000) {
            return "$" + decimalFormat.format(amount / 1_000_000) + "M";
        } else if (amount >= 1_000) {
            return "$" + decimalFormat.format(amount / 1_000) + "K";
        } else {
            return "$" + decimalFormat.format(amount);
        }
    }

}
