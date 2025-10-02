package com.vortex.blackjack.commands.sub;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.table.TableManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CreateTable extends SubCommand {

    public CreateTable(BlackjackPlugin plugin) {
        super(plugin, "createtable");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        ConfigManager configManager = this.plugin.getConfigManager();

        if (!player.hasPermission("blackjack.admin")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        TableManager tableManager = plugin.getTableManager();

        if (tableManager.createTable(player.getLocation()))
            player.sendMessage(configManager.getMessage("table-created"));
        else
            player.sendMessage(configManager.getMessage("table-already-exists"));

        return true;
    }

}
