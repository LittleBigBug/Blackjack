package com.vortex.blackjack.commands.sub;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class Reload extends SubCommand {

    public Reload(BlackjackPlugin plugin) {
        super(plugin, "reload");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        ConfigManager configManager = this.plugin.getConfigManager();

        if (!sender.hasPermission("blackjack.admin")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        this.plugin.reloadConfig();
        this.plugin.getGuiConfig().reload();
        this.plugin.getMessagesConfig().reload();
        this.plugin.getChipsConfig().reload();

        // todo; pls reload messages & chips
        configManager.reload();

        sender.sendMessage(configManager.getMessage("config-reloaded"));
        return true;
    }

}
