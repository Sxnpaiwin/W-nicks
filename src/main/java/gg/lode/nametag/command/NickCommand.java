package gg.lode.nametag.command;

import dev.jorel.commandapi.nametag.CommandAPICommand;
import dev.jorel.commandapi.nametag.arguments.Argument;
import dev.jorel.commandapi.nametag.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.nametag.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.nametag.arguments.GreedyStringArgument;
import dev.jorel.commandapi.nametag.arguments.StringArgument;
import dev.jorel.commandapi.nametag.executors.ExecutorType;
import gg.lode.bookshelfapi.api.Task;
import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.bookshelfcmd.argument.FlagArgument;
import gg.lode.nametag.NameTagPlugin;
import gg.lode.nametag.util.MojangSkinFetcher;
import gg.lode.nametagapi.api.NickPlayer;
import gg.lode.nametagapi.api.Skin;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.Nullable;

public class NickCommand extends CommandAPICommand implements Listener {
   private final NameTagPlugin plugin;
   private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("<(rn|nn):([^>]+)>");
   private static final Pattern RANK_FLAG_PATTERN = Pattern.compile("(?:^|\\s)-r\\s+(\\S+)");
   private static final Set<String> NICK_NAME_ACTIONS = Set.of("as", "with_name", "with_skin", "from_url");
   private static final Set<String> WHISPER_COMMANDS = Set.of("/w", "/whisper", "/tell", "/msg", "/message");
   private static final Set<String> REPLY_COMMANDS = Set.of("/reply", "/r");

