package com.vortex.blackjack.commands.sub;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.table.BlackjackTable;
import com.vortex.blackjack.table.TableManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class Join extends SubCommand {

    public Join(BlackjackPlugin plugin) {
        super(plugin, "join");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        TableManager tableManager = this.plugin.getTableManager();
        ConfigManager configManager = this.plugin.getConfigManager();

        if (tableManager.getPlayerTable(player) != null) {
            player.sendMessage(configManager.getMessage("already-at-table"));
            return true;
        }

        BlackjackTable nearestTable = tableManager.findNearestTable(player.getLocation());
        if (nearestTable != null)
            nearestTable.addPlayer(player);
        else
            player.sendMessage(configManager.getMessage("no-table-nearby"));

        return true;
    }

}
