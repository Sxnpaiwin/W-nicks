package gg.lode.nametag.command;

import dev.jorel.commandapi.nametag.CommandAPICommand;
import dev.jorel.commandapi.nametag.arguments.Argument;
import dev.jorel.commandapi.nametag.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.nametag.executors.ExecutorType;
import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.nametag.NameTagPlugin;
import gg.lode.nametagapi.api.NickPlayer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public class NameTagCommand extends CommandAPICommand {
   public NameTagCommand(NameTagPlugin plugin) {
      super("nametag");
      this.withSubcommand(
         new CommandAPICommand("version")
            .executes((dev.jorel.commandapi.nametag.executors.CommandExecutor) (sender, args) -> sender.sendMessage(MiniMessageHelper.deserialize("Running W-Nick: v1.0.81-W-Nick (LuckPerms extended fork of Name-Tag)")), new ExecutorType[0])
      );
      this.withSubcommand(
         new CommandAPICommand("refresh_player")
            .withPermission("lodestone.commands.nametag.refresh_player")
            .withArguments(new Argument[]{new EntitySelectorArgument.OnePlayer("target")})
            .executes((sender, args) -> {
               if (args.get(0) instanceof Player target) {
                  plugin.getPaperSkinManager().refreshPlayer(target);
                  sender.sendMessage(MiniMessageHelper.deserialize("Successfully refreshed " + target.getName()));
               }
            }, new ExecutorType[0])
      );
      this.withSubcommand(new CommandAPICommand("reload").withPermission("lodestone.commands.nametag.reload").executes((sender, args) -> {
         plugin.config().initialize();
         sender.sendMessage(MiniMessageHelper.deserialize("<gray><italic>[Name Tag configuration has been reloaded]"));
      }, new ExecutorType[0]));
      this.withSubcommand(
         new CommandAPICommand("debug")
            .withPermission("lodestone.commands.nametag.debug")
            .withArguments(new Argument[]{new EntitySelectorArgument.OnePlayer("target")})
            .executes((sender, args) -> {
               if (args.get(0) instanceof Player target) {
                  NickPlayer nickPlayer = plugin.getPlayerCache().get(target.getUniqueId());
                  if (nickPlayer == null) {
                     sender.sendMessage(MiniMessageHelper.deserialize("Name: " + target.getName()));
                     sender.sendMessage(MiniMessageHelper.deserialize("UUID: " + target.getUniqueId()));
                     sender.sendMessage(MiniMessageHelper.deserialize("<red>" + target.getName() + " is not nicked!"));
                     return;
                  }

                  sender.sendMessage(MiniMessageHelper.deserialize("Original Name: " + nickPlayer.getOriginalName()));
                  sender.sendMessage(MiniMessageHelper.deserialize("Original UUID: " + nickPlayer.getOriginalUniqueId()));
                  sender.sendMessage(Component.empty());
                  sender.sendMessage(MiniMessageHelper.deserialize("Nickname: " + nickPlayer.getNickname()));
                  sender.sendMessage(MiniMessageHelper.deserialize("Nicked UUID: " + nickPlayer.getUuid()));
               }
            }, new ExecutorType[0])
      );
   }
}