   public NickCommand(NameTagPlugin plugin) {
      super("nick");
      this.plugin = plugin;
      plugin.getServer().getPluginManager().registerEvents(this, plugin);
      this.withSubcommand(new CommandAPICommand("reset_all").withPermission("lodestone.nametag.commands.nick.reset_all").executes((sender, args) -> {
         sender.sendMessage(MiniMessageHelper.deserialize("<yellow>Starting complete reset of all nickname data..."));
         plugin.resetAllNicks();
         sender.sendMessage(MiniMessageHelper.deserialize("Reset command executed. Check console for details."));
      }, new ExecutorType[0]));
      this.withSubcommand(new CommandAPICommand("save_all").withPermission("lodestone.nametag.commands.nick.save_all").executes((sender, args) -> {
         sender.sendMessage(MiniMessageHelper.deserialize("<yellow>Starting sequential save of all online players' data..."));
         plugin.saveAllPlayersData();
         sender.sendMessage(MiniMessageHelper.deserialize("Save command executed. Check console for details."));
      }, new ExecutorType[0]));
      this.withSubcommand(new CommandAPICommand("save_cached").withPermission("lodestone.nametag.commands.nick.save_cached").executes((sender, args) -> {
         sender.sendMessage(MiniMessageHelper.deserialize("<yellow>Starting sequential save of all cached players' data..."));
         plugin.saveAllCachedPlayersData();
         sender.sendMessage(MiniMessageHelper.deserialize("Save command executed. Check console for details."));
      }, new ExecutorType[0]));
      this.withSubcommand(
         new CommandAPICommand("reset")
            .withPermission("lodestone.nametag.commands.nick.reset")
            .withOptionalArguments(new Argument[]{new EntitySelectorArgument.ManyPlayers("targets").withPermission("lodestone.nametag.commands.nick.others")})
            .executes(
               (sender, args) -> {
                  List<Player> targets = (List<Player>)args.get("targets");
                  if (targets != null && !targets.isEmpty()) {
                     for (Player target : targets) {
                        if (!sender.hasPermission("lodestone.nametag.commands.nick.others") && target != sender) {
                           sender.sendMessage(MiniMessageHelper.deserialize("<red>You do not have permission to nick other players."));
                           return;
                        }

                        if (!plugin.hasNick(target)) {
                           if (targets.size() == 1) {
                              sender.sendMessage(
                                 MiniMessageHelper.deserialize(
                                    String.format(
                                       "<red>%s isn't currently nicked",
                                       MiniMessageHelper.serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(target.getName()))
                                    )
                                 )
                              );
                              return;
                           }
                        } else {
                           plugin.resetNick(target);
                           sender.sendMessage(
                              MiniMessageHelper.deserialize(
                                 target == sender
                                    ? "Successfully reset your nick"
                                    : "Successfully reset "
                                       + MiniMessageHelper.serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(target.getName()))
                                       + "'s nick"
                              )
                           );
                        }
                     }
                  } else {
                     if (sender instanceof Player player) {
                        if (!plugin.hasNick(player)) {
                           sender.sendMessage(MiniMessageHelper.deserialize("<red>You aren't currently nicked"));
                           return;
                        }

                        plugin.resetNick(player);
                        sender.sendMessage(MiniMessageHelper.deserialize("Successfully reset your nick"));
                     } else {
                        sender.sendMessage(MiniMessageHelper.deserialize("<red>Invalid arguments. /nick reset [<targets>]"));
                     }
                  }
               },
               new ExecutorType[0]
            )
      );
      this.withPermission("lodestone.nametag.commands.nick");
      this.withArguments(
         new Argument[]{
            new EntitySelectorArgument.ManyPlayers("targets")
               .replaceSuggestions(
                  ArgumentSuggestions.strings(
                     s -> s.sender().hasPermission("lodestone.nametag.commands.nick.others")
                           ? plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toArray(String[]::new)
                           : new String[]{s.sender().getName()}
                  )
               )
         }
      );
      this.withArguments(
         new Argument[]{
            new StringArgument("action")
               .replaceSuggestions(ArgumentSuggestions.strings(s -> new String[]{"reset", "as", "with_name", "with_skin", "from_url"}))
         }
      );
      this.withOptionalArguments(
         new Argument[]{
            new GreedyStringArgument("name")
               .replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toArray(String[]::new)))
         }
      );
      this.executes(
         (sender, args) -> {
            List<Player> targets = (List<Player>)args.get("targets");
            if (targets != null && !targets.isEmpty()) {
               if (args.get(1) instanceof String action) {
                  if (action.equalsIgnoreCase("reset")) {
                     for (Player target : targets) {
                        if (!sender.hasPermission("lodestone.nametag.commands.nick.others") && target != sender) {
                           sender.sendMessage(MiniMessageHelper.deserialize("<red>You do not have permission to nick other players."));
                           return;
                        }

                        if (plugin.hasNick(target)) {
                           plugin.resetNick(target);
                           sender.sendMessage(
                              MiniMessageHelper.deserialize(
                                 "Successfully reset "
                                    + MiniMessageHelper.serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(target.getName()))
                                    + "'s nick"
                              )
                           );
                           return;
                        }

                        if (targets.size() == 1) {
                           sender.sendMessage(
                              MiniMessageHelper.deserialize(
                                 String.format(
                                    "<red>%s isn't currently nicked",
                                    MiniMessageHelper.serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(target.getName()))
                                 )
                              )
                           );
                           return;
                        }
                     }
                  }

                  if (args.get(2) instanceof String text) {
                     for (Player target : targets) {
                        if (!sender.hasPermission("lodestone.nametag.commands.nick.others") && target != sender) {
                           sender.sendMessage(MiniMessageHelper.deserialize("<red>You do not have permission to nick other players."));
                           return;
                        }

                        String originalName = MiniMessageHelper.serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(target.getName()));
                        String var9 = action.toLowerCase();
                        switch (var9) {
                           case "as":
                              boolean forcex = FlagArgument.hasFlags(text, "f");
                              String rankIdx = extractRankFlag(text);
                              String textNoRankx = stripRankFlag(text);
                              if (textNoRankx.split(" ").length > 1 && !FlagArgument.hasFlags(textNoRankx, "f")) {
                                 sender.sendMessage(MiniMessageHelper.deserialize("<red>Your nick cannot contain spaces"));
                                 return;
                              }

                              String rawTextx = FlagArgument.sanitizeInput(textNoRankx, Set.of('f'));
                              String sanitizedTextx = LegacyComponentSerializer.legacySection().serialize(MiniMessageHelper.deserialize(rawTextx));
                              if (rankIdx != null && plugin.getFakeRankManager() != null && plugin.getFakeRankManager().getRank(rankIdx) == null) {
                                 sender.sendMessage(MiniMessageHelper.deserialize("<red>Unknown rank: " + rankIdx));
                                 return;
                              }

                              Task.runAsync(plugin, () -> {
                                 if (!plugin.config().getBoolean("can_use_existing_players") && !forcex && MojangSkinFetcher.fetchUUID(sanitizedTextx) != null) {
                                    sender.sendMessage(MiniMessageHelper.deserialize("<red>You cannot nick as an existing Minecraft player!"));
                                 } else {
                                    if (plugin.hasNick(target)) {
                                       plugin.resetNick(target);
                                    }

                                    Task.run(plugin, () -> {
                                       plugin.setNickFromPlayer(target, sanitizedTextx);
                                       if (rankIdx != null) {
                                          NickPlayer nickDatax = plugin.getPlayerCache().get(target.getUniqueId());
                                          if (nickDatax != null) {
                                             nickDatax.setFakeRankId(rankIdx);
                                             plugin.getStorageManager().getStorage().savePlayer(nickDatax);
                                          }
                                       }
                                    });
                                    sender.sendMessage(MiniMessageHelper.deserialize("Successfully nicked " + originalName + " as " + sanitizedTextx));
                                 }
                              });
                              break;
                           case "with_name":
                              boolean force = FlagArgument.hasFlags(text, "f");
                              String rankId = extractRankFlag(text);
                              String textNoRank = stripRankFlag(text);
                              String rawText = FlagArgument.sanitizeInput(textNoRank, Set.of('f'));
                              String sanitizedText = LegacyComponentSerializer.legacySection()
                                 .serialize(MiniMessageHelper.deserialize(MiniMessageHelper.convertAmpersandToMiniMessage(rawText)));
                              if (rawText.split(" ").length > 1 && !force) {
                                 sender.sendMessage(MiniMessageHelper.deserialize("<red>Your nick name cannot contain spaces"));
                                 return;
                              }

                              if (!plugin.config().getBoolean("can_use_existing_players") && !force && MojangSkinFetcher.fetchUUID(sanitizedText) != null) {
                                 sender.sendMessage(MiniMessageHelper.deserialize("<red>You cannot nick as an existing Minecraft player!"));
                                 return;
                              }

                              if (rankId != null && plugin.getFakeRankManager() != null && plugin.getFakeRankManager().getRank(rankId) == null) {
                                 sender.sendMessage(MiniMessageHelper.deserialize("<red>Unknown rank: " + rankId));
                                 return;
                              }

                              plugin.setNickname(target, rawText);
                              if (rankId != null) {
                                 NickPlayer nickData = plugin.getPlayerCache().get(target.getUniqueId());
                                 if (nickData != null) {
                                    nickData.setFakeRankId(rankId);
                                    plugin.getStorageManager().getStorage().savePlayer(nickData);
                                 }
                              }

                              sender.sendMessage(
                                 MiniMessageHelper.deserialize("Successfully set " + originalName + "'s name to ")
                                    .append(MiniMessageHelper.deserialize(MiniMessageHelper.convertAmpersandToMiniMessage(rawText)))
                              );
                              break;
                           case "with_skin":
                              if (text.split(" ").length > 1) {
                                 sender.sendMessage(MiniMessageHelper.deserialize("<red>Your nick skin cannot contain spaces"));
                                 return;
                              }

                              plugin.setSkinFromPlayer(target, text);
                              sender.sendMessage(MiniMessageHelper.deserialize("Successfully set " + originalName + "'s skin to " + text));
                              break;
                           case "from_url":
                              if (text.matches("https://(?:minesk\\.in|(?:www\\.)?mineskin\\.org/skins)/[a-f0-9]{32}")) {
                                 if (!plugin.setSkinFromMineskinUrl(target, text)) {
                                    sender.sendMessage(MiniMessageHelper.deserialize("<red>Failed to fetch skin from the Mineskin URL"));
                                    return;
                                 }

                                 sender.sendMessage(MiniMessageHelper.deserialize("Successfully set " + originalName + "'s skin to the skin from the URL"));
                              } else {
                                 if (!plugin.setSkinFromMineskinId(target, text)) {
                                    sender.sendMessage(MiniMessageHelper.deserialize("<red>Failed to fetch skin. Please supply a valid Mineskin ID or URL."));
                                    return;
                                 }

                                 sender.sendMessage(MiniMessageHelper.deserialize("Successfully set " + originalName + "'s skin to the skin from the URL"));
                              }
                        }
                     }
                  } else {
                     sender.sendMessage(MiniMessageHelper.deserialize("<red>Invalid arguments. /nick <sender> <reset|as|with_name|with_skin|from_url> <name>"));
                  }
               }
            } else {
               sender.sendMessage(MiniMessageHelper.deserialize("<red>Invalid arguments. /nick [<targets>] <reset|as|with_name|with_skin|from_url> <name>"));
            }
         },
         new ExecutorType[0]
      );
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void on(PlayerDeathEvent event) {
      Player player = event.getPlayer();
      String nickName = this.plugin.getNick(player);
      if (nickName != null) {
         event.deathMessage(
            event.deathMessage() == null
               ? null
               : Objects.requireNonNull(event.deathMessage()).replaceText(builder -> builder.match(player.getName()).replacement(nickName))
         );
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void on(PlayerAdvancementDoneEvent event) {
      Player player = event.getPlayer();
      String nickName = this.plugin.getNick(player);
      if (nickName != null) {
         event.message(
            event.message() == null
               ? null
               : Objects.requireNonNull(event.message()).replaceText(builder -> builder.match(player.getName()).replacement(nickName))
         );
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void on(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      if (this.plugin.config().getBoolean("allow_cloud_nicking")) {
         this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
               this.plugin.getCloudNickService().registerPlayer(player.getUniqueId(), player.getName());
            } catch (Exception var3x) {
               this.plugin.getLogger().warning("[Cloud Nick] Failed to register player on join: " + var3x.getMessage());
            }
         });
      }

      Component originalJoinMessage = event.joinMessage();
      event.joinMessage(null);
      this.plugin.getStorageManager().getStorage().loadPlayer(player.getUniqueId()).whenComplete((nameTagPlayer, throwable) -> {
         if (throwable != null) {
            this.plugin.getLogger().severe("Failed to load player data for " + player.getName() + ": " + throwable.getMessage());
            throwable.printStackTrace();
         } else {
            if (nameTagPlayer != null) {
               this.plugin.getPlayerCache().put(player.getUniqueId(), nameTagPlayer);
               String name = nameTagPlayer.getNickname();
               Skin skin = nameTagPlayer.getSkin();
               String skinName = nameTagPlayer.getSkinName();
               if (name != null) {
                  Component nickComponent = MiniMessage.miniMessage().deserialize(name);
                  String legacyName = LegacyComponentSerializer.legacySection().serialize(nickComponent);
                  Task.run(this.plugin, () -> {
                     player.displayName(nickComponent);
                     player.playerListName(nickComponent);
                     this.plugin.getPaperSkinManager().setProfileName(player, legacyName);
                     if (skin != null) {
                        this.plugin.getPaperSkinManager().setSkin(player, skin.texture(), skin.signature());
                        this.plugin.getPaperSkinManager().refreshPlayer(player);
                     } else if (skinName != null) {
                        this.plugin.getPaperSkinManager().setSkinFromName(this.plugin, player, skinName);
                     } else if (nameTagPlayer.getOriginalTexture() != null) {
                        this.plugin.getPaperSkinManager().setSkin(player, nameTagPlayer.getOriginalTexture(), nameTagPlayer.getOriginalSignature());
                        this.plugin.getPaperSkinManager().refreshPlayer(player);
                     }

                     if (this.plugin.shouldChangeUniqueId()) {
                        this.plugin.getPaperSkinManager().setUniqueId(player, legacyName);
                     }
                  });
                  if (originalJoinMessage != null) {
                     this.handleJoinMessageWithNick(Objects.requireNonNullElse(nameTagPlayer.getOriginalName(), name), originalJoinMessage, name);
                  }
               } else {
                  if (skinName != null) {
                     Task.run(this.plugin, () -> this.plugin.setSkinFromPlayer(player, skinName));
                  }

                  if (skin != null) {
                     Task.run(this.plugin, () -> this.plugin.setSkinFromTextureAndSignature(player, skin.texture(), skin.signature()));
                  }

                  if (originalJoinMessage != null) {
                     Task.run(this.plugin, () -> this.handleJoinMessageWithNick(player.getName(), originalJoinMessage, player.getName()));
                  }
               }

               if (this.plugin.getServer().getPluginManager().isPluginEnabled("TAB")) {
                  this.plugin.attemptToUpdateTabPlayer(player);
               }
            } else if (originalJoinMessage != null) {
               Task.run(this.plugin, () -> this.handleJoinMessageWithNick(player.getName(), originalJoinMessage, player.getName()));
            }
         }
      });
   }

   private void handleJoinMessageWithNick(@RegExp String originalName, Component originalJoinMessage, String nickName) {
      Bukkit.getScheduler()
         .runTask(
            this.plugin,
            () -> {
               try {
                  String joinMsg = MiniMessageHelper.serialize(originalJoinMessage);
                  if (joinMsg.contains("(formerly known as")) {
                     Pattern pattern = Pattern.compile("^(.+)\\s+\\(formerly known as (.+)\\)\\s+(.+)$");
                     Matcher matcher = pattern.matcher(joinMsg);
                     if (matcher.find()) {
                        String restOfMessage = matcher.group(3).trim();
                        joinMsg = nickName + " " + restOfMessage;
                        this.plugin.getServer().broadcast(MiniMessageHelper.deserialize(joinMsg).color(originalJoinMessage.color()));
                     } else {
                        Component modifiedMessage = originalJoinMessage.replaceText(
                           TextReplacementConfig.builder().match(originalName).replacement(nickName).build()
                        );
                        this.plugin.getServer().broadcast(modifiedMessage);
                     }
                  } else {
                     Component modifiedMessage = originalJoinMessage.replaceText(
                        TextReplacementConfig.builder().match(originalName).replacement(nickName).build()
                     );
                     this.plugin.getServer().broadcast(modifiedMessage);
                  }
               } catch (Exception var8) {
                  this.plugin.getLogger().warning("Failed to handle join message with nick: " + var8.getMessage());
                  var8.printStackTrace();
               }
            }
         );
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void on(PlayerCommandPreprocessEvent event) {
      String message = event.getMessage();
      if (event.getPlayer().isOp()) {
         message = this.resolvePlaceholders(message);
      }

      String resolved = this.resolveNickedNames(message);
      if (!resolved.equals(event.getMessage())) {
         event.setMessage(resolved);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void on(ServerCommandEvent event) {
      String command = event.getCommand();
      String withPlaceholders = this.resolvePlaceholders(command);
      String resolved = this.resolveNickedNames("/" + withPlaceholders).substring(1);
      if (!resolved.equals(event.getCommand())) {
         event.setCommand(resolved);
      }
   }

   @Nullable
   private static String extractRankFlag(String input) {
      Matcher m = RANK_FLAG_PATTERN.matcher(input);
      return m.find() ? m.group(1) : null;
   }

   private static String stripRankFlag(String input) {
      return input.replaceAll("(?:^|\\s)-r\\s+\\S+", "").trim();
   }

   private String resolvePlaceholders(String command) {
      Matcher matcher = PLACEHOLDER_PATTERN.matcher(command);
      if (!matcher.find()) {
         return command;
      } else {
         matcher.reset();
         StringBuffer sb = new StringBuffer();

         while (matcher.find()) {
            String type = matcher.group(1);
            String input = matcher.group(2);
            String replacement = null;

            for (NickPlayer nickPlayer : this.plugin.getPlayerCache().values()) {
               if (nickPlayer.getOriginalName() != null) {
                  boolean matchesReal = nickPlayer.getOriginalName().equalsIgnoreCase(input);
                  boolean matchesNick = nickPlayer.getNickname() != null && nickPlayer.getNickname().equalsIgnoreCase(input);
                  if (matchesReal || matchesNick) {
                     replacement = type.equals("rn")
                        ? nickPlayer.getOriginalName()
                        : (nickPlayer.getNickname() != null ? nickPlayer.getNickname() : nickPlayer.getOriginalName());
                     break;
                  }
               }
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement != null ? replacement : matcher.group()));
         }

         matcher.appendTail(sb);
         return sb.toString();
      }
   }

   private String resolveNickedNames(String command) {
      String[] parts = command.split(" ");
      boolean changed = false;
      String cmd = parts.length > 0 ? parts[0].toLowerCase() : "";
      int skipFrom = Integer.MAX_VALUE;
      if (WHISPER_COMMANDS.contains(cmd)) {
         skipFrom = 2;
      } else if (REPLY_COMMANDS.contains(cmd)) {
         skipFrom = 1;
      } else if (cmd.equalsIgnoreCase("/nick")) {
         for (int j = 1; j < parts.length; j++) {
            if (NICK_NAME_ACTIONS.contains(parts[j].toLowerCase())) {
               skipFrom = j + 1;
               break;
            }
         }
      }

      for (int i = 1; i < parts.length; i++) {
         if (i < skipFrom) {
            for (Entry<UUID, NickPlayer> entry : this.plugin.getPlayerCache().entrySet()) {
               NickPlayer nickPlayer = entry.getValue();
               if (nickPlayer.getNickname() != null
                  && nickPlayer.getOriginalName() != null
                  && !nickPlayer.getNickname().equalsIgnoreCase(nickPlayer.getOriginalName())
                  && parts[i].equalsIgnoreCase(nickPlayer.getNickname())
                  && Bukkit.getPlayerExact(nickPlayer.getNickname()) == null) {
                  parts[i] = nickPlayer.getOriginalName();
                  changed = true;
                  break;
               }
            }
         }
      }

      return changed ? String.join(" ", parts) : command;
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void on(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      String name = this.plugin.getNick(player);
      if (name != null) {
         Component quitMessage = event.quitMessage();
         if (quitMessage != null) {
            event.quitMessage(quitMessage.replaceText(TextReplacementConfig.builder().match(player.getName()).replacement(name).build()));
         }
      }

      NickPlayer playerData = this.plugin.getPlayerCache().get(player.getUniqueId());
      if (playerData != null) {
         this.plugin.getStorageManager().getStorage().savePlayer(playerData).whenComplete((result, throwable) -> {
            if (throwable != null) {
               this.plugin.getLogger().warning("Failed to save data for " + player.getName() + " on quit: " + throwable.getMessage());
            } else {
               this.plugin.getLogger().info("Saved data for " + player.getName() + " on quit");
            }

            this.plugin.getPlayerCache().remove(player.getUniqueId());
         });
      } else {
         this.plugin.getPlayerCache().remove(player.getUniqueId());
      }
   }
}
