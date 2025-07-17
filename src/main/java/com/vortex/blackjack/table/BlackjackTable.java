package com.vortex.blackjack.table;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.game.BlackjackEngine;
import com.vortex.blackjack.model.Card;
import com.vortex.blackjack.model.Deck;
import com.vortex.blackjack.util.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a single blackjack table with game logic
 */
public class BlackjackTable {
    private final BlackjackPlugin plugin;
    private final TableManager tableManager;
    private final ConfigManager configManager;
    private final Location centerLoc;
    
    // Game state
    private final List<Player> players = new ArrayList<>();
    private final Map<Player, List<Card>> playerHands = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerSeats = new ConcurrentHashMap<>();
    private final Set<Player> finishedPlayers = ConcurrentHashMap.newKeySet();
    private boolean gameInProgress = false;
    private Player currentPlayer;
    private List<Card> dealerHand = new ArrayList<>();
    private Deck deck = new Deck();
    
    // Display entities
    private final Map<Player, List<ItemDisplay>> playerCardDisplays = new ConcurrentHashMap<>();
    private final Map<Player, List<ItemDisplay>> playerDealerDisplays = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();
    
    public BlackjackTable(BlackjackPlugin plugin, TableManager tableManager, ConfigManager configManager, Location centerLoc) {
        this.plugin = plugin;
        this.tableManager = tableManager;
        this.configManager = configManager;
        this.centerLoc = centerLoc;
    }
    
