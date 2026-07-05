package wnick.command;

import dev.jorel.commandapi.nametag.CommandAPICommand;
import dev.jorel.commandapi.nametag.arguments.Argument;
import dev.jorel.commandapi.nametag.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.nametag.executors.ExecutorType;
import wnick.util.MiniMsg;
import wnick.NameTagPlugin;
import wnick.NickPlayer;
import org.bukkit.entity.Player;

public class RealNameCommand extends CommandAPICommand {
   public RealNameCommand(NameTagPlugin plugin) {
      super("realname");
      this.withPermission("wnick.commands.realname");
      this.withArguments(new Argument[]{new EntitySelectorArgument.OnePlayer("target")});
      this.executes((sender, args) -> {
         if (args.get(0) instanceof Player target) {
            NickPlayer nickPlayer = plugin.getPlayerCache().get(target.getUniqueId());
            if (nickPlayer == null || !nickPlayer.hasNick()) {
               sender.sendMessage(MiniMsg.deserialize("<gray>" + target.getName() + " is not currently nicked."));
               return;
            }

            String originalName = nickPlayer.getOriginalName() != null ? nickPlayer.getOriginalName() : target.getName();
            String uuid = nickPlayer.getOriginalUniqueId().toString();
            sender.sendMessage(MiniMsg.deserialize("<gray>Real name: <white>" + originalName));
            sender.sendMessage(MiniMsg.deserialize("<gray>UUID: <white>" + uuid));
         }
      }, new ExecutorType[0]);
   }
}
