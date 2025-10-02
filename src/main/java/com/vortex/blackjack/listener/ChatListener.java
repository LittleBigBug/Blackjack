package com.vortex.blackjack.listener;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.table.BlackjackTable;
import com.vortex.blackjack.table.TableManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class ChatListener implements Listener {

    private final BlackjackPlugin plugin;

    public ChatListener(BlackjackPlugin plugin) {
        this.plugin = plugin;
    }

    private void runCmdAsync(Player player, String cmd) {
        new BukkitRunnable() {
            @Override
            public void run() { Bukkit.dispatchCommand(player, cmd); }
        }.runTask(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        TableManager tableManager = this.plugin.getTableManager();

        Player player = event.getPlayer();
        BlackjackTable table = tableManager.getPlayerTable(player);

        if (table == null) return;

        String message = event.getMessage().toLowerCase();

        if (message.matches("^ *hit( ?me)?[^\\w]*$"))
            runCmdAsync(player, "blackjack hit");
        else if (message.matches("^ *stand[^\\w]*$"))
            runCmdAsync(player, "blackjack stand");
        else if (message.matches("^ *double( ?down)?[^\\w]*$"))
            runCmdAsync(player, "blackjack doubledown");
    }

}
