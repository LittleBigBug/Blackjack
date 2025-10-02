package com.vortex.blackjack.listener;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.table.BlackjackTable;
import com.vortex.blackjack.table.TableManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Collection;

public class InteractListener implements Listener {

    private final BlackjackPlugin plugin;

    public InteractListener(BlackjackPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        Bukkit.getLogger().info("Interact event");
        Entity ent = event.getRightClicked();
        Bukkit.getLogger().info(ent.getType().toString());

        if (!(ent instanceof Interaction interaction)) return;

        TableManager tableManager = plugin.getTableManager();
        Collection<BlackjackTable> tables = tableManager.getAllTables().values();

        BlackjackTable table = null;

        for (BlackjackTable t : tables)
            if (t.getInteractionEntity() == interaction) {
                table = t;
                break;
            }

        Bukkit.getLogger().info("Table: " + table);
        if (table == null) return;

        Player ply = event.getPlayer();

        if (table.getPlayers().contains(ply)) return;

        table.addPlayer(ply);
        event.setCancelled(true);
    }

}
