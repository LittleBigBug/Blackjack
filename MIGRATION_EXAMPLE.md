# Configuration Migration System Example

## How the Migration System Works

### On Plugin Startup:
1. Plugin loads existing config.yml and messages.yml files
2. ConfigManager.migrateConfiguration() checks for missing/obsolete keys
3. Adds any missing configuration options with default values
4. Removes any obsolete configuration options that are no longer used
5. Saves updated files if changes were made
6. Logs the results to console

### Example Migration Scenario:

**Before Update (Old config.yml):**
```yaml
betting:
  min-bet: 10
  max-bet: 10000
  # Missing: cooldown-ms, quick-bets

table:
  max-players: 4
  # Missing: max-join-distance, table-material, chair-material

# Missing entire sections: game, display, sounds, particles, performance, game-settings
```

**After Migration (Updated config.yml):**
```yaml
betting:
  min-bet: 10                    # Preserved existing value
  max-bet: 10000                 # Preserved existing value  
  cooldown-ms: 2000              # Added with default value
  quick-bets:                    # Added with default values
    small: [10, 25, 50]
    medium: [100, 250, 500]
    large: [1000, 2500, 5000]

table:
  max-players: 4                 # Preserved existing value
  max-join-distance: 10.0        # Added with default value
  table-material: GREEN_TERRACOTTA # Added with default value
  chair-material: DARK_OAK_STAIRS  # Added with default value

game:                            # Added entire section
  hit-soft-17: false

display:                         # Added entire section
  card:
    scale: 0.35
    spacing: 0.25
    player:
      height: 1.05
    dealer:
      height: 1.2

sounds:                          # Added entire section
  enabled: true
  card-deal:
    sound: BLOCK_WOODEN_BUTTON_CLICK_ON
    volume: 1.0
    pitch: 1.2
  # ... (all other sound settings)

particles:                       # Added entire section
  enabled: true
  win:
    type: HAPPY_VILLAGER
    count: 20
    spread: 0.5
    height: 2.0
  # ... (all other particle settings)

performance:                     # Added entire section
  stats-save-interval: 3

game-settings:                   # Added entire section
  refund-on-leave: true
```

## Console Output Examples:

### No Migration Needed:
```
[INFO] Checking configuration files for updates...
[INFO] Configuration files are up to date.
```

### Migration Applied:
```
[INFO] Checking configuration files for updates...
[INFO] Configuration files updated with new settings!
```

### During Reload:
```
[Player] /bj reload
[INFO] Configuration files updated with new settings!
[Player receives message]: Configuration reloaded! (Updated with new settings)
```

## Benefits:

1. **Seamless Updates**: Users don't need to manually update config files
2. **Preserved Settings**: Existing user configurations are maintained
3. **Clean Migration**: Obsolete settings are automatically removed
4. **Version Agnostic**: Works for any version upgrade
5. **Manual Trigger**: Can also be triggered with `/bj reload`

## Adding New Settings:

To add a new configuration option in future versions:

1. Add the key-value pair to the `expectedKeys` array in `migrateMainConfig()`
2. Add the corresponding getter method in `ConfigManager`
3. The migration system will automatically add it to existing installations

## Removing Old Settings:

To remove obsolete settings in future versions:

1. Add the old key to the `obsoleteKeys` array in `migrateMainConfig()`
2. The migration system will automatically remove it from existing installations
