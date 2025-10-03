package com.vortex.blackjack.gui;

import com.vortex.blackjack.BlackjackPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public abstract class BaseGUI implements InventoryHolder {

    protected final BlackjackPlugin plugin;
    protected final Player player;
    protected final Inventory inventory;

    public BaseGUI(BlackjackPlugin plugin, Player player, String title, int size) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = plugin.getServer().createInventory(this, size, title);
    }

    public abstract void update();
    public abstract void handleClick(InventoryClickEvent event);
    public abstract void handleClose(InventoryCloseEvent event);

    public BaseGUI open() {
        this.update();
        this.player.openInventory(this.inventory);
        return this;
    }

    public void close() {
        this.player.closeInventory();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }

    public Player getPlayer() {
        return this.player;
    }

}
