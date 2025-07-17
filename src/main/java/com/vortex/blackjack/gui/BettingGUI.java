package com.vortex.blackjack.gui;

import com.vortex.blackjack.BlackjackPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GUI for quick betting without typing commands
 */
public class BettingGUI implements Listener {
    private final BlackjackPlugin plugin;
    private final Map<UUID, Inventory> openGuis = new HashMap<>();
    
    // Betting amounts and their corresponding slots
    private static final int[] BET_AMOUNTS = {10, 25, 50, 100, 250, 500, 1000, 2500};
    private static final Material[] BET_MATERIALS = {
        Material.COPPER_INGOT,    // 10
        Material.IRON_INGOT,      // 25  
        Material.GOLD_INGOT,      // 50
        Material.DIAMOND,         // 100
        Material.EMERALD,         // 250
        Material.NETHERITE_INGOT, // 500
        Material.NETHER_STAR,     // 1000
        Material.BEACON           // 2500
    };
    
    public BettingGUI(BlackjackPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Open the betting GUI for a player
     */
    public void openBettingGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§6§lQuick Bet Menu");
        
        // Add betting options
        for (int i = 0; i < BET_AMOUNTS.length; i++) {
            int amount = BET_AMOUNTS[i];
            Material material = BET_MATERIALS[i];
            
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§e§lBet $" + amount);
            meta.setLore(Arrays.asList(
                "§7Click to bet §a$" + amount,
                "",
                "§8Left-click: Set bet",
                "§8Right-click: Add to current bet"
            ));
            item.setItemMeta(meta);
            
            gui.setItem(9 + i, item); // Place in middle row
        }
        
        // Add custom bet option
        ItemStack customBet = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta customMeta = customBet.getItemMeta();
        customMeta.setDisplayName("§b§lCustom Bet");
        customMeta.setLore(Arrays.asList(
            "§7Click to enter custom amount",
            "§7Use: §e/bet <amount>"
        ));
        customBet.setItemMeta(customMeta);
        gui.setItem(22, customBet);
        
        // Add current bet display
        ItemStack currentBet = new ItemStack(Material.PAPER);
        ItemMeta currentMeta = currentBet.getItemMeta();
        currentMeta.setDisplayName("§a§lCurrent Bet");
        currentMeta.setLore(Arrays.asList(
            "§7Your current bet: §e$" + getCurrentBet(player),
            "",
            "§7Total balance: §2$" + getBalance(player)
        ));
        currentBet.setItemMeta(currentMeta);
        gui.setItem(4, currentBet);
        
        openGuis.put(player.getUniqueId(), gui);
        player.openInventory(gui);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        Inventory clickedInv = event.getClickedInventory();
        if (!openGuis.containsValue(clickedInv)) {
            return;
        }
        
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        // Handle betting options
        for (int i = 0; i < BET_AMOUNTS.length; i++) {
            if (event.getSlot() == 9 + i) {
                int amount = BET_AMOUNTS[i];
                handleBet(player, amount, event.isRightClick());
                return;
            }
        }
        
        // Handle custom bet
        if (event.getSlot() == 22) {
            player.closeInventory();
            player.sendMessage("§eType §a/bet <amount> §eto set a custom bet amount!");
            return;
        }
    }
    
    private void handleBet(Player player, int amount, boolean addToCurrent) {
        player.closeInventory();
        
        if (addToCurrent) {
            int currentBet = getCurrentBet(player);
            amount += currentBet;
            player.sendMessage("§eAdding to your current bet...");
        }
        
        // Execute bet command
        String[] args = {"bet", String.valueOf(amount)};
        plugin.onCommand(player, plugin.getCommand("blackjack"), "blackjack", args);
    }
    
    private int getCurrentBet(Player player) {
        // This would need to be implemented to get current bet from plugin
        // For now, return 0
        return 0;
    }
    
    private String getBalance(Player player) {
        // This would need to integrate with economy
        // For now, return placeholder
        return "Loading...";
    }
    
    /**
     * Clean up when player leaves
     */
    public void cleanup(Player player) {
        openGuis.remove(player.getUniqueId());
    }
}
