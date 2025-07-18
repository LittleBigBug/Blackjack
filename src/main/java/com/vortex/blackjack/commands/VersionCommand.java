package com.vortex.blackjack.commands;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.util.VersionChecker;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * Handle the /bjversion command for checking plugin version
 */
public class VersionCommand extends BlackjackCommand {
    private final VersionChecker versionChecker;
    
    public VersionCommand(BlackjackPlugin plugin, VersionChecker versionChecker) {
        this.versionChecker = versionChecker;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("blackjack.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        sender.sendMessage(ChatColor.GOLD + "BLACKJACK PLUGIN VERSION INFO");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.WHITE + "Plugin: " + ChatColor.GREEN + "Blackjack");
        sender.sendMessage(ChatColor.WHITE + "Author: " + ChatColor.AQUA + "DefectiveVortex");
        sender.sendMessage(ChatColor.WHITE + "Current Version: " + ChatColor.GREEN + versionChecker.getCurrentVersion());
        
        if (versionChecker.getLatestVersion() != null) {
            sender.sendMessage(ChatColor.WHITE + "Latest Version: " + ChatColor.GREEN + versionChecker.getLatestVersion());
        }
        
        sender.sendMessage("");
        
        if (versionChecker.isOutdated()) {
            sender.sendMessage(ChatColor.RED + "⚠ UPDATE AVAILABLE!");
            sender.sendMessage(ChatColor.AQUA + "Download: " + ChatColor.BLUE + "https://github.com/DefectiveVortex/Blackjack/releases/latest");
        } else {
            sender.sendMessage(ChatColor.GREEN + "✓ UP TO DATE!");
        }
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "GitHub: " + ChatColor.BLUE + "https://github.com/DefectiveVortex/Blackjack");
        
        return true;
    }
}
