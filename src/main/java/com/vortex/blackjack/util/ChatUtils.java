package com.vortex.blackjack.util;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Utility for creating interactive chat messages with clickable actions
 */
public class ChatUtils {
    
    /**
     * Send a clickable message to perform a command
     */
    public static void sendClickableCommand(Player player, String message, String command, String hoverText) {
        TextComponent text = new TextComponent(ChatColor.translateAlternateColorCodes('&', message));
        text.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        // Skip hover event for now due to API changes
        
        player.spigot().sendMessage(text);
    }
    
    /**
     * Send a clickable message to suggest a command
     */
    public static void sendClickableSuggestion(Player player, String message, String command, String hoverText) {
        TextComponent text = new TextComponent(ChatColor.translateAlternateColorCodes('&', message));
        text.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));
        // Skip hover event for now due to API changes
        
        player.spigot().sendMessage(text);
    }
    
    /**
     * Create a game action bar with clickable options
     */
    public static void sendGameActionBar(Player player) {
        sendGameActionBar(player, true); // Default to showing doubledown
    }
    
    /**
     * Create a game action bar with clickable options
     */
    public static void sendGameActionBar(Player player, boolean showDoubleDown) {
        TextComponent hitButton = new TextComponent("§a§l[HIT]");
        hitButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hit"));
        
        TextComponent standButton = new TextComponent("§c§l[STAND]");
        standButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stand"));
        
        TextComponent separator = new TextComponent("§7 | ");
        
        // Combine components
        TextComponent fullMessage = new TextComponent("§7Your turn: ");
        fullMessage.addExtra(hitButton);
        fullMessage.addExtra(separator);
        fullMessage.addExtra(standButton);
        
        // Add doubledown button if appropriate
        if (showDoubleDown) {
            TextComponent doubleDownButton = new TextComponent("§6§l[DOUBLE DOWN]");
            doubleDownButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/doubledown"));
            fullMessage.addExtra(separator);
            fullMessage.addExtra(doubleDownButton);
        }
        
        player.spigot().sendMessage(fullMessage);
    }
    
    /**
     * Create betting options with clickable amounts
     */
    public static void sendBettingOptions(Player player) {
        player.sendMessage("§e§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§6§l                          QUICK BET");
        player.sendMessage("");
        
        // Row 1: Small bets
        TextComponent smallBets = new TextComponent("§7Small: ");
        addBetButton(smallBets, "§a$10", "/bet 10", "§eClick to bet $10");
        smallBets.addExtra(" ");
        addBetButton(smallBets, "§a$25", "/bet 25", "§eClick to bet $25");
        smallBets.addExtra(" ");
        addBetButton(smallBets, "§a$50", "/bet 50", "§eClick to bet $50");
        player.spigot().sendMessage(smallBets);
        
        // Row 2: Medium bets
        TextComponent mediumBets = new TextComponent("§7Medium: ");
        addBetButton(mediumBets, "§e$100", "/bet 100", "§eClick to bet $100");
        mediumBets.addExtra(" ");
        addBetButton(mediumBets, "§e$250", "/bet 250", "§eClick to bet $250");
        mediumBets.addExtra(" ");
        addBetButton(mediumBets, "§e$500", "/bet 500", "§eClick to bet $500");
        player.spigot().sendMessage(mediumBets);
        
        // Row 3: Large bets
        TextComponent largeBets = new TextComponent("§7Large: ");
        addBetButton(largeBets, "§c$1000", "/bet 1000", "§eClick to bet $1000");
        largeBets.addExtra(" ");
        addBetButton(largeBets, "§c$2500", "/bet 2500", "§eClick to bet $2500");
        largeBets.addExtra(" ");
        addBetButton(largeBets, "§d$5000", "/bet 5000", "§eClick to bet $5000");
        player.spigot().sendMessage(largeBets);
        
        player.sendMessage("");
        
        // Custom bet option
        sendClickableSuggestion(player, "§b§l[CUSTOM BET]", "/bet ", "§eClick to enter custom amount");
        
        player.sendMessage("§e§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
    
    /**
     * Send clickable "Play Again" and "Leave Table" buttons
     */
    public static void sendGameEndOptions(Player player) {
        TextComponent playAgainButton = new TextComponent("§a§l[Play Again]");
        playAgainButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/start"));
        
        TextComponent leaveButton = new TextComponent("§c§l[Leave Table]");
        leaveButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/leave"));
        
        TextComponent separator = new TextComponent("§7 | ");
        
        // Combine components
        TextComponent fullMessage = new TextComponent("§7Choose: ");
        fullMessage.addExtra(playAgainButton);
        fullMessage.addExtra(separator);
        fullMessage.addExtra(leaveButton);
        
        player.spigot().sendMessage(fullMessage);
    }
    
    private static void addBetButton(TextComponent parent, String buttonText, String command, String hoverText) {
        TextComponent button = new TextComponent(buttonText);
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        // Skip hover event for now due to API changes
        parent.addExtra(button);
    }
}
