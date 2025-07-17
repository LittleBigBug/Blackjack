package com.vortex.blackjack.commands;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.gui.BettingGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * Handle the /bet command with smart amount suggestions and GUI
 */
public class BetCommand extends BlackjackCommand {
    private final BlackjackPlugin plugin;
    private final BettingGUI bettingGUI;
    private static final List<String> QUICK_BETS = Arrays.asList("10", "25", "50", "100", "250", "500", "1000", "gui", "menu");
    
    public BetCommand(BlackjackPlugin plugin) {
        this.plugin = plugin;
        this.bettingGUI = new BettingGUI(plugin);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!isPlayer(sender)) {
            sendPlayerOnlyMessage(sender);
            return true;
        }
        
        Player player = getPlayer(sender);
        
        if (args.length == 0) {
            // Show chat-based betting options instead of GUI
            showChatBettingOptions(player);
            return true;
        }
        
        // Check for GUI command
        String firstArg = args[0].toLowerCase();
        if (firstArg.equals("gui") || firstArg.equals("menu")) {
            bettingGUI.openBettingGUI(player);
            return true;
        }
        
        // Forward to main plugin with "bet" prepended
        String[] newArgs = new String[args.length + 1];
        newArgs[0] = "bet";
        System.arraycopy(args, 0, newArgs, 1, args.length);
        
        return plugin.onCommand(sender, command, label, newArgs);
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterCompletions(QUICK_BETS, args[0]);
        }
        return super.onTabComplete(sender, command, alias, args);
    }
    
    public BettingGUI getBettingGUI() {
        return bettingGUI;
    }
    
    private void showChatBettingOptions(Player player) {
        player.sendMessage("§6§l=== Quick Bet Menu ===");
        player.sendMessage("§7Click on an amount to place your bet:");
        
        // Create clickable bet buttons
        com.vortex.blackjack.util.ChatUtils.sendBettingOptions(player);
    }
}
