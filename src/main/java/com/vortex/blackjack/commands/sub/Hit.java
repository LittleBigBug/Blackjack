package com.vortex.blackjack.commands.sub;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.table.TableManager;
import com.vortex.blackjack.util.GenericUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class Hit extends SubCommand {

    public Hit(BlackjackPlugin plugin) {
        super(plugin, "hit");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        ConfigManager configManager = this.plugin.getConfigManager();
        TableManager tableManager = this.plugin.getTableManager();

        return GenericUtils.handleTableAction(player, tableManager, configManager, "hit",
                table -> table.hit(player));
    }

}
