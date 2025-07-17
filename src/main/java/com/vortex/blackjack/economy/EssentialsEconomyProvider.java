package com.vortex.blackjack.economy;

import com.earth2me.essentials.api.Economy;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * EssentialsX economy provider implementation
 */
public class EssentialsEconomyProvider implements EconomyProvider {
    
    @Override
    public boolean hasEnough(UUID playerUuid, BigDecimal amount) {
        try {
            return Economy.hasEnough(playerUuid, amount);
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public boolean add(UUID playerUuid, BigDecimal amount) {
        try {
            Economy.add(playerUuid, amount);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public boolean subtract(UUID playerUuid, BigDecimal amount) {
        try {
            Economy.subtract(playerUuid, amount);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public BigDecimal getBalance(UUID playerUuid) {
        try {
            return Economy.getMoneyExact(playerUuid);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
    
    @Override
    public String getProviderName() {
        return "EssentialsX";
    }
}
