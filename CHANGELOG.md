# Changelog

## 1.0.0

- Target Paper API 1.21.4; supports Minecraft 1.21.x (Java 21+) and 26.x.x (Java 25+).
- Folia support declared via `folia-supported: true`.
- Graceful degradation on Spigot/Bukkit: the Paper-only `PaperServerListPingEvent`
  listener is now registered conditionally via class-presence check, so the plugin
  loads on non-Paper servers (server-brand spoofing simply becomes a no-op).
- All player-facing messages now use the Adventure `Component` API instead of the
  deprecated legacy color-code strings.
- `/version` spoof now reflects the running server's Minecraft version dynamically
  instead of a hard-coded `1.21.1`.
- Build: Kotlin 2.1.20, Shadow 8.3.6, jar version is now wired through `plugin.yml`
  via `${version}`, `shadowJar` is minimized.
- README rewritten as proper UTF-8 (was UTF-16 with BOM).

## 0.1

- Initial release.
