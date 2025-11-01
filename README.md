# PluginGuard

#### Protect your server by hiding installed plugins from users

For Minecraft Paper 1.21.1 - 1.21.10

<br>

> Donate: https://ko-fi.com/darkstarworks

<br>

<br>

Options and how to configure `config.yml`:

<br>

#### ===== BASIC SETTINGS =====

Hide Mode Options:
- `"unknown-command"`: Shows "Unknown command" message (most realistic)
- `"empty"`: Shows "Plugins (0):"
- `"fake-list"`: Shows configured fake plugins below
- `"permission-denied"`: Shows permission error message
```yaml
hide-mode: "unknown-command"
```
<br>

Fake plugins to display when `hide-mode: "fake-list"`
(Use vanilla-sounding names to appear legitimate)
```yaml
fake-plugins:
  - "ServerCore"
  - "WorldManager"
  - "CoreProtect"
  - "EveryoneChat"
```
<br>

Permission node to bypass all plugin hiding (staff/admin only)
```yaml
bypass-permission: "pluginguard.bypass"
```
<br>

#### ===== COMMAND PROTECTION =====

Commands to intercept and hide (supports bukkit: and minecraft: prefixes)
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
<br>

Block all bukkit: and minecraft: prefixed commands (prevents probing)
```yaml
block-bukkit-commands: true
```
<br>

Redirect `bukkit:` commands to show spoofed list instead of blocking
Only works if `block-bukkit-commands: true`
```yaml
redirect-bukkit-commands: false
```
<br>

#### ===== TAB COMPLETION PROTECTION =====

Remove plugin commands from tab-completion suggestions
Prevents discovery through `/[tab]` probing
```yaml
hide-tab-completion: true
```
<br>

#### ===== ADVANCED PROTECTION =====

Block unknown commands with permission errors
Returns "Unknown command" even if the player lacks permission
Prevents probing for plugin existence via permission responses
```yaml
block-unknown-commands: true
```
<br>

Common plugin commands to block (case-insensitive)
Add popular plugin commands that users might probe for
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
<br>

Block common plugin commands from the list above
```yaml
block-common-plugin-commands: true
```
<br>

#### ===== SERVER METADATA PROTECTION =====

Hide server software in query responses and MOTD ping
Prevents protocol-level discovery
```yaml
hide-server-brand: true
```
<br>

Fake server brand to display (e.g., "vanilla", "1.21.1", "custom")
```yaml
fake-server-brand: "vanilla"
```
<br>

#### ===== AGGRESSIVE MODE =====

**AGGRESSIVE MODE: Block ALL plugin commands for players without explicit permission**

**WARNING**: This will hide even beneficial plugin commands for regular players.

Use <ins>only</ins> if you want maximum security and manually grant command permissions

Requires players to have `<command>.use` permission to use any plugin command
```yaml
aggressive-mode: false
```


