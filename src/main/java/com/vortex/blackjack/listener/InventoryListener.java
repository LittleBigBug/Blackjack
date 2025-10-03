package com.vortex.blackjack.listener;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.gui.BaseGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class InventoryListener implements Listener {

    private final BlackjackPlugin plugin;

    public InventoryListener(BlackjackPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inventory = event.getClickedInventory();
        if (inventory == null) return;

        // Prevent moving items from player inventory to custom inventories by shift-clicking.
        InventoryHolder tempHolder = event.getView().getTopInventory().getHolder();
        if (inventory.getType() == InventoryType.PLAYER
                && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && tempHolder instanceof BaseGUI) {
            event.setCancelled(true);
            return;
        }

        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof BaseGUI gui)) return;

        event.setCancelled(true);

        if (player != gui.getPlayer()) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        gui.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof BaseGUI gui)) return;

        gui.handleClose(event);
    }

}
