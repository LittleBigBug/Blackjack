package com.vortex.blackjack.commands;

import com.vortex.blackjack.BlackjackPlugin;
import org.bukkit.command.PluginCommand;

/**
 * Manages registration of all blackjack commands
 */
public class CommandManager {
    private final BlackjackPlugin plugin;
    
    public CommandManager(BlackjackPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Register all individual commands for better UX
     */
    public void registerCommands() {
        // Main command (keeps existing functionality)
        PluginCommand mainCmd = plugin.getCommand("blackjack");
        if (mainCmd != null) {
            mainCmd.setExecutor(plugin);
        }
    }
    
    private void registerCommand(String name, BaseCommand executor) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            plugin.getLogger().warning("Could not register command: " + name);
        }
    }
}
