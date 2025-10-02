package com.vortex.blackjack.table;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.economy.EconomyProvider;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BetManager {

    private final BlackjackPlugin plugin;

    private final Map<Player, Integer> playerBets = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerPersistentBets = new ConcurrentHashMap<>(); // Keeps bet amount for "Play Again"
    private final Map<Player, Long> lastBetTime = new ConcurrentHashMap<>();

    public BetManager(BlackjackPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean processBet(Player player, int amount) {
        ConfigManager configManager = plugin.getConfigManager();
        EconomyProvider economyProvider = plugin.getEconomyProvider();

        // Validate bet amount
        if (amount < configManager.getMinBet() || amount > configManager.getMaxBet()) {
            player.sendMessage(configManager.formatMessage("invalid-bet",
                    "min_bet", configManager.getMinBet(), "max_bet", configManager.getMaxBet()));
            return true;
        }

        // Check cooldown
        long currentTime = System.currentTimeMillis();
        long lastBet = lastBetTime.getOrDefault(player, 0L);
        if (currentTime - lastBet < configManager.getBetCooldown()) {
            player.sendMessage(configManager.getMessage("bet-cooldown"));
            return true;
        }

        // Check if player has enough money
        if (!economyProvider.hasEnough(player.getUniqueId(), BigDecimal.valueOf(amount))) {
            player.sendMessage(configManager.formatMessage("insufficient-funds", "amount", amount));
            return true;
        }

        // Process the bet
        int previousBet = playerBets.getOrDefault(player, 0);
        int difference = amount - previousBet;

        if (difference > 0) {
            // Taking more money
            if (economyProvider.subtract(player.getUniqueId(), BigDecimal.valueOf(difference))) {
                playerBets.put(player, amount);
                playerPersistentBets.put(player, amount); // Store for "Play Again"
                lastBetTime.put(player, currentTime);
                player.sendMessage(configManager.formatMessage("bet-set", "amount", amount));

                // Auto-start game if player is at table and no game in progress
                BlackjackTable table = this.plugin.getTableManager().getPlayerTable(player);
                if (table != null && !table.isGameInProgress() && previousBet == 0) {
                    // First bet placed, try to start game
                    this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                        if (table.canStartGame())
                            table.startGame();
                    }, 20L); // 1 second delay to allow other players to bet
                }
            } else
                player.sendMessage(configManager.getMessage("bet-failed"));
        } else if (difference < 0) {
            // Refunding some money
            int refund = -difference;
            if (economyProvider.add(player.getUniqueId(), BigDecimal.valueOf(refund))) {
                playerBets.put(player, amount);
                playerPersistentBets.put(player, amount); // Store for "Play Again"
                lastBetTime.put(player, currentTime);
                player.sendMessage(configManager.formatMessage("bet-reduced-refunded", "amount", amount, "refund", refund));
            } else
                player.sendMessage(configManager.getMessage("bet-refund-failed"));
        } else
            player.sendMessage(configManager.formatMessage("bet-already-set", "amount", amount));

        return true;
    }

    public void refundAllBets() {
        for (Map.Entry<Player, Integer> entry : playerBets.entrySet()) {
            Player player = entry.getKey();
            Integer amount = entry.getValue();

            if (amount == null || amount <= 0) continue;

            BlackjackTable table = this.plugin.getTableManager().getPlayerTable(player);

            if (table != null && table.isGameInProgress()) continue;

            this.plugin.getEconomyProvider().add(player.getUniqueId(), BigDecimal.valueOf(amount));
            if (!player.isOnline()) continue;

            player.sendMessage(this.plugin.getConfigManager().formatMessage("bet-refunded-shutdown", "amount", amount));
        }

        playerBets.clear();
    }

    public void removePlayer(Player player) {
        playerBets.remove(player);
        playerPersistentBets.remove(player);
        lastBetTime.remove(player);
    }

    public void removePersistent(Player player) { playerPersistentBets.remove(player); }

    public Map<Player, Integer> getPlayerBets() { return playerBets; }
    public Map<Player, Integer> getPlayerPersistentBets() { return playerPersistentBets; }

}
