package com.vortex.blackjack.commands.sub;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.table.TableManager;
import com.vortex.blackjack.util.GenericUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class Stand extends SubCommand {

    public Stand(BlackjackPlugin plugin) {
        super(plugin, "stand");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        ConfigManager configManager = this.plugin.getConfigManager();
        TableManager tableManager = this.plugin.getTableManager();

        return GenericUtils.handleTableAction(player, tableManager, configManager, "stand",
                table -> table.stand(player));
    }

}
