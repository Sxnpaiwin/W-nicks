package gg.lode.nametag.command;

import dev.jorel.commandapi.nametag.CommandAPICommand;
import dev.jorel.commandapi.nametag.arguments.Argument;
import dev.jorel.commandapi.nametag.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.nametag.executors.ExecutorType;
import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.nametag.NameTagPlugin;
import gg.lode.nametag.util.FakeRankManager;
import gg.lode.nametagapi.api.NickPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /wnick — the W-Nick master command.
 *
 *   /wnick                - show the help screen
 *   /wnick help           - show the help screen
 *   /wnick info [player]  - show all known nick state for yourself (or someone else)
 *   /wnick version        - show the installed version
 *   /wnick reload         - reload config.yml (alias of /nametag reload)
 *
 * The classic /nick, /randomnick, /nickrank commands still exist; this is
 * just a friendlier entry point that surfaces everything W-Nick can do.
 */
public class WNickCommand extends CommandAPICommand {

   public WNickCommand(NameTagPlugin plugin) {
      super("wnick");
      this.withPermission("wnick.commands.wnick");
      this.withAliases("wn");

      // No-arg: show help
      this.executes((dev.jorel.commandapi.nametag.executors.CommandExecutor) (sender, args) -> sendHelp(sender, plugin), new ExecutorType[0]);

      // /wnick help
      this.withSubcommand(
         new CommandAPICommand("help")
            .executes((dev.jorel.commandapi.nametag.executors.CommandExecutor) (sender, args) -> sendHelp(sender, plugin), new ExecutorType[0])
      );

      // /wnick guide — opens an interactive Paper Dialog (requires Paper 1.21.8+)
      this.withSubcommand(new WNickGuideCommand(plugin));

      // /wnick version
      this.withSubcommand(
         new CommandAPICommand("version")
            .executes(
               (dev.jorel.commandapi.nametag.executors.CommandExecutor) (sender, args) -> sender.sendMessage(
                  MiniMessageHelper.deserialize(
                     "<gold>W-Nick <gray>(fork of Name-Tag v1.0.81)</gray> — version <white>"
                        + NameTagPlugin.VERSION + "</white>"
                  )
               ),
               new ExecutorType[0]
            )
      );

      // /wnick info [target]
      this.withSubcommand(
         new CommandAPICommand("info")
            .withPermission("wnick.commands.wnick.info")
            .withOptionalArguments(
               new Argument[]{
                  new EntitySelectorArgument.OnePlayer("target")
                     .withPermission("wnick.commands.wnick.info.others")
               }
            )
            .executes((sender, args) -> {
               Player target;
               if (args.get("target") instanceof Player p) {
                  if (!sender.hasPermission("wnick.commands.wnick.info.others") && p != sender) {
                     sender.sendMessage(plugin.prefixedMessage("<red>You do not have permission to view other players' info."));
                     return;
                  }
                  target = p;
               } else if (sender instanceof Player self) {
                  target = self;
               } else {
                  sender.sendMessage(plugin.prefixedMessage("<red>You must specify a target when running from console."));
                  return;
               }

               NickPlayer data = plugin.getPlayerCache().get(target.getUniqueId());
               sendInfo(sender, plugin, target, data);
            }, new ExecutorType[0])
      );

      // /wnick reload
      this.withSubcommand(
         new CommandAPICommand("reload")
            .withPermission("wnick.admin.reload")
            .executes((sender, args) -> {
               plugin.config().initialize();
               sender.sendMessage(plugin.prefixedMessage("<gray><italic>Configuration reloaded."));
            }, new ExecutorType[0])
      );
   }

