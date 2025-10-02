package com.vortex.blackjack.commands;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.commands.sub.*;
import com.vortex.blackjack.config.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class BlackjackCommand extends BaseCommand {

    private final HashMap<String, SubCommand> subCommands = new HashMap<>();

    public BlackjackCommand(BlackjackPlugin plugin) {
        super(plugin);

        SubCommand[] subCommands = new SubCommand[] {
                new Bet(plugin),
                new CreateTable(plugin),
                new DoubleDown(plugin),
                new Hit(plugin),
                new Join(plugin),
                new Leave(plugin),
                new Reload(plugin),
                new RemoveTable(plugin),
                new Stand(plugin),
                new Start(plugin),
                new Stats(plugin),
        };

        this.registerSubCommands(subCommands);
    }

    private void registerSubCommands(SubCommand[] subCommands) {
        for (SubCommand subCommand : subCommands)
            this.subCommands.put(subCommand.getSubCommandName(), subCommand);
    }

    private void sendHelp(CommandSender sender) {
        ConfigManager configManager = this.plugin.getConfigManager();

        sender.sendMessage(configManager.getMessage("prefix") + configManager.getMessage("help-header"));

        if (sender.hasPermission("blackjack.admin")) {
            sender.sendMessage(configManager.getMessage("help-admin-create"));
            sender.sendMessage(configManager.getMessage("help-admin-remove"));
            sender.sendMessage(configManager.getMessage("help-admin-reload"));
        }

        sender.sendMessage(configManager.getMessage("help-join"));
        sender.sendMessage(configManager.getMessage("help-leave"));
        sender.sendMessage(configManager.getMessage("help-bet"));
        sender.sendMessage(configManager.getMessage("help-start"));
        sender.sendMessage(configManager.getMessage("help-hit"));
        sender.sendMessage(configManager.getMessage("help-stand"));
        sender.sendMessage(configManager.getMessage("help-stats"));

        if (sender.hasPermission("blackjack.stats.others"))
            sender.sendMessage(configManager.getMessage("help-stats-others"));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        ConfigManager configManager = this.plugin.getConfigManager();

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        SubCommand subCommand = this.subCommands.get(action);

        if (subCommand != null) {
            if (subCommand.isPlayerOnly() && !(sender instanceof Player)){
                sender.sendMessage(configManager.getMessage("player-only-command"));
                return true;
            }

            return subCommand.onCommand(sender, args);
        }

        this.sendHelp(sender);
        return true;
    }

}
