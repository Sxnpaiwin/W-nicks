package wnick.util;

import wnick.NameTagPlugin;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * Pure-reflection integration with the TAB plugin
 * (https://github.com/NEZNAMY/TAB).
 *
 * Why reflection instead of a compile-time dependency?
 *
 * The TAB API has changed method signatures across versions. The most notable
 * break is {@code EventBus.register(Class, ...)}:
 *
 *   - Older TAB:  register(Class<?>, Consumer<?>)
 *   - Current TAB: register(Class<E extends TabEvent>, EventHandler<E>)
 *
 * If W-Nick compiled against the old signature, it throws NoSuchMethodError
 * at runtime when loaded against the new one (and vice versa). Using
 * reflection + java.lang.reflect.Proxy lets us implement whichever functional
 * interface the runtime TAB exposes, without linking to any specific
 * signature at compile time.
 *
 * Every reflective call is wrapped so that ANY version mismatch degrades
 * gracefully: if TAB's API changes in a way we can't handle, the plugin
 * logs a warning and continues without TAB integration (the core nick /
 * fake-rank features still work).
 *
 * The managers we use (all confirmed present in current TAB API):
 *   - TabAPI.getInstance()                       -> TabAPI
 *   - TabAPI.getEventBus()                       -> EventBus
 *   - EventBus.register(Class, EventHandler)     -> void
 *   - TabAPI.getPlayer(UUID)                     -> TabPlayer
 *   - TabAPI.getTabListFormatManager()           -> TabListFormatManager
 *   - TabListFormatManager.setName/setPrefix/setSuffix(TabPlayer, String)
 *   - TabAPI.getNameTagManager()                 -> NameTagManager
 *   - NameTagManager.setPrefix/setSuffix(TabPlayer, String)
 *   - TabPlayer.getName()                        -> String
 *   - TabPlayer.getUniqueId()                    -> UUID
 */
public final class TabIntegration {

   private final NameTagPlugin plugin;
   private final Logger logger;

   // Cached reflection metadata (looked up once on hook)
   private Class<?>  tabApiClass;
   private Method    getInstanceMethod;
   private Method    getEventBusMethod;
   private Method    getPlayerMethod;
   private Method    getTabListFormatManagerMethod;
   private Method    getNameTagManagerMethod;
   private Method    eventBusRegisterMethod;
   private Class<?>  eventHandlerClass;        // me.neznamy.tab.api.event.EventHandler
   private Method    eventHandlerHandleMethod; // EventHandler.handle(TabEvent)

   // Manager method references (TabListFormatManager + NameTagManager)
   private Method    tlfSetNameMethod;
   private Method    tlfSetPrefixMethod;
   private Method    tlfSetSuffixMethod;
   private Method    ntSetPrefixMethod;
   private Method    ntSetSuffixMethod;
   // TabPlayer.getName / getUniqueId
   private Method    tabPlayerGetNameMethod;
   private Method    tabPlayerGetUniqueIdMethod;

   private boolean   initialized = false;
   private boolean   hookSucceeded = false;
   // The PlayerLoadEvent class (so we can subscribe to it)
   private Class<?>  playerLoadEventClass;

   public TabIntegration(NameTagPlugin plugin) {
      this.plugin = plugin;
      this.logger = plugin.getLogger();
   }

   /**
    * Attempt to look up all the reflective metadata needed for the TAB hook.
    * Returns true if the hook is ready to use; false if TAB isn't installed
    * or its API doesn't match what we expect (in which case the plugin
    * should continue without TAB integration).
    */
   public boolean hook() {
      if (initialized) return hookSucceeded;
      initialized = true;

      try {
         tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
         getInstanceMethod = tabApiClass.getMethod("getInstance");
         getEventBusMethod = tabApiClass.getMethod("getEventBus");
         getPlayerMethod   = tabApiClass.getMethod("getPlayer", UUID.class);
         getTabListFormatManagerMethod = tabApiClass.getMethod("getTabListFormatManager");
         getNameTagManagerMethod       = tabApiClass.getMethod("getNameTagManager");

         // PlayerLoadEvent — subscribe target
         try {
            playerLoadEventClass = Class.forName("me.neznamy.tab.api.event.player.PlayerLoadEvent");
         } catch (ClassNotFoundException ex) {
            playerLoadEventClass = null;
            logger.warning("[TAB] PlayerLoadEvent class not found; player-load hook disabled (continuing without).");
         }

         // EventHandler interface (current TAB API)
         try {
            eventHandlerClass = Class.forName("me.neznamy.tab.api.event.EventHandler");
            eventHandlerHandleMethod = eventHandlerClass.getMethod("handle", Object.class);
         } catch (ClassNotFoundException ex) {
            eventHandlerClass = null;
            eventHandlerHandleMethod = null;
         }

         // EventBus.register(Class, ?) — try the new signature first (Class, EventHandler),
         // then fall back to the old signature (Class, Consumer).
         eventBusRegisterMethod = findEventBusRegisterMethod();
         if (eventBusRegisterMethod == null) {
            logger.warning("[TAB] Could not find EventBus.register(Class, ...) — TAB hook disabled. W-Nick will run without TAB integration.");
            return false;
         }

         // TabListFormatManager methods
         Class<?> tlfClass = Class.forName("me.neznamy.tab.api.tablist.TabListFormatManager");
         tlfSetNameMethod  = tlfClass.getMethod("setName", tabApiClass.getMethod("getPlayer", UUID.class).getReturnType(), String.class);
         tlfSetPrefixMethod = tlfClass.getMethod("setPrefix", tlfSetNameMethod.getParameterTypes()[0], String.class);
         tlfSetSuffixMethod = tlfClass.getMethod("setSuffix", tlfSetNameMethod.getParameterTypes()[0], String.class);

         // NameTagManager methods
         Class<?> ntClass = Class.forName("me.neznamy.tab.api.nametag.NameTagManager");
         ntSetPrefixMethod = ntClass.getMethod("setPrefix", tlfSetNameMethod.getParameterTypes()[0], String.class);
         ntSetSuffixMethod = ntClass.getMethod("setSuffix", tlfSetNameMethod.getParameterTypes()[0], String.class);

         // TabPlayer methods
         Class<?> tabPlayerClass = Class.forName("me.neznamy.tab.api.TabPlayer");
         tabPlayerGetNameMethod    = tabPlayerClass.getMethod("getName");
         tabPlayerGetUniqueIdMethod = tabPlayerClass.getMethod("getUniqueId");

         hookSucceeded = true;
         return true;
      } catch (Throwable t) {
         // ClassNotFoundException, NoSuchMethodException, LinkageError, etc.
         logger.warning("[TAB] Failed to initialise TAB integration: " + t.getClass().getSimpleName() + ": " + t.getMessage());
         logger.warning("[TAB] W-Nick will continue without TAB integration. Nicknames and fake ranks still work; only TAB-driven prefix/suffix rendering is disabled.");
         hookSucceeded = false;
         return false;
      }
   }

   /**
    * Try to find an EventBus.register(Class, ...) method, regardless of
    * whether the second parameter is the new EventHandler or the old Consumer.
    */
   private Method findEventBusRegisterMethod() {
      try {
         Class<?> eventBusClass = Class.forName("me.neznamy.tab.api.event.EventBus");
         for (Method m : eventBusClass.getMethods()) {
            if (!"register".equals(m.getName())) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 2 && params[0] == Class.class) {
               // Found it. Either (Class, EventHandler) or (Class, Consumer) — we'll
               // build a Proxy implementing the right interface when subscribing.
               return m;
            }
         }
      } catch (Throwable ignored) {}
      return null;
   }

   /**
    * Register a listener for TAB's PlayerLoadEvent — when TAB finishes loading
    * a player, we refresh their nick/rank in TAB's tab-list and nametag managers.
    */
   public void registerPlayerLoadListener() {
      if (!hookSucceeded) return;
      if (playerLoadEventClass == null) return;
      try {
         Object tabApi = getInstanceMethod.invoke(null);
         Object eventBus = getEventBusMethod.invoke(tabApi);
         if (eventBus == null) {
            logger.warning("[TAB] EventBus is null; player-load hook disabled.");
            return;
         }

         // Build the handler argument as a Proxy implementing whatever
         // functional interface the discovered register() method expects.
         Class<?> handlerParamType = eventBusRegisterMethod.getParameterTypes()[1];
         Object handlerProxy = buildHandlerProxy(handlerParamType, event -> {
            try {
               Method getPlayer = event.getClass().getMethod("getPlayer");
               Object tabPlayer = getPlayer.invoke(event);
               if (tabPlayer != null) {
                  updateTabPlayer(tabPlayer);
               }
            } catch (Throwable t) {
               logger.warning("[TAB] Failed to handle PlayerLoadEvent: " + t.getMessage());
            }
         });

         if (handlerProxy == null) {
            logger.warning("[TAB] Could not build handler proxy for type " + handlerParamType.getName() + "; player-load hook disabled.");
            return;
         }

         eventBusRegisterMethod.invoke(eventBus, playerLoadEventClass, handlerProxy);
         logger.warning("==========================================");
         logger.warning("Hooked into TAB!");
         logger.warning("W-Nick will now use TAB to display nicknames.");
         logger.warning("==========================================");
      } catch (Throwable t) {
         logger.warning("[TAB] Failed to register player-load listener: " + t.getClass().getSimpleName() + ": " + t.getMessage());
         logger.warning("[TAB] W-Nick will continue without TAB player-load hook. Manual /wnick info will still work.");
      }
   }

   /**
    * Build a Proxy implementing the given functional interface (either
    * me.neznamy.tab.api.event.EventHandler or java.util.function.Consumer),
    * forwarding the single abstract method's invocation to the given callback.
    */
   private Object buildHandlerProxy(Class<?> interfaceType, Consumer<Object> callback) {
      if (!interfaceType.isInterface()) return null;
      return Proxy.newProxyInstance(
         interfaceType.getClassLoader(),
         new Class<?>[] { interfaceType },
         (proxy, method, args) -> {
            // Any method called on the proxy with one argument forwards to callback.
            if (args != null && args.length == 1) {
               callback.accept(args[0]);
               return null;
            }
            // Object methods (toString, hashCode, equals) — return defaults
            if ("toString".equals(method.getName())) return "WNickTabHandler";
            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
            if ("equals".equals(method.getName()))   return proxy == args[0];
            return null;
         }
      );
   }

   /**
    * Look up the TabPlayer for a Bukkit Player and push the W-Nick nickname
    * + fake rank prefix/suffix to TAB.
    */
   public void refreshPlayer(Player bukkitPlayer) {
      if (!hookSucceeded) return;
      try {
         Object tabApi = getInstanceMethod.invoke(null);
         Object tabPlayer = getPlayerMethod.invoke(tabApi, bukkitPlayer.getUniqueId());
         if (tabPlayer == null) {
            // TAB hasn't loaded this player yet — will be picked up on PlayerLoadEvent.
            return;
         }
         updateTabPlayer(tabPlayer);
      } catch (Throwable t) {
         logger.warning("[TAB] Failed to refresh player " + bukkitPlayer.getName() + ": " + t.getMessage());
      }
   }

   /**
    * Push the current nick + fake rank to TAB for a TabPlayer.
    */
   private void updateTabPlayer(Object tabPlayer) {
      if (!hookSucceeded) return;
      try {
         UUID uuid = (UUID) tabPlayerGetUniqueIdMethod.invoke(tabPlayer);
         String originalName = (String) tabPlayerGetNameMethod.invoke(tabPlayer);

         boolean nicked = plugin.hasNick(uuid);
         String name = nicked ? plugin.getNick(uuid) : originalName;
         String fakePrefix = null;
         String fakeSuffix = null;

         if (nicked && plugin.getFakeRankManager() != null) {
            wnick.NickPlayer nickData = plugin.getPlayerCache().get(uuid);
            if (nickData != null && nickData.getFakeRankId() != null) {
               wnick.util.FakeRankManager.FakeRank rank = plugin.getFakeRankManager().getRank(nickData.getFakeRankId());
               if (rank != null) {
                  fakePrefix = rank.prefix().isEmpty() ? null : rank.prefix();
                  fakeSuffix = rank.suffix().isEmpty() ? null : rank.suffix();
               }
            }
         }

         Object tabApi = getInstanceMethod.invoke(null);
         Object tlf = getTabListFormatManagerMethod.invoke(tabApi);
         if (tlf != null) {
            tlfSetNameMethod.invoke(tlf, tabPlayer, name);
            tlfSetPrefixMethod.invoke(tlf, tabPlayer, fakePrefix);
            tlfSetSuffixMethod.invoke(tlf, tabPlayer, fakeSuffix);
         }

         Object ntm = getNameTagManagerMethod.invoke(tabApi);
         if (ntm != null) {
            ntSetPrefixMethod.invoke(ntm, tabPlayer, fakePrefix);
            ntSetSuffixMethod.invoke(ntm, tabPlayer, fakeSuffix);
         }

         logger.info("[TAB] Updated " + originalName + " to " + name);
      } catch (Throwable t) {
         logger.warning("[TAB] Failed to update TAB player: " + t.getClass().getSimpleName() + ": " + t.getMessage());
      }
   }
}
