# Changelog

## [1.0.1]

### Added
- `chunky.mode` option with three modes: `ASK` (default), `AUTO` and `OFF`.
- Clickable chat warning in `ASK` mode with **[Continue Chunky]** and **[Stop Chunky]** buttons, shown only when the
  server stays overloaded while a Chunky task is running.
- Options `chunky.warnAfterSeconds`, `chunky.warnRepeatSeconds` and `chunky.ignoreMinutes`.
- Commands: `/disconnectum chunky continue`, `/disconnectum chunky stop`, `/disconnectum chunky mode ask|auto|off`
  and `/disconnectum preset relaxed|balanced|strict|off`. Allowed for operators and for the singleplayer world owner.
- Config presets (`relaxed`, `balanced`, `strict`, `off`) that are saved to the config file and applied immediately.
- English and Vietnamese translations for the new messages and config screen entries.
- Detailed `README.md`: features, commands, presets, full config reference, compatibility notes and recommendations.
- `CHANGELOG.md` (this file).

### Changed
- Chunky is no longer paused automatically by default. Automatic pausing is now the opt-in `AUTO` mode.
- `monitor.startupGraceSeconds` now defaults to `0` (was `30`). It is mainly useful together with `chunky.mode = AUTO`.
- The mod description in `neoforge.mods.toml` is now short; the details moved to the README.

### Fixed
- The server-side keep-alive mixin never applied in 1.0.0 because the hook targeted `keepConnection`. In the 1.21.1
  Mojang mappings the method is `keepConnectionAlive`. The old name is kept as a fallback.

### Removed
- `chunky.pauseWhenOverloaded` (replaced by `chunky.mode`).

### Upgrade notes
- An existing `config/disconnectum-common.toml` keeps its old values. Run `/disconnectum preset balanced`, or delete the
  file and restart, to get the new defaults.
- `chunky.maxWorkingCount` is read by Chunky once at startup, so changing it needs a full game restart.

## [1.0.0]

### Added
- Server load monitor with hysteresis (smoothed tick time, loop lag and spike detection).
- Chunky integration through its public API (reflection only): pauses tasks while the server is overloaded and continues
  them afterwards, and limits Chunky's simultaneous chunk requests through `chunky.maxWorkingCount`.
- Worker thread priority lowering for `Worker-Main-*` and `Chunky-*` threads.
- Keep-alive guard: a server-side mixin and a client-side Netty handler for remote servers.
- Addon API: `DisconnectumApi`, `BackgroundWorkThrottle` and `OverloadStateChangedEvent`.
- `/disconnectum status` command, common config and in-game config screen.
