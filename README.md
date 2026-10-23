# Disconnectum

NeoForge 1.21.1 · Java 21 · NeoForge 21.1.264 · MIT · author: thanhtnt2 · version 1.0.1

Keeps a very large modpack (300+ mods) from kicking you out of your own world with *"Connection Lost / Disconnected"*
or *"Timed out"*, mainly on weak PCs and old laptops where the integrated server is starved of CPU and memory.

> Disconnectum prevents **lag from turning into a disconnect**. It cannot fix a crash caused by another mod
> (for example a worldgen exception while Chunky generates). Read the crash report first.

---

## Features

### 1. Load monitor
Measures every server tick and decides, with hysteresis, whether the server is **overloaded**.
Two signals are used: *tick time* (like MSPT) and *loop lag* (how far the server runs behind its 50 ms schedule, which also
shows work done between ticks). Entering overload needs `tick >= 45 ms` **or** `lag >= 15 ms`, or a single tick longer than
1.5 s. Leaving needs 40 calm ticks, a minimum hold of 3 s, and values below the exit thresholds.

### 2. Chunky protection (3 modes, default `ASK`)
Chunky is controlled through its public API (reflection only, nothing is patched).

| Mode | Behaviour |
|------|-----------|
| `ASK` (default) | Never touches Chunky by itself, so pre-generating in a fresh world is never interrupted. If the server stays overloaded for `warnAfterSeconds` **and** a Chunky task is running, a chat message appears with two clickable buttons: **[Continue Chunky]** (high risk, stand still so no new chunks load, warnings muted for `ignoreMinutes`) and **[Stop Chunky]** (pauses it; Disconnectum continues it when the server recovers, or use `/disconnectum chunky continue`). |
| `AUTO` | Pauses Chunky automatically while overloaded and continues it afterwards. Strict; on a very slow PC the server may stay "overloaded" for a long time and Chunky stays paused until you run `/disconnectum chunky continue`. |
| `OFF` | Leaves Chunky alone. |

Also: Chunky normally keeps up to 50 chunk requests in flight. Disconnectum sets Chunky's own system property
`chunky.maxWorkingCount` to **8** (config `chunky.maxWorkingCount`) unless you already set it in your launcher JVM arguments.

