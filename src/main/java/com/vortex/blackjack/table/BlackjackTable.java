package com.vortex.blackjack.table;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.game.BlackjackEngine;
import com.vortex.blackjack.gui.bet.BetGUI;
import com.vortex.blackjack.model.Card;
import com.vortex.blackjack.model.Deck;
import com.vortex.blackjack.util.ChatUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a single blackjack table with game logic
 */
public class BlackjackTable {

    private final BlackjackPlugin plugin;
    private final BlackjackEngine gameEngine;

    private final int id;
    private final Location centerLoc;
    
    // Game state
    private final List<Player> players = new ArrayList<>();
    private final Map<Player, List<Card>> playerHands = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerSeats = new ConcurrentHashMap<>();
    private final Set<Player> finishedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<Player> doubleDownPlayers = ConcurrentHashMap.newKeySet();
    private boolean gameInProgress = false;
    private boolean readyToPlay = false;
    private Player currentPlayer;
    private List<Card> dealerHand = new ArrayList<>();
    private Deck deck = new Deck();

    // Display entities
    private ItemDisplay tableDisplay;
    private final Map<Integer, ArmorStand> seatEntities = new ConcurrentHashMap<>();
    private final Map<Integer, ItemDisplay> seatDisplays = new ConcurrentHashMap<>();
    private final Map<Player, List<ItemDisplay>> playerCardDisplays = new ConcurrentHashMap<>();
    private final Map<Player, List<ItemDisplay>> playerDealerDisplays = new ConcurrentHashMap<>();

    private Interaction interactionEntity;

    private final Map<UUID, Long> lastMessageTime = new HashMap<>();
    
    // Auto-leave tracking
    private final Map<Player, Long> gameEndTimes = new ConcurrentHashMap<>();
    private BukkitTask autoLeaveTask;
    
    public BlackjackTable(BlackjackPlugin plugin, int id, Location centerLoc) {
        this.id = id;
        this.plugin = plugin;
        this.gameEngine = new BlackjackEngine();
        this.centerLoc = centerLoc;

        this.createTableModels();
        readyToPlay = true;
    }
    
    /**
     * Add a player to this table
     */
    public boolean addPlayer(Player player) {
        return this.addPlayer(player, -1);
    }

    public boolean addPlayer(Player player, int desiredSeat) {
        ChatUtils chatUtils = plugin.getChatUtils();
        TableManager tableManager = plugin.getTableManager();
        ConfigManager configManager = plugin.getConfigManager();

        synchronized (this) {
            if (players.contains(player)) {
                player.sendMessage(configManager.getMessage("already-at-table"));
                return false;
            }
            
            if (tableManager.getPlayerTable(player) != null) {
                player.sendMessage(configManager.getMessage("already-at-table"));
                return false;
            }
            
            if (players.size() >= configManager.getMaxPlayers()) {
                player.sendMessage(configManager.getMessage("table-full"));
                return false;
            }
            
            if (gameInProgress) {
                player.sendMessage(configManager.getMessage("game-in-progress"));
                return false;
            }
            
            if (player.getLocation().distance(centerLoc) > configManager.getMaxJoinDistance()) {
                player.sendMessage(configManager.getMessage("too-far"));
                return false;
            }

            int seatNumber = desiredSeat;

            if (seatNumber < 0 || !this.seatAvailable(seatNumber))
                seatNumber = this.getNextAvailableSeatNumber();

            if (seatNumber < 0) {
                player.sendMessage(configManager.getMessage("no-seats"));
                return false;
            }

            try {
                // Add player to table
                players.add(player);
                playerSeats.put(player, seatNumber);
                playerHands.put(player, new ArrayList<>());
                playerCardDisplays.put(player, new ArrayList<>());
                playerDealerDisplays.put(player, new ArrayList<>());
                tableManager.setPlayerTable(player, this);

                // Teleport to seat
                ArmorStand seatEnt = seatEntities.get(seatNumber);
                if (seatEnt != null) seatEnt.addPassenger(player);

                broadcastTableMessage(configManager.formatMessage("player-joined-table", "player", player.getName()));

                new BetGUI(this.plugin, player).open();

                return true;
            } catch (Exception e) {
                // Cleanup on error
                players.remove(player);
                playerSeats.remove(player);
                playerHands.remove(player);
                playerCardDisplays.remove(player);
                playerDealerDisplays.remove(player);
                tableManager.setPlayerTable(player, null);
                
                player.sendMessage(configManager.getMessage("join-error"));
                plugin.getLogger().severe("Error adding player to table: " + e);
                return false;
            }
        }
    }
    
    /**
     * Remove a player from this table
     */
    public void removePlayer(Player player) {
        removePlayer(player, "left the table");
    }
    
