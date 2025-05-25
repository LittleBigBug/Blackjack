# Blackjack

A physical blackjack table plugin for Minecraft servers that creates interactive card tables in the game world. Players can join tables, place bets, and play blackjack with realistic 3D card displays.

## Features

- Physical card tables in the game world
- Interactive 3D card displays using custom resource pack
- Multiple player support (up to 4 players per table)
- Integration with EssentialsX economy
- Player statistics tracking
- Configurable settings
- Particle and sound effects
- Betting system with cooldowns

## Requirements

- Spigot/Paper 1.21.3 or higher
- EssentialsX (for economy support)
- Custom resource pack (@playing_cards)

## Installation

1. Place the `Blackjack.jar` in your server's `plugins` folder
2. Install the required @playing_cards resource pack
3. Restart your server
4. Configure the plugin in `config.yml` if desired

## Commands

- `/createtable` - Create a new blackjack table (Admin)
- `/removetable` - Remove the nearest table (Admin)
- `/join` - Join the nearest table
- `/leave` - Leave your current table
- `/start` - Start a new game
- `/hit` - Take another card
- `/stand` - End your turn
- `/bet <amount>` - Place or change your bet
- `/stats` - View your statistics

## Permissions

- `blackjack.admin` - Access to admin commands (default: op)
- `blackjack.play` - Access to player commands (default: true)

## Configuration

The plugin is highly configurable through `config.yml`. You can adjust:

- Betting limits and cooldowns
- Table settings and materials
- Card display positions and scaling
- Sound and particle effects
- Custom messages

## Statistics

The plugin tracks player statistics including:
- Hands won/lost/pushed
- Current and best streaks
- Total winnings

## Support

For issues, bug reports, or feature requests, please create an issue on our GitHub repository.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details. 