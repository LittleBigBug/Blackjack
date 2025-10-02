package com.vortex.blackjack.listener;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.table.BlackjackTable;
import com.vortex.blackjack.table.TableManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final BlackjackPlugin plugin;

    public PlayerListener(BlackjackPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ConfigManager configManager = plugin.getConfigManager();

        // Check if player is admin and notify about updates
        if (configManager.getShouldNotifyUpdates() && player.hasPermission("blackjack.admin"))
            plugin.getVersionChecker().notifyAdmin(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        this.plugin.getBetManager().removePlayer(player);
        this.plugin.getTableManager().removePlayerFromTable(player, "disconnected from the server");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Only check if player moved to a different block (optimization)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ())
            return;

        TableManager tableManager = plugin.getTableManager();

        // Check if player is at a table
        BlackjackTable table = tableManager.getPlayerTable(player);
        if (table == null) return;

        ConfigManager configManager = plugin.getConfigManager();

        double distance = player.getLocation().distance(table.getCenterLocation());
        double maxDistance = configManager.getMaxJoinDistance();

        if (distance > maxDistance) {
            // Player moved too far from table, auto-leave
            table.removePlayer(player, "moved too far from the table");
            player.sendMessage(configManager.getMessage("auto-left-table")
                    .replace("%distance%", String.format("%.1f", distance))
                    .replace("%max_distance%", String.format("%.1f", maxDistance)));
        }
    }

}
