package wnick.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Task scheduler utility — replaces wnick.util.Tasks.
 */
public final class Tasks {
   public static void run(Plugin plugin, Runnable runnable) {
      Bukkit.getScheduler().runTask(plugin, runnable);
   }

   public static void runAsync(Plugin plugin, Runnable runnable) {
      Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
   }

   public static void later(Plugin plugin, Runnable runnable, long delayTicks) {
      Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
   }
}
