package com.vortex.blackjack.commands;

import com.vortex.blackjack.BlackjackPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for blackjack commands with common functionality
 */
public abstract class BaseCommand implements CommandExecutor, TabCompleter {

    protected final BlackjackPlugin plugin;

    public BaseCommand(BlackjackPlugin plugin) {
        this.plugin = plugin;
    }

    protected boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }
    
    protected Player getPlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        return null;
    }
    
    protected void sendPlayerOnlyMessage(CommandSender sender) {
        // We'll need to get the config manager to send this message properly
        // For now, use the hardcoded message to avoid breaking functionality
        sender.sendMessage("§cThis command can only be used by players!");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList(); // Override in subclasses if needed
    }
    
    /**
     * Helper method for tab completion with string matching
     */
    protected List<String> filterCompletions(List<String> completions, String partial) {
        List<String> result = new ArrayList<>();
        StringUtil.copyPartialMatches(partial, completions, result);
        Collections.sort(result);
        return result;
    }
}
