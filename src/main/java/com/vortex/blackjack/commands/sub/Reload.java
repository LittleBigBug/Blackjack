package com.vortex.blackjack.commands.sub;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;

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

        // Reload messages configuration
        File messagesFile = new File(this.plugin.getDataFolder(), "messages.yml");
        FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        configManager.reload(this.plugin.getConfig(), messagesConfig);
        sender.sendMessage(configManager.getMessage("config-reloaded"));
        return true;
    }

}
