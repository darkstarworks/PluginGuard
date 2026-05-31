# PluginGuard

#### Protect your server by hiding installed plugins from users

For Minecraft Paper-based servers — supports **1.21.x** (Java 21+) and **26.x.x** (Java 25+).
Works on Paper, Purpur, Pufferfish, Folia, and other Paper forks. Spigot/Bukkit will load
the plugin but server-brand spoofing is disabled (Paper-only API).

> Donate: https://ko-fi.com/darkstarworks

### Features

- **Hide Mode** — choose from several types of "Access Denied" responses
- **Plugin Spoofing** — return a configurable list of fake plugins
- **Optional Bypass Permission**
- **High-level Bypass Protection**
- **Command Redirection**
- **Advanced Protection**
- **Custom Protection**
- **Server Metadata Protection**
- **Optional Server Brand Spoofing** — return e.g. "Vanilla" instead of "Paper"
- **Optional Aggressive Mode** — block everything; see config below
- **Folia-compatible**

## Configuration (`config.yml`)

### Basic settings

Hide Mode options:
- `"unknown-command"` — shows "Unknown command" (most realistic)
- `"empty"` — shows `Plugins (0):`
- `"fake-list"` — shows the configured fake plugins below
- `"permission-denied"` — shows a permission error

```yaml
hide-mode: "unknown-command"
```

Fake plugins to display when `hide-mode: "fake-list"`
(use vanilla-sounding names to appear legitimate):

```yaml
fake-plugins:
  - "ServerCore"
  - "WorldManager"
  - "CoreProtect"
  - "EveryoneChat"
```

Permission node to bypass all plugin hiding (staff/admin only):

```yaml
bypass-permission: "pluginguard.bypass"
```

### Command protection

Commands to intercept and hide (supports `bukkit:` and `minecraft:` prefixes):

```yaml
protected-commands:
  - "pl"
  - "plugins"
  - "ver"
  - "version"
  - "?"
  - "help"
  - "about"
  - "icanhasbukkit"
```

Block all `bukkit:` and `minecraft:` prefixed commands (prevents probing):

```yaml
block-bukkit-commands: true
```

Redirect `bukkit:` commands to the spoofed list instead of blocking
(only works if `block-bukkit-commands: true`):

```yaml
redirect-bukkit-commands: false
```

### Tab-completion protection

Remove plugin commands from tab-completion to prevent `/[tab]` probing:

```yaml
hide-tab-completion: true
```

### Advanced protection

Return "Unknown command" even when the player simply lacks permission —
prevents probing for plugin existence via permission responses:

```yaml
block-unknown-commands: true
```

Common plugin commands to block (case-insensitive):

```yaml
common-plugin-commands:
  - "essentials"
  - "ess"
  - "worldedit"
  - "we"
  - "luckperms"
  - "lp"
  - "coreprotect"
  - "co"
  - "vault"
  - "multiverse"
  - "mv"
  - "citizens"
  - "npc"
  - "clearlag"
  - "dynmap"
  - "griefprevention"
  - "gp"
  - "holographicdisplays"
  - "hd"
```

```yaml
block-common-plugin-commands: true
```

### Server-metadata protection

Hide server software in query responses and MOTD ping (Paper/Folia only):

```yaml
hide-server-brand: true
fake-server-brand: "vanilla"
```

### Aggressive mode

Blocks **all** plugin commands for players without `<command>.use` permission.
This will hide even beneficial plugin commands for regular players — use only if
you want maximum security and are willing to manually grant per-command permissions.

```yaml
aggressive-mode: false
```

## Commands

- `/pluginguard reload` — reload config (requires `pluginguard.reload`)
- `/pluginguard status` — show current protection status

## Building

```
./gradlew build
```

The shaded jar lands in `build/libs/PluginGuard-<version>.jar`.
