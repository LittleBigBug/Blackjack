package com.vortex.blackjack.commands.sub;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.table.BetManager;
import com.vortex.blackjack.table.BlackjackTable;
import com.vortex.blackjack.table.TableManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class Start extends SubCommand {

    public Start(BlackjackPlugin plugin) {
        super(plugin, "start");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        TableManager tableManager = this.plugin.getTableManager();
        ConfigManager configManager = this.plugin.getConfigManager();

        BlackjackTable table = tableManager.getPlayerTable(player);
        if (table == null) {
            player.sendMessage(configManager.getMessage("not-at-table"));
            return true;
        }

        BetManager betManager = this.plugin.getBetManager();

        // Auto-bet if player has a persistent bet amount but no current bet
        Integer currentBet = betManager.getPlayerBets().get(player);
        Integer persistentBet = betManager.getPlayerPersistentBets().get(player);

        if ((currentBet == null || currentBet == 0) && persistentBet != null && persistentBet > 0)
            // Attempt to place the persistent bet automatically
            if (betManager.processBet(player, persistentBet))
                player.sendMessage(configManager.formatMessage("auto-bet-placed", "amount", persistentBet));

        table.startGame();

        return true;
    }

}
