package com.vortex.blackjack.commands.sub;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.table.BlackjackTable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class Leave extends SubCommand {

    public Leave(BlackjackPlugin plugin) {
        super(plugin, "leave");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        BlackjackTable table = this.plugin.getTableManager().getPlayerTable(player);

        if (table != null) {
            table.removePlayer(player);
            this.plugin.getBetManager().removePersistent(player);
        } else
            player.sendMessage(this.plugin.getConfigManager().getMessage("not-at-table"));

        return true;
    }

}
