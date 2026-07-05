# Changelog

All notable changes to W-Nick are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/).

## [1.0.81-W-Nick] — 2026-07-06

### Added

#### `/wnick guide` — Paper Dialog API
- New subcommand that opens an interactive Paper Dialog (requires Paper 1.21.8+)
- Shows the player's live nick status (nicked? fake rank? LuckPerms hooked?)
- Grid of clickable action buttons for one-click command execution:
  - List LuckPerms ranks
  - Pick a fake rank
  - Random nick
  - Reset nick
  - Show info
  - Help in chat
- Console fallback: degrades gracefully to a chat-based guide
- Uses `DialogAction.staticAction(ClickEvent.runCommand(...))` for command dispatch
- New permission: `wnick.commands.wnick.guide`

#### `/nickrank` command
- Dedicated UI for managing the LuckPerms fake rank independently of nicks
- `list [all|assignable]` — preview every LuckPerms group with prefix + suffix
- `set <rank> [targets]` — set a fake rank (tab-completes, case-insensitive)
- `clear [targets]` — clear a fake rank
- `random [targets]` — pick a random assignable rank
- `current [target]` — show current rank + preview
- Aliases: `/nr`, `/fakerank`

#### `/wnick` master command
- `help` — friendly help screen
- `info [player]` — full debug view (UUID, original name, nick, skin, fake rank)
- `version` — version info
- `reload` — reload config

#### `/randomnick -r <rank>` flag
- Pick a specific LuckPerms rank when random-nicking
- Example: `/randomnick -r moderator` or `/randomnick @a -r vip --skip`

#### Auto-apply saved nick on join
- Players who reconnect after a server restart get their nick, skin, and fake rank restored automatically
- Controlled by `auto_apply_nick_on_join` config option (default: `true`)

#### Configurable message prefix
- `message_prefix` in `config.yml` (default: `<gold>[W-Nick]</gold> `)
- Supports MiniMessage formatting. Set to `""` to disable.

#### `FakeRankManager` enhancements
- `getAllRanks()` — list every LuckPerms group, not just assignable ones
- `getRanksWithFormatting()` — filter to groups with non-empty prefix/suffix
- `resolveRankIdCaseInsensitive(input)` — case-insensitive rank lookup

#### Automated test suite
- `JarSmokeTest` — loads every `.class` in the final JAR, catches missing-dependency crashes before they reach users
- `RankFlagParsingTest` — 7 JUnit tests for the `-r <rank>` flag parsing
- `UsernameGeneratorTest` — verifies the random username generator
- `scripts/test.sh` — runs the full test suite locally

### Changed

#### Paper plugin conversion
- Added `paper-plugin.yml` alongside `plugin.yml` (hybrid pattern recommended by PaperMC)
- Structured dependencies: LuckPerms, TAB, PlaceholderAPI loaded `BEFORE` with `required: false` and `join-classpath: true`
- Bumped `api-version` from `'1.21'` to `'1.21.8'`

#### Permissions renamed to `wnick.*` namespace
- `lodestone.nametag.commands.*` → `wnick.commands.*`
- `lodestone.commands.nametag.*` → `wnick.admin.*`
- `lodestone.nametag.randomly_assignable` → `wnick.rank.assignable`

#### TAB integration rewritten with pure reflection
- No longer links to any TAB API class at compile time
- Survives any TAB API version mismatch (notably `EventBus.register` signature changes)
- Uses `java.lang.reflect.Proxy` to implement whichever functional interface the runtime TAB exposes
- Degrades gracefully on any TAB API drift — logs a warning and continues without TAB integration

#### Config migration v5 → v6
- Adds `message_prefix`, `auto_apply_nick_on_join`, `auto_assign_random_rank_on_random_nick`
- Nulls out the deprecated `allow_cloud_nicking` key

#### PlaceholderAPI identifier changed
- `nametag` → `wnick` (placeholders now `%wnick_*%`)

#### Branding
- Plugin name: `W-Nick` (was `NameTag`)
- Author: `Joehe`
- Version string: `v1.0.81-W-Nick`

### Fixed