    /**
     * Remove a player from this table with custom reason
     */
    public void removePlayer(Player player, String reason) {
        BetManager betManager = plugin.getBetManager();
        TableManager tableManager = plugin.getTableManager();
        ConfigManager configManager = plugin.getConfigManager();

        // todo; if a player wants to leave during their hand they can surrender half their bet
        synchronized (this) {
            if (!players.contains(player)) return;

            // Check if player has a bet that needs to be refunded
            boolean shouldRefundBet = gameInProgress && configManager.shouldRefundOnLeave();
            Integer betAmount = betManager.getPlayerBets().get(player);

            ArmorStand seatEnt = this.seatEntities.get(playerSeats.get(player));

            if (seatEnt.getPassengers().contains(player))
                seatEnt.removePassenger(player);

            // Cleanup player data
            players.remove(player);
            playerSeats.remove(player);
            playerHands.remove(player);
            finishedPlayers.remove(player);
            doubleDownPlayers.remove(player);
            tableManager.setPlayerTable(player, null);
            
            // Refund bet if player leaves mid-game and refunds are enabled
            if (shouldRefundBet && betAmount != null && betAmount > 0) {
                betManager.getPlayerBets().remove(player);
                if (plugin.getEconomyProvider().add(player.getUniqueId(), BigDecimal.valueOf(betAmount))) {
                    player.sendMessage(configManager.formatMessage("left-table-bet-refunded", "amount", betAmount));
                } else {
                    player.sendMessage(configManager.getMessage("error-refund"));
                    plugin.getLogger().severe("Failed to refund bet for " + player.getName() + " when leaving mid-game");
                }
            } else if (gameInProgress && betAmount != null && betAmount > 0) {
                // Player left mid-game but refunds are disabled - remove bet without refunding
                betManager.getPlayerBets().remove(player);
                player.sendMessage(configManager.formatMessage("left-table-bet-forfeit", "amount", betAmount));
            } else {
                player.sendMessage(configManager.getMessage("left-table"));
            }
            
            // Remove display entities
            List<ItemDisplay> cardDisplays = playerCardDisplays.remove(player);
            if (cardDisplays != null) {
                cardDisplays.forEach(display -> {
                    if (display != null && !display.isDead()) {
                        display.remove();
                    }
                });
            }
            
            List<ItemDisplay> dealerDisplays = playerDealerDisplays.remove(player);
            if (dealerDisplays != null) {
                dealerDisplays.forEach(display -> {
                    if (display != null && !display.isDead()) {
                        display.remove();
                    }
                });
            }
            
            // Handle game state
            if (players.isEmpty()) {
                endGame();
            } else if (gameInProgress && currentPlayer != null && currentPlayer.equals(player)) {
                nextTurn();
                broadcastTableMessage(configManager.formatMessage("player-left-during-turn", "player", player.getName(), "reason", reason));
            } else {
                broadcastTableMessage(configManager.formatMessage("player-left-table", "player", player.getName(), "reason", reason));
            }
        }
    }
    
    /**
     * Remove all players from the table
     */
    public void removeAllPlayers() {
        synchronized (this) {
            List<Player> playersToRemove = new ArrayList<>(players);
            for (Player player : playersToRemove) {
                removePlayer(player);
            }
        }
    }
    
    /**
     * Start a new game at this table
     */
    public void startGame() {
        ChatUtils chatUtils = plugin.getChatUtils();
        BetManager betManager = plugin.getBetManager();
        ConfigManager configManager = plugin.getConfigManager();

        synchronized (this) {
            if (gameInProgress || !readyToPlay) {
                broadcastTableMessage(configManager.getMessage("game-in-progress"));
                return;
            }

            if (players.isEmpty()) {
                broadcastTableMessage(configManager.getMessage("game-error-no-players"));
                return;
            }

            // Check if all players have placed bets
            java.util.Map<org.bukkit.entity.Player, Integer> playerBets = betManager.getPlayerBets();
            java.util.List<org.bukkit.entity.Player> playersWithoutBets = new java.util.ArrayList<>();
            
            for (org.bukkit.entity.Player player : players) {
                Integer bet = playerBets.get(player);
                if (bet == null || bet <= 0) {
                    playersWithoutBets.add(player);
                }
            }
            
            if (!playersWithoutBets.isEmpty()) {
                for (org.bukkit.entity.Player player : playersWithoutBets) {
                    player.sendMessage(configManager.getMessage("bet-required"));
                }
                broadcastTableMessage(configManager.getMessage("game-error-all-must-bet"));
                return;
            }

            // Initialize game
            readyToPlay = false;
            gameInProgress = true;
            deck = new Deck();
            clearAllCardDisplays();
            finishedPlayers.clear();
            doubleDownPlayers.clear();

            // Deal initial cards (2 per player)
            for (Player player : players) {
                List<Card> hand = new ArrayList<>();
                hand.add(deck.drawCard());
                hand.add(deck.drawCard());
                playerHands.put(player, hand);
                updatePlayerCardDisplays(player, hand);
            }

            // Deal dealer cards
            dealerHand = new ArrayList<>();
            dealerHand.add(deck.drawCard());
            dealerHand.add(deck.drawCard());
            updateDealerDisplays();

            // Start first player's turn
            currentPlayer = players.get(0);
            broadcastTableMessage(
                    configManager.formatMessage("game-started", "player", currentPlayer.getName()),
                    true
            );

            // Send interactive turn message (doubledown available on first turn)
            chatUtils.sendGameActionBar(currentPlayer, true);
        }
    }
    
    /**
     * Player hits (takes another card)
     */
    public void hit(Player player) {
        ChatUtils chatUtils = plugin.getChatUtils();
        ConfigManager configManager = plugin.getConfigManager();

        synchronized (this) {
            if (!gameInProgress || !player.equals(currentPlayer)) return;
            
            List<Card> hand = playerHands.get(player);
            Card newCard = deck.drawCard();
            hand.add(newCard);
            
            playCardSound(player.getLocation());
            updatePlayerCardDisplays(player, hand);
            
            int value = gameEngine.calculateHandValue(hand);
            // Don't send individual hand value - it's already shown in updateCardDisplays
            
            if (gameEngine.isBusted(hand)) {
                finishedPlayers.add(player);
                broadcastTableMessage(configManager.formatMessage("player-busts", "player", player.getName()));
                playLoseSound(player);
                nextTurn();
            } else if (value == 21) {
                finishedPlayers.add(player);
                broadcastTableMessage(configManager.formatMessage("player-hits-21", "player", player.getName()));
                playWinSound(player);
                nextTurn();
            } else {
                // Send action buttons again (no doubledown after hitting)
                chatUtils.sendGameActionBar(player, false);
            }
        }
    }
    
