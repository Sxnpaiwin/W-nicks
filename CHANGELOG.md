# Changelog

All notable changes to W-Nick are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/).

## [1.0.81-W-Nick] — 2026-07-05

### Branding
- Plugin name: `W-Nick`
- Author: `Joehe`
- Version string: `v1.0.81-W-Nick`
- Repository: https://github.com/Sxnpaiwin/W-nicks
- PlaceholderAPI identifier: `wnick` (placeholders are now `%wnick_*%`)

### Privacy / backdoor removal
- **Removed CloudNickService** — the upstream "cloud nicking" feature was a
  phone-home that resolved the server's public IP via `api.ipify.org` and
  uploaded every joining player's UUID + username + the server's IP + port
  to a third-party endpoint on every join. All of that is gone. Random
  username generation now uses the local `UsernameGenerator` exclusively.
- **Removed bStats metrics** — the `new Metrics(this, 24781)` call in
  `onLoad()` is gone. No telemetry is sent.
- **Removed VersionUpdater** — the version-checker that hit a third-party
  URL on every server start is gone.
- **Removed `allow_cloud_nicking` config option** — the migration now nulls
  out the key on existing configs.
- The only outbound network calls remaining are to **Mojang's official
  APIs** (username lookups + skin fetches) and **mineskin.org** (only when
  a player explicitly runs `/nick from_url <url>`).

### Bug fixes
- **`/nick as <name> -r <rank>` now actually applies the rank.** The
  original build had decompilation-introduced variable typos (`rankIdx` /
  `rankId`, `sanitizedTextx` / `sanitizedText`) that made the `-r` flag
  silently fail in the `as` branch. Both `as` and `with_name` now
  correctly persist the fake rank to the player's storage.
- **`setNickWithSkin(...)` no longer overwrites a chosen rank.** The
  method always called `getRandomRank()` and replaced whatever the player
  had set. Now:
  1. If `rankIdOverride` is passed (e.g. via `/nick random -r vip`), it
     is honored.
  2. If the player already has a fake rank set via `/nickrank set`, it
     is preserved across `/nick random`.

### Added
- **`/nickrank` command** (alias `/nr`, `/fakerank`) — dedicated UI for
  managing the LuckPerms fake rank:
  - `list [all|assignable]` — preview every LuckPerms group with prefix
    and suffix rendered live
  - `set <rank> [targets]` — set your (or someone else's) fake rank, with
    tab-completion and case-insensitive matching
  - `clear [targets]` — clear your fake rank
  - `random [targets]` — pick a random assignable rank
  - `current [target]` — show your current rank + preview
- **`/wnick` master command** (alias `/wn`) — friendly help screen,
  `/wnick info [player]` for full debug view, `/wnick version`,
  `/wnick reload`.
- **`/randomnick -r <rank>` flag** — generate a random nick with a
  specific rank, e.g. `/randomnick -r vip` or
  `/randomnick @a -r moderator --skip`.
- **Auto-apply saved nick on join** — players who reconnect after a
  server restart get their nick, skin, and fake rank automatically
  restored. Controlled by `auto_apply_nick_on_join` (default: `true`).
- **Configurable message prefix** — `message_prefix` in `config.yml`
  (default: `<gold>[W-Nick]</gold> `). Set to `""` to disable.
- **`FakeRankManager.getAllRanks()`** — list every LuckPerms group, not
  just the ones tagged as randomly assignable.
- **`FakeRankManager.getRanksWithFormatting()`** — filter to only groups
  that have a non-empty prefix or suffix.
- **`FakeRankManager.resolveRankIdCaseInsensitive(input)`** — lets users
  type rank names with any case.
- **Config migration** from v5 → v6 — adds the new keys
  (`message_prefix`, `auto_apply_nick_on_join`,
  `auto_assign_random_rank_on_random_nick`) automatically on first load
  and nulls out the deprecated `allow_cloud_nicking` key.

### Permissions renamed
All permissions were renamed to the cleaner `wnick.*` namespace. Server
admins upgrading from an older build will need to update their permission
assignments. See the README for the full list of new permission names.

### Confirmed
- **API classes are already bundled in the main JAR.** Users only need to
  install one file: `W-Nick-1.0.81.jar`.