   private static void sendHelp(CommandSender sender, NameTagPlugin plugin) {
      sender.sendMessage(MiniMessageHelper.deserialize("<gold><bold>W-Nick</bold> <gray>by Joehe</gray>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<yellow>/nick <reset|as|with_name|with_skin|from_url> [name]"));
      sender.sendMessage(MiniMessageHelper.deserialize("<gray>   Set, reset, or change your nick. Add <white>-r <rank></white> to spoof a LuckPerms rank.</gray>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<yellow>/randomnick [selector] [-r <rank>] [--skip]"));
      sender.sendMessage(MiniMessageHelper.deserialize("<gray>   Generate a random nick (optionally with a specific rank).</gray>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<yellow>/nickrank <list|set|clear|random|current>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<gray>   Manage the LuckPerms fake rank independently of your nick.</gray>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<yellow>/realname <nick>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<gray>   Look up who's behind a nick.</gray>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<yellow>/wnick guide"));
      sender.sendMessage(MiniMessageHelper.deserialize("<gray>   Open this help as an interactive in-game dialog (Paper 1.21.8+).</gray>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<yellow>/wnick info [player]"));
      sender.sendMessage(MiniMessageHelper.deserialize("<gray>   Show all known nick state for yourself or someone else.</gray>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<yellow>/wnick version / reload"));
      sender.sendMessage(MiniMessageHelper.deserialize("<gray>   Version info and config reload.</gray>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
      if (plugin.getFakeRankManager() == null) {
         sender.sendMessage(MiniMessageHelper.deserialize("<red>LuckPerms is not installed. Rank-spoofing features are disabled.</red>"));
      } else {
         int total = plugin.getFakeRankManager().getAllRanks().size();
         int assignable = plugin.getFakeRankManager().getAssignableRanks().size();
         sender.sendMessage(MiniMessageHelper.deserialize(
            "<gray>LuckPerms hooked: <green>yes</green>. <white>" + total + "</white> groups, <white>" + assignable + "</white> assignable.</gray>"
         ));
      }
   }

   private static void sendInfo(CommandSender sender, NameTagPlugin plugin, Player target, NickPlayer data) {
      Component header = Component.empty()
         .append(Component.text("━━ ", NamedTextColor.DARK_GRAY))
         .append(Component.text(target.getName(), NamedTextColor.GOLD))
         .append(Component.text(" ━━", NamedTextColor.DARK_GRAY));
      sender.sendMessage(header);

      if (data == null) {
         sender.sendMessage(Component.text("  No nick data cached.", NamedTextColor.GRAY));
         sender.sendMessage(Component.text("  Use /nick to set a nick.", NamedTextColor.DARK_GRAY));
         return;
      }

      line(sender, "UUID", target.getUniqueId().toString());
      line(sender, "Original name", data.getOriginalName() != null ? data.getOriginalName() : "(unknown)");
      line(sender, "Current nick", data.getNickname() != null ? data.getNickname() : "(not nicked)");
      line(sender, "Skin name", data.getSkinName() != null ? data.getSkinName() : "(default)");
      line(sender, "Has custom skin", String.valueOf(data.getSkin() != null));
      line(sender, "Fake rank id", data.getFakeRankId() != null ? data.getFakeRankId() : "(none)");

      if (data.getFakeRankId() != null && plugin.getFakeRankManager() != null) {
         FakeRankManager.FakeRank rank = plugin.getFakeRankManager().getRank(data.getFakeRankId());
         if (rank != null) {
            Component rankLine = Component.empty()
               .append(Component.text("  Rank preview: ", NamedTextColor.GRAY));
            String preview = rank.prefix() + (data.getNickname() != null ? data.getNickname() : target.getName()) + rank.suffix();
            rankLine = rankLine.append(LegacyComponentSerializer.legacyAmpersand().deserialize(preview));
            sender.sendMessage(rankLine);
         } else {
            sender.sendMessage(Component.text("  Rank preview: (group no longer exists in LuckPerms)", NamedTextColor.RED));
         }
      }

      // Original UUID (for spoofed-uuid mode)
      if (data.getOriginalUniqueId() != null) {
         line(sender, "Original UUID", data.getOriginalUniqueId().toString());
      }
   }

   private static void line(CommandSender sender, String key, String value) {
      sender.sendMessage(
         Component.empty()
            .append(Component.text("  " + key + ": ", NamedTextColor.GRAY))
            .append(Component.text(value, NamedTextColor.WHITE))
      );
   }
}