    /**
     * Player stands (ends their turn)
     */
    public void stand(Player player) {
        synchronized (this) {
            if (!gameInProgress || !player.equals(currentPlayer)) return;
            
            finishedPlayers.add(player);
            int value = gameEngine.calculateHandValue(playerHands.get(player));
            broadcastTableMessage(
                    this.plugin.getConfigManager().formatMessage("player-stands",
                            "player", player.getName(),
                            "value", formatHandValue(value)
                    ),
                    true
            );
            nextTurn();
        }
    }
    
    /**
     * Player doubles down (doubles bet, gets exactly one more card, then stands)
     */
    public void doubleDown(Player player) {
        ConfigManager configManager = plugin.getConfigManager();

        synchronized (this) {
            if (!gameInProgress || !player.equals(currentPlayer)) return;
            
            // Check if double down is allowed (only on first 2 cards)
            List<Card> hand = playerHands.get(player);
            if (hand.size() != 2) {
                player.sendMessage(configManager.getMessage("double-down-first-two-cards"));
                return;
            }
            
            // Check if player has already doubled down
            if (doubleDownPlayers.contains(player)) {
                player.sendMessage(configManager.getMessage("double-down-already-used"));
                return;
            }

            BetManager betManager = plugin.getBetManager();
            
            // Check if player has sufficient funds
            Integer currentBet = betManager.getPlayerBets().get(player);
            if (currentBet == null) currentBet = 0;
            
            if (!plugin.getEconomyProvider().hasEnough(player.getUniqueId(), java.math.BigDecimal.valueOf(currentBet))) {
                player.sendMessage(configManager.getMessage("double-down-insufficient-funds"));
                return;
            }
            
            // Double the bet
            plugin.getEconomyProvider().subtract(player.getUniqueId(), java.math.BigDecimal.valueOf(currentBet));
            betManager.getPlayerBets().put(player, currentBet * 2);
            
            // Mark player as doubled down
            doubleDownPlayers.add(player);
            
            // Deal exactly one card
            Card newCard = deck.drawCard();
            hand.add(newCard);
            
            playCardSound(player.getLocation());
            updatePlayerCardDisplays(player, hand);
            
            int value = gameEngine.calculateHandValue(hand);
            broadcastTableMessage(
                    configManager.formatMessage("player-doubles-down",
                            "player", player.getName(),
                            "value", formatHandValue(value)
                    ),
                    true
            );
            
            // Player is automatically done after double down
            finishedPlayers.add(player);
            
            if (gameEngine.isBusted(hand)) {
                broadcastTableMessage(configManager.formatMessage("player-busts", "player", player.getName()), true);
                playLoseSound(player);
            } else if (value == 21) {
                broadcastTableMessage(configManager.formatMessage("player-hits-21", "player", player.getName()), true);
                playWinSound(player);
            }
            
            nextTurn();
        }
    }
    
    private void nextTurn() {
        ChatUtils chatUtils = plugin.getChatUtils();
        ConfigManager configManager = plugin.getConfigManager();

        if (finishedPlayers.size() >= players.size()) {
            endGame();
            return;
        }
        
        int currentIndex = players.indexOf(currentPlayer);
        int attempts = 0;
        do {
            currentIndex = (currentIndex + 1) % players.size();
            currentPlayer = players.get(currentIndex);
            attempts++;
            // Prevent infinite loop
            if (attempts >= players.size()) {
                endGame();
                return;
            }
        } while (finishedPlayers.contains(currentPlayer));
        
        if (currentPlayer != null && !finishedPlayers.contains(currentPlayer)) {
            // More compact turn announcement
            broadcastTableMessage(configManager.formatMessage("player-turn", "player", currentPlayer.getName()));
            
            // Show doubledown only if player has exactly 2 cards and hasn't doubled down yet
            List<Card> hand = playerHands.get(currentPlayer);
            boolean canDoubleDown = hand != null && hand.size() == 2 && !doubleDownPlayers.contains(currentPlayer);
            chatUtils.sendGameActionBar(currentPlayer, canDoubleDown);
        } else {
            endGame();
        }
    }
    