    /**
     * Add a player to this table
     */
    public boolean addPlayer(Player player) {
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
            
            int seatNumber = getNextAvailableSeatNumber();
            if (seatNumber == -1) {
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
                Location seatLoc = getSeatLocation(seatNumber);
                if (seatLoc != null) {
                    player.teleport(seatLoc);
                }
                
                broadcastTableMessage(ChatColor.GREEN + player.getName() + " joined the table!");
                
                // Show betting options if UX features enabled
                if (configManager.areParticlesEnabled()) { // Using as UX enabled check
                    ChatUtils.sendBettingOptions(player);
                }
                
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
                plugin.getLogger().severe("Error adding player to table: " + e.getMessage());
                return false;
            }
        }
    }
    
    /**
     * Remove a player from this table
     */
    public void removePlayer(Player player) {
        synchronized (this) {
            if (!players.contains(player)) return;
            
            // Cleanup player data
            players.remove(player);
            playerSeats.remove(player);
            playerHands.remove(player);
            tableManager.setPlayerTable(player, null);
            
            // Remove display entities
            // Remove player displays
            List<ItemDisplay> cardDisplays = playerCardDisplays.remove(player);
            if (cardDisplays != null) {
                cardDisplays.forEach(ItemDisplay::remove);
            }
            
            List<ItemDisplay> dealerDisplays = playerDealerDisplays.remove(player);
            if (dealerDisplays != null) {
                dealerDisplays.forEach(ItemDisplay::remove);
            }
            
            // Handle game state
            if (players.isEmpty()) {
                endGame();
            } else if (gameInProgress && currentPlayer != null && currentPlayer.equals(player)) {
                nextTurn();
                broadcastTableMessage(ChatColor.RED + player.getName() + " left during their turn.");
            } else {
                broadcastTableMessage(ChatColor.RED + player.getName() + " left the table.");
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
        synchronized (this) {
            if (gameInProgress) {
                broadcastTableMessage(configManager.getMessage("game-in-progress"));
                return;
            }
            
            if (players.isEmpty()) {
                broadcastTableMessage("§cCannot start game with no players!");
                return;
            }
            
            // Check if all players have placed bets
            java.util.Map<org.bukkit.entity.Player, Integer> playerBets = plugin.getPlayerBets();
            java.util.List<org.bukkit.entity.Player> playersWithoutBets = new java.util.ArrayList<>();
            
            for (org.bukkit.entity.Player player : players) {
                Integer bet = playerBets.get(player);
                if (bet == null || bet <= 0) {
                    playersWithoutBets.add(player);
                }
            }
            
            if (!playersWithoutBets.isEmpty()) {
                for (org.bukkit.entity.Player player : playersWithoutBets) {
                    player.sendMessage("§cYou must place a bet before the game can start! Use /bet <amount>");
                }
                broadcastTableMessage("§cAll players must place bets before starting!");
                return;
            }
            
            // Initialize game
            gameInProgress = true;
            deck = new Deck();
            clearAllDisplays();
            finishedPlayers.clear();
            
            // Deal initial cards (2 per player)
            for (Player player : players) {
                List<Card> hand = new ArrayList<>();
                hand.add(deck.drawCard());
                hand.add(deck.drawCard());
                playerHands.put(player, hand);
                updateCardDisplays(player, hand);
            }
            
            // Deal dealer cards
            dealerHand = new ArrayList<>();
            dealerHand.add(deck.drawCard());
            dealerHand.add(deck.drawCard());
            updateDealerDisplays();
            
            // Start first player's turn
            currentPlayer = players.get(0);
            broadcastTableMessage(ChatColor.GREEN + "Game started! " + ChatColor.AQUA + currentPlayer.getName() + "'s turn");
            
            // Send interactive turn message
            ChatUtils.sendGameActionBar(currentPlayer);
        }
    }
    
    /**
     * Player hits (takes another card)
     */
    public void hit(Player player) {
        synchronized (this) {
            if (!gameInProgress || !player.equals(currentPlayer)) {
                return;
            }
            
            List<Card> hand = playerHands.get(player);
            Card newCard = deck.drawCard();
            hand.add(newCard);
            
            playCardSound(player.getLocation());
            updateCardDisplays(player, hand);
            
            int value = BlackjackEngine.calculateHandValue(hand);
            // Don't send individual hand value - it's already shown in updateCardDisplays
            
            if (BlackjackEngine.isBusted(hand)) {
                finishedPlayers.add(player);
                broadcastTableMessage(player.getName() + " " + ChatColor.RED + "BUSTS!" + ChatColor.RESET);
                playLoseSound(player);
                nextTurn();
            } else if (value == 21) {
                finishedPlayers.add(player);
                broadcastTableMessage(player.getName() + " " + ChatColor.GOLD + "hits 21!" + ChatColor.RESET);
                playWinSound(player);
                nextTurn();
            } else {
                // Send action buttons again
                ChatUtils.sendGameActionBar(player);
            }
        }
    }
    
    /**
     * Player stands (ends their turn)
     */
    public void stand(Player player) {
        synchronized (this) {
            if (!gameInProgress || !player.equals(currentPlayer)) {
                return;
            }
            
            finishedPlayers.add(player);
            int value = BlackjackEngine.calculateHandValue(playerHands.get(player));
            broadcastTableMessage(player.getName() + " " + ChatColor.BLUE + "stands" + ChatColor.RESET + " with " + formatHandValue(value));
            nextTurn();
        }
    }
    
    private void nextTurn() {
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
            broadcastTableMessage(ChatColor.AQUA + currentPlayer.getName() + "'s turn");
            ChatUtils.sendGameActionBar(currentPlayer);
        } else {
            endGame();
        }
    }
    
    private void endGame() {
        synchronized (this) {
            if (!gameInProgress) return;
            
            // Dealer logic
            boolean anyValidPlayers = players.stream()
                .anyMatch(p -> !BlackjackEngine.isBusted(playerHands.get(p)));
            
            if (anyValidPlayers) {
                while (BlackjackEngine.dealerShouldHit(dealerHand, configManager.shouldHitSoft17())) {
                    dealerHand.add(deck.drawCard());
                }
            }
            
            // Set game as finished BEFORE showing final dealer cards
            gameInProgress = false;
            
            // Update dealer displays and show final hand with cards and value
            updateDealerDisplays();
            int dealerValue = BlackjackEngine.calculateHandValue(dealerHand);
            String dealerHandDisplay = formatHand(dealerHand);
            String dealerValueDisplay = formatHandValue(dealerValue);
            broadcastTableMessage("Dealer: " + dealerHandDisplay + " | " + dealerValueDisplay);
            
            // Handle payouts for each player with a small delay to let dealer cards show
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player player : new ArrayList<>(players)) {
                    if (player.isOnline()) {
                        handlePayout(player, dealerValue);
                    } else {
                        removePlayer(player);
                    }
                }
                
                // Show game ended message and buttons after payouts
                if (!players.isEmpty()) {
                    broadcastTableMessage(ChatColor.GREEN + "Game ended!");
                    sendGameEndButtons();
                }
            }, 20L); // 1 second delay
            
            // Clear displays after longer delay to let players see final results
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                clearAllDisplays();
                resetGameState();
            }, 100L); // 5 second delay
        }
    }
    
    private void resetGameState() {
        currentPlayer = null;
        finishedPlayers.clear();
        playerHands.clear();
        dealerHand.clear();
        deck = new Deck();
    }
    
    private void handlePayout(Player player, int dealerValue) {
        List<Card> playerHand = playerHands.get(player);
        BlackjackEngine.GameResult result = BlackjackEngine.determineResult(playerHand, dealerHand);
        
        // Get the player's bet amount
        Integer betAmount = plugin.getPlayerBets().get(player);
        if (betAmount == null) {
            betAmount = 0;
        }
        
        switch (result) {
            case PLAYER_BLACKJACK:
                // Blackjack pays 3:2
                int blackjackPayout = (int) (betAmount * 2.5); // bet + 1.5x bet = 2.5x bet
                plugin.getEconomyProvider().add(player.getUniqueId(), java.math.BigDecimal.valueOf(blackjackPayout));
                broadcastTableMessage(ChatColor.GOLD + "" + ChatColor.BOLD + player.getName() + " BLACKJACK! " + ChatColor.GREEN + "+$" + blackjackPayout);
                playWinSound(player);
                updatePlayerStats(player, true, blackjackPayout);
                break;
            case PLAYER_WIN:
            case DEALER_BUST:
                // Regular win pays 2:1 (bet back + equal amount)
                int winPayout = betAmount * 2;
                plugin.getEconomyProvider().add(player.getUniqueId(), java.math.BigDecimal.valueOf(winPayout));
                broadcastTableMessage(ChatColor.GREEN + "" + ChatColor.BOLD + player.getName() + " WINS! " + ChatColor.GREEN + "+$" + winPayout);
                playWinSound(player);
                updatePlayerStats(player, true, betAmount);
                break;
            case DEALER_WIN:
            case DEALER_BLACKJACK:
            case PLAYER_BUST:
                // Player loses their bet (already taken when bet was placed)
                broadcastTableMessage(ChatColor.RED + "" + ChatColor.BOLD + player.getName() + " loses -$" + betAmount);
                playLoseSound(player);
                updatePlayerStats(player, false, -betAmount);
                break;
            case PUSH:
                // Push - return bet to player
                plugin.getEconomyProvider().add(player.getUniqueId(), java.math.BigDecimal.valueOf(betAmount));
                broadcastTableMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + player.getName() + " PUSH (tie) - $" + betAmount + " returned");
                player.playSound(player.getLocation(), configManager.getPushSound(), 1.0F, 1.0F);
                updatePlayerStats(player, null, 0); // Push doesn't count as win or loss
                break;
        }
        
        // Clear the bet
        plugin.getPlayerBets().remove(player);
    }
    
    private void updatePlayerStats(Player player, Boolean won, int winnings) {
        com.vortex.blackjack.model.PlayerStats stats = plugin.getPlayerStats().get(player.getUniqueId());
        if (stats == null) {
            stats = new com.vortex.blackjack.model.PlayerStats();
            plugin.getPlayerStats().put(player.getUniqueId(), stats);
        }
        
        if (won == null) {
            // Push
            stats.setHandsPushed(stats.getHandsPushed() + 1);
        } else if (won) {
            // Win
            stats.setHandsWon(stats.getHandsWon() + 1);
            stats.setTotalWinnings(stats.getTotalWinnings() + winnings);
            stats.setCurrentStreak(Math.max(0, stats.getCurrentStreak()) + 1);
            stats.setBestStreak(Math.max(stats.getBestStreak(), stats.getCurrentStreak()));
            
            // Check for blackjack
            List<Card> playerHand = playerHands.get(player);
            if (playerHand.size() == 2 && BlackjackEngine.calculateHandValue(playerHand) == 21) {
                stats.setBlackjacks(stats.getBlackjacks() + 1);
            }
        } else {
            // Loss
            stats.setHandsLost(stats.getHandsLost() + 1);
            stats.setTotalWinnings(stats.getTotalWinnings() + winnings); // winnings will be negative
            stats.setCurrentStreak(Math.min(0, stats.getCurrentStreak()) - 1);
            
            // Check for bust
            List<Card> playerHand = playerHands.get(player);
            if (BlackjackEngine.calculateHandValue(playerHand) > 21) {
                stats.setBusts(stats.getBusts() + 1);
            }
        }
    }
    
    // Helper methods
    private int getNextAvailableSeatNumber() {
        Set<Integer> takenSeats = new HashSet<>(playerSeats.values());
        for (int i = 0; i < configManager.getMaxPlayers(); i++) {
            if (!takenSeats.contains(i)) {
                return i;
            }
        }
        return -1;
    }
    
    private Location getSeatLocation(int seatNumber) {
        switch (seatNumber) {
            case 0:
                return centerLoc.clone().add(2.0, 0.0, 0.0);
            case 1:
                return centerLoc.clone().add(0.0, 0.0, 2.0);
            case 2:
                return centerLoc.clone().add(-2.0, 0.0, 0.0);
            case 3:
                return centerLoc.clone().add(0.0, 0.0, -2.0);
            default:
                return null;
        }
    }
    
    private Transformation createCardTransformation(boolean isDealer, int seatNumber) {
        if (isDealer) {
            float yRotation = switch (seatNumber) {
                case 0 -> (float) (-Math.PI / 2);
                case 1 -> (float) Math.PI;
                case 2 -> (float) (Math.PI / 2);
                case 3 -> 0.0f;
                default -> 0.0f;
            };
            return new Transformation(
                new Vector3f(0.0f, 0.0f, 0.0f),
                new AxisAngle4f(yRotation, 0.0f, 1.0f, 0.0f),
                new Vector3f(0.35f, 0.35f, 0.35f),
                new AxisAngle4f((float)Math.toRadians(15.0), 1.0f, 0.0f, 0.0f)
            );
        } else {
            float xRotation = (float) (Math.PI / 2);
            float zRotation = 0.0f;
            switch (seatNumber) {
                case 0:
                    zRotation = (float) (Math.PI / 2);
                    break;
                case 1:
                    zRotation = (float) Math.PI;
                    break;
                case 2:
                    zRotation = (float) (Math.PI / 2);
                    break;
                case 3:
                    zRotation = (float) Math.PI;
            }

            return new Transformation(
                new Vector3f(0.0f, 0.0f, 0.0f),
                new AxisAngle4f(xRotation, 1.0f, 0.0f, 0.0f),
                new Vector3f(0.35f, 0.35f, 0.35f),
                new AxisAngle4f(zRotation, 0.0f, 0.0f, 1.0f)
            );
        }
    }
    
    private ItemDisplay createCardDisplay(Location loc, Card card, boolean isDealer, int seatNumber) {
        World world = loc.getWorld();
        Location displayLoc = new Location(world, loc.getBlockX() + 0.5, loc.getBlockY(), loc.getBlockZ() + 0.5, 0.0f, 0.0f);
        ItemDisplay display = (ItemDisplay)world.spawn(displayLoc, ItemDisplay.class);
        
        if (card != null) {
            String cardIdentifier = getCardIdentifier(card);
            ItemStack cardItem = new ItemStack(Material.CLOCK);
            ItemMeta meta = cardItem.getItemMeta();
            meta.setItemModel(new NamespacedKey("playing_cards", "card/" + cardIdentifier.toLowerCase()));
            cardItem.setItemMeta(meta);
            display.setItemStack(cardItem);
        } else {
            ItemStack cardBack = new ItemStack(Material.CLOCK);
            ItemMeta meta = cardBack.getItemMeta();
            meta.setItemModel(new NamespacedKey("playing_cards", "card/back"));
            cardBack.setItemMeta(meta);
            display.setItemStack(cardBack);
        }

        Transformation transform = createCardTransformation(isDealer, seatNumber);
        display.setTransformation(transform);
        return display;
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
    
    private void sendPlayerMessage(Player player, String message) {
        // Always use compact mode - no config needed
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastMessageTime.get(playerId);
        
        // Bypass cooldown for critical messages (payouts, results, dealer final hand)
        boolean isCriticalMessage = message.contains("WINS!") || message.contains("BLACKJACK!") || 
                                  message.contains("loses") || message.contains("PUSH") ||
                                  message.startsWith("Dealer: ") && message.contains("|");
        
        // Only send if it's been more than 1.5 seconds since last message, OR if it's a critical message
        if (isCriticalMessage || lastTime == null || currentTime - lastTime > 1500) {
            player.sendMessage(ChatColor.YELLOW + message);
            lastMessageTime.put(playerId, currentTime);
        }
    }
    
    private void broadcastTableMessage(String message) {
        // Send to all players at the table with spam reduction
        for (Map.Entry<Player, Integer> entry : playerSeats.entrySet()) {
            Player player = entry.getKey();
            if (player != null && player.isOnline()) {
                sendPlayerMessage(player, message);
            }
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
        if (value == 21) {
            valueColor = ChatColor.GOLD;          // 21 = Gold
        } else if (value > 21) {
            valueColor = ChatColor.RED;           // Bust = Red  
        } else if (value >= 18) {
            valueColor = ChatColor.GREEN;         // Good hand = Green
        } else {
            valueColor = ChatColor.YELLOW;        // Normal = Yellow
        }
        
        return "" + ChatColor.BOLD + valueColor + "Value: " + value + ChatColor.RESET;
    }
    
    public void broadcastToTable(String message) {
        broadcastTableMessage(message);
    }
    
    private void playCardSound(Location loc) {
        if (configManager.areSoundsEnabled()) {
            loc.getWorld().playSound(loc, configManager.getCardDealSound(), 
                configManager.getCardDealVolume(), configManager.getCardDealPitch());
        }
    }
    
    private void playWinSound(Player player) {
        if (configManager.areSoundsEnabled()) {
            player.playSound(player.getLocation(), configManager.getWinSound(), 1.0F, 1.0F);
        }
        
        if (configManager.areParticlesEnabled()) {
            player.spawnParticle(configManager.getWinParticle(), 
                player.getLocation().add(0.0, 2.0, 0.0), 20, 0.5, 0.5, 0.5);
        }
    }
    
    private void playLoseSound(Player player) {
        if (configManager.areSoundsEnabled()) {
            player.playSound(player.getLocation(), configManager.getLoseSound(), 1.0F, 1.0F);
        }
        
        if (configManager.areParticlesEnabled()) {
            player.spawnParticle(configManager.getLoseParticle(), 
                player.getLocation().add(0.0, 2.0, 0.0), 10, 0.5, 0.5, 0.5);
        }
    }
    
    // Display management methods - ORIGINAL IMPLEMENTATION
    private void updateCardDisplays(Player player, List<Card> hand) {
        int seatNumber = playerSeats.get(player);
        Location baseDisplayLoc = getSeatLocation(seatNumber);
        
        if (playerCardDisplays.containsKey(player)) {
            for (ItemDisplay display : playerCardDisplays.get(player)) {
                display.remove();
            }
            playerCardDisplays.get(player).clear();
        }

        playerCardDisplays.putIfAbsent(player, new ArrayList<>());
        double cardSpacing = configManager.getCardSpacing();
        double playerHeight = configManager.getPlayerCardHeight();
        double distanceFromPlayer = 1.0; // Original hardcoded value

        for (int i = 0; i < hand.size(); i++) {
            Card card = hand.get(i);
            Location spawnLoc = baseDisplayLoc.clone();
            ItemDisplay display = createCardDisplay(spawnLoc, card, false, seatNumber);
            Vector3f translation = new Vector3f();
            float xOffset = 0.0f;
            float zOffset = 0.0f;
            
            switch (seatNumber) {
                case 0:
                    xOffset = (float)(-distanceFromPlayer);
                    zOffset = (float)(i * cardSpacing - (hand.size() - 1) * cardSpacing / 2.0);
                    break;
                case 1:
                    xOffset = (float)(i * cardSpacing - (hand.size() - 1) * cardSpacing / 2.0);
                    zOffset = (float)(-distanceFromPlayer);
                    break;
                case 2:
                    xOffset = (float)distanceFromPlayer;
                    zOffset = (float)(-(i * cardSpacing) + (hand.size() - 1) * cardSpacing / 2.0);
                    break;
                case 3:
                    xOffset = (float)(-(i * cardSpacing) + (hand.size() - 1) * cardSpacing / 2.0);
                    zOffset = (float)distanceFromPlayer;
            }

            translation.set(xOffset, playerHeight, zOffset);
            Transformation currentTransform = display.getTransformation();
            Transformation newTransform = new Transformation(
                translation, currentTransform.getLeftRotation(), currentTransform.getScale(), currentTransform.getRightRotation()
            );
            display.setTransformation(newTransform);
            playerCardDisplays.get(player).add(display);
        }

        int handValue = BlackjackEngine.calculateHandValue(hand);
        // Send colorized hand info - more compact and readable
        player.sendMessage(ChatColor.AQUA + "Hand: " + formatHand(hand) + " | " + formatHandValue(handValue));
    }

    private void updateDealerDisplays() {
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
            List<ItemDisplay> dealerDisplays = new ArrayList<>();
            int seatNumber = playerSeats.get(player);
            Location baseDisplayLoc = centerLoc.clone();
            double cardSpacing = configManager.getCardSpacing();
            double dealerHeight = configManager.getDealerCardHeight();
            double distanceFromCenter = 0.75; // Original hardcoded value
            
            if (!dealerHand.isEmpty()) {
                Card dealerVisibleCard = dealerHand.get(0);
                // More compact dealer card message
                player.sendMessage(ChatColor.GREEN + "Dealer shows: " + formatCard(dealerVisibleCard) + 
                    ChatColor.GRAY + " (" + dealerVisibleCard.getValue() + ")");
            }

            for (int i = 0; i < dealerHand.size(); i++) {
                Card card = dealerHand.get(i);
                Location spawnLoc = baseDisplayLoc.clone();
                Card displayCard = gameInProgress && i > 0 ? null : card;
                ItemDisplay display = createCardDisplay(spawnLoc, displayCard, true, seatNumber);
                Vector3f translation = new Vector3f();
                float xOffset = 0.0f;
                float zOffset = 0.0f;
                
                switch (seatNumber) {
                    case 0:
                        xOffset = (float)distanceFromCenter;
                        zOffset = (float)(i * cardSpacing - (dealerHand.size() - 1) * cardSpacing / 2.0);
                        break;
                    case 1:
                        xOffset = (float)(i * cardSpacing - (dealerHand.size() - 1) * cardSpacing / 2.0);
                        zOffset = (float)distanceFromCenter;
                        break;
                    case 2:
                        xOffset = (float)(-distanceFromCenter);
                        zOffset = (float)(-(i * cardSpacing) + (dealerHand.size() - 1) * cardSpacing / 2.0);
                        break;
                    case 3:
                        xOffset = (float)(-(i * cardSpacing) + (dealerHand.size() - 1) * cardSpacing / 2.0);
                        zOffset = (float)(-distanceFromCenter);
                }

                translation.set(xOffset, dealerHeight, zOffset);
                Transformation currentTransform = display.getTransformation();
                Transformation newTransform = new Transformation(
                    translation, currentTransform.getLeftRotation(), currentTransform.getScale(), currentTransform.getRightRotation()
                );
                display.setTransformation(newTransform);
                dealerDisplays.add(display);
            }

            playerDealerDisplays.put(player, dealerDisplays);
        }
    }
    
    private void clearAllDisplays() {
        for (Player player : players) {
            List<ItemDisplay> cardDisplays = playerCardDisplays.remove(player);
            if (cardDisplays != null) {
                cardDisplays.forEach(ItemDisplay::remove);
            }
            
            List<ItemDisplay> dealerDisplays = playerDealerDisplays.remove(player);
            if (dealerDisplays != null) {
                dealerDisplays.forEach(ItemDisplay::remove);
            }
        }
    }
    
    /**
     * Cleanup all resources for this table
     */
    public void cleanup() {
        clearAllDisplays();
        players.clear();
        playerHands.clear();
        playerSeats.clear();
        finishedPlayers.clear();
    }
    
    // Getters
    public Location getCenterLocation() { return centerLoc; }
    public List<Player> getPlayers() { return new ArrayList<>(players); }
    public boolean isGameInProgress() { return gameInProgress; }
    
    private void sendGameEndButtons() {
        for (Player player : players) {
            com.vortex.blackjack.util.ChatUtils.sendGameEndOptions(player);
        }
    }

    public boolean canStartGame() {
        if (gameInProgress || players.isEmpty()) {
            return false;
        }
        
        // Check if all players have bets
        java.util.Map<org.bukkit.entity.Player, Integer> playerBets = plugin.getPlayerBets();
        for (org.bukkit.entity.Player player : players) {
            Integer bet = playerBets.get(player);
            if (bet == null || bet <= 0) {
                return false;
            }
        }
        return true;
    }
}
