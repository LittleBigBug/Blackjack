package com.vortex.blackjack.commands.sub;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.model.PlayerStats;
import com.vortex.blackjack.util.GenericUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class Stats extends SubCommand {

    public Stats(BlackjackPlugin plugin) {
        super(plugin, "stats");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        // todo; allow console
        if (!(sender instanceof Player player)) return true;

        ConfigManager configManager = this.plugin.getConfigManager();

        UUID targetUUID = player.getUniqueId();
        String targetName = player.getName();

        // Check if admin is checking another player's stats
        if (args.length > 1) {
            if (!player.hasPermission("blackjack.stats.others")) {
                player.sendMessage(configManager.getMessage("stats-no-permission"));
                return true;
            }

            targetName = args[1];
            Player targetPlayer = this.plugin.getServer().getPlayer(targetName);
            if (targetPlayer != null) {
                targetUUID = targetPlayer.getUniqueId();
                targetName = targetPlayer.getName();
            } else {
                // Try to find offline player using UUID (avoiding deprecated method)
                try {
                    // Attempt to get UUID from Mojang API or cache (implement as needed)
                    // Example: Use a UUID cache or external API here for production
                    // For now, fallback to searching known offline players
                    org.bukkit.OfflinePlayer[] offlinePlayers = this.plugin.getServer().getOfflinePlayers();
                    org.bukkit.OfflinePlayer offlinePlayer = null;
                    for (org.bukkit.OfflinePlayer op : offlinePlayers) {
                        if (op.getName() != null && op.getName().equalsIgnoreCase(targetName)) {
                            offlinePlayer = op;
                            break;
                        }
                    }
                    if (offlinePlayer != null && offlinePlayer.hasPlayedBefore()) {
                        targetUUID = offlinePlayer.getUniqueId();
                        targetName = offlinePlayer.getName();
                    } else {
                        player.sendMessage(configManager.formatMessage("stats-player-not-found", "player", targetName));
                        return true;
                    }
                } catch (Exception ex) {
                    player.sendMessage(configManager.formatMessage("stats-player-not-found", "player", targetName));
                    return true;
                }
            }
        }

        // Load stats for the target player
        FileConfiguration statsConfig = YamlConfiguration.loadConfiguration(this.plugin.getStatsFile());
        PlayerStats stats = GenericUtils.loadPlayerStats(statsConfig, targetUUID);

        if (stats.getTotalHands() == 0) {
            if (targetUUID.equals(player.getUniqueId()))
                player.sendMessage(configManager.getMessage("stats-none-found"));
            else
                player.sendMessage(configManager.formatMessage("stats-none-found-player", "player", targetName));
            return true;
        }

        // Use generic stats display method
        GenericUtils.sendStatsToPlayer(player, stats, configManager, targetName,
                targetUUID.equals(player.getUniqueId()));

        return true;
    }

}
