# W-Nick

A LuckPerms-aware nick plugin for Paper / Folia Minecraft servers.

**W-Nick** lets players nick themselves with a fake name, fake skin, AND a
fake rank (prefix + suffix pulled from any LuckPerms group), so they can
look like a VIP, Moderator, or any other rank while nicked.

> **One JAR, no separate API plugin needed.** The API classes are already
> bundled inside `W-Nick-1.0.81.jar` — just drop the single file into your
> `plugins/` folder and you're done.

**Author:** Joehe · **Repo:** https://github.com/Sxnpaiwin/W-nicks

---

## Features

### Nicking
- `/nick reset` — clear your nick
- `/nick as <player>` — nick as an existing Minecraft player (skin + name)
- `/nick with_name <name>` — set a custom nickname
- `/nick with_skin <player>` — copy another player's skin
- `/nick from_url <url>` — apply a Mineskin skin
- `/randomnick [selector] [-r <rank>] [--skip]` — generate a random nick,
  optionally with a specific LuckPerms rank

### LuckPerms rank spoofing (W-Nick's headline feature)
- `/nickrank list [all|assignable]` — preview every LuckPerms group with
  its prefix + suffix rendered live
- `/nickrank set <rank> [targets]` — set your fake rank (tab-completes all
  group names, case-insensitive)
- `/nickrank clear [targets]` — clear your fake rank
- `/nickrank random [targets]` — pick a random assignable rank
- `/nickrank current [target]` — show your current rank + preview
- Works whether or not you currently have a nick set — the rank will be
  applied automatically the next time you `/nick`

You can also pass `-r <rank>` directly to `/nick` and `/randomnick`:
```
/nick with_name Steve -r vip
/randomnick -r moderator
```

### W-Nick extras
- `/wnick` — friendly help screen showing every command
- `/wnick info [player]` — full debug view (UUID, original name, current
  nick, skin, fake rank, rank preview)
- `/wnick version` / `/wnick reload`
- Configurable message prefix (`message_prefix` in `config.yml`)
- Auto-apply saved nick on join (`auto_apply_nick_on_join` in `config.yml`)
  — players who reconnect after a restart get their nick back automatically

### Other integrations
- **TAB** (optional) — when installed, W-Nick pushes the spoofed prefix and
  suffix to TAB's tab-list and above-head nametag managers
- **PlaceholderAPI** (optional) — exposes `%wnick_nickname%`,
  `%wnick_has_nick%`, `%wnick_fake_rank%`, `%wnick_original_name%`,
  `%wnick_skin_name%`, etc.
- **Folia** supported

### Privacy
W-Nick ships **no telemetry and no phone-home calls**:
- No bStats / metrics
- No version-checker that hits third-party servers
- No "cloud nick" service that uploads your server IP / player UUIDs
- The only outbound network calls are to **Mojang's official APIs**
  (to look up usernames and skins) and **mineskin.org** (only when a
  player explicitly uses `/nick from_url <url>`)

---

## Quick start

1. Install [LuckPerms](https://luckperms.net/) (required for rank spoofing).
2. (Optional) Install [TAB](https://github.com/NEZNAMY/TAB) and
   [PlaceholderAPI](https://github.com/HelpChat/PlaceholderAPI) for full
   feature parity.
3. Download `W-Nick-1.0.81.jar` from the [releases page](../../releases) and
   drop it into your server's `plugins/` folder.
4. Restart your server.
5. In LuckPerms, tag a group as randomly assignable:
   ```
   /lp group vip permission set wnick.rank.assignable true
   ```
6. In-game, run `/nickrank list` to see available ranks, then
   `/nickrank set vip` to pick one, then `/nick random` to apply a random
   nick with that rank.

---

## Permissions

| Permission                                  | Default | Description                              |
|---------------------------------------------|---------|------------------------------------------|
| `wnick.commands.nick`                       | op      | Access to `/nick`                        |
| `wnick.commands.nick.others`                | op      | Nick other players                       |
| `wnick.commands.nick.reset`                 | op      | `/nick reset`                            |
| `wnick.commands.nick.reset_all`             | op      | `/nick reset_all`                        |
| `wnick.commands.nick.save_all`              | op      | `/nick save_all`                         |
| `wnick.commands.nick.save_cached`           | op      | `/nick save_cached`                      |
| `wnick.commands.randomnick`                 | op      | Access to `/randomnick`                  |
| `wnick.commands.randomnick.others`          | op      | Random-nick other players                |
| `wnick.commands.realname`                   | true    | Access to `/realname`                    |
| `wnick.commands.nickrank`                   | op      | Access to `/nickrank`                    |
| `wnick.commands.nickrank.list`              | op      | List ranks                               |
| `wnick.commands.nickrank.set`               | op      | Set a fake rank on yourself              |
| `wnick.commands.nickrank.clear`             | op      | Clear your fake rank                     |
| `wnick.commands.nickrank.random`            | op      | Pick a random assignable rank            |
| `wnick.commands.nickrank.current`           | op      | View your current fake rank              |
| `wnick.commands.nickrank.others`            | op      | Act on other players' fake rank          |
| `wnick.commands.wnick`                      | op      | Access to `/wnick`                       |
| `wnick.commands.wnick.info`                 | op      | View your own nick info                  |
| `wnick.commands.wnick.info.others`          | op      | View other players' nick info            |
| `wnick.admin.reload`                        | op      | `/wnick reload` and `/nametag reload`    |
| `wnick.admin.refresh_player`                | op      | `/nametag refresh_player`                |
| `wnick.admin.debug`                         | op      | `/nametag debug`                         |
| `wnick.rank.assignable`                     | false   | Tags a LuckPerms group as randomly assignable |

---

## Configuration

`plugins/W-Nick/config.yml`:

```yaml
version: 6
can_use_existing_players: true
should_spoof_uuid: false

# Prefix prepended to most user-facing plugin messages.
# Supports MiniMessage formatting. Set to "" to disable.
message_prefix: "<gold>[W-Nick]</gold> "

# Re-apply a player's saved nick automatically when they log in.
auto_apply_nick_on_join: true

# When true, /nick random automatically picks a random assignable rank
# from LuckPerms (only if the player doesn't already have one set).
auto_assign_random_rank_on_random_nick: true

storage:
  type: "LOCAL"        # or MONGODB
  mongodb:
    uri: "mongodb://localhost:27017"
    database: "nametag"
    collection: "players"
    pool-size: 10
```

---

## Building from source

This repository ships the patched Java sources under `src/`. The bundled
dependencies (PacketEvents, CommandAPI, BookshelfAPI, MongoDB driver, etc.)
are not open source and are not included — to build, you need the original
JAR which serves as both the source of bundled libs and the base for the
repackaged JAR.

Quick build:
```bash
# Requires JDK 21
./scripts/package.sh    # produces dist/W-Nick-1.0.81.jar
```

---

## License

MIT — see [LICENSE](LICENSE).

---

## Credits

W-Nick is authored and maintained by **Joehe**.
