package com.vortex.blackjack.game;

import com.vortex.blackjack.model.Card;
import java.util.List;

/**
 * Core game logic for blackjack calculations and rules
 */
public class BlackjackEngine {
    
    /**
     * Calculate the optimal value of a blackjack hand
     * @param hand List of cards in the hand
     * @return The best possible value (handling Aces optimally)
     */
    public int calculateHandValue(List<Card> hand) {
        int value = 0;
        int aces = 0;

        // First pass: count face value and Aces
        for (Card card : hand) {
            if (card.getRank().equalsIgnoreCase("A")) {
                aces++;
                value += 11; // Start with Ace as 11
            } else {
                value += card.getValue();
            }
        }

        // Convert Aces from 11 to 1 as needed to avoid busting
        while (value > 21 && aces > 0) {
            value -= 10; // Convert Ace from 11 to 1
            aces--;
        }

        return value;
    }

    /**
     * Check if a hand is a blackjack (21 with exactly 2 cards)
     */
    public boolean isBlackjack(List<Card> hand) {
        return hand.size() == 2 && calculateHandValue(hand) == 21;
    }

    /**
     * Check if a hand is busted (over 21)
     */
    public boolean isBusted(List<Card> hand) {
        return calculateHandValue(hand) > 21;
    }

    /**
     * Check if dealer should hit (less than 17, or soft 17 depending on rules)
     */
    public boolean dealerShouldHit(List<Card> hand, boolean hitSoft17) {
        int value = calculateHandValue(hand);

        if (value < 17) return true;

        if (value == 17 && hitSoft17)
            // Check if it's a soft 17 (contains an Ace counted as 11)
            return isSoft17(hand);

        return false;
    }

    /**
     * Check if hand is a soft 17 (Ace + 6, or Ace + 5 + Ace, etc.)
     */
    private boolean isSoft17(List<Card> hand) {
        if (calculateHandValue(hand) != 17) {
            return false;
        }
        
        // Check if there's an Ace being counted as 11
        int valueWithoutAces = 0;
        int aces = 0;
        
        for (Card card : hand) {
            if (card.getRank().equalsIgnoreCase("A")) {
                aces++;
            } else {
                valueWithoutAces += card.getValue();
            }
        }
        
        // If we have aces and the non-ace value + aces as 1s + one ace as 11 = 17
        return aces > 0 && (valueWithoutAces + aces - 1 + 11) == 17;
    }

    /**
     * Determine game outcome for a player vs dealer
     */
    public GameResult determineResult(List<Card> playerHand, List<Card> dealerHand) {
        int playerValue = calculateHandValue(playerHand);
        int dealerValue = calculateHandValue(dealerHand);
        
        boolean playerBlackjack = isBlackjack(playerHand);
        boolean dealerBlackjack = isBlackjack(dealerHand);
        
        // Player busted
        if (playerValue > 21) {
            return GameResult.PLAYER_BUST;
        }
        
        // Both have blackjack
        if (playerBlackjack && dealerBlackjack) {
            return GameResult.PUSH;
        }
        
        // Player blackjack, dealer doesn't
        if (playerBlackjack && !dealerBlackjack) {
            return GameResult.PLAYER_BLACKJACK;
        }
        
        // Dealer blackjack, player doesn't
        if (dealerBlackjack && !playerBlackjack) {
            return GameResult.DEALER_BLACKJACK;
        }
        
        // Dealer busted
        if (dealerValue > 21) {
            return GameResult.DEALER_BUST;
        }
        
        // Compare values
        if (playerValue > dealerValue) {
            return GameResult.PLAYER_WIN;
        } else if (dealerValue > playerValue) {
            return GameResult.DEALER_WIN;
        } else {
            return GameResult.PUSH;
        }
    }

    public enum GameResult {
        PLAYER_WIN(2.0),        // 1:1 payout
        PLAYER_BLACKJACK(2.5),  // 3:2 payout
        DEALER_WIN(0.0),        // Lose bet
        DEALER_BLACKJACK(0.0),  // Lose bet
        PLAYER_BUST(0.0),       // Lose bet
        DEALER_BUST(2.0),       // 1:1 payout
        PUSH(1.0);              // Return bet

        private final double payoutMultiplier;

        GameResult(double payoutMultiplier) {
            this.payoutMultiplier = payoutMultiplier;
        }

        public double getPayoutMultiplier() {
            return payoutMultiplier;
        }
    }
}
