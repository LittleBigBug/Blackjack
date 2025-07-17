package com.vortex.blackjack.model;

/**
 * Represents a playing card in the blackjack game
 */
public class Card {
    private final String suit;
    private final String rank;

    public Card(String suit, String rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public String getSuit() {
        return suit;
    }

    public String getRank() {
        return rank;
    }

    @Override
    public String toString() {
        return rank + suit;
    }

    /**
     * Get the blackjack value of this card
     * @return the numeric value (Ace = 11, Face cards = 10, numbers = face value)
     */
    public int getValue() {
        String rankLower = rank.toLowerCase();
        return switch (rankLower) {
            case "a" -> 11;
            case "k", "q", "j" -> 10;
            default -> {
                try {
                    yield Integer.parseInt(rankLower);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid card rank: " + rank);
                    yield 0;
                }
            }
        };
    }

    /**
     * Get the identifier string for resource pack card textures
     */
    public String getCardIdentifier() {
        String suitCode = switch (suit) {
            case "♠" -> "s";
            case "♥" -> "h";
            case "♦" -> "d";
            case "♣" -> "c";
            default -> throw new IllegalArgumentException("Invalid suit: " + suit);
        };

        String rankCode = switch (rank) {
            case "A" -> "1";
            case "J" -> "j";
            case "Q" -> "q";
            case "K" -> "k";
            default -> rank.toLowerCase();
        };

        return suitCode + rankCode;
    }
}
