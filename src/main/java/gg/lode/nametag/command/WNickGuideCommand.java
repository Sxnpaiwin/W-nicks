package gg.lode.nametag.command;

import dev.jorel.commandapi.nametag.CommandAPICommand;
import dev.jorel.commandapi.nametag.executors.ExecutorType;
import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.nametag.NameTagPlugin;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /wnick guide — opens an interactive Paper Dialog (introduced in 1.21.7+)
 * that walks the player through everything W-Nick can do.
 *
 * The dialog has multiple action buttons that dispatch real commands
 * (e.g. /nickrank list, /nick random). Each button uses
 * {@link DialogAction#commandTemplate(String)} so the player just clicks
 * and the command runs — no chat typing required.
 *
 * Requires Paper 1.21.8+ (the Dialog API was finalised in 1.21.7-1.21.8).
 */
public class WNickGuideCommand extends CommandAPICommand {

   private static final TextColor ACCENT = TextColor.color(0xFFC857);
   private static final TextColor GREEN  = TextColor.color(0xAEFFC1);
   private static final TextColor RED    = TextColor.color(0xFFA0B1);

   public WNickGuideCommand(NameTagPlugin plugin) {
      super("guide");
      this.withPermission("wnick.commands.wnick.guide");
      this.executes(
         (dev.jorel.commandapi.nametag.executors.CommandExecutor) (sender, args) -> {
            // Console can't open dialogs — fall back to text output.
            if (!(sender instanceof Player player)) {
               sendTextGuide(sender, plugin);
               return;
            }
            player.showDialog(buildGuide(plugin, player));
         },
         new ExecutorType[0]
      );
   }

   /** Build the interactive Paper Dialog for the guide. */
   private static Dialog buildGuide(NameTagPlugin plugin, Player player) {
      List<DialogBody> body = new ArrayList<>();
      body.add(DialogBody.plainMessage(
         Component.text("Welcome to W-Nick!", ACCENT)
      ));
      body.add(DialogBody.plainMessage(
         Component.text("A LuckPerms-aware nick plugin. Pick an option below or close this dialog and explore on your own.", NamedTextColor.GRAY)
      ));
      body.add(DialogBody.plainMessage(
         Component.text("─────────────────────────", NamedTextColor.DARK_GRAY)
      ));
      body.add(DialogBody.plainMessage(
         Component.text("Your status:", NamedTextColor.GOLD)
      ));

      // Show the player's current nick state in the dialog body.
      gg.lode.nametagapi.api.NickPlayer data = plugin.getPlayerCache().get(player.getUniqueId());
      boolean nicked = data != null && data.getNickname() != null;
      String rankId  = data != null ? data.getFakeRankId() : null;
      body.add(DialogBody.plainMessage(
         Component.text("• Nick: ", NamedTextColor.GRAY)
            .append(Component.text(nicked ? data.getNickname() : "(not nicked)", nicked ? GREEN : NamedTextColor.RED))
      ));
      body.add(DialogBody.plainMessage(
         Component.text("• Fake rank: ", NamedTextColor.GRAY)
            .append(Component.text(rankId != null ? rankId : "(none)", rankId != null ? GREEN : NamedTextColor.RED))
      ));
      boolean lpHooked = plugin.getFakeRankManager() != null;
      body.add(DialogBody.plainMessage(
         Component.text("• LuckPerms: ", NamedTextColor.GRAY)
            .append(Component.text(lpHooked ? "hooked" : "NOT installed", lpHooked ? GREEN : RED))
      ));
      body.add(DialogBody.plainMessage(
         Component.text("─────────────────────────", NamedTextColor.DARK_GRAY)
      ));
      body.add(DialogBody.plainMessage(
         Component.text("Click an action below to run a command instantly:", NamedTextColor.GRAY)
      ));

      // Action buttons — each runs a real command via DialogAction.commandTemplate.
      // The "commandTemplate" action is the safest way to dispatch commands from
      // a dialog button; it uses the vanilla command dispatcher, so all permission
      // checks still apply.
      List<ActionButton> actions = new ArrayList<>();
      actions.add(ActionButton.builder(Component.text("List LuckPerms ranks", GREEN))
         .tooltip(Component.text("/nickrank list — see every group with its prefix/suffix preview"))
         .action(DialogAction.commandTemplate("nickrank list"))
         .build());
      actions.add(ActionButton.builder(Component.text("Pick a fake rank", GREEN))
         .tooltip(Component.text("/nickrank set <rank> — opens a list you can click"))
         .action(DialogAction.commandTemplate("nickrank list"))
         .build());
      actions.add(ActionButton.builder(Component.text("Random nick", ACCENT))
         .tooltip(Component.text("/randomnick — generate a random nick with a random rank"))
         .action(DialogAction.commandTemplate("randomnick"))
         .build());
      actions.add(ActionButton.builder(Component.text("Reset my nick", RED))
         .tooltip(Component.text("/nick reset — restore your original name and skin"))
         .action(DialogAction.commandTemplate("nick reset"))
         .build());
      actions.add(ActionButton.builder(Component.text("Show my info", NamedTextColor.AQUA))
         .tooltip(Component.text("/wnick info — full debug view in chat"))
         .action(DialogAction.commandTemplate("wnick info"))
         .build());
      actions.add(ActionButton.builder(Component.text("Help in chat", NamedTextColor.WHITE))
         .tooltip(Component.text("/wnick help — full command list"))
         .action(DialogAction.commandTemplate("wnick help"))
         .build());

      // Exit button — null action just closes the dialog.
      ActionButton exit = ActionButton.builder(Component.text("Close", NamedTextColor.GRAY))
         .tooltip(Component.text("Close this dialog"))
         .action(null)
         .build();

      DialogBase base = DialogBase.builder(Component.text("W-Nick Guide", ACCENT))
         .body(body)
         .canCloseWithEscape(true)
         .pause(false)
         .build();

      // multiAction gives a grid of buttons (4 per row here).
      return Dialog.create(b -> b.empty()
         .base(base)
         .type(DialogType.multiAction(actions, exit, 2)));
   }

   /** Console-safe fallback: dump the guide as chat messages. */
   private static void sendTextGuide(CommandSender sender, NameTagPlugin plugin) {
      sender.sendMessage(MiniMessageHelper.deserialize("<gold><bold>W-Nick Guide</bold></gold>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<yellow>/nick <reset|as|with_name|with_skin|from_url> [name]"));
      sender.sendMessage(MiniMessageHelper.deserialize("<gray>Set, reset, or change your nick. Add <white>-r <rank></white> to spoof a LuckPerms rank.</gray>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<yellow>/randomnick [selector] [-r <rank>] [--skip]"));
      sender.sendMessage(MiniMessageHelper.deserialize("<gray>Generate a random nick (optionally with a specific rank).</gray>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<yellow>/nickrank <list|set|clear|random|current>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<gray>Manage the LuckPerms fake rank independently of your nick.</gray>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<yellow>/wnick guide"));
      sender.sendMessage(MiniMessageHelper.deserialize("<gray>Open this guide as an interactive in-game dialog (player only).</gray>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<yellow>/wnick info [player]"));
      sender.sendMessage(MiniMessageHelper.deserialize("<gray>Show all known nick state for yourself or someone else.</gray>"));
      sender.sendMessage(MiniMessageHelper.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
   }
}
