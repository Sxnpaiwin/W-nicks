# PaperMC Dialog API — Comprehensive Research Report

> Sources: PaperMC docs (https://docs.papermc.io/paper/dev/dialogs/), Paper 1.21.8 JavaDoc,
> Paper-API source on GitHub (`main` branch), and the real-world **UniDialog** wrapper library
> (https://github.com/ProjectUnified/UniDialog). Last verified against docs updated Oct 4, 2025.

---

## 0. TL;DR / Critical corrections to your assumptions

| Your assumption | Reality |
|---|---|
| "Dialogs were added in Paper **1.21.4**" | ❌ **Wrong.** Dialogs were added to **Minecraft in 1.21.6**, and Paper shipped the dev API in **1.21.7**. The 1.21.4 JavaDoc URLs you supplied all return **HTTP 404** (verified). Use `https://jd.papermc.io/paper/1.21.8/...` instead. The docs page itself is "Written for version: 1.21.8". |
| "Show via `player.openDialog(dialog)`" | ❌ **Wrong name in the Paper API.** The Paper/Adventure method is **`player.showDialog(dialog)`** (inherited from `Audience#showDialog(DialogLike)`). `openDialog(...)` is the *Mojang-mappings NMS* method name (`ServerPlayerEntity.openDialog`), used in Fabric/Yarn code — NOT the Paper API. |
| "Are dialogs registered centrally first, or shown ad-hoc?" | ✅ **Both are possible.** Ad-hoc via `Dialog.create(...)` at runtime (no registration needed), OR registered in the dialog registry during bootstrap via `RegistryEvents.DIALOG`. Registry registration is required only if you want to reference the dialog from a command `dialog` argument, or share it across code by key. |
| "Do I have to switch to `paper-plugin.yml`?" | ❌ **No for ad-hoc dialogs.** A plain legacy `JavaPlugin` with `plugin.yml` can build dialogs via `Dialog.create(...)`, show them via `player.showDialog(...)`, and read responses via `PlayerCustomClickEvent` (a normal Bukkit event). `paper-plugin.yml` + a `PluginBootstrap` is required **only** for registry-registered dialogs. |

---

## A. Paper Dialog API

### A.1 Package & class location

Full package: **`io.papermc.paper.dialog`**

The package contains exactly two public types (per the 1.21.8 JavaDoc package summary):

```
Package io.papermc.paper.dialog   @NullMarked
  Interface  Dialog              "Represents a dialog."
  Interface  DialogResponseView  "A view for a possible response to a dialog."
```

The builder/helper types live under **`io.papermc.paper.registry.data.dialog`** (and sub-packages `type`, `body`, `input`, `action`).

### A.2 The `Dialog` interface — verbatim source

```java
// paper-api/src/main/java/io/papermc/paper/dialog/Dialog.java
package io.papermc.paper.dialog;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryBuilderFactory;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.data.InlinedRegistryBuilderProvider;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import java.util.function.Consumer;
import net.kyori.adventure.dialog.DialogLike;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.KeyPattern;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface Dialog extends Keyed, DialogLike {

    @ApiStatus.Experimental
    static Dialog create(final Consumer<RegistryBuilderFactory<Dialog, ? extends DialogRegistryEntry.Builder>> value) {
        return InlinedRegistryBuilderProvider.instance().createDialog(value);
    }

    // Built-in (vanilla) dialogs:
    Dialog CUSTOM_OPTIONS = getDialog("custom_options");
    Dialog QUICK_ACTIONS   = getDialog("quick_actions");
    Dialog SERVER_LINKS    = getDialog("server_links");

    private static Dialog getDialog(@KeyPattern.Value final String value) {
        final Registry<Dialog> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.DIALOG);
        return registry.getOrThrow(Key.key(Key.MINECRAFT_NAMESPACE, value));
    }

    @Deprecated(since = "1.21.8", forRemoval = true)
    @Override
    NamespacedKey getKey();   // Dialogs can exist WITHOUT a key (since 1.21.8)
}
```

Key facts:
- `Dialog` extends `Keyed` **and** Adventure's `net.kyori.adventure.dialog.DialogLike` (this is why `Audience#showDialog(DialogLike)` accepts it).
- Three **built-in** static dialogs: `Dialog.SERVER_LINKS`, `Dialog.QUICK_ACTIONS`, `Dialog.CUSTOM_OPTIONS`.
- The static factory **`Dialog.create(Consumer<RegistryBuilderFactory<Dialog, ? extends DialogRegistryEntry.Builder>>)`** builds a dialog at runtime — **no registry/registration needed**.
- Since 1.21.8, dialogs can exist **without a key**; `getKey()` / `key()` are deprecated for removal. To look up a registered dialog use `RegistryAccess.registryAccess().getRegistry(RegistryKey.DIALOG).get(Key)`.

### A.3 How to SHOW a dialog to a player

Per the docs (verbatim):

> "Dialogs can be shown in-game using the `/dialog show <players> <dialog>` command. Alternatively, you can show them using the API by using **`Audience#showDialog(DialogLike)`**."

Because `org.bukkit.entity.Player` extends `Audience`, the call is:

```java
player.showDialog(dialog);     // correct Paper API
// player.openDialog(...)      // this is NMS/Mojang mappings, NOT the Paper API
```

To **close** a dialog (verbatim from docs):

> "The intended way of using `Adventure#closeDialog()`. The slightly hacky way of using `Player#closeInventory()`."

```java
player.closeDialog();      // Adventure method — keeps previous screens open
// player.closeInventory() // also works but closes any open inventory too
```

### A.4 How to BUILD a dialog — full builder pattern

A dialog is built from two mandatory parts:
1. a **`DialogBase`** (title, body, inputs, after-action behaviour, escape/pause flags)
2. a **`DialogType`** (notice / confirmation / multiAction / dialogList / serverLinks)

The builder entry point is `Dialog.create(...)`, whose `RegistryBuilderFactory` exposes:
- `.empty()` — start a brand-new dialog (most common)
- `.base(DialogBase)` / `.type(DialogType)` — set the two required fields

#### Minimal notice dialog (verbatim from PaperMC docs)

```java
Dialog dialog = Dialog.create(builder -> builder
    .empty()
    .base(DialogBase.builder(Component.text("Title")).build())
    .type(DialogType.notice())
);
player.showDialog(dialog);
```

#### The five `DialogType` factories (verbatim signatures from `DialogType.java`)

```java
// io.papermc.paper.registry.data.dialog.type.DialogType
public sealed interface DialogType permits ConfirmationType, DialogListType,
    MultiActionType, NoticeType, ServerLinksType {

    static ConfirmationType confirmation(ActionButton yesButton, ActionButton noButton);

    static DialogListType dialogList(RegistrySet<Dialog> dialogs,
                                     @Nullable ActionButton exitAction,
                                     @Positive int columns,
                                     @Range(from = 1, to = 1024) int buttonWidth);
    static DialogListType.Builder dialogList(RegistrySet<Dialog> dialogs); // builder variant

    static MultiActionType multiAction(List<ActionButton> actions,
                                       @Nullable ActionButton exitAction,
                                       @Positive int columns);
    static MultiActionType.Builder multiAction(List<ActionButton> actions); // builder variant

    static NoticeType notice();
    static NoticeType notice(ActionButton action);

    static ServerLinksType serverLinks(@Nullable ActionButton exitAction,
                                       @Positive int columns,
                                       @Range(from = 1, to = 1024) int buttonWidth);
}
```

| Type | When to use |
|---|---|
| `notice()` | One OK/dismiss button. Simple info popup. |
| `confirmation(yes, no)` | Yes/No choice. |
| `multiAction(actions, exit, columns)` | A grid of buttons. |
| `dialogList(dialogs, exit, cols, width)` | Opens a nested list of *other registered* dialogs. |
| `serverLinks(exit, cols, width)` | Renders the server-links list. |

#### `DialogBase` builder (verbatim method list from `DialogBase.java`)

```java
// io.papermc.paper.registry.data.dialog.DialogBase
DialogBase.builder(Component title)
    .externalTitle(@Nullable Component externalTitle)   // shown on buttons that open this dialog
    .canCloseWithEscape(boolean)                        // default true
    .pause(boolean)                                     // single-player only
    .afterAction(DialogAfterAction afterAction)         // CLOSE | NONE | WAIT_FOR_RESPONSE
    .body(List<? extends DialogBody> body)
    .inputs(List<? extends DialogInput> inputs)
    .build();

enum DialogAfterAction { CLOSE, NONE, WAIT_FOR_RESPONSE }
```

#### `DialogBody` factory (verbatim from `DialogBody.java`)

```java
// io.papermc.paper.registry.data.dialog.body.DialogBody  (sealed: ItemDialogBody | PlainMessageDialogBody)
DialogBody.plainMessage(Component contents);
DialogBody.plainMessage(Component contents, @Range(from=1,to=1024) int width);
DialogBody.item(ItemStack item);                         // returns ItemDialogBody.Builder
DialogBody.item(ItemStack item, @Nullable PlainMessageDialogBody description,
                boolean showDecorations, boolean showTooltip,
                @Range(from=1,to=256) int width, @Range(from=1,to=256) int height);
```

#### `DialogInput` factory — all four input kinds (verbatim from `DialogInput.java`)

```java
// io.papermc.paper.registry.data.dialog.input.DialogInput
//   sealed: BooleanDialogInput | NumberRangeDialogInput | SingleOptionDialogInput | TextDialogInput

// 1. Boolean (tick box)
DialogInput.bool(String key, Component label);                       // -> BooleanDialogInput.Builder
DialogInput.bool(String key, Component label, boolean initial,
                 String onTrue, String onFalse);                    // quick build

// 2. Number range (slider)
DialogInput.numberRange(String key, Component label, float start, float end);  // -> Builder
DialogInput.numberRange(String key, @Range(1,1024) int width, Component label,
                        String labelFormat, float start, float end,
                        @Nullable Float initial, @Positive @Nullable Float step);

// 3. Single option (radio / dropdown)
DialogInput.singleOption(String key, Component label,
                         List<SingleOptionDialogInput.OptionEntry> entries);   // -> Builder
DialogInput.singleOption(String key, @Range(1,1024) int width, Component label,
                         List<SingleOptionDialogInput.OptionEntry> entries,
                         boolean labelVisible);

// 4. Text field
DialogInput.text(String key, Component label);                       // -> Builder
DialogInput.text(String key, @Range(1,1024) int width, Component label,
                 boolean labelVisible, String initial, @Positive int maxLength,
                 @Nullable TextDialogInput.MultilineOptions multilineOptions);
```

The first argument `key` is **the string you later use to retrieve the value** from `DialogResponseView`.

#### `ActionButton` builder (verbatim from `ActionButton.java`)

```java
// io.papermc.paper.registry.data.dialog.ActionButton
ActionButton.builder(Component label)
    .tooltip(@Nullable Component tooltip)
    .width(@Range(from=1,to=1024) int width)
    .action(@Nullable DialogAction action)
    .build();

// shortcut:
ActionButton.create(Component label, @Nullable Component tooltip,
                    @Range(from=1,to=1024) int width, @Nullable DialogAction action);
```

#### `DialogAction` factory — the three action kinds (verbatim from `DialogAction.java`)

```java
// io.papermc.paper.registry.data.dialog.action.DialogAction
//   sealed: CommandTemplateAction | StaticAction | CustomClickAction

// (a) Run a command, substituting $(input_key) with input values:
DialogAction.commandTemplate(String template);
//     e.g. "give $(player) diamond $(count)"

// (b) A static Adventure ClickEvent (OPEN_URL, RUN_COMMAND, etc.):
DialogAction.staticAction(ClickEvent value);

// (c) Custom click — server-side handling. Two variants:
DialogAction.customClick(Key id, @Nullable BinaryTagHolder additions);
//     -> fires PlayerCustomClickEvent with getIdentifier()==id
DialogAction.customClick(DialogActionCallback callback, ClickCallback.Options options);
//     -> runs the callback directly (DialogActionCallback = (response, audience) -> {})
```

`DialogActionCallback` is a `@FunctionalInterface`:

```java
// io.papermc.paper.registry.data.dialog.action.DialogActionCallback
@FunctionalInterface
public interface DialogActionCallback {
    @ApiStatus.OverrideOnly
    void accept(DialogResponseView response, Audience audience);
}
```

### A.5 How to HANDLE the player's response — `DialogResponseView`

Verbatim source:

```java
// io.papermc.paper.dialog.DialogResponseView
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface DialogResponseView {

    BinaryTagHolder payload();                 // raw NBT payload of the whole response

    @Nullable String  getText(String key);     // value of a DialogInput.text field
    @Nullable Boolean getBoolean(String key);  // value of a DialogInput.bool field
    @Nullable Float   getFloat(String key);    // value of a DialogInput.numberRange field
}
```

> Docs note: "There are no guarantees that this is an actual response to a dialog form. It is on the plugin to validate that the response is valid."

**There is no `getSingleOption`/`getInt` helper** — single-option selections come back as a **String** via `getText(key)` (the selected option's `onTrue`/value string), and number ranges come back as **Float** via `getFloat(key)`.

#### Two response-handling strategies

**Strategy 1 — Global `PlayerCustomClickEvent` listener** (works for any dialog whose buttons use `DialogAction.customClick(Key, ...)`):

```java
// io.papermc.paper.event.player.PlayerCustomClickEvent  (verbatim signatures)
@ApiStatus.Experimental @ApiStatus.NonExtendable @NullMarked
public abstract class PlayerCustomClickEvent extends Event {
    public final Key getIdentifier();
    public abstract @Nullable BinaryTagHolder getTag();
    public abstract @Nullable DialogResponseView getDialogResponseView();
    public final PlayerCommonConnection getCommonConnection();   // PlayerGameConnection | PlayerConfigurationConnection | ...
}
```

The `getCommonConnection()` is important: during the configuration phase the player is **not yet a `Player`** — you must cast:

```java
// PlayerGameConnection (in-game)
public interface PlayerGameConnection extends PlayerCommonConnection {
    void reenterConfiguration();
    Player getPlayer();
}
// PlayerConfigurationConnection (config phase — no Player object exists yet)
public interface PlayerConfigurationConnection extends PlayerCommonConnection {
    Audience getAudience();
    PlayerProfile getProfile();
    void clearChat();
    void completeReconfiguration();
}
```

Real-world handling pattern (from UniDialog's `PaperDialogManager`, verbatim):

```java
@EventHandler
public void onCustomClick(PlayerCustomClickEvent event) {
    Key key = event.getIdentifier();
    Consumer<DialogPayload> action = customActions.get(key);
    if (action == null) return;

    UUID uuid = switch (event.getCommonConnection()) {
        case PlayerGameConnection pgc -> pgc.getPlayer().getUniqueId();
        case PlayerConfigurationConnection pcc -> pcc.getProfile().getUniqueId();
        default -> null;
    };
    if (uuid == null) return;

    action.accept(new PaperDialogPayload(uuid, event.getDialogResponseView()));
}
```

**Strategy 2 — Inline callback via `DialogAction.customClick(DialogActionCallback, ClickCallback.Options)`** (no event listener needed; the callback is registered against the specific button instance):

```java
DialogAction.customClick(
    (view, audience) -> {
        int levels = view.getFloat("level").intValue();
        float exp  = view.getFloat("experience").floatValue();
        if (audience instanceof Player player) {
            player.setLevel(levels);
            player.setExp(exp / 100);
        }
    },
    ClickCallback.Options.builder()
        .uses(1)                                  // default 1
        .lifetime(ClickCallback.DEFAULT_LIFETIME) // default 12 hours
        .build()
);
```

### A.6 Registry registration (the `paper-plugin.yml` path)

From the docs (verbatim registration snippet):

```java
// YourPluginBootstrapper.java
@Override
public void bootstrap(BootstrapContext context) {
    context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose()
        .newHandler(event -> event.registry().register(
            DialogKeys.create(Key.key("papermc:custom_dialog")),
            builder -> builder
                // Build your dialog here ...
                .base(DialogBase.builder(Component.text("Title")).build())
                .type(DialogType.notice())
        )));
}
```

The event used is **`io.papermc.paper.registry.event.RegistryEvents.DIALOG`** — a
`RegistryEventProvider<Dialog, DialogRegistryEntry.Builder>` (confirmed present in 1.21.8 JavaDoc; **absent in 1.21.4**, since the whole dialog API postdates 1.21.4).

> Docs tip: "The advantage of registering dialogs in the registry is that it allows you to use that same dialog elsewhere in your code without having to pass around the `Dialog` object. This also allows the dialog to be referenced in commands with a dialog parameter."

`DialogRegistryEntry.Builder` requires exactly two fields: `.base(DialogBase)` and `.type(DialogType)`. It also exposes `.registryValueSet()` for building a `RegistrySet<Dialog>` to pass to `DialogType.dialogList(...)`.

Lookup at runtime:

```java
Dialog dialog = RegistryAccess.registryAccess()
    .getRegistry(RegistryKey.DIALOG)
    .get(Key.key("papermc:custom_dialog"));
```

### A.7 Verbatim end-to-end example from PaperMC docs

#### Example 1 — Blocking confirmation dialog during the configuration phase

Register the dialog in the bootstrapper:

```java
// CustomPluginBootstrapper.java
ctx.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose(),
    e -> e.registry().register(
        DialogKeys.create(Key.key("papermc:praise_paperchan")),
        builder -> builder
            .base(DialogBase.builder(Component.text("Accept our rules!", NamedTextColor.LIGHT_PURPLE))
                .canCloseWithEscape(false)
                .body(List.of(
                    DialogBody.plainMessage(Component.text("By joining our server you agree that Paper-chan is cute!"))
                ))
                .build()
            )
            .type(DialogType.confirmation(
                ActionButton.builder(Component.text("Paper-chan is cute!", TextColor.color(0xEDC7FF)))
                    .tooltip(Component.text("Click to agree!"))
                    .action(DialogAction.customClick(Key.key("papermc:paperchan/agree"), null))
                    .build(),
                ActionButton.builder(Component.text("I hate Paper-chan!", TextColor.color(0xFF8B8E)))
                    .tooltip(Component.text("Click this if you are a bad person!"))
                    .action(DialogAction.customClick(Key.key("papermc:paperchan/disagree"), null))
                    .build()
            ))
    ));
```

Show it during configuration and block until the player responds:

```java
// ServerJoinListener.java
@NullMarked
public class ServerJoinListener implements Listener {

    private final Map<UUID, CompletableFuture<Boolean>> awaitingResponse = new ConcurrentHashMap<>();

    @EventHandler
    void onPlayerConfigure(AsyncPlayerConnectionConfigureEvent event) {
        Dialog dialog = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.DIALOG)
            .get(Key.key("papermc:praise_paperchan"));
        if (dialog == null) return;

        PlayerConfigurationConnection connection = event.getConnection();
        UUID uniqueId = connection.getProfile().getId();
        if (uniqueId == null) return;

        CompletableFuture<Boolean> response = new CompletableFuture<>();
        response.completeOnTimeout(false, 1, TimeUnit.MINUTES);
        awaitingResponse.put(uniqueId, response);

        Audience audience = connection.getAudience();
        audience.showDialog(dialog);

        if (!response.join()) {
            audience.closeDialog();
            connection.disconnect(Component.text("You hate Paper-chan :(", NamedTextColor.RED));
        }
        awaitingResponse.remove(uniqueId);
    }

    @EventHandler
    void onHandleDialog(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerConfigurationConnection configurationConnection)) return;
        UUID uniqueId = configurationConnection.getProfile().getId();
        if (uniqueId == null) return;

        Key key = event.getIdentifier();
        if (key.equals(Key.key("papermc:paperchan/disagree"))) {
            setConnectionJoinResult(uniqueId, false);
        } else if (key.equals(Key.key("papermc:paperchan/agree"))) {
            setConnectionJoinResult(uniqueId, true);
        }
    }

    @EventHandler
    void onConnectionClose(PlayerConnectionCloseEvent event) {
        awaitingResponse.remove(event.getPlayerUniqueId());
    }

    private void setConnectionJoinResult(UUID uniqueId, boolean value) {
        CompletableFuture<Boolean> future = awaitingResponse.get(uniqueId);
        if (future != null) future.complete(value);
    }
}
```

#### Example 2 — Reading number-range input from a dialog (verbatim)

Build the dialog:

```java
Dialog.create(builder -> builder.empty()
    .base(DialogBase.builder(Component.text("Configure your new experience value"))
        .inputs(List.of(
            DialogInput.numberRange("level", Component.text("Level", NamedTextColor.GREEN), 0f, 100f)
                .step(1f).initial(0f).width(300).build(),
            DialogInput.numberRange("experience", Component.text("Experience", NamedTextColor.GREEN), 0f, 100f)
                .step(1f).initial(0f)
                .labelFormat("%s: %s percent to the next level")
                .width(300).build()
        ))
        .build()
    )
    .type(DialogType.confirmation(
        ActionButton.create(
            Component.text("Confirm", TextColor.color(0xAEFFC1)),
            Component.text("Click to confirm your input."),
            100,
            DialogAction.customClick(Key.key("papermc:user_input/confirm"), null)
        ),
        ActionButton.create(
            Component.text("Discard", TextColor.color(0xFFA0B1)),
            Component.text("Click to discard your input."),
            100,
            null  // null action just closes the dialog
        )
    ))
);
```

Read the input via the event:

```java
@EventHandler
void handleLevelsDialog(PlayerCustomClickEvent event) {
    if (!event.getIdentifier().equals(Key.key("papermc:user_input/confirm"))) return;
    DialogResponseView view = event.getDialogResponseView();
    if (view == null) return;

    int levels = view.getFloat("level").intValue();
    float exp  = view.getFloat("experience").floatValue();

    if (event.getCommonConnection() instanceof PlayerGameConnection conn) {
        Player player = conn.getPlayer();
        player.sendRichMessage(
            "You selected <color:#ccfffd><level> levels</color> and <color:#ccfffd><exp>% exp</color> to the next level!",
            Placeholder.component("level", Component.text(levels)),
            Placeholder.component("exp", Component.text(exp))
        );
        player.setLevel(levels);
        player.setExp(exp / 100);
    }
}
```

### A.8 Built-in dialogs (verbatim from `Dialog.java` + `DialogKeys.java`)

| Static field | Registry key |
|---|---|
| `Dialog.SERVER_LINKS` | `minecraft:server_links` |
| `Dialog.QUICK_ACTIONS` | `minecraft:quick_actions` |
| `Dialog.CUSTOM_OPTIONS` | `minecraft:custom_options` |

Server links can be mutated via `Bukkit.getServer().getServerLinks()`; the player opens the Server Links menu from the ESC pause menu (the button only appears if links are present).

---

## B. `paper-plugin.yml` format

### B.1 Real example — the Paper test-plugin's `paper-plugin.yml` (verbatim)

```yaml
name: Paper-Test-Plugin
version: ${version}
main: io.papermc.testplugin.TestPlugin
description: Paper Test Plugin
author: PaperMC
api-version: ${apiversion}
load: STARTUP
bootstrapper: io.papermc.testplugin.TestPluginBootstrap
loader: io.papermc.testplugin.TestPluginLoader
defaultPerm: FALSE
permissions:
dependencies:
```

### B.2 Canonical example from the docs (verbatim)

```yaml
name: Paper-Test-Plugin
version: '1.0'
main: io.papermc.testplugin.TestPlugin
description: Paper Test Plugin
api-version: '26.1.2'
bootstrapper: io.papermc.testplugin.TestPluginBootstrap
loader: io.papermc.testplugin.TestPluginLoader
```

### B.3 Dependency declaration (verbatim)

```yaml
dependencies:
  bootstrap:
    RegistryPlugin:
      load: BEFORE        # BEFORE | AFTER | OMIT (default OMIT)
      required: true      # default true
      join-classpath: true# default true — gives your plugin access to their classpath
  server:
    RequiredPlugin:
      load: AFTER
      required: true
      join-classpath: false
```

Dependencies are split into `bootstrap` (used during the bootstrap phase) and `server` (used at runtime). Load order is **not** inferred from a flat `dependencies:` list the way Bukkit does it — Paper plugins use this explicit `load: BEFORE|AFTER` model, and cyclic loops are *not* auto-resolved.

### B.4 Required vs optional fields

| Field | Required? | Notes |
|---|---|---|
| `name` | yes | Plugin name |
| `version` | yes | |
| `main` | yes | The `JavaPlugin` subclass |
| `api-version` | yes | e.g. `'1.21.8'` or `'26.1.2'` |
| `bootstrapper` | no | Class implementing `PluginBootstrap` |
| `loader` | no | Class implementing `PluginLoader` |
| `description`, `author`, `load`, `permissions`, `defaultPerm` | no | Same semantics as `plugin.yml` |
| `dependencies` | no | Split `bootstrap`/`server` map (see above) |

### B.5 The bootstrapper & loader interfaces (verbatim from docs)

```java
// Bootstrapper — implements io.papermc.paper.plugin.bootstrap.PluginBootstrap
public class TestPluginBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext context) {
        // runs BEFORE the JavaPlugin is created; this is where you register
        // RegistryEvents.DIALOG, LifecycleEvents.COMMANDS, datapack discovery, etc.
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        return new TestPlugin("My custom parameter");  // lets you inject ctor args
    }
}
```

```java
// Loader — implements io.papermc.paper.plugin.loader.PluginLoader
public class TestPluginLoader implements PluginLoader {
    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        classpathBuilder.addLibrary(new JarLibrary(Path.of("dependency.jar")));
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addDependency(new Dependency(new DefaultArtifact("com.example:example:version"), null));
        resolver.addRepository(new RemoteRepository.Builder(
            "paper", "default", "https://repo.papermc.io/repository/maven-public/").build());
        classpathBuilder.addLibrary(resolver);
    }
}
```

### B.6 How lifecycle events fit in

The `LifecycleEventManager` is obtained either from the `JavaPlugin` (`this.getLifecycleManager()`) or from the `BootstrapContext` (`context.getLifecycleManager()`). You then register handlers from `LifecycleEvents` (commands, tags, datapack discovery) or from `RegistryEvents` (registry modification, e.g. `RegistryEvents.DIALOG`).

Verbatim lifecycle-registration patterns:

```java
// From a JavaPlugin
@Override public void onEnable() {
    LifecycleEventManager<Plugin> mgr = this.getLifecycleManager();
    mgr.registerEventHandler(LifecycleEvents.COMMANDS, event -> { /* ... */ });
}

// From a bootstrapper
@Override public void bootstrap(BootstrapContext context) {
    LifecycleEventManager<BootstrapContext> mgr = context.getLifecycleManager();
    mgr.registerEventHandler(RegistryEvents.DIALOG.compose().newHandler(event -> { /* ... */ }));
}
```

Configuration options per handler: `.priority(int)` (lower runs earlier; default 0) and `.monitor()` (runs after all non-monitor handlers; read-only). These two are mutually exclusive.

### B.7 Commands & permissions in `paper-plugin.yml`

> Docs (verbatim): **"Paper plugins do not use the `commands` field to register commands. This means that you do not need to include all of your commands in the `paper-plugin.yml` file. Instead, you can register commands using the Brigadier Command API."**

So `paper-plugin.yml` has **no `commands:` block** — you register commands via `LifecycleEvents.COMMANDS` (which can be done from `JavaPlugin#onEnable()`). The `permissions:` block, however, *is* still supported (see the test-plugin yml above).

### B.8 Can a Paper plugin still use `plugin.yml`? (back-compat)

Yes — verbatim from docs:

> "It should be noted that you still have the ability to include **both `paper-plugin.yml` and `plugin.yml` in the same JAR**."

`plugin.yml` remains the fully-supported default for plugins that don't need bootstrap-phase features. `paper-plugin.yml` is **opt-in**.

---

## C. Compatibility questions

### C.1 Can a plugin use BOTH `plugin.yml` (for commands) AND `paper-plugin.yml` (for lifecycle events)?

**Yes — both files can coexist in the same JAR** (explicitly confirmed in the docs, see B.8). The typical hybrid pattern is:

- `paper-plugin.yml` declares `bootstrapper` / `loader` / split `dependencies` — the things that *require* the Paper-plugin model.
- `plugin.yml` declares the legacy `commands:` and `permissions:` blocks and the `depend:`/`softdepend:` load-order used by older Bukkit plugins.

Caveat to verify by testing: when both files are present, Paper loads the plugin using `paper-plugin.yml` as the primary descriptor. The `commands:` block from `plugin.yml` is still honoured by the Bukkit command layer, but **paper-plugin.yml plugins are encouraged to register commands via `LifecycleEvents.COMMANDS` (Brigadier) instead** — mixing both for the *same* command is not recommended. The docs do not document the exact precedence for every field, so test the specific combination you need.

### C.2 Can a legacy `JavaPlugin` (no `paper-plugin.yml`) use the Dialog API?

**Yes — for ad-hoc dialogs. No — for registry-registered dialogs.**

The Dialog API splits cleanly into two tiers:

| Capability | Needs `paper-plugin.yml`? | Why |
|---|---|---|
| `Dialog.create(...)` at runtime | No | It's a plain static method on `io.papermc.paper.dialog.Dialog`. |
| `player.showDialog(dialog)` / `player.closeDialog()` | No | Inherited from Adventure's `Audience`, which `Player` extends. |
| Listening to `PlayerCustomClickEvent` to read responses | No | It's a normal `org.bukkit.event.Event` with a `HandlerList`; register it with `PluginManager#registerEvents(listener, plugin)` from `onEnable()`. |
| Listening to `AsyncPlayerConnectionConfigureEvent` (config-phase dialogs) | No (but see note) | Also a normal `Event`. It fires per-player-join, well after `onEnable()`. A legacy plugin can handle it. |
| Registering a dialog in the **dialog registry** (`RegistryEvents.DIALOG`) | **Yes** | The registry-modification event must be registered in a `PluginBootstrap` (the bootstrap phase), which only exists for `paper-plugin.yml` plugins. |
| Using `/dialog show <players> <yourdialog>` with a custom dialog key, or a Brigadier `dialog` argument | **Yes** | Requires the dialog to be registry-registered. |

**Conclusion:** A plugin that only needs to pop up ad-hoc dialogs at runtime (build -> show -> read via `PlayerCustomClickEvent`) can stay 100% on `plugin.yml` + `JavaPlugin`. Only if you want centrally-registered, key-addressable dialogs (or config-phase-blocking flows that look up a dialog by `Key`) do you need to add `paper-plugin.yml` + a `PluginBootstrap`.

### C.3 Is there a way to use Dialogs without switching to `paper-plugin.yml`?

Yes — exactly the ad-hoc path above. Minimal legacy-plugin recipe:

```java
public class MyPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new DialogListener(), this);
    }

    public void showRules(Player player) {
        Dialog dialog = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Rules"))
                .body(List.of(DialogBody.plainMessage(Component.text("Be nice!"))))
                .build())
            .type(DialogType.confirmation(
                ActionButton.builder(Component.text("Agree"))
                    .action(DialogAction.customClick(Key.key("myplugin:agree"), null))
                    .build(),
                ActionButton.builder(Component.text("Disagree"))
                    .action(DialogAction.customClick(Key.key("myplugin:disagree"), null))
                    .build()))
        );
        player.showDialog(dialog);   // <-- Paper API method (NOT openDialog)
    }

    public static class DialogListener implements Listener {
        @EventHandler
        public void onClick(PlayerCustomClickEvent event) {
            Key id = event.getIdentifier();
            if (id.equals(Key.key("myplugin:agree")))      { /* ... */ }
            else if (id.equals(Key.key("myplugin:disagree"))){ /* ... */ }
            DialogResponseView view = event.getDialogResponseView();
            if (view != null) {
                String name  = view.getText("name");        // from a DialogInput.text("name", ...)
                Boolean opt  = view.getBoolean("opt");      // from DialogInput.bool("opt", ...)
                Float lvl    = view.getFloat("level");      // from DialogInput.numberRange("level", ...)
            }
        }
    }
}
```

No `paper-plugin.yml`, no bootstrapper, no `PluginBootstrap` — works on Paper 1.21.7+ with a normal `plugin.yml`.

---

## D. Real-world working plugins using the Paper Dialog API

### D.1 Official PaperMC test-plugin

The `test-plugin` module in the Paper repo (`PaperMC/Paper` -> `test-plugin/`) does **not** currently contain dialog example code (only Brigadier command tests). Its `paper-plugin.yml` is reproduced above in B.1, and its `TestPluginBootstrap` is empty:

```java
package io.papermc.testplugin;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import org.jetbrains.annotations.NotNull;

public class TestPluginBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        // io.papermc.testplugin.brigtests.Registration.registerViaBootstrap(context);
    }
}
```

So the **canonical reference examples are the ones in the PaperMC docs page itself** (reproduced verbatim in A.7 above), not a separate test plugin.

### D.2 UniDialog — a real, multi-platform wrapper library (recommended reading)

Repo: **https://github.com/ProjectUnified/UniDialog** (master branch). Its `paper/` module is a clean, idiomatic Paper-API consumer. The most instructive files:

#### `PaperDialogOpener.java` — proves `showDialog` is the API call

```java
package io.github.projectunified.unidialog.paper.opener;

import io.github.projectunified.unidialog.core.opener.DialogOpener;
import io.papermc.paper.dialog.Dialog;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;

public record PaperDialogOpener(Dialog dialog) implements DialogOpener {
    public void open(Audience audience) {
        audience.showDialog(dialog);     // <-- THE Paper API call
    }
    @Override
    public boolean open(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;
        open(player);
        return true;
    }
}
```

#### `PaperDialog.java` — the core build/register/create logic (abridged)

```java
// builds the DialogBase from collected title/body/inputs/...
private DialogBase getDialogBase() {
    return DialogBase.create(
        title != null ? title : Component.text("Dialog"),
        externalTitle,
        canCloseWithEscape,
        pause,
        afterAction,
        body  != null ? body  : Collections.emptyList(),
        input != null ? input : Collections.emptyList()
    );
}

public final Consumer<DialogRegistryEntry.Builder> getDialogBuilder() {
    return builder -> builder.base(getDialogBase()).type(getDialogType());
}

// Ad-hoc creation (NO registry needed):
public final Dialog getDialog() {
    return Dialog.create(factory -> getDialogBuilder().accept(factory.empty()));
}

// Registry registration (in a PluginBootstrap):
public final void register(Key key, WritableRegistry<Dialog, DialogRegistryEntry.Builder> registry) {
    registry.register(TypedKey.create(RegistryKey.DIALOG, key), getDialogBuilder());
}
public final void register(Key key, RegistryComposeEvent<Dialog, DialogRegistryEntry.Builder> event) {
    register(key, event.registry());
}
```

#### `PaperNoticeDialog.java` / `PaperConfirmationDialog.java` / `PaperMultiActionDialog.java` — type factories in action

```java
// Notice
protected DialogType getDialogType() {
    return action != null ? DialogType.notice(action) : DialogType.notice();
}

// Confirmation (with sensible defaults)
private static final ActionButton DEFAULT_YES_ACTION = ActionButton.create(Component.text("Yes"), null, 150, null);
private static final ActionButton DEFAULT_NO_ACTION  = ActionButton.create(Component.text("No"),  null, 150, null);
protected DialogType getDialogType() {
    return DialogType.confirmation(
        yesAction != null ? yesAction : DEFAULT_YES_ACTION,
        noAction  != null ? noAction  : DEFAULT_NO_ACTION
    );
}

// Multi-action
protected DialogType getDialogType() {
    return DialogType.multiAction(
        actions    != null ? actions    : Collections.emptyList(),
        exitAction,
        columns > 0 ? columns : DEFAULT_COLUMNS
    );
}
```

#### `PaperDialogManager.java` — `PlayerCustomClickEvent` handling + closing (abridged)

```java
@EventHandler
public void onCustomClick(PlayerCustomClickEvent event) {
    Key key = event.getIdentifier();
    Consumer<DialogPayload> action = customActions.get(key);
    if (action == null) return;

    UUID uuid = switch (event.getCommonConnection()) {
        case PlayerGameConnection pgc          -> pgc.getPlayer().getUniqueId();
        case PlayerConfigurationConnection pcc -> pcc.getProfile().getUniqueId();
        default -> null;
    };
    if (uuid == null) return;
    action.accept(new PaperDialogPayload(uuid, event.getDialogResponseView()));
}

@Override
public boolean clearDialog(UUID uuid) {
    Player player = plugin.getServer().getPlayer(uuid);
    if (player == null) return false;
    try {
        player.closeDialog();          // <-- Adventure close
    } catch (Throwable e) {
        player.closeInventory();       // <-- fallback
    }
    return true;
}
```

#### `PaperDialogPayload.java` — reading a `DialogResponseView`

```java
public record PaperDialogPayload(UUID owner, @Nullable DialogResponseView view) implements DialogPayload {
    @Override public @Nullable String  textValue(String key)    { return view == null ? null : view.getText(key); }
    @Override public @Nullable Boolean booleanValue(String key) { return view == null ? null : view.getBoolean(key); }
    @Override public @Nullable Number  numberValue(String key)  { return view == null ? null : view.getFloat(key); }
}
```

### D.3 Other resources found

- **`DialogAPI`** plugin on Modrinth (https://modrinth.com/project/e0UoAJ6j) — "a developer-focused API for easily testing and extending Minecraft's new native dialogs" by AlepandoCR.
- **UniDialog** SpigotMC thread (https://www.spigotmc.org/threads/unidialog-fluent-library-to-make-minecraft-graphical-dialogs.697105) — fluent wrapper across Paper/Spigot/Bungee/PacketEvents.
- **GitHub Gist by sylvxa** (https://gist.github.com/sylvxa/4e3b18cd6957c49315e74f1dc0101c92) — this is **Fabric/Mojang-mappings** code (`net.minecraft.dialog.*`, `ServerPlayerEntity#openDialog`), **not** the Paper API. Useful only to understand the underlying vanilla dialog model. Do not copy its imports into a Paper plugin.
- **PaperMC/Velocity#1618** (https://github.com/PaperMC/Velocity/issues/1618) — example plugin that sends countdown dialogs during the configuration phase; relevant if you proxy through Velocity.

---

## E. Version reference & import cheat-sheet

### When was the Dialog API added?

| Layer | Version |
|---|---|
| Minecraft vanilla dialog feature | **1.21.6** |
| Paper developer API for dialogs | **1.21.7** |
| Docs page "Written for version" | **1.21.8** |
| `Dialog#getKey()` deprecated (dialogs can be keyless) | since **1.21.8** |

The `https://jd.papermc.io/paper/1.21.4/...` URLs you provided all return **404** because the `io.papermc.paper.dialog` package did not exist in 1.21.4. Use `1.21.8` (or the latest, currently advertised as `26.2` on the JavaDoc site) instead.

### Full import list for a typical Paper dialog plugin

```java
// Core dialog types
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;

// Registry / builder plumbing
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.RegistryBuilderFactory;
import io.papermc.paper.registry.keys.DialogKeys;             // vanilla dialog keys
import io.papermc.paper.registry.event.RegistryEvents;       // RegistryEvents.DIALOG
import io.papermc.paper.registry.event.RegistryComposeEvent;
import io.papermc.paper.registry.event.WritableRegistry;

// Dialog data builders
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody;
import io.papermc.paper.registry.data.dialog.body.ItemDialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.BooleanDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.type.ConfirmationType;
import io.papermc.paper.registry.data.dialog.type.NoticeType;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import io.papermc.paper.registry.data.dialog.type.DialogListType;
import io.papermc.paper.registry.data.dialog.type.ServerLinksType;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.set.RegistrySet;

// Events
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerGameConnection;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;

// Lifecycle (only for paper-plugin.yml plugins)
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

// Adventure
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
```

### Method-name quick reference (the ones you asked about)

| Task | Correct Paper API call |
|---|---|
| Build ad-hoc dialog | `Dialog.create(b -> b.empty().base(...).type(...))` |
| Build registered dialog | in a `PluginBootstrap`: `RegistryEvents.DIALOG.compose().newHandler(e -> e.registry().register(key, b -> b.base(...).type(...)))` |
| Look up a registered dialog | `RegistryAccess.registryAccess().getRegistry(RegistryKey.DIALOG).get(key)` |
| Show to player | `player.showDialog(dialog)` (NOT `openDialog`) |
| Show during config phase | `connection.getAudience().showDialog(dialog)` |
| Close | `player.closeDialog()` (or `player.closeInventory()` as fallback) |
| Read text input | `responseView.getText(key)` |
| Read boolean/tick input | `responseView.getBoolean(key)` |
| Read number-range input | `responseView.getFloat(key)` |
| Read single-option input | `responseView.getText(key)` (returns the selected option's string value) |
| Get raw NBT | `responseView.payload()` (`BinaryTagHolder`) |
| Receive button click (global) | listen to `PlayerCustomClickEvent`, check `event.getIdentifier()`, read `event.getDialogResponseView()` |
| Receive button click (inline) | `DialogAction.customClick((view, audience) -> {...}, ClickCallback.Options.builder().uses(1).build())` |

---

## F. URL reachability log

| # | URL | Result |
|---|---|---|
| 1 | https://docs.papermc.io/paper/dev/dialogs/ | 200 — full content extracted |
| 2 | https://jd.papermc.io/paper/1.21.4/io/papermc/paper/dialog/package-summary.html | **404** — package did not exist in 1.21.4 |
| 3 | https://jd.papermc.io/paper/1.21.4/io/papermc/paper/dialog/Dialog.html | **404** |
| 4 | https://jd.papermc.io/paper/1.21.4/io/papermc/paper/dialog/DialogResponseView.html | **404** |
| 2b | https://jd.papermc.io/paper/1.21.8/io/papermc/paper/dialog/package-summary.html | 200 (substituted) |
| 3b | https://jd.papermc.io/paper/1.21.8/io/papermc/paper/dialog/Dialog.html | 200 (substituted) |
| 4b | https://jd.papermc.io/paper/1.21.8/io/papermc/paper/dialog/DialogResponseView.html | 200 (substituted) |
| 5 | https://github.com/.../Dialog.java (blob) | 200 — but fetched raw source instead for cleanliness |
| 6 | https://github.com/.../DialogResponseView.java (blob) | 200 — raw source fetched |
| 7 | GitHub code search for `Dialog.Builder` | GitHub code-search API requires auth; used repo tree walking + the UniDialog repo instead |
| 8 | https://docs.papermc.io/paper/dev/getting-started/paper-plugins | 200 |
| 9 | https://jd.papermc.io/paper/1.21.4/.../LifecycleEvents.html | 200 — but only lists COMMANDS/TAGS/DATAPACK_DISCOVERY (no DIALOG; that lives in `RegistryEvents`, absent in 1.21.4) |
