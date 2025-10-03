package com.vortex.blackjack.gui.bet;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.util.GenericUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class ChipItem extends ItemStack {

    private final BlackjackPlugin plugin;

    private final String name;
    private final String url;
    private final int price;

    public ChipItem(BlackjackPlugin plugin, String name, ConfigurationSection chipCfg) {
        super(Material.PLAYER_HEAD, 1);

        this.plugin = plugin;

        this.name = name;
        this.url = chipCfg.getString("url");

        this.setHead();

        String priceStr = chipCfg.getString("price");
        if (priceStr != null) this.price = (int) Math.floor(Float.parseFloat(priceStr));
        else this.price = 0;

        this.setData();
    }
    public ChipItem(BlackjackPlugin plugin, String name, int price) {
        super(Material.PLAYER_HEAD, 1);
        this.plugin = plugin;
        this.name = name;
        this.url = null;
        this.price = price;
    }

    private void setData() {
        if (!(this.getItemMeta() instanceof SkullMeta meta)) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();

        container.set(this.plugin.getChipNameKey(), PersistentDataType.STRING, name);
        container.set(this.plugin.getChipPriceKey(), PersistentDataType.INTEGER, price);

        this.setItemMeta(meta);
    }

    private void setHead() {
        if (!(this.getItemMeta() instanceof SkullMeta meta)) return;

        GenericUtils.applySkin(meta, this.url, true);
        this.setItemMeta(meta);
    }

    public boolean isAllIn() {
        return this.price < 0;
    }

    public int getPrice() {
        return this.price;
    }

    public String getName() {
        return this.name;
    }

    public String getUrl() {
        return this.url;
    }

}
