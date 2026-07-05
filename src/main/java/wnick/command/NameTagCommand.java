package wnick.command;

import dev.jorel.commandapi.nametag.CommandAPICommand;
import dev.jorel.commandapi.nametag.arguments.Argument;
import dev.jorel.commandapi.nametag.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.nametag.executors.ExecutorType;
import wnick.util.MiniMsg;
import wnick.NameTagPlugin;
import wnick.NickPlayer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public class NameTagCommand extends CommandAPICommand {
   public NameTagCommand(NameTagPlugin plugin) {
      super("nametag");
      this.withSubcommand(
         new CommandAPICommand("version")
            .executes((dev.jorel.commandapi.nametag.executors.CommandExecutor) (sender, args) -> sender.sendMessage(MiniMsg.deserialize("Running W-Nick: v1.0.81-W-Nick (LuckPerms extended fork of Name-Tag)")), new ExecutorType[0])
      );
      this.withSubcommand(
         new CommandAPICommand("refresh_player")
            .withPermission("wnick.admin.refresh_player")
            .withArguments(new Argument[]{new EntitySelectorArgument.OnePlayer("target")})
            .executes((sender, args) -> {
               if (args.get(0) instanceof Player target) {
                  plugin.getPaperSkinManager().refreshPlayer(target);
                  sender.sendMessage(MiniMsg.deserialize("Successfully refreshed " + target.getName()));
               }
            }, new ExecutorType[0])
      );
      this.withSubcommand(new CommandAPICommand("reload").withPermission("wnick.admin.reload").executes((sender, args) -> {
         plugin.config().initialize();
         sender.sendMessage(MiniMsg.deserialize("<gray><italic>[Name Tag configuration has been reloaded]"));
      }, new ExecutorType[0]));
      this.withSubcommand(
         new CommandAPICommand("debug")
            .withPermission("wnick.admin.debug")
            .withArguments(new Argument[]{new EntitySelectorArgument.OnePlayer("target")})
            .executes((sender, args) -> {
               if (args.get(0) instanceof Player target) {
                  NickPlayer nickPlayer = plugin.getPlayerCache().get(target.getUniqueId());
                  if (nickPlayer == null) {
                     sender.sendMessage(MiniMsg.deserialize("Name: " + target.getName()));
                     sender.sendMessage(MiniMsg.deserialize("UUID: " + target.getUniqueId()));
                     sender.sendMessage(MiniMsg.deserialize("<red>" + target.getName() + " is not nicked!"));
                     return;
                  }

                  sender.sendMessage(MiniMsg.deserialize("Original Name: " + nickPlayer.getOriginalName()));
                  sender.sendMessage(MiniMsg.deserialize("Original UUID: " + nickPlayer.getOriginalUniqueId()));
                  sender.sendMessage(Component.empty());
                  sender.sendMessage(MiniMsg.deserialize("Nickname: " + nickPlayer.getNickname()));
                  sender.sendMessage(MiniMsg.deserialize("Nicked UUID: " + nickPlayer.getUuid()));
               }
            }, new ExecutorType[0])
      );
   }
}
