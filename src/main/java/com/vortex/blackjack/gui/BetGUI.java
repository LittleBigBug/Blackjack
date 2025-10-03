package com.vortex.blackjack.gui;

import com.vortex.blackjack.BlackjackPlugin;
import org.bukkit.entity.Player;

public class BetGUI extends BaseGUI {

    private final BlackjackPlugin plugin;

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};

    public BetGUI(BlackjackPlugin plugin, Player player) {
        super(plugin, 36);
        this.plugin = plugin;
    }

    private void getChips() {

    }

    public void update() {
        this.inventory.clear();

    }

}
