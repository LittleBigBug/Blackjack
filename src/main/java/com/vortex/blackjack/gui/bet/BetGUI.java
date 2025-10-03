package com.vortex.blackjack.gui.bet;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.Config;
import com.vortex.blackjack.gui.BaseGUI;
import com.vortex.blackjack.table.BlackjackTable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;

public class BetGUI extends BaseGUI {

    private boolean madeBet = false;
    private int currentPage = 1;

    public BetGUI(BlackjackPlugin plugin, Player player) {
        super(plugin, player, plugin.getGuiConfig().getString("bet.title"), 36);
    }

    private void createChips(int page) {
        Config chipsCfg = this.plugin.getChipsConfig();
        ArrayList<String> chips = new ArrayList<>(chipsCfg.getConfigurationSection("chips").getKeys(false));

        int start = (page - 1) * 9;
        int end = Math.min(start + 9, chips.size());

        int c = 0;
        for (int i = start; i < end; i++) {
            String chipName = chips.get(i);
            ConfigurationSection chip = chipsCfg.getConfigurationSection("chips." + chipName);
            if (chip == null) continue;

            ChipItem item = new ChipItem(this.plugin, chipName, chip);
            this.inventory.setItem(9 + c, item);

            c++;
        }
    }

    @Override
    public void update() {
        this.inventory.clear();
        this.createChips(this.currentPage);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        this.plugin.getLogger().info("Clicked item: " + item + " (" + item.getType() + ") " + (item instanceof ChipItem ? "YES" : "NO"));

        ItemMeta imeta = item.getItemMeta();
        if (imeta == null) return;

        PersistentDataContainer data = imeta.getPersistentDataContainer();
        if (!data.has(this.plugin.getChipPriceKey())) return;

        BlackjackTable table = this.plugin.getTableManager().getPlayerTable(this.player);
        if (table == null) {
            this.player.closeInventory();
            return;
        }

        int price = data.getOrDefault(this.plugin.getChipPriceKey(), PersistentDataType.INTEGER, 10);
        if (price < 0)
            price = this.plugin.getEconomyProvider().getBalance(this.player.getUniqueId()).intValue();

        this.madeBet = true;

        this.plugin.getBetManager().processBet(this.player, price);
        this.player.closeInventory();
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (!this.madeBet)
            this.player.sendMessage(plugin.getConfigManager().getMessage("bet-gui-closed"));
    }

}
