package com.vortex.blackjack.gui;

import org.bukkit.event.inventory.InventoryCloseEvent;

@FunctionalInterface
public interface OnClose {
    void onClose(InventoryCloseEvent event);
}