#### `/nick as <name> -r <rank>` now applies the rank
- The original build had decompilation-introduced variable typos (`rankIdx`/`rankId`, `sanitizedTextx`/`sanitizedText`) that made the `-r` flag silently fail in the `as` branch
- Both `as` and `with_name` now correctly persist the fake rank to storage

#### `setNickWithSkin(...)` no longer overwrites a chosen rank
- The method always called `getRandomRank()` and replaced whatever the player had set
- Now honors `rankIdOverride` when passed (e.g. via `/nick random -r vip`)
- Preserves any rank set via `/nickrank set` across `/nick random`

#### `/wnick guide` "No variables in macro" crash
- `DialogAction.commandTemplate(...)` expects a Minecraft macro with at least one `$variable`
- Switched to `DialogAction.staticAction(ClickEvent.runCommand(...))` for static commands

#### `NoClassDefFoundError: net/luckperms/api/LuckPerms`
- Wrapped the LuckPerms hook in a defensive `try/catch (NoClassDefFoundError)`
- Falls back to no-LuckPerms mode instead of crashing `onEnable`
- Caused by `paper-plugin.yml`'s `join-classpath: false` — fixed by setting `join-classpath: true`

#### `NoSuchMethodError: EventBus.register`
- TAB's `EventBus.register` changed signature between versions (`Consumer` → `EventHandler`)
- Rewrote TAB integration with pure reflection (see above)

#### `NoClassDefFoundError: bstats/charts/CustomChart`
- PacketEvents' `SpigotPacketEventsBuilder$1` references bStats classes in method signatures
- Restored the bStats package (only ~51 KB compressed)

#### `NoClassDefFoundError: net/kyori/adventure/nbt/BinaryTag`
- PacketEvents' `SynchronizedRegistriesHandler` needs Adventure NBT at class-init
- Restored the `net/kyori/` package (PaperPluginClassLoader doesn't expose Paper's internal adventure-nbt)

#### `NoClassDefFoundError: org/slf4j/Logger`
- CommandAPI's `CommandAPILogger` and every `NMS_*` class reference slf4j
- Restored the `org/slf4j/` package (~50 KB compressed)

### Removed

#### Backdoors / phone-home calls
- **`CloudNickService`** — was resolving the server's public IP via `api.ipify.org` and uploading every joining player's UUID + username + server IP + port to a third-party endpoint on every join
- **`Metrics(this, 24781)`** — bStats analytics call using the upstream plugin's ID
- **`VersionUpdater`** — phoned a third-party URL on every server start
- **`PlayerJoinEvent` phone-home** — the `cloudNickService.registerPlayer()` call in `NickCommand`
- **`allow_cloud_nicking` config option** — migration nulls it out

The only outbound network calls remaining are to **Mojang's official APIs** (username + skin lookups) and **mineskin.org** (only when a player explicitly runs `/nick from_url <url>`).

#### Lodestone / Apollo30 branding
- All `Lodestone`, `Apollo30`, `lode.gg`, and `Name-Tag` string references removed from source, config, and docs
- `PlaceholderManager.getAuthor()` changed from `Lodestone` to `Joehe`
- `Apollo30` removed from the hardcoded username list in `SkinProvider`

#### Dead-weight bundled libraries (JAR size: 10.4 MB → 5.3 MB, 49% smaller)
- `com/mongodb` + `org/bson` — MongoDB driver (not needed for LOCAL storage; loaded reflectively only when `storage.type=MONGODB`)
- `org/apache/hc/` + `mozilla/public-suffix-list.txt` — Apache HttpClient (was for the removed CloudNickService)
- `net/infumia/titleupdater/` — unused title library
- `GeyserUtil.class` — no GeyserMC support
- Old CommandAPI NMS classes (R1–R6, 1.20_R4) — kept only R7, 26_1, Common
- Old PacketEvents block-state mappings (1.13–1.20.5) — kept only 1.21+

### Confirmed
- **API classes are already bundled in the main JAR** — users only need to install one file
- **No telemetry or phone-home calls** — verified by source audit
- **All 12 JUnit tests pass** + JAR smoke test passes
