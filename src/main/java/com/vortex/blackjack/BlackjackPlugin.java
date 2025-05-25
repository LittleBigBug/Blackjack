package com.vortex.blackjack;

import com.earth2me.essentials.api.Economy;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class BlackjackPlugin extends JavaPlugin implements Listener {
   private Map<Location, BlackjackPlugin.BlackjackTable> tables;
   private Map<Player, BlackjackPlugin.BlackjackTable> playerTables;
   private FileConfiguration config;
   private Map<Player, Integer> playerBets = new HashMap<>();
   private final Map<Player, Long> lastBetTime = new HashMap<>();
   private final Object tableLock = new Object();
   private final Object playerLock = new Object();
   private final Set<Location> pendingEconomyTransactions = new HashSet<>();
   private Map<UUID, BlackjackPlugin.PlayerStats> playerStats = new HashMap<>();
   private final File statsFile = new File(this.getDataFolder(), "stats.yml");

   private int getMinBet() {
      return config.getInt("betting.min-bet", 10);
   }

   private int getMaxBet() {
      return config.getInt("betting.max-bet", 10000);
   }

   private long getBetCooldown() {
      return config.getLong("betting.cooldown-ms", 2000L);
   }

   private double getMaxJoinDistance() {
      return config.getDouble("table.max-join-distance", 10.0);
   }

   private String getMessage(String path) {
      return ChatColor.translateAlternateColorCodes('&', config.getString("messages." + path, ""));
   }

   private String formatMessage(String path, Object... args) {
      String message = getMessage(path);
      for (int i = 0; i < args.length; i += 2) {
         message = message.replace("%" + args[i] + "%", String.valueOf(args[i + 1]));
      }
      return message;
   }

   public void onEnable() {
      this.tables = new HashMap<>();
      this.playerTables = new HashMap<>();
      this.saveDefaultConfig();
      this.config = this.getConfig();
      this.getCommand("blackjack").setExecutor(this);
      this.getServer().getPluginManager().registerEvents(this, this);
      if (this.config.contains("tables")) {
         for (String worldName : this.config.getConfigurationSection("tables").getKeys(false)) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
               for (String locString : this.config.getConfigurationSection("tables." + worldName).getKeys(false)) {
                  String[] parts = locString.split("_");
                  int x = Integer.parseInt(parts[0]);
                  int y = Integer.parseInt(parts[1]);
                  int z = Integer.parseInt(parts[2]);
                  Location loc = new Location(world, x, y, z);
                  this.createTable(loc);
               }
            }
         }
      }

      this.getLogger().info("Blackjack plugin enabled!");
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      this.lastBetTime.remove(player);
      if (this.playerTables.containsKey(player)) {
         BlackjackPlugin.BlackjackTable table = this.playerTables.get(player);
         table.removePlayer(player);
      }

      this.savePlayerStats();
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage("This command can only be used by players!");
         return true;
      } else if (args.length == 0) {
         this.sendHelp(player);
         return true;
      } else {
         String var6 = args[0].toLowerCase();
         switch (var6) {
            case "createtable":
               if (!player.hasPermission("blackjack.admin")) {
                  player.sendMessage(ChatColor.RED + "You don't have permission to create tables!");
                  return true;
               }

               this.createTable(player.getLocation());
               player.sendMessage(ChatColor.GREEN + "Blackjack table created!");
               break;
            case "join":
               if (this.playerTables.containsKey(player)) {
                  player.sendMessage(ChatColor.RED + "You are already at a table! Use /leave to leave your current table.");
                  return true;
               }

               BlackjackPlugin.BlackjackTable nearestTable = this.findNearestTable(player.getLocation());
               if (nearestTable != null) {
                  nearestTable.addPlayer(player);
               } else {
                  player.sendMessage(ChatColor.RED + "No table found nearby!");
               }
               break;
            case "start":
               if (this.playerTables.containsKey(player)) {
                  this.playerTables.get(player).startGame();
               } else {
                  player.sendMessage(ChatColor.RED + "You're not at a table! Use /join near a table to play.");
               }
               break;
            case "hit":
               if (this.playerTables.containsKey(player)) {
                  this.playerTables.get(player).hit(player);
               } else {
                  player.sendMessage(ChatColor.RED + "You're not at a table! Use /join near a table to play.");
               }
               break;
            case "stand":
               if (this.playerTables.containsKey(player)) {
                  this.playerTables.get(player).stand(player);
               } else {
                  player.sendMessage(ChatColor.RED + "You're not at a table! Use /join near a table to play.");
               }
               break;
            case "leave":
               if (this.playerTables.containsKey(player)) {
                  this.playerTables.get(player).removePlayer(player);
                  this.playerBets.put(player, 0);
               } else {
                  player.sendMessage(ChatColor.RED + "You're not at a table! Use /join near a table to play.");
               }
               break;
            case "bet":
               if (this.playerTables.containsKey(player)) {
                  BlackjackPlugin.BlackjackTable table = this.playerTables.get(player);
                  long currentTime = System.currentTimeMillis();
                  long lastBet = this.lastBetTime.getOrDefault(player, 0L);
                  if (currentTime - lastBet < getBetCooldown()) {
                     player.sendMessage(ChatColor.RED + "Please wait a moment before changing your bet again.");
                     return true;
                  }

                  if (table.gameInProgress) {
                     player.sendMessage(ChatColor.RED + "Cannot change bet during an active game!");
                     return true;
                  }

                  if (args.length < 2) {
                     player.sendMessage(ChatColor.RED + "Usage: /bet <amount>");
                     return true;
                  }

                  int betAmount;
                  try {
                     betAmount = Integer.parseInt(args[1]);
                  } catch (NumberFormatException var28) {
                     player.sendMessage(ChatColor.RED + "Invalid amount.");
                     return true;
                  }

                  if (!this.validateBet(player, betAmount)) {
                     return true;
                  }

                  synchronized (this.playerLock) {
                     boolean var10000;
                     try {
                        int previousBet = this.playerBets.getOrDefault(player, 0);
                        int additionalNeeded = betAmount - previousBet;
                        if (additionalNeeded > 0) {
                           if (!Economy.hasEnough(player.getUniqueId(), BigDecimal.valueOf(additionalNeeded))) {
                              player.sendMessage(ChatColor.RED + "You don't have enough money to bet $" + betAmount);
                              var10000 = true;
                           } else {
                              this.pendingEconomyTransactions.add(player.getLocation());

                              try {
                                 Economy.subtract(player.getUniqueId(), BigDecimal.valueOf(additionalNeeded));
                                 this.playerBets.put(player, betAmount);
                                 this.lastBetTime.put(player, currentTime);
                                 if (previousBet > 0) {
                                    player.sendMessage(ChatColor.GREEN + "You have increased your bet to $" + betAmount + "!");
                                 } else {
                                    player.sendMessage(ChatColor.GREEN + "You have placed a bet of $" + betAmount + "!");
                                 }
                                 break;
                              } finally {
                                 this.pendingEconomyTransactions.remove(player.getLocation());
                              }
                           }
                        } else {
                           if (additionalNeeded < 0) {
                              int refundAmount = -additionalNeeded;
                              Economy.add(player.getUniqueId(), BigDecimal.valueOf(refundAmount));
                              this.playerBets.put(player, betAmount);
                              this.lastBetTime.put(player, currentTime);
                              player.sendMessage(ChatColor.GREEN + "You have reduced your bet to $" + betAmount + " and been refunded $" + refundAmount + "!");
                           }
                           break;
                        }
                     } catch (Exception var29) {
                        player.sendMessage(ChatColor.RED + "An error occurred while processing your bet. Contact a staff member!");
                        this.getLogger().severe("Economy error for player " + player.getUniqueId() + ": " + var29.getMessage());
                        break;
                     }

                     return var10000;
                  }
               } else {
                  player.sendMessage(ChatColor.RED + "You're not at a table!");
                  break;
               }
            case "removetable":
               if (!player.hasPermission("blackjack.admin")) {
                  player.sendMessage(ChatColor.RED + "You don't have permission to remove tables!");
                  return true;
               }

               Location playerLoc = player.getLocation();
               BlackjackPlugin.BlackjackTable tableToRemove = this.findNearestTable(playerLoc);
               if (tableToRemove != null) {
                  Location tableLoc = tableToRemove.centerLoc;
                  this.tables.remove(tableLoc);
                  String worldName = tableLoc.getWorld().getName();
                  int centerX = tableLoc.getBlockX();
                  int centerY = tableLoc.getBlockY();
                  int centerZ = tableLoc.getBlockZ();
                  if (this.config.contains("tables." + worldName)) {
                     ConfigurationSection tablesSection = this.config.getConfigurationSection("tables." + worldName);

                     for (String key : tablesSection.getKeys(false)) {
                        String[] parts = key.split("_");
                        if (parts.length == 3) {
                           int configX = Integer.parseInt(parts[0]);
                           int configY = Integer.parseInt(parts[1]);
                           int configZ = Integer.parseInt(parts[2]);
                           if (configX == centerX && configY == centerY && configZ == centerZ) {
                              tablesSection.set(key, null);
                              this.saveConfig();
                              this.getLogger().info("Table at " + tableLoc + " removed from config.");
                              break;
                           }
                        }
                     }
                  }

                  tableToRemove.clearDisplays();
                  player.sendMessage(ChatColor.GREEN + "Nearest table removed!");
               } else {
                  player.sendMessage(ChatColor.RED + "No table found within the range!");
               }
               break;
            case "stats":
               BlackjackPlugin.PlayerStats stats = this.playerStats.get(player.getUniqueId());
               if (stats == null) {
                  this.loadPlayerStats(player);
                  stats = this.playerStats.get(player.getUniqueId());
               }

               player.sendMessage(ChatColor.GOLD + "=== Blackjack Statistics ===");
               player.sendMessage(ChatColor.YELLOW + "Hands Won: " + stats.handsWon);
               player.sendMessage(ChatColor.YELLOW + "Hands Lost: " + stats.handsLost);
               player.sendMessage(ChatColor.YELLOW + "Hands Pushed: " + stats.handsPushed);
               player.sendMessage(ChatColor.YELLOW + "Best Streak: " + stats.bestStreak);
               player.sendMessage(ChatColor.YELLOW + "Current Streak: " + stats.currentStreak);
               player.sendMessage(ChatColor.YELLOW + "Total Winnings: $" + String.format("%.2f", stats.totalWinnings));
               break;
            default:
               this.sendHelp(player);
         }

         return true;
      }
   }

   private void loadPlayerStats(Player player) {
      if (!this.playerStats.containsKey(player.getUniqueId())) {
         FileConfiguration statsConfig = YamlConfiguration.loadConfiguration(this.statsFile);
         BlackjackPlugin.PlayerStats stats = new BlackjackPlugin.PlayerStats();
         String path = "players." + player.getUniqueId() + ".";
         stats.handsWon = statsConfig.getInt(path + "handsWon", 0);
         stats.handsLost = statsConfig.getInt(path + "handsLost", 0);
         stats.handsPushed = statsConfig.getInt(path + "handsPushed", 0);
         stats.currentStreak = statsConfig.getInt(path + "currentStreak", 0);
         stats.bestStreak = statsConfig.getInt(path + "bestStreak", 0);
         stats.totalWinnings = statsConfig.getDouble(path + "totalWinnings", 0.0);
         this.playerStats.put(player.getUniqueId(), stats);
      }
   }

   private void savePlayerStats() {
      if (!this.playerStats.isEmpty()) {
         if (!this.statsFile.getParentFile().exists()) {
            this.statsFile.getParentFile().mkdirs();
         }

         FileConfiguration statsConfig = YamlConfiguration.loadConfiguration(this.statsFile);

         for (Entry<UUID, BlackjackPlugin.PlayerStats> entry : this.playerStats.entrySet()) {
            String path = "players." + entry.getKey() + ".";
            BlackjackPlugin.PlayerStats stats = entry.getValue();
            statsConfig.set(path + "handsWon", stats.handsWon);
            statsConfig.set(path + "handsLost", stats.handsLost);
            statsConfig.set(path + "handsPushed", stats.handsPushed);
            statsConfig.set(path + "currentStreak", stats.currentStreak);
            statsConfig.set(path + "bestStreak", stats.bestStreak);
            statsConfig.set(path + "totalWinnings", stats.totalWinnings);
         }

         try {
            statsConfig.save(this.statsFile);
         } catch (IOException var6) {
            this.getLogger().severe("Could not save stats: " + var6.getMessage());
            var6.printStackTrace();
         }
      }
   }

   private boolean validateBet(Player player, int amount) {
      if (amount < getMinBet()) {
         player.sendMessage(formatMessage("invalid-bet", "min_bet", getMinBet(), "max_bet", getMaxBet()));
         return false;
      } else if (amount > getMaxBet()) {
         player.sendMessage(formatMessage("invalid-bet", "min_bet", getMinBet(), "max_bet", getMaxBet()));
         return false;
      } else {
         return true;
      }
   }

   private void sendHelp(Player player) {
      player.sendMessage(formatMessage("prefix") + ChatColor.RESET + "Available Commands:");
      if (player.hasPermission("blackjack.admin")) {
         player.sendMessage(ChatColor.YELLOW + "/createtable " + ChatColor.GRAY + "- Create a new blackjack table");
         player.sendMessage(ChatColor.YELLOW + "/removetable " + ChatColor.GRAY + "- Remove the nearest table");
      }
      player.sendMessage(ChatColor.YELLOW + "/join " + ChatColor.GRAY + "- Join the nearest table");
      player.sendMessage(ChatColor.YELLOW + "/leave " + ChatColor.GRAY + "- Leave your current table");
      player.sendMessage(ChatColor.YELLOW + "/start " + ChatColor.GRAY + "- Start a new game");
      player.sendMessage(ChatColor.YELLOW + "/hit " + ChatColor.GRAY + "- Take another card");
      player.sendMessage(ChatColor.YELLOW + "/stand " + ChatColor.GRAY + "- End your turn");
      player.sendMessage(ChatColor.YELLOW + "/bet <amount> " + ChatColor.GRAY + "- Place or change your bet");
      player.sendMessage(ChatColor.YELLOW + "/stats " + ChatColor.GRAY + "- View your statistics");
   }

   private BlackjackPlugin.BlackjackTable findNearestTable(Location playerLoc) {
      synchronized (this.tableLock) {
         double closestDistance = 10.0;
         BlackjackPlugin.BlackjackTable closestTable = null;

         for (Entry<Location, BlackjackPlugin.BlackjackTable> entry : this.tables.entrySet()) {
            if (entry.getKey().getWorld().equals(playerLoc.getWorld())) {
               double distance = entry.getKey().distance(playerLoc);
               if (distance < closestDistance) {
                  closestDistance = distance;
                  closestTable = entry.getValue();
               }
            }
         }

         return closestTable;
      }
   }

   public void createTable(Location centerLoc) {
      synchronized (this.tableLock) {
         for (Location loc : this.tables.keySet()) {
            if (loc.equals(centerLoc)) {
               return;
            }
         }

         World world = centerLoc.getWorld();
         if (world != null) {
            int centerX = centerLoc.getBlockX();
            int centerY = centerLoc.getBlockY();
            int centerZ = centerLoc.getBlockZ();
            world.getChunkAt(centerLoc).load();

            Material tableMaterial = Material.valueOf(config.getString("table.table-material", "GREEN_TERRACOTTA"));
            Material chairMaterial = Material.valueOf(config.getString("table.chair-material", "DARK_OAK_STAIRS"));

            for (int x = -1; x <= 1; x++) {
               for (int z = -1; z <= 1; z++) {
                  if (x != 0 || z != 0) {
                     Location blockLoc = new Location(world, centerX + x, centerY, centerZ + z);
                     blockLoc.getBlock().setType(tableMaterial);
                  }
               }
            }

            this.placeStair(world, centerX + 2, centerY, centerZ, BlockFace.EAST, chairMaterial);
            this.placeStair(world, centerX - 2, centerY, centerZ, BlockFace.WEST, chairMaterial);
            this.placeStair(world, centerX, centerY, centerZ + 2, BlockFace.SOUTH, chairMaterial);
            this.placeStair(world, centerX, centerY, centerZ - 2, BlockFace.NORTH, chairMaterial);
            String tablePath = "tables." + centerLoc.getWorld().getName() + "." + centerX + "_" + centerY + "_" + centerZ;
            this.config.set(tablePath, true);
            this.saveConfig();
            BlackjackPlugin.BlackjackTable table = new BlackjackPlugin.BlackjackTable(this, centerLoc);
            this.tables.put(centerLoc, table);
         }
      }
   }

   private void placeStair(World world, int x, int y, int z, BlockFace facing, Material material) {
      Block block = world.getBlockAt(x, y, z);
      block.setType(material);
      Stairs stairs = (Stairs)block.getBlockData();
      stairs.setFacing(facing);
      block.setBlockData(stairs);
   }

   public void onDisable() {
      synchronized (this.playerLock) {
         for (Entry<Player, BlackjackPlugin.BlackjackTable> entry : this.playerTables.entrySet()) {
            Player player = entry.getKey();
            BlackjackPlugin.BlackjackTable table = entry.getValue();
            if (!table.gameInProgress) {
               int betAmount = this.playerBets.getOrDefault(player, 0);
               if (betAmount > 0) {
                  try {
                     Economy.add(player.getUniqueId(), BigDecimal.valueOf(betAmount));
                     player.sendMessage(ChatColor.GREEN + "Your bet was refunded due to server shutdown.");
                  } catch (Exception var9) {
                     this.getLogger().severe("Failed to refund bet for " + player.getUniqueId() + " during shutdown: " + var9.getMessage());
                  }
               }
            }
         }
      }

      for (BlackjackPlugin.BlackjackTable table : this.tables.values()) {
         table.clearDisplays();
         table.clearAllDisplays();
      }

      this.tables.clear();
      this.playerTables.clear();
      this.playerBets.clear();
      this.lastBetTime.clear();
      this.pendingEconomyTransactions.clear();
      this.savePlayerStats();
      this.getLogger().info("Blackjack plugin disabled!");
   }

   private class BlackjackTable {
      private final Location centerLoc;
      private final List<Player> players;
      private final Map<Player, List<ItemDisplay>> playerCardDisplays;
      private final BlackjackPlugin plugin;
      private boolean gameInProgress;
      private Player currentPlayer;
      private List<BlackjackPlugin.Card> dealerHand;
      private Map<Player, List<BlackjackPlugin.Card>> playerHands;
      private BlackjackPlugin.Deck deck;
      private Set<Player> finishedPlayers;
      private final Map<Player, Integer> playerSeats;
      private final Map<Player, List<ItemDisplay>> playerDealerDisplays;

      public BlackjackTable(BlackjackPlugin plugin, Location centerLoc) {
         this.plugin = plugin;
         this.centerLoc = centerLoc;
         this.players = new ArrayList<>();
         this.playerCardDisplays = new HashMap<>();
         this.gameInProgress = false;
         this.dealerHand = new ArrayList<>();
         this.playerHands = new HashMap<>();
         this.deck = BlackjackPlugin.this.new Deck();
         this.finishedPlayers = new HashSet<>();
         this.playerSeats = new HashMap<>();
         this.playerDealerDisplays = new HashMap<>();
      }

      private void playCardSound(Location loc) {
         if (config.getBoolean("sounds.enabled", true)) {
            float volume = (float) config.getDouble("sounds.card-deal.volume", 1.0);
            float pitch = (float) config.getDouble("sounds.card-deal.pitch", 1.2);
            loc.getWorld().playSound(loc, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, volume, pitch);
         }
      }

      private void playWinSound(Player player) {
         if (config.getBoolean("sounds.enabled", true)) {
            float volume = (float) config.getDouble("sounds.win.volume", 1.0);
            float pitch = (float) config.getDouble("sounds.win.pitch", 1.0);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, volume, pitch);
         }
         
         if (config.getBoolean("particles.enabled", true)) {
            String particleType = config.getString("particles.win.type", "HAPPY_VILLAGER");
            int count = config.getInt("particles.win.count", 20);
            double spread = config.getDouble("particles.win.spread", 0.5);
            double height = config.getDouble("particles.win.height", 2.0);
            player.spawnParticle(Particle.valueOf(particleType), 
               player.getLocation().add(0.0, height, 0.0), 
               count, spread, spread, spread);
         }
      }

      private void playLoseSound(Player player) {
         if (config.getBoolean("sounds.enabled", true)) {
            float volume = (float) config.getDouble("sounds.lose.volume", 1.0);
            float pitch = (float) config.getDouble("sounds.lose.pitch", 1.0);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, volume, pitch);
         }
         
         if (config.getBoolean("particles.enabled", true)) {
            String particleType = config.getString("particles.lose.type", "ANGRY_VILLAGER");
            int count = config.getInt("particles.lose.count", 10);
            double spread = config.getDouble("particles.lose.spread", 0.5);
            double height = config.getDouble("particles.lose.height", 2.0);
            player.spawnParticle(Particle.valueOf(particleType), 
               player.getLocation().add(0.0, height, 0.0), 
               count, spread, spread, spread);
         }
      }

      public void addPlayer(Player player) {
         if (this.players.contains(player)) {
            player.sendMessage(ChatColor.RED + "You are already at a table! Use /leave to leave your current table.");
         } else {
            synchronized (this.plugin.playerLock) {
               if (!this.players.contains(player) && !this.plugin.playerTables.containsKey(player)) {
                  if (this.players.size() >= 4) {
                     player.sendMessage(ChatColor.RED + "This table is full!");
                  } else if (this.gameInProgress) {
                     player.sendMessage(ChatColor.RED + "Game in progress! Wait for the next round.");
                  } else if (player.getLocation().distance(this.centerLoc) > getMaxJoinDistance()) {
                     player.sendMessage(ChatColor.RED + "You are too far from the table to join!");
                  } else {
                     int seatNumber = this.getNextAvailableSeatNumber();
                     if (seatNumber == -1) {
                        player.sendMessage(ChatColor.RED + "No available seats!");
                     } else {
                        try {
                           this.plugin.loadPlayerStats(player);
                           this.players.add(player);
                           this.playerSeats.put(player, seatNumber);
                           this.playerHands.put(player, new ArrayList<>());
                           this.playerCardDisplays.put(player, new ArrayList<>());
                           this.plugin.playerTables.put(player, this);
                           Location seatLoc = this.getSeatLocation(seatNumber);
                           if (seatLoc != null) {
                              player.teleport(seatLoc);
                           }

                           this.broadcastToTable(ChatColor.GREEN + player.getName() + " joined the table!");
                        } catch (Exception var6) {
                           this.players.remove(player);
                           this.playerSeats.remove(player);
                           this.playerHands.remove(player);
                           this.playerCardDisplays.remove(player);
                           this.plugin.playerTables.remove(player);
                           player.sendMessage(ChatColor.RED + "Error joining table. Please try again.");
                           this.plugin.getLogger().severe("Error adding player to table: " + var6.getMessage());
                        }
                     }
                  }
               } else {
                  player.sendMessage(ChatColor.RED + "You are already at a table! Use /leave to leave your current table.");
               }
            }
         }
      }

      public void removePlayer(Player player) {
         synchronized (this.plugin.playerLock) {
            if (!this.gameInProgress && this.plugin.playerBets.containsKey(player)) {
               int betAmount = this.plugin.playerBets.get(player);

               try {
                  Economy.add(player.getUniqueId(), BigDecimal.valueOf(betAmount));
                  player.sendMessage(ChatColor.GREEN + "Your bet of $" + betAmount + " has been refunded.");
                  this.plugin.playerBets.put(player, 0);
               } catch (Exception var6) {
                  player.sendMessage(ChatColor.RED + "An error occurred while refunding your bet. Contact a staff member!");
                  var6.printStackTrace();
               }

               this.plugin.savePlayerStats();
               this.plugin.playerBets.remove(player);
            }

            this.players.remove(player);
            this.playerSeats.remove(player);
            this.plugin.playerTables.remove(player);
            this.playerHands.remove(player);
            if (this.playerCardDisplays.containsKey(player)) {
               for (ItemDisplay display : this.playerCardDisplays.get(player)) {
                  display.remove();
               }

               this.playerCardDisplays.remove(player);
            }

            if (this.playerDealerDisplays.containsKey(player)) {
               for (ItemDisplay display : this.playerDealerDisplays.get(player)) {
                  display.remove();
               }

               this.playerDealerDisplays.remove(player);
            }

            if (this.players.isEmpty()) {
               this.endGame();
               this.broadcastToTable(ChatColor.RED + "The game has ended because the last player left the table.");
            } else if (this.gameInProgress && this.currentPlayer != null && this.currentPlayer.equals(player)) {
               this.nextTurn();
               this.broadcastToTable(ChatColor.RED + player.getName() + " left during their turn.");
            } else {
               this.broadcastToTable(ChatColor.RED + player.getName() + " left the table.");
            }
         }
      }

      private int getNextAvailableSeatNumber() {
         Set<Integer> takenSeats = new HashSet<>(this.playerSeats.values());

         for (int i = 0; i < 4; i++) {
            if (!takenSeats.contains(i)) {
               return i;
            }
         }

         return -1;
      }

      private Location getSeatLocation(int seatNumber) {
         switch (seatNumber) {
            case 0:
               return this.centerLoc.clone().add(2.0, 0.0, 0.0);
            case 1:
               return this.centerLoc.clone().add(0.0, 0.0, 2.0);
            case 2:
               return this.centerLoc.clone().add(-2.0, 0.0, 0.0);
            case 3:
               return this.centerLoc.clone().add(0.0, 0.0, -2.0);
            default:
               return null;
         }
      }

      public void startGame() {
         synchronized (this.plugin.playerLock) {
            if (this.gameInProgress) {
               this.broadcastToTable(ChatColor.RED + "A game is already in progress!");
            } else if (this.players.isEmpty()) {
               this.broadcastToTable(ChatColor.RED + "Cannot start the game with no players!");
            } else {
               List<Player> playersWithoutBets = new ArrayList<>();

               for (Player player : this.players) {
                  int betAmount = this.plugin.playerBets.getOrDefault(player, 0);
                  if (betAmount < 10) {
                     playersWithoutBets.add(player);
                  }
               }

               if (!playersWithoutBets.isEmpty()) {
                  StringBuilder message = new StringBuilder(ChatColor.YELLOW + "The following players need to place a bet (/bet <amount>): ");

                  for (Player playerx : playersWithoutBets) {
                     message.append(playerx.getName()).append(", ");
                  }

                  message.setLength(message.length() - 2);
                  this.broadcastToTable(message.toString());
               } else {
                  this.gameInProgress = true;
                  this.deck = BlackjackPlugin.this.new Deck();
                  this.clearDisplays();
                  this.finishedPlayers.clear();

                  for (Player playerx : this.players) {
                     List<BlackjackPlugin.Card> hand = new ArrayList<>();
                     hand.add(this.deck.drawCard());
                     hand.add(this.deck.drawCard());
                     this.playerHands.put(playerx, hand);
                     this.updateCardDisplays(playerx, hand);
                  }

                  this.dealerHand = new ArrayList<>();
                  this.dealerHand.add(this.deck.drawCard());
                  this.dealerHand.add(this.deck.drawCard());
                  this.updateDealerDisplays();
                  this.currentPlayer = this.players.get(0);
                  this.broadcastToTable(ChatColor.GREEN + "Game started! " + this.currentPlayer.getName() + "'s turn.");
               }
            }
         }
      }

      public void hit(Player player) {
         if (this.gameInProgress && player == this.currentPlayer) {
            List<BlackjackPlugin.Card> hand = this.playerHands.get(player);
            BlackjackPlugin.Card newCard = this.deck.drawCard();
            hand.add(newCard);
            Location cardLoc = player.getLocation().add(0.0, 1.0, 0.0);
            this.playCardSound(cardLoc);
            this.updateCardDisplays(player, hand);
            int value = this.calculateHand(hand);
            player.sendMessage(ChatColor.YELLOW + "Your hand value: " + value);
            if (value > 21) {
               this.finishedPlayers.add(player);
               this.broadcastToTable(ChatColor.RED + player.getName() + " busts!");
               this.playLoseSound(player);
               this.nextTurn();
            } else if (value == 21) {
               this.finishedPlayers.add(player);
               this.broadcastToTable(ChatColor.GREEN + player.getName() + " stands at 21!");
               this.playWinSound(player);
               this.nextTurn();
            }
         }
      }

      public void stand(Player player) {
         if (this.gameInProgress && player == this.currentPlayer) {
            this.finishedPlayers.add(player);
            this.nextTurn();
         }
      }

      private void nextTurn() {
         if (this.finishedPlayers.size() >= this.players.size()) {
            this.endGame();
         } else {
            int currentIndex = this.players.indexOf(this.currentPlayer);

            do {
               currentIndex = (currentIndex + 1) % this.players.size();
               this.currentPlayer = this.players.get(currentIndex);
            } while (this.finishedPlayers.contains(this.currentPlayer) && this.players.contains(this.currentPlayer));

            if (this.currentPlayer != null) {
               this.broadcastToTable(ChatColor.GREEN + this.currentPlayer.getName() + "'s turn!");
            }
         }
      }

      private void endGame() {
         synchronized (this.plugin.playerLock) {
            boolean anyValidPlayers = this.players.stream().anyMatch(p -> this.calculateHand(this.playerHands.get(p)) <= 21);
            if (anyValidPlayers) {
               while (this.calculateHand(this.dealerHand) < 17) {
                  this.dealerHand.add(this.deck.drawCard());
               }
            }

            this.updateDealerDisplays();
            int dealerValue = this.calculateHand(this.dealerHand);
            this.broadcastToTable(ChatColor.YELLOW + "Dealer's final hand value: " + dealerValue);

            for (Player player : new ArrayList<>(this.players)) {
               try {
                  if (!player.isOnline()) {
                     this.removePlayer(player);
                  } else {
                     this.handlePayout(player, dealerValue);
                  }
               } catch (Exception var8) {
                  BlackjackPlugin.this.getLogger().severe("Error handling payout for " + player.getName() + ": " + var8.getMessage());
               }
            }

            this.gameInProgress = false;
            this.clearAllDisplays();
            if (this.players.isEmpty()) {
               this.cleanup();
            } else {
               this.broadcastToTable(ChatColor.GREEN + "Game ended! Use /start for a new game.");
            }
         }
      }

      private void handlePayout(Player player, int dealerValue) {
         synchronized (this.plugin.playerLock) {
            try {
               BlackjackPlugin.PlayerStats stats = this.plugin.playerStats.get(player.getUniqueId());
               int betAmount = this.plugin.playerBets.getOrDefault(player, 0);
               if (betAmount <= 0) {
                  return;
               }

               this.plugin.pendingEconomyTransactions.add(player.getLocation());

               try {
                  int playerValue = this.calculateHand(this.playerHands.get(player));
                  if (playerValue > 21) {
                     this.broadcastToTable(ChatColor.RED + player.getName() + " busts and loses their bet of $" + betAmount);
                     stats.incrementLosses();
                     stats.addWinnings(-betAmount);
                     this.playLoseSound(player);
                  } else if (dealerValue > 21) {
                     this.broadcastToTable(ChatColor.GREEN + player.getName() + " wins and gets back $" + betAmount * 2 + "!");
                     Economy.add(player.getUniqueId(), BigDecimal.valueOf(betAmount * 2));
                     stats.incrementWins();
                     stats.addWinnings(betAmount);
                     this.playWinSound(player);
                  } else if (playerValue > dealerValue) {
                     this.broadcastToTable(ChatColor.GREEN + player.getName() + " wins and gets back $" + betAmount * 2 + "!");
                     Economy.add(player.getUniqueId(), BigDecimal.valueOf(betAmount * 2));
                     stats.incrementWins();
                     stats.addWinnings(betAmount);
                     this.playWinSound(player);
                  } else if (playerValue < dealerValue) {
                     this.broadcastToTable(ChatColor.RED + player.getName() + " loses their bet of $" + betAmount);
                     stats.incrementLosses();
                     stats.addWinnings(-betAmount);
                     this.playLoseSound(player);
                  } else {
                     this.broadcastToTable(ChatColor.YELLOW + player.getName() + " pushes and gets their bet of $" + betAmount + " back.");
                     Economy.add(player.getUniqueId(), BigDecimal.valueOf(betAmount));
                     stats.incrementPushes();
                     player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
                  }
               } finally {
                  this.plugin.pendingEconomyTransactions.remove(player.getLocation());
               }

               this.plugin.savePlayerStats();
               this.plugin.playerBets.put(player, 0);
            } catch (Exception var13) {
               player.sendMessage(ChatColor.RED + "An error occurred processing your payout. Contact a staff member!");
               BlackjackPlugin.this.getLogger().severe("Payout error for " + player.getName() + ": " + var13.getMessage());
            }
         }
      }

      private void cleanup() {
         synchronized (this.plugin.tableLock) {
            this.clearAllDisplays();
         }
      }

      private void clearAllDisplays() {
         synchronized (this.plugin.tableLock) {
            for (List<ItemDisplay> displays : this.playerCardDisplays.values()) {
               for (ItemDisplay display : displays) {
                  if (display != null && !display.isDead()) {
                     display.remove();
                  }
               }

               displays.clear();
            }

            this.playerCardDisplays.clear();

            for (List<ItemDisplay> displays : this.playerDealerDisplays.values()) {
               for (ItemDisplay displayx : displays) {
                  if (displayx != null && !displayx.isDead()) {
                     displayx.remove();
                  }
               }

               displays.clear();
            }

            this.playerDealerDisplays.clear();
         }
      }

      private void updateCardDisplays(Player player, List<BlackjackPlugin.Card> hand) {
         int seatNumber = this.playerSeats.get(player);
         Location baseDisplayLoc = this.getSeatLocation(seatNumber);
         if (this.playerCardDisplays.containsKey(player)) {
            for (ItemDisplay display : this.playerCardDisplays.get(player)) {
               display.remove();
            }

            this.playerCardDisplays.get(player).clear();
         }

         this.playerCardDisplays.putIfAbsent(player, new ArrayList<>());
         double cardSpacing = config.getDouble("display.card.spacing", 0.25);
         double playerHeight = config.getDouble("display.card.player.height", 1.05);
         double distanceFromPlayer = config.getDouble("display.card.player.distance", 1.0);

         for (int i = 0; i < hand.size(); i++) {
            BlackjackPlugin.Card card = hand.get(i);
            Location spawnLoc = baseDisplayLoc.clone();
            ItemDisplay display = this.createCardDisplay(spawnLoc, card, false, seatNumber);
            Vector3f translation = new Vector3f();
            float xOffset = 0.0F;
            float zOffset = 0.0F;
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
            this.playerCardDisplays.get(player).add(display);
         }

         int handValue = this.calculateHand(hand);
         player.sendMessage(ChatColor.YELLOW + "Your hand: " + hand.toString() + " | Value: " + handValue);
      }

      private void updateDealerDisplays() {
         for (Player player : this.players) {
            if (this.playerDealerDisplays.containsKey(player)) {
               for (ItemDisplay display : this.playerDealerDisplays.get(player)) {
                  display.remove();
               }

               this.playerDealerDisplays.get(player).clear();
            }
         }

         for (Player playerx : this.players) {
            this.playerDealerDisplays.putIfAbsent(playerx, new ArrayList<>());
            List<ItemDisplay> dealerDisplays = new ArrayList<>();
            int seatNumber = this.playerSeats.get(playerx);
            Location baseDisplayLoc = this.centerLoc.clone();
            double cardSpacing = config.getDouble("display.card.spacing", 0.25);
            double dealerHeight = config.getDouble("display.card.dealer.height", 1.2);
            double distanceFromCenter = config.getDouble("display.card.dealer.distance", 0.75);
            if (!this.dealerHand.isEmpty()) {
               BlackjackPlugin.Card dealerVisibleCard = this.dealerHand.get(0);
               playerx.sendMessage(ChatColor.GREEN + "Dealer's visible card: " + dealerVisibleCard + " | Value: " + dealerVisibleCard.getValue());
            }

            for (int i = 0; i < this.dealerHand.size(); i++) {
               BlackjackPlugin.Card card = this.dealerHand.get(i);
               Location spawnLoc = baseDisplayLoc.clone();
               BlackjackPlugin.Card displayCard = this.gameInProgress && i > 0 ? null : card;
               ItemDisplay display = this.createCardDisplay(spawnLoc, displayCard, true, seatNumber);
               Vector3f translation = new Vector3f();
               float xOffset = 0.0F;
               float zOffset = 0.0F;
               switch (seatNumber) {
                  case 0:
                     xOffset = (float)distanceFromCenter;
                     zOffset = (float)(i * cardSpacing - (this.dealerHand.size() - 1) * cardSpacing / 2.0);
                     break;
                  case 1:
                     xOffset = (float)(i * cardSpacing - (this.dealerHand.size() - 1) * cardSpacing / 2.0);
                     zOffset = (float)distanceFromCenter;
                     break;
                  case 2:
                     xOffset = (float)(-distanceFromCenter);
                     zOffset = (float)(-(i * cardSpacing) + (this.dealerHand.size() - 1) * cardSpacing / 2.0);
                     break;
                  case 3:
                     xOffset = (float)(-(i * cardSpacing) + (this.dealerHand.size() - 1) * cardSpacing / 2.0);
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

            this.playerDealerDisplays.put(playerx, dealerDisplays);
         }
      }

      private Transformation createCardTransformation(boolean isDealer, int seatNumber) {
         if (isDealer) {
            float yRotation = switch (seatNumber) {
               case 0 -> (float) (-Math.PI / 2);
               case 1 -> (float) Math.PI;
               case 2 -> (float) (Math.PI / 2);
               case 3 -> 0.0F;
               default -> 0.0F;
            };
            return new Transformation(
               new Vector3f(0.0F, 0.0F, 0.0F),
               new AxisAngle4f(yRotation, 0.0F, 1.0F, 0.0F),
               new Vector3f(0.35F, 0.35F, 0.35F),
               new AxisAngle4f((float)Math.toRadians(15.0), 1.0F, 0.0F, 0.0F)
            );
         } else {
            float xRotation = (float) (Math.PI / 2);
            switch (seatNumber) {
               case 0:
                  break;
               case 1:
                  break;
               case 2:
                  break;
               case 3:
                  break;
               default:
                  break;
            }

            float zRotation = 0.0F;
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
               new Vector3f(0.0F, 0.0F, 0.0F),
               new AxisAngle4f(xRotation, 1.0F, 0.0F, 0.0F),
               new Vector3f(0.35F, 0.35F, 0.35F),
               new AxisAngle4f(zRotation, 0.0F, 0.0F, 1.0F)
            );
         }
      }

      private ItemDisplay createCardDisplay(Location loc, BlackjackPlugin.Card card, boolean isDealer, int seatNumber) {
         World world = loc.getWorld();
         Location displayLoc = new Location(world, loc.getBlockX() + 0.5, loc.getBlockY(), loc.getBlockZ() + 0.5, 0.0F, 0.0F);
         ItemDisplay display = (ItemDisplay)world.spawn(displayLoc, ItemDisplay.class);
         if (card != null) {
            String cardIdentifier = this.getCardIdentifier(card);
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

         Transformation transform = this.createCardTransformation(isDealer, seatNumber);
         display.setTransformation(transform);
         return display;
      }

      private String getCardIdentifier(BlackjackPlugin.Card card) {
         String rank = card.suit;

         String suit = switch (rank) {
            case "♠" -> "s";
            case "♥" -> "h";
            case "♦" -> "d";
            case "♣" -> "c";
            default -> throw new IllegalArgumentException("Invalid suit: " + card.suit);
         };
         String var7 = card.rank;

         rank = switch (var7) {
            case "A" -> "1";
            case "J" -> "j";
            case "Q" -> "q";
            case "K" -> "k";
            default -> card.rank.toLowerCase();
         };
         return suit + rank;
      }

      private void clearDisplays() {
         for (List<ItemDisplay> displays : this.playerCardDisplays.values()) {
            for (ItemDisplay display : displays) {
               display.remove();
            }

            displays.clear();
         }

         for (List<ItemDisplay> displays : this.playerDealerDisplays.values()) {
            for (ItemDisplay display : displays) {
               display.remove();
            }

            displays.clear();
         }
      }

      private int calculateHand(List<BlackjackPlugin.Card> hand) {
         int value = 0;
         int aces = 0;

         for (BlackjackPlugin.Card card : hand) {
            String rankLower = card.rank.toLowerCase();
            if (rankLower.equals("a")) {
               aces++;
               value += 11;
            } else if (!rankLower.equals("k") && !rankLower.equals("q") && !rankLower.equals("j")) {
               try {
                  value += Integer.parseInt(rankLower);
               } catch (NumberFormatException var8) {
                  System.err.println("Invalid card rank: " + card.rank);
               }
            } else {
               value += 10;
            }
         }

         while (value > 21 && aces > 0) {
            value -= 10;
            aces--;
         }

         return value;
      }

      private void broadcastToTable(String message) {
         for (Player player : this.players) {
            player.sendMessage(message);
         }
      }
   }

   private class Card {
      private String suit;
      private String rank;

      public Card(String suit, String rank) {
         this.suit = suit;
         this.rank = rank;
      }

      @Override
      public String toString() {
         return this.rank + this.suit;
      }

      public int getValue() {
         String rankLower = this.rank.toLowerCase();
         if (rankLower.equals("a")) {
            return 11;
         } else if (!rankLower.equals("k") && !rankLower.equals("q") && !rankLower.equals("j")) {
            try {
               return Integer.parseInt(rankLower);
            } catch (NumberFormatException var3) {
               System.err.println("Invalid card rank: " + this.rank);
               return 0;
            }
         } else {
            return 10;
         }
      }
   }

   private class Deck {
      private Stack<BlackjackPlugin.Card> cards = new Stack<>();

      public Deck() {
         String[] suits = new String[]{"♠", "♥", "♦", "♣"};
         String[] ranks = new String[]{"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};

         for (String suit : suits) {
            for (String rank : ranks) {
               this.cards.push(BlackjackPlugin.this.new Card(suit, rank));
            }
         }

         Collections.shuffle(this.cards);
      }

      public BlackjackPlugin.Card drawCard() {
         if (this.cards.isEmpty()) {
            BlackjackPlugin.Deck newDeck = BlackjackPlugin.this.new Deck();
            this.cards = newDeck.cards;
         }

         return this.cards.pop();
      }
   }

   private static class PlayerStats {
      private int handsWon = 0;
      private int handsLost = 0;
      private int handsPushed = 0;
      private int currentStreak = 0;
      private int bestStreak = 0;
      private double totalWinnings = 0.0;

      public synchronized void incrementWins() {
         this.handsWon++;
         this.currentStreak = Math.max(0, this.currentStreak + 1);
         this.bestStreak = Math.max(this.bestStreak, this.currentStreak);
      }

      public synchronized void incrementLosses() {
         this.handsLost++;
         this.currentStreak = 0;
      }

      public synchronized void incrementPushes() {
         this.handsPushed++;
      }

      public synchronized void addWinnings(double amount) {
         this.totalWinnings += amount;
      }
   }
}
