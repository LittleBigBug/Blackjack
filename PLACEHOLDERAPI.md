# Blackjack PlaceholderAPI Documentation

This document lists all available placeholders provided by the Blackjack plugin for PlaceholderAPI integration.

## Installation Requirements
- PlaceholderAPI plugin installed
- Blackjack plugin v2.3+

## Placeholder Categories

### Player Statistics (`%blackjack_stats_*%`)
Track comprehensive player performance metrics:

- `%blackjack_stats_hands_won%` - Total hands won by the player
- `%blackjack_stats_hands_lost%` - Total hands lost by the player  
- `%blackjack_stats_hands_pushed%` - Total hands pushed (tied) by the player
- `%blackjack_stats_total_hands%` - Total hands played by the player
- `%blackjack_stats_blackjacks%` - Total natural blackjacks achieved
- `%blackjack_stats_busts%` - Total times player went bust
- `%blackjack_stats_current_streak%` - Current winning/losing streak
- `%blackjack_stats_best_streak%` - Best winning streak achieved
- `%blackjack_stats_win_rate%` - Win rate as percentage (e.g., "65.2")
- `%blackjack_stats_win_rate_raw%` - Win rate as decimal (e.g., "0.652")
- `%blackjack_stats_total_winnings%` - Total money won/lost (raw number)
- `%blackjack_stats_total_winnings_formatted%` - Total winnings with formatting ($1.2K, $5.6M)
- `%blackjack_stats_has_played%` - "true" if player has played before, "false" otherwise

### Table Information (`%blackjack_table_*%`)
Get current table status and information:

- `%blackjack_table_at_table%` - "true" if player is at a table, "false" otherwise
- `%blackjack_table_players%` - Current number of players at table (0 if not at table)
- `%blackjack_table_max_players%` - Maximum players allowed per table
- `%blackjack_table_seats_available%` - Available seats at current table
- `%blackjack_table_is_full%` - "true" if table is full, "false" otherwise
- `%blackjack_table_game_in_progress%` - "true" if game is active, "false" otherwise
- `%blackjack_table_can_join%` - "true" if player can join a table, "false" otherwise
- `%blackjack_table_location_x%` - X coordinate of table center
- `%blackjack_table_location_y%` - Y coordinate of table center  
- `%blackjack_table_location_z%` - Z coordinate of table center
- `%blackjack_table_world%` - World name where table is located

### Game State (`%blackjack_game_*%`)
Real-time game information for active players:

- `%blackjack_game_hand_value%` - Current hand value (0 if no hand)
- `%blackjack_game_hand_cards%` - Number of cards in hand (0 if no hand)
- `%blackjack_game_is_turn%` - "true" if it's player's turn, "false" otherwise
- `%blackjack_game_is_finished%` - "true" if player has finished their turn
- `%blackjack_game_has_blackjack%` - "true" if player has natural blackjack
- `%blackjack_game_is_busted%` - "true" if player has busted (>21)
- `%blackjack_game_can_double_down%` - "true" if player can double down
- `%blackjack_game_has_doubled_down%` - "true" if player has doubled down
- `%blackjack_game_dealer_visible_value%` - Dealer's visible card value
- `%blackjack_game_dealer_card_count%` - Number of dealer cards visible

### Betting (`%blackjack_bet_*%`)
Current betting information:

- `%blackjack_bet_current%` - Current bet amount (raw number)
- `%blackjack_bet_current_formatted%` - Current bet with currency formatting
- `%blackjack_bet_has_bet%` - "true" if player has an active bet
- `%blackjack_bet_persistent%` - Saved bet amount for "Play Again"
- `%blackjack_bet_persistent_formatted%` - Persistent bet with formatting
- `%blackjack_bet_has_persistent%` - "true" if player has a saved bet
- `%blackjack_bet_min_bet%` - Server minimum bet (raw number)
- `%blackjack_bet_max_bet%` - Server maximum bet (raw number)
- `%blackjack_bet_min_bet_formatted%` - Minimum bet with formatting
- `%blackjack_bet_max_bet_formatted%` - Maximum bet with formatting

### Economy Integration (`%blackjack_economy_*%`)
Player economy and affordability checks:

- `%blackjack_economy_balance%` - Player's current balance (raw number)
- `%blackjack_economy_balance_formatted%` - Balance with smart formatting
- `%blackjack_economy_can_afford_min%` - "true" if can afford minimum bet
- `%blackjack_economy_can_afford_max%` - "true" if can afford maximum bet

## Usage Examples

### Scoreboard Integration
```yaml
# In your scoreboard plugin config
lines:
  - "&6&lBlackjack Stats"
  - "&fHands Won: &a%blackjack_stats_hands_won%"
  - "&fWin Rate: &b%blackjack_stats_win_rate%%"
  - "&fWinnings: &e%blackjack_stats_total_winnings_formatted%"
  - ""
  - "&fAt Table: %blackjack_table_at_table%"
  - "&fCurrent Bet: &6%blackjack_bet_current_formatted%"
```

### Chat Formatting
```yaml
# Player chat format showing game status
format: "%blackjack_table_at_table:&7[&cNot Playing&7] |&7[&aAt Table&7]% %player%: %message%"
```

### TAB List
```yaml
# Show player stats in tab list
tablist-format: 
  - "%player%"
  - "&7Blackjack: %blackjack_stats_win_rate%% WR"
```

### Conditional Messages
```yaml
# Welcome message based on experience
welcome-message: 
  condition: "%blackjack_stats_has_played%"
  true: "&aWelcome back! Your win rate: %blackjack_stats_win_rate%%"
  false: "&eWelcome to Blackjack! Try your luck at the tables!"
```

## Formatting Notes

- **Formatted money**: Automatically converts large numbers (e.g., 1500 → $1.5K, 2000000 → $2M)
- **Boolean values**: Always returns "true" or "false" as strings
- **Zero values**: Returns "0" for numeric placeholders when no data exists
- **Empty values**: Returns empty string for text placeholders when no data exists

## Performance Considerations

All placeholders are optimized for performance:
- Statistics are cached and updated in real-time
- Table lookups use efficient HashMap operations  
- Economy checks are cached when possible
- No database queries are performed for placeholder requests

## Troubleshooting

If placeholders show as `%blackjack_*%` instead of values:
1. Ensure PlaceholderAPI is installed and running
2. Verify Blackjack plugin v2.3+ is installed
3. Check console for PlaceholderAPI registration messages
4. Use `/papi parse [player] %blackjack_stats_total_hands%` to test
