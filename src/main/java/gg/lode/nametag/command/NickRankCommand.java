package gg.lode.nametag.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.jorel.commandapi.nametag.CommandAPICommand;
import dev.jorel.commandapi.nametag.arguments.Argument;
import dev.jorel.commandapi.nametag.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.nametag.arguments.StringArgument;
import dev.jorel.commandapi.nametag.executors.ExecutorType;
import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.nametag.NameTagPlugin;
import gg.lode.nametag.util.FakeRankManager;
import gg.lode.nametagapi.api.NickPlayer;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /nickrank command - dedicated UI to manage the LuckPerms fake rank
 * that gets spoofed alongside a nickname.
 *
 * Subcommands:
 *   /nickrank list [all|assignable]   - list available ranks with prefix/suffix preview
 *   /nickrank set <rank>              - set your (or someone's) fake rank
 *   /nickrank clear                   - clear your (or someone's) fake rank
 *   /nickrank random                  - pick a random assignable rank
 *   /nickrank current                 - show your current fake rank
 *
 * Requires the LuckPerms plugin to be installed and the plugin to have hooked into it.
 * Permissions:
 *   wnick.commands.nickrank           - access to /nickrank
 *   wnick.commands.nickrank.list      - list ranks
 *   wnick.commands.nickrank.set       - set a rank
 *   wnick.commands.nickrank.clear     - clear a rank
 *   wnick.commands.nickrank.random    - random rank
 *   wnick.commands.nickrank.current   - view current rank
 *   wnick.commands.nickrank.others    - act on other players
 */
public class NickRankCommand extends CommandAPICommand {

   public NickRankCommand(NameTagPlugin plugin) {
      super("nickrank");
      this.withPermission("wnick.commands.nickrank");
      this.withAliases("nr", "fakerank");

      // /nickrank list [all|assignable]
      this.withSubcommand(
         new CommandAPICommand("list")
            .withPermission("wnick.commands.nickrank.list")
            .withOptionalArguments(
               new Argument[]{
                  new StringArgument("filter")
                     .replaceSuggestions(
                        dev.jorel.commandapi.nametag.arguments.ArgumentSuggestions.strings(
                           s -> new String[]{"assignable", "all"}
                        )
                     )
               }
            )
            .executes((sender, args) -> {
               if (plugin.getFakeRankManager() == null) {
                  sender.sendMessage(MiniMessageHelper.deserialize("<red>LuckPerms is not installed. Fake rank support is disabled."));
                  return;
               }
               String filter = (String) args.get("filter");
               boolean onlyAssignable = !"all".equalsIgnoreCase(filter);
               List<FakeRankManager.FakeRank> ranks = onlyAssignable
                  ? plugin.getFakeRankManager().getAssignableRanks()
                  : plugin.getFakeRankManager().getAllRanks();

               if (ranks.isEmpty()) {
                  sender.sendMessage(
                     MiniMessageHelper.deserialize(
                        "<yellow>No " + (onlyAssignable ? "assignable" : "") + " ranks found."
                           + (onlyAssignable ? " Tag a LuckPerms group with the permission <gray>wnick.rank.assignable</gray> to make it assignable." : "")
                     )
                  );
                  return;
               }

               sender.sendMessage(MiniMessageHelper.deserialize("<gold><bold>Available ranks" + (onlyAssignable ? " (assignable)" : "") + ":"));
               for (FakeRankManager.FakeRank rank : ranks) {
                  String prefix = rank.prefix();
                  String suffix = rank.suffix();
                  String preview = prefix + "<name>" + suffix;
                  Component line = Component.empty()
                     .append(Component.text(" - ", NamedTextColor.GRAY))
                     .append(Component.text(rank.id(), NamedTextColor.WHITE))
                     .append(Component.text("  (", NamedTextColor.DARK_GRAY));
                  // Render prefix/suffix as legacy (they typically come from LuckPerms as '&c[VIP] ' style strings).
                  if (!prefix.isEmpty()) {
                     line = line.append(LegacyComponentSerializer.legacyAmpersand().deserialize(prefix));
                  } else {
                     line = line.append(Component.text("(no prefix)", NamedTextColor.DARK_GRAY));
                  }
                  line = line.append(Component.text(" / ", NamedTextColor.DARK_GRAY));
                  if (!suffix.isEmpty()) {
                     line = line.append(LegacyComponentSerializer.legacyAmpersand().deserialize(suffix));
                  } else {
                     line = line.append(Component.text("(no suffix)", NamedTextColor.DARK_GRAY));
                  }
                  line = line.append(Component.text(")", NamedTextColor.DARK_GRAY));
                  sender.sendMessage(line);
               }
               sender.sendMessage(MiniMessageHelper.deserialize("<gray>Use <white>/nickrank set <rank></white> to apply a rank to yourself."));
            }, new ExecutorType[0])
      );

      // /nickrank set <rank> [targets]
      this.withSubcommand(
         new CommandAPICommand("set")
            .withPermission("wnick.commands.nickrank.set")
            .withArguments(
               new Argument[]{
                  new StringArgument("rank").replaceSuggestions(
                     dev.jorel.commandapi.nametag.arguments.ArgumentSuggestions.strings(
                        s -> {
                           if (plugin.getFakeRankManager() == null) {
                              return new String[0];
                           }
                           List<String> ids = new ArrayList<>();
                           for (FakeRankManager.FakeRank r : plugin.getFakeRankManager().getAllRanks()) {
                              ids.add(r.id());
                           }
                           return ids.toArray(new String[0]);
                        }
                     )
                  )
               }
            )
            .withOptionalArguments(
               new Argument[]{
                  new EntitySelectorArgument.ManyPlayers("targets")
                     .withPermission("wnick.commands.nickrank.others")
               }
            )
            .executes((sender, args) -> {
               if (plugin.getFakeRankManager() == null) {
                  sender.sendMessage(MiniMessageHelper.deserialize("<red>LuckPerms is not installed. Fake rank support is disabled."));
                  return;
               }
               String rankInput = (String) args.get("rank");
               String resolvedId = plugin.getFakeRankManager().resolveRankIdCaseInsensitive(rankInput);
               if (resolvedId == null) {
                  sender.sendMessage(MiniMessageHelper.deserialize("<red>Unknown rank: " + rankInput));
                  sender.sendMessage(MiniMessageHelper.deserialize("<gray>Use <white>/nickrank list</white> to see available ranks."));
                  return;
               }
               FakeRankManager.FakeRank rank = plugin.getFakeRankManager().getRank(resolvedId);
               if (rank == null) {
                  sender.sendMessage(MiniMessageHelper.deserialize("<red>Unknown rank: " + rankInput));
                  return;
               }

               @SuppressWarnings("unchecked")
               List<Player> targets = (List<Player>) args.get("targets");
               if (targets == null || targets.isEmpty()) {
                  if (!(sender instanceof Player self)) {
                     sender.sendMessage(MiniMessageHelper.deserialize("<red>You must specify a target when running from console."));
                     return;
                  }
                  targets = List.of(self);
               }

               for (Player target : targets) {
                  if (!sender.hasPermission("wnick.commands.nickrank.others") && target != sender) {
                     sender.sendMessage(MiniMessageHelper.deserialize("<red>You do not have permission to change other players' fake rank."));
                     return;
                  }
                  applyFakeRank(plugin, target, resolvedId);
                  String preview = rank.prefix() + (target.getName()) + rank.suffix();
                  sender.sendMessage(
                     MiniMessageHelper.deserialize("<green>Set fake rank of </green>")
                        .append(LegacyComponentSerializer.legacySection().deserialize(target.getName()))
                        .append(MiniMessageHelper.deserialize(" <green>to </green>"))
                        .append(MiniMessageHelper.deserialize("<white>" + rank.id() + "</white>"))
                  );
                  if (!rank.prefix().isEmpty() || !rank.suffix().isEmpty()) {
                     sender.sendMessage(
                        MiniMessageHelper.deserialize("<gray>Preview: ")
                           .append(LegacyComponentSerializer.legacyAmpersand().deserialize(preview))
                     );
                  }
                  if (!plugin.hasNick(target)) {
                     sender.sendMessage(MiniMessageHelper.deserialize("<yellow>Note: " + target.getName() + " is not currently nicked. The rank will apply once they /nick."));
                  }
               }
            }, new ExecutorType[0])
      );

      // /nickrank clear [targets]
      this.withSubcommand(
         new CommandAPICommand("clear")
            .withPermission("wnick.commands.nickrank.clear")
            .withOptionalArguments(
               new Argument[]{
                  new EntitySelectorArgument.ManyPlayers("targets")
                     .withPermission("wnick.commands.nickrank.others")
               }
            )
            .executes((sender, args) -> {
               @SuppressWarnings("unchecked")
               List<Player> targets = (List<Player>) args.get("targets");
               if (targets == null || targets.isEmpty()) {
                  if (!(sender instanceof Player self)) {
                     sender.sendMessage(MiniMessageHelper.deserialize("<red>You must specify a target when running from console."));
                     return;
                  }
                  targets = List.of(self);
               }
               for (Player target : targets) {
                  if (!sender.hasPermission("wnick.commands.nickrank.others") && target != sender) {
                     sender.sendMessage(MiniMessageHelper.deserialize("<red>You do not have permission to clear other players' fake rank."));
                     return;
                  }
                  plugin.clearFakeRank(target);
                  sender.sendMessage(
                     MiniMessageHelper.deserialize("<green>Cleared fake rank of </green>")
                        .append(LegacyComponentSerializer.legacySection().deserialize(target.getName()))
                  );
               }
            }, new ExecutorType[0])
      );

      // /nickrank random [targets]
      this.withSubcommand(
         new CommandAPICommand("random")
            .withPermission("wnick.commands.nickrank.random")
            .withOptionalArguments(
               new Argument[]{
                  new EntitySelectorArgument.ManyPlayers("targets")
                     .withPermission("wnick.commands.nickrank.others")
               }
            )
            .executes((sender, args) -> {
               if (plugin.getFakeRankManager() == null) {
                  sender.sendMessage(MiniMessageHelper.deserialize("<red>LuckPerms is not installed. Fake rank support is disabled."));
                  return;
               }
               FakeRankManager.FakeRank random = plugin.getFakeRankManager().getRandomRank();
               if (random == null) {
                  sender.sendMessage(MiniMessageHelper.deserialize("<red>No assignable ranks found. Tag a LuckPerms group with the permission <gray>wnick.rank.assignable</gray> to make it assignable."));
                  return;
               }
               @SuppressWarnings("unchecked")
               List<Player> targets = (List<Player>) args.get("targets");
               if (targets == null || targets.isEmpty()) {
                  if (!(sender instanceof Player self)) {
                     sender.sendMessage(MiniMessageHelper.deserialize("<red>You must specify a target when running from console."));
                     return;
                  }
                  targets = List.of(self);
               }
               for (Player target : targets) {
                  if (!sender.hasPermission("wnick.commands.nickrank.others") && target != sender) {
                     sender.sendMessage(MiniMessageHelper.deserialize("<red>You do not have permission to change other players' fake rank."));
                     return;
                  }
                  applyFakeRank(plugin, target, random.id());
                  sender.sendMessage(
                     MiniMessageHelper.deserialize("<green>Assigned random fake rank </green>")
                        .append(MiniMessageHelper.deserialize("<white>" + random.id() + "</white>"))
                        .append(MiniMessageHelper.deserialize("<green> to </green>"))
                        .append(LegacyComponentSerializer.legacySection().deserialize(target.getName()))
                  );
               }
            }, new ExecutorType[0])
      );

      // /nickrank current [target]
      this.withSubcommand(
         new CommandAPICommand("current")
            .withPermission("wnick.commands.nickrank.current")
            .withOptionalArguments(
               new Argument[]{
                  new EntitySelectorArgument.OnePlayer("target")
                     .withPermission("wnick.commands.nickrank.others")
               }
            )
            .executes((sender, args) -> {
               Player target;
               if (args.get("target") instanceof Player p) {
                  if (!sender.hasPermission("wnick.commands.nickrank.others") && p != sender) {
                     sender.sendMessage(MiniMessageHelper.deserialize("<red>You do not have permission to view other players' fake rank."));
                     return;
                  }
                  target = p;
               } else if (sender instanceof Player self) {
                  target = self;
               } else {
                  sender.sendMessage(MiniMessageHelper.deserialize("<red>You must specify a target when running from console."));
                  return;
               }
               NickPlayer data = plugin.getPlayerCache().get(target.getUniqueId());
               String rankId = data != null ? data.getFakeRankId() : null;
               if (rankId == null) {
                  sender.sendMessage(
                     MiniMessageHelper.deserialize("<gray>" + target.getName() + " has no fake rank set.</gray>")
                  );
                  return;
               }
               if (plugin.getFakeRankManager() == null) {
                  sender.sendMessage(
                     MiniMessageHelper.deserialize("<yellow>" + target.getName() + " has fake rank id <white>" + rankId + "</white>, but LuckPerms is no longer available to resolve its details.</yellow>")
                  );
                  return;
               }
               FakeRankManager.FakeRank rank = plugin.getFakeRankManager().getRank(rankId);
               if (rank == null) {
                  sender.sendMessage(
                     MiniMessageHelper.deserialize("<yellow>" + target.getName() + " has fake rank id <white>" + rankId + "</white>, but that group no longer exists in LuckPerms.</yellow>")
                  );
                  return;
               }
               Component line = Component.empty()
                  .append(Component.text(target.getName() + " -> ", NamedTextColor.GRAY))
                  .append(Component.text(rank.id(), NamedTextColor.WHITE));
               if (!rank.prefix().isEmpty() || !rank.suffix().isEmpty()) {
                  line = line.append(Component.text("  (", NamedTextColor.DARK_GRAY));
                  if (!rank.prefix().isEmpty()) {
                     line = line.append(LegacyComponentSerializer.legacyAmpersand().deserialize(rank.prefix()));
                  }
                  if (!rank.prefix().isEmpty() && !rank.suffix().isEmpty()) {
                     line = line.append(Component.text(" / ", NamedTextColor.DARK_GRAY));
                  }
                  if (!rank.suffix().isEmpty()) {
                     line = line.append(LegacyComponentSerializer.legacyAmpersand().deserialize(rank.suffix()));
                  }
                  line = line.append(Component.text(")", NamedTextColor.DARK_GRAY));
               }
               sender.sendMessage(line);
            }, new ExecutorType[0])
      );
   }

   /**
    * Apply the fake rank to the given player: writes to the cached NickPlayer,
    * persists it, and refreshes the TAB display (if TAB is hooked).
    *
    * If the player is not currently nicked, the rank is still stored and will be
    * applied automatically next time they /nick.
    */
   private static void applyFakeRank(NameTagPlugin plugin, Player target, String rankId) {
      NickPlayer cached = plugin.getPlayerCache().get(target.getUniqueId());
      if (cached == null) {
         // Load (or create) on the async thread, then mutate.
         plugin.getStorageManager().getStorage().loadPlayer(target.getUniqueId()).thenAccept(nameTagPlayer -> {
            NickPlayer data = nameTagPlayer != null ? nameTagPlayer : new NickPlayer(target.getUniqueId());
            if (data.getOriginalName() == null) {
               data.setOriginalName(target.getName());
            }
            data.setFakeRankId(rankId);
            plugin.getPlayerCache().put(target.getUniqueId(), data);
            plugin.getStorageManager().getStorage().savePlayer(data);
            // Refresh TAB if available
            if (plugin.getServer().getPluginManager().isPluginEnabled("TAB")) {
               plugin.attemptToUpdateTabPlayer(target);
            }
         });
      } else {
         cached.setFakeRankId(rankId);
         plugin.getPlayerCache().put(target.getUniqueId(), cached);
         plugin.getStorageManager().getStorage().savePlayer(cached);
         if (plugin.getServer().getPluginManager().isPluginEnabled("TAB")) {
            plugin.attemptToUpdateTabPlayer(target);
         }
      }
   }
}