    private void endGame() {
        ConfigManager configManager = plugin.getConfigManager();

        synchronized (this) {
            if (!gameInProgress) return;
            gameInProgress = false;
            
            // Dealer logic
            boolean anyValidPlayers = players.stream()
                .anyMatch(p -> !gameEngine.isBusted(playerHands.get(p)));
            
            if (anyValidPlayers)
                while (gameEngine.dealerShouldHit(dealerHand, configManager.shouldHitSoft17()))
                    dealerHand.add(deck.drawCard());
            
            // Update dealer displays and show final hand with cards and value
            this.updateDealerDisplays();

            int dealerValue = gameEngine.calculateHandValue(dealerHand);
            String dealerHandDisplay = formatHand(dealerHand);
            String dealerValueDisplay = formatHandValue(dealerValue);
            broadcastTableMessage("Dealer: " + dealerHandDisplay + " | " + dealerValueDisplay);
            
            // Handle payouts for each player with a small delay to let dealer cards show
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player player : new ArrayList<>(players))
                    if (player.isOnline()) handlePayout(player, dealerValue);
                    else removePlayer(player);
                
                // Reset game state immediately after payouts so new games can start
                resetGameState();
                
                // Show game ended message and buttons after payouts
                if (!players.isEmpty()) {
                    broadcastTableMessage(configManager.getMessage("game-ended"), true);
                    sendGameEndButtons();
                    startAutoLeaveTimer();
                }
            }, 20L); // 1 second delay
        }
    }
    
    private void resetGameState() {
        readyToPlay = true;
        currentPlayer = null;
        finishedPlayers.clear();
        doubleDownPlayers.clear();
        playerHands.clear();
        dealerHand.clear();
        deck = new Deck();
    }
    
    private void handlePayout(Player player, int dealerValue) {
        BetManager betManager = plugin.getBetManager();
        ConfigManager configManager = plugin.getConfigManager();

        List<Card> playerHand = playerHands.get(player);
        BlackjackEngine.GameResult result = gameEngine.determineResult(playerHand, dealerHand);
        
        // Get the player's bet amount
        Integer betAmount = betManager.getPlayerBets().get(player);
        if (betAmount == null) {
            betAmount = 0;
        }
        
        switch (result) {
            case PLAYER_BLACKJACK:
                // Blackjack pays 3:2
                int blackjackPayout = (int) (betAmount * 2.5); // bet + 1.5x bet = 2.5x bet
                plugin.getEconomyProvider().add(player.getUniqueId(), java.math.BigDecimal.valueOf(blackjackPayout));
                broadcastTableMessage(configManager.formatMessage("player-blackjack", 
                    "player", player.getName(), 
                    "payout", String.valueOf(blackjackPayout)));
                playWinSound(player);
                updatePlayerStats(player, true, (double) blackjackPayout);
                break;
            case PLAYER_WIN:
            case DEALER_BUST:
                // Regular win pays 2:1 (bet back + equal amount)
                int winPayout = betAmount * 2;
                plugin.getEconomyProvider().add(player.getUniqueId(), java.math.BigDecimal.valueOf(winPayout));
                broadcastTableMessage(configManager.formatMessage("player-wins", 
                    "player", player.getName(), 
                    "payout", String.valueOf(winPayout)));
                playWinSound(player);
                updatePlayerStats(player, true, (double) betAmount);
                break;
            case DEALER_WIN:
            case DEALER_BLACKJACK:
            case PLAYER_BUST:
                // Player loses their bet (already taken when bet was placed)
                broadcastTableMessage(configManager.formatMessage("player-loses", 
                    "player", player.getName(), 
                    "amount", String.valueOf(betAmount)));
                playLoseSound(player);
                updatePlayerStats(player, false, (double) -betAmount);
                break;
            case PUSH:
                // Push - return bet to player
                plugin.getEconomyProvider().add(player.getUniqueId(), java.math.BigDecimal.valueOf(betAmount));
                broadcastTableMessage(
                        configManager.formatMessage("player-push",
                                "player", player.getName(),
                                "amount", String.valueOf(betAmount)
                        ),
                        true
                );
                player.playSound(player.getLocation(), configManager.getPushSound(), 1.0F, 1.0F);
                updatePlayerStats(player, null, 0.0); // Push doesn't count as win or loss
                break;
        }

        // Clear the bet
        betManager.getPlayerBets().remove(player);
    }
    
    private void updatePlayerStats(Player player, Boolean won, double winnings) {
        com.vortex.blackjack.model.PlayerStats stats = plugin.getPlayerStats().get(player.getUniqueId());
        if (stats == null) {
            stats = new com.vortex.blackjack.model.PlayerStats();
            plugin.getPlayerStats().put(player.getUniqueId(), stats);
        }
        
        if (won == null)
            // Push - use the increment method
            stats.incrementPushes();
        else if (won) {
            // Win - use the increment method which also handles streaks
            stats.incrementWins();
            stats.addWinnings(winnings);
            
            // Check for blackjack
            List<Card> playerHand = playerHands.get(player);
            if (playerHand.size() == 2 && gameEngine.calculateHandValue(playerHand) == 21)
                stats.incrementBlackjacks();
        } else {
            // Loss - use the increment method which also handles streaks
            stats.incrementLosses();
            stats.addLosses(Math.abs(winnings)); // winnings will be negative
            
            // Check for bust
            List<Card> playerHand = playerHands.get(player);
            if (gameEngine.calculateHandValue(playerHand) > 21)
                stats.incrementBusts();
        }
    }
    
    // Helper methods
    private boolean seatAvailable(int seat) {
        if (seat > plugin.getConfigManager().getMaxPlayers()) return false;
        return !playerSeats.containsValue(seat);
    }

    private int getNextAvailableSeatNumber() {
        Set<Integer> takenSeats = new HashSet<>(playerSeats.values());
        for (int i = 0; i < plugin.getConfigManager().getMaxPlayers(); i++)
            if (!takenSeats.contains(i))
                return i;
        return -1;
    }

    public float getYaw() {
        float locYaw = this.centerLoc.getYaw() / 90;
        return Math.round(locYaw) * 90;
    }

    public Location getTableLocation() {
        Location loc = this.centerLoc.clone();

        loc.setPitch(0);
        loc.setYaw(this.getYaw());

        return loc;
    }

    private Location addRelativeOffset(Location location, float offsetX, float offsetY, float offsetZ) {
        double yawRadians = Math.toRadians(location.getYaw());
        double xOffset = -Math.sin(yawRadians) * offsetZ + Math.cos(yawRadians) * offsetX;
        double zOffset = Math.cos(yawRadians) * offsetZ + Math.sin(yawRadians) * offsetX;

        return location.clone().add(xOffset, offsetY, zOffset);
    }

    private Location addRelativeOffset(Location location, Vector3f offset) {
        return this.addRelativeOffset(location, offset.x, offset.y, offset.z);
    }

    public Location getSeatLocation(int seatNumber) {
        Vector3f offset = this.getSeatOffset(seatNumber);
        if (offset == null) return null;

        Location tableLoc = this.getTableLocation();
        return this.addRelativeOffset(tableLoc, offset);
    }
    
    private void sendPlayerMessage(Player player, String message, boolean spacer) {
        // Always use compact mode - no config needed
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastMessageTime.get(playerId);
        
        // Bypass cooldown for critical messages (payouts, results, dealer final hand, doubledown)
        boolean isCriticalMessage = message.contains("WINS!") || message.contains("BLACKJACK!") || 
                                  message.contains("loses") || message.contains("PUSH") ||
                                  message.contains("DOUBLES DOWN") ||
                                  message.startsWith("Dealer: ") && message.contains("|");

        if (spacer) player.sendMessage(" ");

        // Only send if it's been more than 1.5 seconds since last message, OR if it's a critical message
        if (isCriticalMessage || lastTime == null || currentTime - lastTime > 1500) {
            // Check if message is already formatted (contains color codes or special characters)
            if (message.contains("§") || message.contains("&") || isCriticalMessage)
                // Send directly - already formatted
                player.sendMessage(message);
            else
                // Wrap in table broadcast format
                player.sendMessage(this.plugin.getConfigManager().formatMessage("table-message-broadcast", "message", message));
            lastMessageTime.put(playerId, currentTime);
        }
    }

    private void broadcastTableMessage(String message) {
        this.broadcastTableMessage(message, false);
    }

    private void broadcastTableMessage(String message, boolean spacer) {
        // Send to all players at the table with spam reduction
        for (Map.Entry<Player, Integer> entry : playerSeats.entrySet()) {
            Player player = entry.getKey();
            if (player != null && player.isOnline())
                sendPlayerMessage(player, message, spacer);
        }
    }
    
    private String formatHand(List<Card> hand) {
        StringBuilder handStr = new StringBuilder();
        for (int i = 0; i < hand.size(); i++) {
            if (i > 0) handStr.append(" ");
            handStr.append(formatCard(hand.get(i)));
        }
        return handStr.toString();
    }
    
    private String formatCard(Card card) {
        ChatColor suitColor;
        String suit = card.getSuit();
        
        // Color code by suit
        switch (suit) {
            case "♥", "♦" -> suitColor = ChatColor.RED;           // Hearts and Diamonds = Red
            case "♠", "♣" -> suitColor = ChatColor.DARK_GRAY;     // Spades and Clubs = Dark Gray
            default -> suitColor = ChatColor.WHITE;
        }
        
        return suitColor + card.getRank() + suit + ChatColor.RESET;
    }
    
    private String formatHandValue(int value) {
        ChatColor valueColor;
        if (value == 21)
            valueColor = ChatColor.GOLD;          // 21 = Gold
        else if (value > 21)
            valueColor = ChatColor.RED;           // Bust = Red  
        else if (value >= 18)
            valueColor = ChatColor.GREEN;         // Good hand = Green
        else
            valueColor = ChatColor.YELLOW;        // Normal = Yellow
        
        return "" + ChatColor.BOLD + valueColor + "Value: " + value + ChatColor.RESET;
    }
    
    public void broadcastToTable(String message) {
        broadcastTableMessage(message);
    }
    
    private void playCardSound(Location loc) {
        ConfigManager configManager = plugin.getConfigManager();

        if (configManager.areSoundsEnabled())
            loc.getWorld().playSound(loc, configManager.getCardDealSound(), 
                configManager.getCardDealVolume(), configManager.getCardDealPitch());
    }
    
    private void playWinSound(Player player) {
        ConfigManager configManager = plugin.getConfigManager();

        if (configManager.areSoundsEnabled())
            player.playSound(player.getLocation(), configManager.getWinSound(), 1.0F, 1.0F);
        
        if (configManager.areParticlesEnabled())
            player.spawnParticle(configManager.getWinParticle(), 
                player.getLocation().add(0.0, 2.0, 0.0), 20, 0.5, 0.5, 0.5);
    }
    
    private void playLoseSound(Player player) {
        ConfigManager configManager = plugin.getConfigManager();

        if (configManager.areSoundsEnabled())
            player.playSound(player.getLocation(), configManager.getLoseSound(), 1.0F, 1.0F);
        
        if (configManager.areParticlesEnabled())
            player.spawnParticle(configManager.getLoseParticle(), 
                player.getLocation().add(0.0, 2.0, 0.0), 10, 0.5, 0.5, 0.5);
    }

    private String getCardIdentifier(Card card) {
        String suit = switch (card.getSuit()) {
            case "♠" -> "s";
            case "♥" -> "h";
            case "♦" -> "d";
            case "♣" -> "c";
            default -> throw new IllegalArgumentException("Invalid suit: " + card.getSuit());
        };

        String rank = switch (card.getRank()) {
            case "A" -> "1";
            case "J" -> "j";
            case "Q" -> "q";
            case "K" -> "k";
            default -> card.getRank().toLowerCase();
        };

        return suit + rank;
    }

    public Vector3f getSeatOffset(int seatNumber) {
        float yOffset = 0.5f;
        return switch (seatNumber) {
            case 0 -> new Vector3f(2.0f, yOffset, 0.0f);
            case 1 -> new Vector3f(1.0f, yOffset, 1.2f);
            case 2 -> new Vector3f(0.0f, yOffset, 1.5f);
            case 3 -> new Vector3f(-1.0f, yOffset, 1.2f);
            case 4 -> new Vector3f(-2.0f, yOffset, 0.0f);
            default -> null;
        };
    }

    private float getSeatYaw(int seatNumber) {
        return switch (seatNumber) {
            case 0 -> 270f;
            case 1 -> 206f;
            case 2 -> 180f;
            case 3 -> 154f;
            case 4 -> 90f;
            default -> 0.0f;
        };
    }

    private Transformation createCardTransformation(boolean isDealer, int seatNumber) {
        float fScale = plugin.getConfigManager().getCardScale();
        Vector3f offset = new Vector3f(0.0f, 0.0f, 0.0f);
        Vector3f scale = new Vector3f(fScale, fScale, fScale);

        if (isDealer) {
            float yRotation = (float) switch (seatNumber) {
                case 0 -> (-Math.PI / 2); // 270
                case 1 -> Math.toRadians(206);
                case 2 -> Math.PI; // 180
                case 3 -> Math.toRadians(154);
                case 4 -> (Math.PI / 2); // 90
                default -> 0;
            };
            return new Transformation(
                    offset,
                    new AxisAngle4f(yRotation, 0.0f, 1.0f, 0.0f),
                    scale,
                    new AxisAngle4f((float) Math.toRadians(15.0), 1.0f, 0.0f, 0.0f)
            );
        }

        float xRotation = (float) (Math.PI / 2);
        float zRotation = (float) switch (seatNumber) {
            case 0 -> (Math.PI / 2); // 90
            case 1 -> Math.toRadians(154);
            case 2 -> Math.PI; // 180
            case 3 -> Math.toRadians(206);
            case 4 -> -(Math.PI / 2); // 270
            default -> 0;
        };

        return new Transformation(
                offset,
                new AxisAngle4f(xRotation, 1.0f, 0.0f, 0.0f),
                scale,
                new AxisAngle4f(zRotation, 0.0f, 0.0f, 1.0f)
        );
    }

    private ItemDisplay createCardDisplay(Location loc, Card card, boolean isDealer, int seatNumber) {
        World world = loc.getWorld();

        if (world == null) return null;

        Location displayLoc = this.addRelativeOffset(loc, 0.5f, -0.5f, 0.5f);
        ItemDisplay display = world.spawn(displayLoc, ItemDisplay.class);

        ItemStack cardItem = new ItemStack(Material.CLOCK);

        ItemMeta meta = cardItem.getItemMeta();
        if (meta != null) {
            String cardIdentifier = card != null ? getCardIdentifier(card) : "back";
            meta.setItemModel(new NamespacedKey("playing_cards", "card/" + cardIdentifier.toLowerCase()));
            cardItem.setItemMeta(meta);
        }

        display.setItemStack(cardItem);

        Transformation transform = createCardTransformation(isDealer, seatNumber);
        display.setTransformation(transform);

        return display;
    }

    private ItemDisplay createTableDisplay() {
        World world = this.centerLoc.getWorld();

        if (world == null) return null;

        ItemStack tableItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = tableItem.getItemMeta();

        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(Collections.singletonList(66610f)); // todo config
        meta.setCustomModelDataComponent(cmd);

        tableItem.setItemMeta(meta);

        Location location = this.getTableLocation();

        return world.spawn(location, ItemDisplay.class, d -> {
            d.setItemStack(tableItem);
            d.setBillboard(Display.Billboard.FIXED);
            d.setInterpolationDuration(2);

            d.setTransformation(new Transformation(
                    new Vector3f(0, 0.5f, 0),
                    new AxisAngle4f(),
                    new Vector3f(1f, 1f, 1f),
                    new AxisAngle4f()
            ));
            d.setPersistent(false);
        });
    }

    private ItemDisplay createSeatDisplay(int seatNumber) {
        Vector3f offset = this.getSeatOffset(seatNumber);
        if (offset == null) return null;

        Location tableLocation = this.getTableLocation();
        World world = tableLocation.getWorld();
        if (world == null) return null;

        ItemStack seatItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = seatItem.getItemMeta();

        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(Collections.singletonList(77718f)); // todo config
        meta.setCustomModelDataComponent(cmd);

        seatItem.setItemMeta(meta);

        return world.spawn(tableLocation, ItemDisplay.class, d -> {
            d.setItemStack(seatItem);
            d.setBillboard(Display.Billboard.FIXED);
            d.setInterpolationDuration(2);

            float yaw = getSeatYaw(seatNumber);
            float radians = (float) Math.toRadians(yaw);

            d.setTransformation(new Transformation(
                    offset,
                    new AxisAngle4f(),
                    new Vector3f(1f, 1f, 1f),
                    new AxisAngle4f(radians, 0f, 1f, 0f)
            ));
            d.setPersistent(false);
        });
    }

    private ArmorStand createSeatEntity(int seatNumber) {
        Location seatLocation = this.getSeatLocation(seatNumber);
        if (seatLocation == null) return null;

        World world = seatLocation.getWorld();
        if (world == null) return null;

        Location loc = seatLocation.clone().add(0.0, -1.6f, 0.0);

        float yaw = (loc.getYaw() + 180) % 360;
        loc.setYaw(yaw);

        return world.spawn(loc, ArmorStand.class, e -> {
            e.setSilent(true);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setTicksLived(Integer.MAX_VALUE);
            e.setInvisible(true);
        });
    }

    private Interaction createTableInteraction() {
        Location tableLocation = this.getTableLocation();
        World world = tableLocation.getWorld();
        if (world == null) return null;

        return world.spawn(tableLocation, Interaction.class, in -> {
            in.setInteractionWidth(4);
            in.setInteractionHeight(1.5f);
            in.setResponsive(true);
            in.setPersistent(false);
        });
    }

    public void createTableModels() {
        this.tableDisplay = this.createTableDisplay();
        for (int i = 0; i < this.plugin.getConfigManager().getMaxPlayers(); i++) {
            this.seatEntities.put(i, this.createSeatEntity(i));
            this.seatDisplays.put(i, this.createSeatDisplay(i));
        }
        this.interactionEntity = this.createTableInteraction();
    }

    // todo; hitting immediately after 21 results in bugged game
    private Vector3f getCardTranslation(int i, int seatNumber, int deckSize, boolean isDealer) {
        ConfigManager configManager = plugin.getConfigManager();
        double cardSpacing = configManager.getCardSpacing();

        int sizeCap = deckSize - 1;

        float dlrOffset = isDealer ? 0.18f : 0f;
        float cardOffset = (float) (i * cardSpacing - sizeCap * cardSpacing / 2.0);

        float xOffset = switch (seatNumber) {
            case 0 -> -1.35f - dlrOffset;
            case 1 -> cardOffset - 0.85f;
            case 2 -> cardOffset - 0.5f;
            case 3 -> cardOffset - 0.15f;
            case 4 -> 0.35f + dlrOffset;
            default -> 0.0f;
        };
        float zOffset = switch (seatNumber) {
            case 0, 4 -> cardOffset - 0.47f; // done
            case 1, 3 -> -1.2f - dlrOffset;
            case 2 -> -1.35f - dlrOffset; // done
            default -> 0.0f;
        };

        return new Vector3f(xOffset, 0, zOffset);
    }

    private List<ItemDisplay> updateCardDisplays(List<Card> hand, int seatNumber, boolean isDealer, float heightOffset) {
        List<ItemDisplay> displays = new ArrayList<>();

        Location seatLoc = this.getSeatLocation(seatNumber);
        if (seatLoc == null) return displays;

        int size = hand.size();

        for (int i = 0; i < size; i++) {
            Card card = hand.get(i);
            Card displayCard = isDealer && gameInProgress && i > 0 ? null : card;

            ItemDisplay display = this.createCardDisplay(seatLoc, displayCard, isDealer, seatNumber);
            if (display == null) continue;

            Vector3f translation = this.getCardTranslation(i, seatNumber, size, isDealer).add(0, heightOffset, 0);
            Transformation currentTransform = display.getTransformation();
            Transformation newTransform = new Transformation(
                    translation, currentTransform.getLeftRotation(), currentTransform.getScale(), currentTransform.getRightRotation()
            );
            display.setTransformation(newTransform);

            displays.add(display);
        }

        return displays;
    }

    // Display management methods - ORIGINAL IMPLEMENTATION
    private void updatePlayerCardDisplays(Player player, List<Card> hand) {
        ConfigManager configManager = plugin.getConfigManager();

        int seatNumber = playerSeats.get(player);

        if (playerCardDisplays.containsKey(player)) {
            for (ItemDisplay display : playerCardDisplays.get(player))
                display.remove();
            playerCardDisplays.get(player).clear();
        }

        playerCardDisplays.putIfAbsent(player, new ArrayList<>());

        double playerHeight = configManager.getPlayerCardHeight();

        List<ItemDisplay> displays = this.updateCardDisplays(hand, seatNumber, false, (float) playerHeight);
        playerCardDisplays.get(player).addAll(displays);

        int handValue = gameEngine.calculateHandValue(hand);
        // Send colorized hand info - more compact and readable
        player.sendMessage(" ");
        player.sendMessage(configManager.formatMessage("hand-display",
                "hand", formatHand(hand),
                "hand_value", formatHandValue(handValue)));
    }

    private void updateDealerDisplays() {
        ConfigManager configManager = plugin.getConfigManager();

        for (Player player : players) {
            if (playerDealerDisplays.containsKey(player)) {
                for (ItemDisplay display : playerDealerDisplays.get(player)) {
                    display.remove();
                }
                playerDealerDisplays.get(player).clear();
            }
        }

        for (Player player : players) {
            playerDealerDisplays.putIfAbsent(player, new ArrayList<>());
            int seatNumber = playerSeats.get(player);
            double dealerHeight = configManager.getDealerCardHeight();

            if (!dealerHand.isEmpty()) {
                Card dealerVisibleCard = dealerHand.getFirst();
                // More compact dealer card message
                player.sendMessage(" ");
                player.sendMessage(configManager.formatMessage("dealer-shows",
                        "card", formatCard(dealerVisibleCard),
                        "value", dealerVisibleCard.getValue()));
            }

            List<ItemDisplay> dealerDisplays = this.updateCardDisplays(dealerHand, seatNumber, true, (float) dealerHeight);

            playerDealerDisplays.put(player, dealerDisplays);
        }
    }

    private void clearAllCardDisplays() {
        // Clear displays for all players (not just current players list)
        for (List<ItemDisplay> cardDisplays : playerCardDisplays.values())
            if (cardDisplays != null)
                cardDisplays.forEach(display -> {
                    if (display != null && !display.isDead())
                        display.remove();
                });

        for (List<ItemDisplay> dealerDisplays : playerDealerDisplays.values())
            if (dealerDisplays != null)
                dealerDisplays.forEach(display -> {
                    if (display != null && !display.isDead())
                        display.remove();
                });

        playerCardDisplays.clear();
        playerDealerDisplays.clear();
    }

    private void cleanTableDisplay() {
        if (this.tableDisplay != null) tableDisplay.remove();
        tableDisplay = null;

        for (ItemDisplay seat : seatDisplays.values()) seat.remove();
        seatDisplays.clear();

        for (ArmorStand seat : seatEntities.values()) seat.remove();
        seatEntities.clear();

        if (this.interactionEntity != null) this.interactionEntity.remove();
        interactionEntity = null;
    }

    /**
     * Cleanup all resources for this table
     */
    public void cleanup() {
        this.cleanTableDisplay();
        this.clearAllCardDisplays();

        players.clear();
        playerHands.clear();
        playerSeats.clear();
        finishedPlayers.clear();
        doubleDownPlayers.clear();
        playerCardDisplays.clear();
        playerDealerDisplays.clear();
        lastMessageTime.clear();
    }
    
    // Getters
    public Location getCenterLocation() { return centerLoc; }
    public List<Player> getPlayers() { return new ArrayList<>(players); }
    public boolean isGameInProgress() { return gameInProgress || !readyToPlay; }
    
    // PlaceholderAPI support methods
    public int getPlayerCount() { return players.size(); }
    public int getAvailableSeats() { return this.plugin.getConfigManager().getMaxPlayers() - players.size(); }
    public boolean isFull() { return players.size() >= this.plugin.getConfigManager().getMaxPlayers(); }
    public Location getLocation() { return centerLoc; }
    
    public boolean hasPlayerHand(Player player) { return playerHands.containsKey(player); }
    public int getPlayerHandValue(Player player) { 
        List<Card> hand = playerHands.get(player);
        return hand != null ? gameEngine.calculateHandValue(hand) : 0;
    }
    public int getPlayerHandSize(Player player) {
        List<Card> hand = playerHands.get(player);
        return hand != null ? hand.size() : 0;
    }
    
    public boolean isPlayerTurn(Player player) { return currentPlayer == player; }
    public boolean isPlayerFinished(Player player) { return finishedPlayers.contains(player); }
    public boolean hasPlayerBlackjack(Player player) {
        List<Card> hand = playerHands.get(player);
        return hand != null && hand.size() == 2 && gameEngine.calculateHandValue(hand) == 21;
    }
    public boolean isPlayerBusted(Player player) {
        List<Card> hand = playerHands.get(player);
        return hand != null && gameEngine.calculateHandValue(hand) > 21;
    }
    public boolean canPlayerDoubleDown(Player player) {
        List<Card> hand = playerHands.get(player);
        return hand != null && hand.size() == 2 && !doubleDownPlayers.contains(player);
    }
    public boolean hasPlayerDoubledDown(Player player) { return doubleDownPlayers.contains(player); }
    
    public int getDealerVisibleValue() {
        if (dealerHand.isEmpty()) return 0;
        // Only show first card during game
        if (gameInProgress && dealerHand.size() >= 2) {
            List<Card> visibleCards = new ArrayList<>();
            visibleCards.add(dealerHand.getFirst());
            return gameEngine.calculateHandValue(visibleCards);
        }
        return gameEngine.calculateHandValue(dealerHand);
    }
    public int getDealerCardCount() { return dealerHand.size(); }
    
    private void sendGameEndButtons() {
        for (Player player : players)
            this.plugin.getChatUtils().sendGameEndOptions(player);
    }

    public boolean canStartGame() {
        if (gameInProgress || !readyToPlay || players.isEmpty()) return false;

        // Check if all players have bets
        java.util.Map<Player, Integer> playerBets = this.plugin.getBetManager().getPlayerBets();
        for (Player player : players) {
            Integer bet = playerBets.get(player);
            if (bet == null || bet <= 0) return false;
        }

        return true;
    }
    
    private void startAutoLeaveTimer() {
        // Cancel any existing auto-leave task
        if (autoLeaveTask != null)
            autoLeaveTask.cancel();
        
        // Record the game end time for all players
        long gameEndTime = System.currentTimeMillis();
        for (Player player : players)
            gameEndTimes.put(player, gameEndTime);
        
        // Start the auto-leave checker task
        autoLeaveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAutoLeave, 20L * 5L, 20L * 5L); // Check every 5 seconds
    }
    
    private void checkAutoLeave() {
        ConfigManager configManager = plugin.getConfigManager();

        if (gameInProgress || players.size() <= 1) {
            // Cancel auto-leave if game is in progress or only 1 player left
            if (autoLeaveTask != null) {
                autoLeaveTask.cancel();
                autoLeaveTask = null;
            }
            gameEndTimes.clear();
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        int timeoutMs = configManager.getAutoLeaveTimeoutSeconds() * 1000;

        List<Player> playersToRemove = new ArrayList<>();
        for (Player player : new ArrayList<>(players)) {
            Long gameEndTime = gameEndTimes.get(player);
            if (gameEndTime != null && (currentTime - gameEndTime) >= timeoutMs)
                playersToRemove.add(player);
        }
        
        // Remove inactive players
        for (Player player : playersToRemove) {
            if (player.isOnline())
                player.sendMessage(configManager.getMessage("auto-left-inactive"));
            removePlayer(player, "was removed due to inactivity");
            gameEndTimes.remove(player);
        }
        
        // Cancel auto-leave task if no more players or only 1 left
        if (players.size() <= 1) {
            if (autoLeaveTask != null) {
                autoLeaveTask.cancel();
                autoLeaveTask = null;
            }
            gameEndTimes.clear();
        }
    }
    
    public void cancelAutoLeaveTimer() {
        if (autoLeaveTask != null) {
            autoLeaveTask.cancel();
            autoLeaveTask = null;
        }
        gameEndTimes.clear();
    }

    public Interaction getInteractionEntity() { return interactionEntity; }
    public int getId() { return id; }
}
