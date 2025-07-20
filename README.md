# 🃏 Blackjack Plugin

<div align="center">

**A premium physical blackjack table plugin for Minecraft servers**

*Create interactive card tables in your game world with realistic 3D card displays*

</div>

## ✨ Features

### 🎮 **Immersive Gameplay**
- **Physical Card Tables**: Place interactive blackjack tables anywhere in your world
- **3D Card Displays**: Realistic card animations with custom resource pack support
- **Multi-Player Support**: Up to 4 players per table with seamless turn management
- **Smart Game Logic**: Professional blackjack rules with dealer AI

### 💰 **Economy Integration**
- **Vault Compatible**: Works with any Vault-supported economy plugin (EssentialsX, EconomyAPI, CMI, etc.)
- **Flexible Betting**: Configurable bet limits and cooldown periods
- **Secure Transactions**: Anti-cheat measures and bet validation

### 📊 **Player Statistics**
- **Comprehensive Tracking**: Wins, losses, pushes, and streaks
- **Performance Metrics**: Track your best winning streaks and total earnings
- **Persistent Data**: Statistics saved across server restarts

### 🎨 **Visual & Audio**
- **Colorized Chat**: Suit-based card colors (Red ♥♦, Dark Gray ♠♣)
- **Particle Effects**: Customizable win/lose particle displays
- **Sound Effects**: Immersive audio feedback for game events
- **Compact Interface**: Clean, spam-free chat with essential information

## 🚀 Quick Start

### Installation
1. **Download** the latest `Blackjack.jar` from releases
2. **Place** the jar in your server's `plugins` folder
3. **Install** the required `@playing_cards` resource pack
4. **Restart** your server
5. **Configure** settings in `config.yml` (optional)

### Basic Usage
```bash
# Create a table (Admin)
/createtable

# Join and play
/join          # Join nearest table
/bet 100       # Place your bet
/start         # Start the game
/hit           # Take another card
/stand         # End your turn
/stats         # View your statistics
```

## 🔧 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `blackjack.admin` | Create and remove tables | `op` |
| `blackjack.play` | Join tables and play games | `true` |
| `blackjack.stats.others` | View other players' statistics | `op` |

## 🎯 Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/createtable` | Create a new blackjack table | `blackjack.admin` |
| `/removetable` | Remove the nearest table | `blackjack.admin` |
| `/join` | Join the nearest table | `blackjack.play` |
| `/leave` | Leave your current table | `blackjack.play` |
| `/start` | Start a new game | `blackjack.play` |
| `/hit` | Take another card | `blackjack.play` |
| `/stand` | End your turn | `blackjack.play` |
| `/bet <amount>` | Place or change your bet | `blackjack.play` |
| `/stats [player]` | View statistics (admins can check other players) | `blackjack.play` |
| `/bjversion` | Check plugin version and update status | `blackjack.admin` |

## 🔌 Plugin Integration

### Version Checking
- **Automatic Updates**: Admins are notified when joining if a new version is available
- **Manual Check**: Use `/bjversion` to check current version and update status
- **GitHub Integration**: Automatically checks releases from the official repository

### GSit Support
- **Automatic Detection**: Plugin automatically detects if GSit is installed
- **Enhanced Experience**: Players automatically sit when joining tables
- **Graceful Fallback**: Works perfectly without GSit if not available

## ⚙️ Configuration

### Core Settings
```yaml
# Betting Configuration
betting:
  min-bet: 10
  max-bet: 10000
  cooldown-ms: 2000

# Table Settings
table:
  max-players: 4
  max-join-distance: 10.0
  table-material: GREEN_TERRACOTTA
  chair-material: DARK_OAK_STAIRS

# Game Rules
game:
  hit-soft-17: false

# Display Settings
display:
  card:
    scale: 0.35
    spacing: 0.25
```

### Audio & Visual
```yaml
sounds:
  enabled: true
  card-deal:
    sound: BLOCK_WOODEN_BUTTON_CLICK_ON
    volume: 1.0
    pitch: 1.2

particles:
  enabled: true
  win:
    type: HAPPY_VILLAGER
  lose:
    type: ANGRY_VILLAGER
```

## 🎲 Gameplay Features

### 🃏 **Card Display**
- **Realistic 3D Cards**: Custom resource pack with detailed card textures
- **Smooth Animations**: Cards dealt with proper positioning and rotation
- **Hidden Dealer Card**: Traditional blackjack dealer hole card mechanics
- **Color-Coded Suits**: Red hearts/diamonds, dark gray spades/clubs

### 🎯 **Game Flow**
1. **Join Table**: Players approach and join available seats
2. **Place Bets**: Set your wager before the game begins
3. **Deal Cards**: Each player receives 2 cards, dealer gets 1 up + 1 down
4. **Player Turns**: Hit or stand to reach 21 without busting
5. **Dealer Play**: Dealer follows standard rules (hits on 16, stands on 17)
6. **Payouts**: Automatic economy integration with instant payouts

### 🏆 **Results**
- **Blackjack**: 3:2 payout (bet × 2.5)
- **Regular Win**: 2:1 payout (bet × 2)
- **Push (Tie)**: Bet returned
- **Loss**: Bet forfeited

## 📈 Statistics Tracking

Track your performance with detailed statistics:

```
┌─────────────────────────────────────┐
│              Your Stats             │
├─────────────────────────────────────┤
│ Hands Played: 127                   │
│ Wins: 58 (45.7%)                    │
│ Losses: 51 (40.2%)                  │
│ Pushes: 18 (14.2%)                  │
│ Current Streak: 3 wins              │
│ Best Streak: 7 wins                 │
│ Total Winnings: $2,450              │
└─────────────────────────────────────┘
```

## 🔧 Requirements

- **Minecraft Version**: 1.20 or higher
- **Server Software**: Spigot, Paper, or compatible forks
- **Java Version**: 21 or higher
- **Dependencies**: Vault + any economy plugin (EssentialsX, EconomyAPI, CMI, etc.)
- **Resource Pack**: [@playing_cards](https://modrinth.com/resourcepack/bjplayingcards)

## 🤝 Contributing

We welcome contributions! Please feel free to:
- Report bugs via GitHub Issues
- Suggest new features
- Submit pull requests
- Improve documentation

## 📞 Support

Need help? Reach out to us:

- **Discord**: `@vortexunwanted`
- **GitHub Issues**: [Report bugs/requests](https://github.com/DefectiveVortex/Blackjack/issues)

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---
