package com.vortex.blackjack.commands.sub;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.gui.bet.BetGUI;
import com.vortex.blackjack.table.TableManager;
import com.vortex.blackjack.util.GenericUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class Bet extends SubCommand {

    public Bet(BlackjackPlugin plugin) {
        super(plugin, "bet");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        ConfigManager configManager = this.plugin.getConfigManager();
        TableManager tableManager = this.plugin.getTableManager();

        if (tableManager.getPlayerTable(player) == null) {
            player.sendMessage(configManager.getMessage("not-at-table"));
            return true;
        }

        if (args.length < 2) {
            new BetGUI(this.plugin, player).open();
            return true;
        }

        Integer amount = GenericUtils.parseIntegerArgument(args[1], player, configManager, "invalid-amount");
        if (amount == null) return true;

        return this.plugin.getBetManager().processBet(player, amount);
    }

}
