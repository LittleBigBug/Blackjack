package com.vortex.blackjack.util;

import com.vortex.blackjack.BlackjackPlugin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import com.vortex.blackjack.config.ConfigManager;

/**
 * Utility for creating interactive chat messages with clickable actions
 */
public class ChatUtils {
    private final BlackjackPlugin plugin;
    
    /**
     * Constructor for ChatUtils with ConfigManager dependency
     */
    public ChatUtils(BlackjackPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Send a clickable message to perform a command
     */
    public void sendClickableCommand(Player player, String message, String command, String hoverText) {
        TextComponent text = new TextComponent(ChatColor.translateAlternateColorCodes('&', message));
        text.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        // Skip hover event for now due to API changes
        
        player.spigot().sendMessage(text);
    }
    
    /**
     * Send a clickable message to suggest a command
     */
    public void sendClickableSuggestion(Player player, String message, String command, String hoverText) {
        TextComponent text = GenericUtils.createSuggestionButton(message, command, hoverText);
        player.spigot().sendMessage(text);
    }
    
    /**
     * Create a game action bar with clickable options
     */
    public void sendGameActionBar(Player player) {
        sendGameActionBar(player, true); // Default to showing doubledown
    }
    
    /**
     * Create a game action bar with clickable options
     */
    public void sendGameActionBar(Player player, boolean showDoubleDown) {
        ConfigManager configManager = plugin.getConfigManager();

        TextComponent hitButton = GenericUtils.createClickableButton(
            configManager.getButtonText("hit"), 
            configManager.getButtonCommand("hit"), 
            configManager.getButtonHover("hit"));
        
        TextComponent standButton = GenericUtils.createClickableButton(
            configManager.getButtonText("stand"), 
            configManager.getButtonCommand("stand"), 
            configManager.getButtonHover("stand"));
        
        TextComponent separator = new TextComponent(configManager.getGameActionSeparator());
        
        // Combine components
        TextComponent fullMessage = new TextComponent(configManager.getGameActionPrompt());
        fullMessage.addExtra(hitButton);
        fullMessage.addExtra(separator);
        fullMessage.addExtra(standButton);
        
        // Add doubledown button if appropriate
        if (showDoubleDown) {
            TextComponent doubleDownButton = GenericUtils.createClickableButton(
                configManager.getButtonText("double-down"), 
                configManager.getButtonCommand("double-down"), 
                configManager.getButtonHover("double-down"));
            fullMessage.addExtra(separator);
            fullMessage.addExtra(doubleDownButton);
        }
        
        player.spigot().sendMessage(fullMessage);
    }
    
    /**
     * Create betting options with clickable amounts (configurable)
     */
    public void sendBettingOptions(Player player) {
        ConfigManager configManager = plugin.getConfigManager();

        player.sendMessage(configManager.getMessage("quick-bet-border"));
        player.sendMessage(configManager.getMessage("quick-bet-title"));
        player.sendMessage("");
        
        // Use generic method to create betting button rows
        TextComponent smallBets = GenericUtils.createBettingButtonRow(
            configManager.getBettingCategoryLabel("small"), 
            configManager.getSmallBets(), 
            configManager);
        player.spigot().sendMessage(smallBets);
        
        TextComponent mediumBets = GenericUtils.createBettingButtonRow(
            configManager.getBettingCategoryLabel("medium"), 
            configManager.getMediumBets(), 
            configManager);
        player.spigot().sendMessage(mediumBets);
        
        TextComponent largeBets = GenericUtils.createBettingButtonRow(
            configManager.getBettingCategoryLabel("large"), 
            configManager.getLargeBets(), 
            configManager);
        player.spigot().sendMessage(largeBets);
        
        player.sendMessage("");
        
        // Custom bet option
        sendClickableSuggestion(player, configManager.getButtonText("custom-bet"), 
            configManager.getButtonCommand("custom-bet"), configManager.getButtonHover("custom-bet"));
        
        player.sendMessage(configManager.getMessage("quick-bet-border"));
    }
    
    /**
     * Send clickable "Play Again" and "Leave Table" buttons
     */
    public void sendGameEndOptions(Player player) {
        ConfigManager configManager = plugin.getConfigManager();

        TextComponent playAgainButton = GenericUtils.createClickableButton(
                configManager.getButtonText("play-again"),
                configManager.getButtonCommand("play-again"),
                configManager.getButtonHover("play-again")
        );
        
        TextComponent leaveButton = GenericUtils.createClickableButton(
                configManager.getButtonText("leave-table"),
                configManager.getButtonCommand("leave-table"),
                configManager.getButtonHover("leave-table")
        );

        TextComponent changeBetButton = GenericUtils.createClickableButton(
                configManager.getButtonText("change-bet"),
                configManager.getButtonCommand("change-bet"),
                configManager.getButtonHover("change-bet")
        );
        
        TextComponent separator = new TextComponent(configManager.getGameActionSeparator());
        
        // Combine components
        TextComponent fullMessage = new TextComponent(configManager.getPostGamePrompt());
        fullMessage.addExtra(playAgainButton);
        fullMessage.addExtra(separator);
        fullMessage.addExtra(changeBetButton);
        fullMessage.addExtra(separator);
        fullMessage.addExtra(leaveButton);

        player.sendMessage(" ");
        player.spigot().sendMessage(fullMessage);
    }
}
