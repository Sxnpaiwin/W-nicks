package wnick.command;

import dev.jorel.commandapi.nametag.CommandAPICommand;
import dev.jorel.commandapi.nametag.arguments.Argument;
import dev.jorel.commandapi.nametag.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.nametag.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.nametag.arguments.GreedyStringArgument;
import dev.jorel.commandapi.nametag.arguments.StringArgument;
import dev.jorel.commandapi.nametag.executors.ExecutorType;
import wnick.util.Tasks;
import wnick.util.MiniMsg;
import wnick.util.Flags;
import wnick.NameTagPlugin;
import wnick.util.MojangSkinFetcher;
import wnick.NickPlayer;
import wnick.Skin;
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
      this.withSubcommand(new CommandAPICommand("reset_all").withPermission("wnick.commands.nick.reset_all").executes((sender, args) -> {
         sender.sendMessage(MiniMsg.deserialize("<yellow>Starting complete reset of all nickname data..."));
         plugin.resetAllNicks();
         sender.sendMessage(MiniMsg.deserialize("Reset command executed. Check console for details."));
      }, new ExecutorType[0]));
      this.withSubcommand(new CommandAPICommand("save_all").withPermission("wnick.commands.nick.save_all").executes((sender, args) -> {
         sender.sendMessage(MiniMsg.deserialize("<yellow>Starting sequential save of all online players' data..."));
         plugin.saveAllPlayersData();
         sender.sendMessage(MiniMsg.deserialize("Save command executed. Check console for details."));
      }, new ExecutorType[0]));
      this.withSubcommand(new CommandAPICommand("save_cached").withPermission("wnick.commands.nick.save_cached").executes((sender, args) -> {
         sender.sendMessage(MiniMsg.deserialize("<yellow>Starting sequential save of all cached players' data..."));
         plugin.saveAllCachedPlayersData();
         sender.sendMessage(MiniMsg.deserialize("Save command executed. Check console for details."));
      }, new ExecutorType[0]));
      this.withSubcommand(
         new CommandAPICommand("reset")
            .withPermission("wnick.commands.nick.reset")
            .withOptionalArguments(new Argument[]{new EntitySelectorArgument.ManyPlayers("targets").withPermission("wnick.commands.nick.others")})
            .executes(
               (sender, args) -> {
                  List<Player> targets = (List<Player>)args.get("targets");
                  if (targets != null && !targets.isEmpty()) {
                     for (Player target : targets) {
                        if (!sender.hasPermission("wnick.commands.nick.others") && target != sender) {
                           sender.sendMessage(MiniMsg.deserialize("<red>You do not have permission to nick other players."));
                           return;
                        }

                        if (!plugin.hasNick(target)) {
                           if (targets.size() == 1) {
                              sender.sendMessage(
                                 MiniMsg.deserialize(
                                    String.format(
                                       "<red>%s isn't currently nicked",
                                       MiniMsg.serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(target.getName()))
                                    )
                                 )
                              );
                              return;
                           }
                        } else {
                           plugin.resetNick(target);
                           sender.sendMessage(
                              MiniMsg.deserialize(
                                 target == sender
                                    ? "Successfully reset your nick"
                                    : "Successfully reset "
                                       + MiniMsg.serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(target.getName()))
                                       + "'s nick"
                              )
                           );
                        }
                     }
                  } else {
                     if (sender instanceof Player player) {
                        if (!plugin.hasNick(player)) {
                           sender.sendMessage(MiniMsg.deserialize("<red>You aren't currently nicked"));
                           return;
                        }

                        plugin.resetNick(player);
                        sender.sendMessage(MiniMsg.deserialize("Successfully reset your nick"));
                     } else {
                        sender.sendMessage(MiniMsg.deserialize("<red>Invalid arguments. /nick reset [<targets>]"));
                     }
                  }
               },
               new ExecutorType[0]
            )
      );
      this.withPermission("wnick.commands.nick");
      // Single greedy argument that captures everything after /nick.
      // We parse it manually to support both:
      //   /nick as Smazzy              (self, no target specified)
      //   /nick NotCladdy as Smazzy    (target another player)
      //   /nick reset                   (self reset)
      //   /nick NotCladdy reset         (reset another player)
      //   /nick with_name Steve -r vip  (self with rank flag)
      this.withOptionalArguments(
         new Argument[]{
            new GreedyStringArgument("input")
               .replaceSuggestions(ArgumentSuggestions.strings(s -> {
                  // Suggest action keywords + online player names
                  java.util.List<String> suggestions = new java.util.ArrayList<>();
                  suggestions.add("reset");
                  suggestions.add("as ");
                  suggestions.add("with_name ");
                  suggestions.add("with_skin ");
                  suggestions.add("from_url ");
                  if (s.sender().hasPermission("wnick.commands.nick.others")) {
                     for (Player p : plugin.getServer().getOnlinePlayers()) {
                        suggestions.add(p.getName() + " reset");
                        suggestions.add(p.getName() + " as ");
                        suggestions.add(p.getName() + " with_name ");
                     }
                  }
                  return suggestions.toArray(new String[0]);
               }))
         }
      );
      this.executes(
         (sender, args) -> {
            String input = (String) args.get("input");

            // If no input, show help
            if (input == null || input.trim().isEmpty()) {
               sendNickHelp(sender);
               return;
            }

            // Parse the input: check if the first word is an action keyword or a player name
            String[] parts = input.trim().split("\\s+", 2);
            String firstWord = parts[0];
            String rest = parts.length > 1 ? parts[1] : "";

            List<Player> targets;
            String action;
            String text;

            if (NICK_NAME_ACTIONS.contains(firstWord.toLowerCase()) || firstWord.equalsIgnoreCase("reset")) {
               // First word is an action → target is self
               if (!(sender instanceof Player self)) {
                  sender.sendMessage(MiniMsg.deserialize("<red>You must specify a target when running from console."));
                  return;
               }
               targets = List.of(self);
               action = firstWord.toLowerCase();
               text = rest;
            } else {
               // First word is a player name → need an action as second word
               targets = Bukkit.matchPlayer(firstWord);
               if (targets.isEmpty()) {
                  sender.sendMessage(MiniMsg.deserialize("<red>Unknown action or player: " + firstWord));
                  sender.sendMessage(MiniMsg.deserialize("<gray>Usage: /nick [as|with_name|with_skin|from_url|reset] [name]"));
                  sender.sendMessage(MiniMsg.deserialize("<gray>   or: /nick <player> [as|with_name|with_skin|from_url|reset] [name]"));
                  return;
               }
               // Check permission for targeting others
               for (Player target : targets) {
                  if (!sender.hasPermission("wnick.commands.nick.others") && target != sender) {
                     sender.sendMessage(MiniMsg.deserialize("<red>You do not have permission to nick other players."));
                     return;
                  }
               }
               // Parse action from rest
               if (rest.isEmpty()) {
                  sender.sendMessage(MiniMsg.deserialize("<red>Missing action. Usage: /nick <player> <reset|as|with_name|with_skin|from_url> [name]"));
                  return;
               }
               String[] restParts = rest.split("\\s+", 2);
               action = restParts[0].toLowerCase();
               text = restParts.length > 1 ? restParts[1] : "";
            }

            // Validate action
            if (!NICK_NAME_ACTIONS.contains(action) && !action.equals("reset")) {
               sender.sendMessage(MiniMsg.deserialize("<red>Unknown action: " + action));
               sender.sendMessage(MiniMsg.deserialize("<gray>Valid actions: reset, as, with_name, with_skin, from_url"));
               return;
            }

            // --- Handle "reset" action ---
            if (action.equals("reset")) {
               for (Player target : targets) {
                  if (plugin.hasNick(target)) {
                     plugin.resetNick(target);
                     sender.sendMessage(MiniMsg.deserialize(
                        target == sender
                           ? "Successfully reset your nick"
                           : "Successfully reset " + target.getName() + "'s nick"
                     ));
                  } else {
                     sender.sendMessage(MiniMsg.deserialize(
                        target == sender
                           ? "<red>You aren't currently nicked"
                           : "<red>" + target.getName() + " isn't currently nicked"
                     ));
                  }
               }
               return;
            }

            // --- Handle actions that need a name argument ---
            if (text.isEmpty()) {
               sender.sendMessage(MiniMsg.deserialize("<red>Missing name. Usage: /nick " + action + " <name>"));
               return;
            }

            for (Player target : targets) {
               String originalName = target.getName();
               switch (action) {
                  case "as" -> handleAs(sender, target, originalName, text);
                  case "with_name" -> handleWithName(sender, target, originalName, text);
                  case "with_skin" -> handleWithSkin(sender, target, originalName, text);
                  case "from_url" -> handleFromUrl(sender, target, originalName, text);
               }
            }
         },
         new ExecutorType[0]
      );
   }

   /**
    * /nick as <name> — nick as an existing Minecraft player (copies skin + name).
    * Preserves the existing fake rank if no -r flag is passed.
    */
   private void handleAs(org.bukkit.command.CommandSender sender, Player target, String originalName, String text) {
      boolean force = Flags.hasFlags(text, "f");
      String rankId = extractRankFlag(text);
      String textNoRank = stripRankFlag(text);
      if (textNoRank.split(" ").length > 1 && !force) {
         sender.sendMessage(MiniMsg.deserialize("<red>Your nick cannot contain spaces"));
         return;
      }

      String rawText = Flags.sanitizeInput(textNoRank, Set.of('f'));
      String sanitizedText = LegacyComponentSerializer.legacySection().serialize(MiniMsg.deserialize(rawText));

      if (rankId != null && plugin.getFakeRankManager() != null && plugin.getFakeRankManager().getRank(rankId) == null) {
         sender.sendMessage(MiniMsg.deserialize("<red>Unknown rank: " + rankId));
         return;
      }

      // Save the current fake rank so we can restore it after the reset.
      NickPlayer existingData = plugin.getPlayerCache().get(target.getUniqueId());
      String preservedRankId = (rankId != null) ? rankId
         : (existingData != null ? existingData.getFakeRankId() : null);

      Tasks.runAsync(plugin, () -> {
         if (!plugin.config().getBoolean("can_use_existing_players") && !force && MojangSkinFetcher.fetchUUID(sanitizedText) != null) {
            sender.sendMessage(MiniMsg.deserialize("<red>You cannot nick as an existing Minecraft player!"));
         } else {
            if (plugin.hasNick(target)) {
               plugin.resetNick(target);
            }

            Tasks.run(plugin, () -> {
               plugin.setNickFromPlayer(target, sanitizedText);
               // Restore or set the fake rank
               if (preservedRankId != null) {
                  NickPlayer nickData = plugin.getPlayerCache().get(target.getUniqueId());
                  if (nickData != null) {
                     nickData.setFakeRankId(preservedRankId);
                     plugin.getStorageManager().getStorage().savePlayer(nickData);
                     // Refresh TAB display so the rank prefix/suffix shows
                     if (plugin.getServer().getPluginManager().isPluginEnabled("TAB")) {
                        plugin.attemptToUpdateTabPlayer(target);
                     }
                  }
               }
            });
            String rankMsg = preservedRankId != null ? " <gray>(rank: " + preservedRankId + ")" : "";
            sender.sendMessage(MiniMsg.deserialize("Successfully nicked " + originalName + " as " + sanitizedText + rankMsg));
         }
      });
   }

   /**
    * /nick with_name <name> — set a custom nickname (no skin change).
    * Preserves the existing fake rank if no -r flag is passed.
    */
   private void handleWithName(org.bukkit.command.CommandSender sender, Player target, String originalName, String text) {
      boolean force = Flags.hasFlags(text, "f");
      String rankId = extractRankFlag(text);
      String textNoRank = stripRankFlag(text);
      String rawText = Flags.sanitizeInput(textNoRank, Set.of('f'));
      String sanitizedText = LegacyComponentSerializer.legacySection()
         .serialize(MiniMsg.deserialize(MiniMsg.convertAmpersandToMiniMessage(rawText)));

      if (rawText.split(" ").length > 1 && !force) {
         sender.sendMessage(MiniMsg.deserialize("<red>Your nick name cannot contain spaces"));
         return;
      }

      if (!plugin.config().getBoolean("can_use_existing_players") && !force && MojangSkinFetcher.fetchUUID(sanitizedText) != null) {
         sender.sendMessage(MiniMsg.deserialize("<red>You cannot nick as an existing Minecraft player!"));
         return;
      }

      if (rankId != null && plugin.getFakeRankManager() != null && plugin.getFakeRankManager().getRank(rankId) == null) {
         sender.sendMessage(MiniMsg.deserialize("<red>Unknown rank: " + rankId));
         return;
      }

      // Preserve existing rank if no -r flag
      NickPlayer existingData = plugin.getPlayerCache().get(target.getUniqueId());
      String effectiveRankId = (rankId != null) ? rankId
         : (existingData != null ? existingData.getFakeRankId() : null);

      plugin.setNickname(target, rawText);
      if (effectiveRankId != null) {
         NickPlayer nickData = plugin.getPlayerCache().get(target.getUniqueId());
         if (nickData != null) {
            nickData.setFakeRankId(effectiveRankId);
            plugin.getStorageManager().getStorage().savePlayer(nickData);
            if (plugin.getServer().getPluginManager().isPluginEnabled("TAB")) {
               plugin.attemptToUpdateTabPlayer(target);
            }
         }
      }

      sender.sendMessage(
         MiniMsg.deserialize("Successfully set " + originalName + "'s name to ")
            .append(MiniMsg.deserialize(MiniMsg.convertAmpersandToMiniMessage(rawText)))
      );
   }

   /**
    * /nick with_skin <player> — copy another player's skin.
    */
   private void handleWithSkin(org.bukkit.command.CommandSender sender, Player target, String originalName, String text) {
      if (text.split(" ").length > 1) {
         sender.sendMessage(MiniMsg.deserialize("<red>Your nick skin cannot contain spaces"));
         return;
      }

      plugin.setSkinFromPlayer(target, text);
      sender.sendMessage(MiniMsg.deserialize("Successfully set " + originalName + "'s skin to " + text));
   }

   /**
    * /nick from_url <url> — apply a Mineskin skin from a URL or ID.
    */
   private void handleFromUrl(org.bukkit.command.CommandSender sender, Player target, String originalName, String text) {
      if (text.matches("https://(?:minesk\\.in|(?:www\\.)?mineskin\\.org/skins)/[a-f0-9]{32}")) {
         if (!plugin.setSkinFromMineskinUrl(target, text)) {
            sender.sendMessage(MiniMsg.deserialize("<red>Failed to fetch skin from the Mineskin URL"));
            return;
         }
         sender.sendMessage(MiniMsg.deserialize("Successfully set " + originalName + "'s skin to the skin from the URL"));
      } else {
         if (!plugin.setSkinFromMineskinId(target, text)) {
            sender.sendMessage(MiniMsg.deserialize("<red>Failed to fetch skin. Please supply a valid Mineskin ID or URL."));
            return;
         }
         sender.sendMessage(MiniMsg.deserialize("Successfully set " + originalName + "'s skin to the skin from the URL"));
      }
   }

   /** Show help for /nick */
   private static void sendNickHelp(org.bukkit.command.CommandSender sender) {
      sender.sendMessage(MiniMsg.deserialize("<gold><bold>/nick</bold> <gray>commands:"));
      sender.sendMessage(MiniMsg.deserialize("<yellow>/nick as <name> [-r <rank>] <gray>— nick as another player (copies skin + name)"));
      sender.sendMessage(MiniMsg.deserialize("<yellow>/nick with_name <name> [-r <rank>] <gray>— set a custom name (keeps your skin)"));
      sender.sendMessage(MiniMsg.deserialize("<yellow>/nick with_skin <player> <gray>— copy another player's skin"));
      sender.sendMessage(MiniMsg.deserialize("<yellow>/nick from_url <url|id> <gray>— apply a Mineskin skin"));
      sender.sendMessage(MiniMsg.deserialize("<yellow>/nick reset <gray>— restore your original name and skin"));
      sender.sendMessage(MiniMsg.deserialize("<dark_gray>─</dark_gray> <gray>For other players: /nick <player> <action> [name]"));
      sender.sendMessage(MiniMsg.deserialize("<dark_gray>─</dark_gray> <gray>Rank flag: -r <rank> (e.g. -r vip)"));
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
      // [W-Nick] Cloud-nick phone-home call removed — player UUIDs and usernames
      // are no longer uploaded to any third-party service on join.

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
                  Tasks.run(this.plugin, () -> {
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
                     Tasks.run(this.plugin, () -> this.plugin.setSkinFromPlayer(player, skinName));
                  }

                  if (skin != null) {
                     Tasks.run(this.plugin, () -> this.plugin.setSkinFromTextureAndSignature(player, skin.texture(), skin.signature()));
                  }

                  if (originalJoinMessage != null) {
                     Tasks.run(this.plugin, () -> this.handleJoinMessageWithNick(player.getName(), originalJoinMessage, player.getName()));
                  }
               }

               if (this.plugin.getServer().getPluginManager().isPluginEnabled("TAB")) {
                  this.plugin.attemptToUpdateTabPlayer(player);
               }
            } else if (originalJoinMessage != null) {
               Tasks.run(this.plugin, () -> this.handleJoinMessageWithNick(player.getName(), originalJoinMessage, player.getName()));
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
                  String joinMsg = MiniMsg.serialize(originalJoinMessage);
                  if (joinMsg.contains("(formerly known as")) {
                     Pattern pattern = Pattern.compile("^(.+)\\s+\\(formerly known as (.+)\\)\\s+(.+)$");
                     Matcher matcher = pattern.matcher(joinMsg);
                     if (matcher.find()) {
                        String restOfMessage = matcher.group(3).trim();
                        joinMsg = nickName + " " + restOfMessage;
                        this.plugin.getServer().broadcast(MiniMsg.deserialize(joinMsg).color(originalJoinMessage.color()));
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
