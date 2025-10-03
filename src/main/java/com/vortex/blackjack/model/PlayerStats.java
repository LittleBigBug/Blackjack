package com.vortex.blackjack.model;

/**
 * Tracks player statistics for blackjack games
 */
public class PlayerStats {
    private int handsWon = 0;
    private int handsLost = 0;
    private int handsPushed = 0;
    private int currentStreak = 0;
    private int bestStreak = 0;
    private double totalWinnings = 0.0;
    private double totalLosses = 0.0;
    private int blackjacks = 0;
    private int busts = 0;

    // Getters
    public int getHandsWon() { return handsWon; }
    public int getHandsLost() { return handsLost; }
    public int getHandsPushed() { return handsPushed; }
    public int getCurrentStreak() { return currentStreak; }
    public int getBestStreak() { return bestStreak; }
    public double getTotalWinnings() { return totalWinnings; }
    public double getTotalLosses() { return totalLosses; }
    public double getTotalNet() { return totalWinnings - totalLosses; }
    public int getBlackjacks() { return blackjacks; }
    public int getBusts() { return busts; }

    public int getTotalHands() {
        return handsWon + handsLost + handsPushed;
    }

    public double getWinRate() {
        int total = getTotalHands();
        return total > 0 ? (double) handsWon / total * 100 : 0.0;
    }

    public synchronized void incrementWins() {
        handsWon++;
        currentStreak = Math.max(0, currentStreak + 1);
        bestStreak = Math.max(bestStreak, currentStreak);
    }

    public synchronized void incrementLosses() {
        handsLost++;
        currentStreak = Math.min(0, currentStreak - 1);
    }

    public synchronized void incrementPushes() {
        handsPushed++;
        // Pushes don't affect streak
    }

    public synchronized void incrementBlackjacks() {
        blackjacks++;
    }

    public synchronized void incrementBusts() {
        busts++;
    }

    public synchronized void addWinnings(double amount) {
        totalWinnings += amount;
    }

    public synchronized void addLosses(double amount) {
        totalLosses += amount;
    }

    // Setters for loading from config
    public void setHandsWon(int handsWon) { this.handsWon = handsWon; }
    public void setHandsLost(int handsLost) { this.handsLost = handsLost; }
    public void setHandsPushed(int handsPushed) { this.handsPushed = handsPushed; }
    public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }
    public void setBestStreak(int bestStreak) { this.bestStreak = bestStreak; }
    public void setTotalWinnings(double totalWinnings) { this.totalWinnings = totalWinnings; }
    public void setTotalLosses(double totalLosses) { this.totalLosses = totalLosses; }
    public void setBlackjacks(int blackjacks) { this.blackjacks = blackjacks; }
    public void setBusts(int busts) { this.busts = busts; }
}
