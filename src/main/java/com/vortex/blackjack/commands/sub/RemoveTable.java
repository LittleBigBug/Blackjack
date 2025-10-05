package com.vortex.blackjack.commands.sub;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.table.BlackjackTable;
import com.vortex.blackjack.table.TableManager;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RemoveTable extends SubCommand {

    public RemoveTable(BlackjackPlugin plugin) {
        super(plugin, "removetable");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        // todo; allow console to remove tables by ID
        if (!(sender instanceof Player player)) return true;

        ConfigManager configManager = this.plugin.getConfigManager();

        if (!player.hasPermission("blackjack.admin")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        TableManager tableManager = this.plugin.getTableManager();

        BlackjackTable nearestTable = tableManager.findNearestTable(player.getLocation());

        if (nearestTable != null) {
            if (tableManager.removeTable(nearestTable.getId()))
                player.sendMessage(configManager.getMessage("table-removed"));
            else
                player.sendMessage(configManager.getMessage("table-remove-failed"));
        } else
            player.sendMessage(configManager.getMessage("no-table-nearby"));

        return true;
    }

}