### 3. Worker thread priority
Lowers `Worker-Main-*` (Minecraft's shared pool, chunk generation among other things) and `Chunky-*` threads to priority 3
so the server and render threads win the CPU. The thread list is rescanned every 100 ticks because the pool creates and
retires threads. Clear effect on **Windows**; Linux/macOS ignore Java thread priorities unless the JVM is started with special flags.

### 4. Keep-alive guard
* **Server side (mixin on `ServerCommonPacketListenerImpl#keepConnectionAlive`, a single `@Inject` at `HEAD`, no `@Overwrite`).**
  Vanilla disconnects a player when a keep-alive reply has not been processed 15 s later, but the reply is handled on the server
  thread *between* ticks. One very long tick therefore produces a false "Timed out". Disconnectum postpones the timeout when
  (a) the connection is the singleplayer/LAN owner, or (b) the check runs inside a tick that has already lasted `serverStallMillis`.
* **Client side (remote servers only).** Answers keep-alive packets from the network thread, so a frozen render thread cannot get
  you kicked. Not used in singleplayer.

### 5. Addon API
Package `me.thanhtnt2.disconnectum.optimization.api`: `DisconnectumApi` (load snapshot, register a `BackgroundWorkThrottle`),
`OverloadStateChangedEvent` (NeoForge game bus). Chunky support is just one registered throttle.

---

## Commands

Allowed for operators (permission level 2) and for the owner of a singleplayer world even with cheats off.

| Command | Effect |
|---------|--------|
| `/disconnectum status` | Smoothed tick time, lag, peak, overload state, held Chunky worlds. |
| `/disconnectum chunky continue` | Continue held tasks and mute warnings for `ignoreMinutes`. |
| `/disconnectum chunky stop` | Pause all running Chunky tasks now. |
| `/disconnectum chunky mode ask\|auto\|off` | Change the Chunky mode; saved, **no restart needed**. |
| `/disconnectum preset relaxed\|balanced\|strict\|off` | Apply a preset; saved, effective immediately (see below). |

### Presets

| Option | `relaxed` (good PC) | `balanced` (default) | `strict` (weak PC) | `off` |
|--------|--------------------|----------------------|--------------------|-------|
| enabled | true | true | true | **false** |
| monitor.enterTickMillis / exitTickMillis | 50 / 40 | 45 / 35 | 40 / 30 | 45 / 35 |
| monitor.enterLagMillis / exitLagMillis | 30 / 5 | 15 / 3 | 10 / 2 | 15 / 3 |
| monitor.spikeTickMillis | 2500 | 1500 | 1000 | 1500 |
| monitor.startupGraceSeconds | 0 | 0 | 30 | 0 |
| chunky.mode | ASK | ASK | AUTO | OFF |
| chunky.maxWorkingCount | 16 | 8 | 4 | 0 (untouched) |
| threads.workerPriority | 4 | 3 | 2 | 5 |
| keepAlive.serverStallMillis | 5000 | 3000 | 2000 | 3000 |

A preset changes **only** the options in this table. Everything else keeps your values.

**When do changes need a restart or a reset?**
* `chunky.maxWorkingCount` is read by Chunky once at startup → **restart the game** (the command tells you).
* `keepAlive.clientFastReply` → rejoin the world/server.
* `monitor.startupGraceSeconds` → applies the next time a world starts.
* Everything else applies immediately.
* **Upgrading from 1.0.0:** your old config file keeps its old values (for example `startupGraceSeconds = 30`) and the removed
  `chunky.pauseWhenOverloaded`. Run `/disconnectum preset balanced`, or delete `config/disconnectum-common.toml` and restart, to get the new defaults.

---

## Configuration reference (`config/disconnectum-common.toml`)

You can also edit it in game: **Mods → Disconnectum → Config**.

| Option | Default | Range | Meaning |
|--------|---------|-------|---------|
| `enabled` | true | | Master switch. |
| `logTransitions` | true | | Log a line when the server enters/leaves overload. |
| **monitor** | | | |
| `enterTickMillis` | 45.0 | 5–5000 | Smoothed tick time at/above which the server is overloaded. |
| `exitTickMillis` | 35.0 | 1–5000 | Must drop below this to recover (never effectively above enter). |
| `enterLagMillis` | 15.0 | 1–5000 | Smoothed loop lag that counts as overload. |
| `exitLagMillis` | 3.0 | 0–5000 | Lag must drop below this to recover. |
| `smoothingFactor` | 0.1 | 0.01–1 | Weight of the newest tick (lower = calmer). |
| `spikeTickMillis` | 1500.0 | 100–60000 | One tick this long triggers overload at once. |
| `recoveryTicks` | 40 | 1–2400 | Calm ticks needed to recover (20 = 1 s). |
| `minThrottleMillis` | 3000 | 0–600000 | Minimum time the overload state is held; prevents flapping. |
| `lagSampleCapMillis` | 2000 | 100–60000 | Gaps between ticks above this are ignored (game paused). |
| `startupGraceSeconds` | 0 | 0–600 | Treat the server as overloaded this long after start. Mostly useful with `chunky.mode = AUTO`. |
| **chunky** | | | |
| `mode` | ASK | ASK/AUTO/OFF | See the table above. |
| `warnAfterSeconds` | 10 | 0–600 | ASK: overload must last this long before warning. |
| `warnRepeatSeconds` | 90 | 10–3600 | ASK: minimum time between warnings. |
| `ignoreMinutes` | 10 | 1–240 | Quiet time after you click Continue. |
| `maxWorkingCount` | 8 | 0–512 | Simultaneous chunks for Chunky; 0 = untouched. **Game restart.** |
| **threads** | | | |
| `lowerWorkerPriority` | true | | Enable priority lowering. |
| `workerPriority` | 3 | 1–5 | Java priority for matching threads (3 = below normal on Windows). |
| `scanIntervalTicks` | 100 | 20–2400 | How often to look for new worker threads. |
| `threadNamePrefixes` | `Worker-Main-`, `Chunky-` | | Threads starting with these names are lowered. |
| **keepAlive** | | | |
| `forgiveSingleplayerOwner` | true | | Never time out the singleplayer/LAN owner. |
| `forgiveServerStalls` | true | | Don't time players out inside a tick that already lasted `serverStallMillis`. |
| `serverStallMillis` | 3000 | 500–14000 | Stall length for the rule above. |
| `clientFastReply` | true | | Fast keep-alive replies on remote servers. |

---

## Compatibility and recommendations

**Verified by reading the jars** (no overlap with Disconnectum, nothing to disable or lower):
ModernFix 5.27.26, FerriteCore 7.0.3, Sodium 0.8.13, Embeddium 1.0.15, Entity Culling 1.11.3, Flerovium 1.1.2, Gnetum 3.3.6,
ImmediatelyFast 1.6.15, AllTheLeaks 1.1.13, Saturn 0.1.5, Chunky 1.4.23. None of them touches keep-alive handling,
worker thread priorities or Chunky's tasks.

* **ModernFix**: keep `perf.suspend_integrated_server_during_load` enabled. It delays integrated-server ticks until you have joined,
  which complements Disconnectum. With it on, `startupGraceSeconds = 0` is enough.
* **Chunky**: do not also set `-Dchunky.maxWorkingCount` in your launcher unless you want your own value; yours wins.
  If you use `chunky.mode = AUTO`, you may want Chunky's `silent` option on to reduce chat messages.

**Not inspected** (named from a modpack list, judged only by their general purpose; check their configs yourself):
* Network mods (Krypton FNP, PacketFixer, PacketWeave) and anything that edits timeouts: if one also changes keep-alive or
  timeout behaviour, set `keepAlive.forgiveServerStalls = false` (and `forgiveSingleplayerOwner = false` if that mod already
  covers the singleplayer owner) so the same thing is not done twice.
* Mods that change thread priorities or thread pools: set `threads.lowerWorkerPriority = false`.
* Mods that already pause or limit Chunky: set `chunky.mode = OFF`.
* TT20, Immersive Optimization, AI-Improvements, Lithium, Noisium, BadOptimizations: these reduce server load or speed up
  world generation. They work well **with** Disconnectum; if you add them, a lower tick time may let you use the `relaxed` preset.

**Worth installing** (complementary, not required): ModernFix (above), FerriteCore and Saturn (less memory → fewer swap stalls),
a worldgen speed-up such as Noisium (Chunky finishes sooner), an entity/AI tick limiter such as Immersive Optimization, and
**spark** to find what actually eats your tick time (`/spark profiler start`).

**Memory matters most on weak PCs.** If Windows starts using swap because the game was given most of your RAM, every thread
freezes for seconds regardless of any mod. Leave several GB for the system.

---

## Loggings

Check `logs/latest.log` for `disconnectum` and `FAILED during APPLY`.