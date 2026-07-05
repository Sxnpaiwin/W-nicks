package gg.lode.nametag.command;

import dev.jorel.commandapi.nametag.CommandAPICommand;
import dev.jorel.commandapi.nametag.arguments.Argument;
import dev.jorel.commandapi.nametag.executors.ExecutorType;
import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.bookshelfcmd.argument.FlagArgument;
import gg.lode.nametag.NameTagPlugin;
import gg.lode.nametag.util.FakeRankManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class RandomNickCommand extends CommandAPICommand {

   /** Matches a "-r <rank>" flag anywhere in the argument string. */
   private static final Pattern RANK_FLAG_PATTERN = Pattern.compile("(?:^|\\s)-r\\s+(\\S+)");

   public RandomNickCommand(NameTagPlugin plugin) {
      super("randomnick");
      this.withPermission("wnick.commands.randomnick");
      this.withOptionalArguments(
         new Argument[]{
            new FlagArgument("args", Set.of(), Set.of(), Set.of("skip"))
               .withPermission("wnick.commands.randomnick.others")
               .replaceSuggestions((info, builder) -> {
                  String current = info.currentArg();
                  boolean endsWithSpace = info.currentInput().endsWith(" ");
                  boolean hasWordFlag = FlagArgument.hasWordFlag(info.currentArg(), "skip");
                  String stripped = FlagArgument.sanitizeInput(current, Set.of(), Set.of("skip")).trim();
                  if (stripped.isEmpty()) {
                     for (String sel : List.of("@a", "@r", "@s", "@e[type=player]")) {
                        builder.suggest(sel);
                     }
                  }

                  if (!hasWordFlag) {
                     if (endsWithSpace) {
                        builder.suggest(current + "--skip");
                     } else if (current.startsWith("--")) {
                        if ("--skip".startsWith(current)) {
                           builder.suggest("--skip");
                        }
                     } else if (!current.isEmpty()) {
                        builder.suggest(current + " --skip");
                     } else {
                        builder.suggest("--skip");
                     }
                  }

                  // [LuckPerms extension] Suggest -r <rank> when LuckPerms is hooked
                  if (plugin.getFakeRankManager() != null) {
                     Matcher m = RANK_FLAG_PATTERN.matcher(" " + current);
                     boolean alreadyHasRank = m.find();
                     if (!alreadyHasRank) {
                        // Find the last word; if it looks like "-r" prefix, suggest ranks
                        String[] parts = current.split("\\s+");
                        String last = parts.length > 0 ? parts[parts.length - 1] : "";
                        if (last.equals("-r") && endsWithSpace) {
                           for (FakeRankManager.FakeRank r : plugin.getFakeRankManager().getAllRanks()) {
                              builder.suggest(r.id());
                           }
                        } else if (endsWithSpace && !current.contains("-r")) {
                           builder.suggest(current + "-r ");
                        }
                     }
                  }

                  return builder.buildFuture();
               })
         }
      );
      this.executes(
         (sender, args) -> {
            String rawArgs = (String)args.get("args");

            // [LuckPerms extension] Extract a "-r <rank>" override (case-insensitive).
            String rankOverride = extractRankFlag(rawArgs);
            String rawArgsNoRank = stripRankFlag(rawArgs);
            if (rankOverride != null) {
               if (plugin.getFakeRankManager() == null) {
                  sender.sendMessage(MiniMessageHelper.deserialize("<red>LuckPerms is not installed. The -r flag is unavailable."));
                  return;
               }
               String resolvedId = plugin.getFakeRankManager().resolveRankIdCaseInsensitive(rankOverride);
               if (resolvedId == null) {
                  sender.sendMessage(MiniMessageHelper.deserialize("<red>Unknown rank: " + rankOverride));
                  sender.sendMessage(MiniMessageHelper.deserialize("<gray>Use <white>/nickrank list</white> to see available ranks."));
                  return;
               }
               rankOverride = resolvedId;
            }

            boolean skip = FlagArgument.hasWordFlag(rawArgsNoRank, "skip");
            String selectorStr = rawArgsNoRank != null ? FlagArgument.sanitizeInput(rawArgsNoRank, Set.of(), Set.of("skip")).trim() : "";
            if (selectorStr.isEmpty()) {
               if (sender instanceof Player player) {
                  if (!skip || !plugin.hasNick(player)) {
                     sender.sendMessage(MiniMessageHelper.deserialize("<yellow>Generating random nickname" + (rankOverride != null ? " with rank " + rankOverride : "") + "...</yellow>"));
                     if (rankOverride != null) {
                        plugin.randomNick(player, rankOverride);
                     } else {
                        plugin.randomNick(player);
                     }
                  }
               } else {
                  sender.sendMessage(MiniMessageHelper.deserialize("<red>This command can only be executed by players when no target is specified."));
               }
            } else if (!sender.hasPermission("wnick.commands.randomnick.others")) {
               sender.sendMessage(MiniMessageHelper.deserialize("<red>You do not have permission to nick other players."));
            } else {
               Collection<Player> targets;
               try {
                  targets = Bukkit.selectEntities(sender, selectorStr).stream().filter(e -> e instanceof Player).map(e -> (Player)e).toList();
               } catch (IllegalArgumentException var11) {
                  sender.sendMessage(MiniMessageHelper.deserialize("<red>Invalid selector: " + selectorStr));
                  return;
               }

               if (targets.isEmpty()) {
                  sender.sendMessage(MiniMessageHelper.deserialize("<red>No players matched."));
               } else {
                  int skipped = 0;
                  int queued = 0;

                  for (Player target : targets) {
                     if (skip && plugin.hasNick(target)) {
                        skipped++;
                     } else {
                        if (rankOverride != null) {
                           plugin.randomNick(target, rankOverride);
                        } else {
                           plugin.randomNick(target);
                        }
                        queued++;
                     }
                  }

                  if (queued > 0) {
                     sender.sendMessage(
                        MiniMessageHelper.deserialize(
                           "<yellow>Generating random nickname"
                              + (queued > 1 ? "s" : "")
                              + (rankOverride != null ? " with rank " + rankOverride : "")
                              + " for "
                              + queued
                              + " player"
                              + (queued > 1 ? "s" : "")
                              + "...</yellow>"
                        )
                     );
                  }

                  if (skipped > 0) {
                     sender.sendMessage(
                        MiniMessageHelper.deserialize("<gray>Skipped " + skipped + " already-nicked player" + (skipped > 1 ? "s" : "") + ".</gray>")
                     );
                  }
               }
            }
         },
         new ExecutorType[0]
      );
   }

   /** Extract the rank id from a "-r <rank>" flag. */
   private static String extractRankFlag(String input) {
      if (input == null) return null;
      Matcher m = RANK_FLAG_PATTERN.matcher(input);
      return m.find() ? m.group(1) : null;
   }

   /** Remove a "-r <rank>" flag from the argument string. */
   private static String stripRankFlag(String input) {
      if (input == null) return null;
      return input.replaceAll("(?:^|\\s)-r\\s+\\S+", "").trim();
   }
}
