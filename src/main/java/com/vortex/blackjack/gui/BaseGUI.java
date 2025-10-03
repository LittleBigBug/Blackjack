package com.vortex.blackjack.gui;

import com.vortex.blackjack.BlackjackPlugin;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class BaseGUI implements InventoryHolder {

    protected final Inventory inventory;

    public BaseGUI(BlackjackPlugin plugin, int size) {
        this.inventory = plugin.getServer().createInventory(this, size);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }

}
