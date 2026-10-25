# Argon

A client-side Fabric mod for **Minecraft 1.21.11, 26.1.2, 26.2 and 26.3** that adds extra performance work on top of [Sodium](https://modrinth.com/mod/sodium).
Sodium already speeds up terrain rendering and chunk building; Argon targets the parts it leaves alone.

**New here?** Read the [FPS boosting guide](docs/FPS-BOOSTING.md) for what each option does, preset values and tuning tips.

## What it does

| Feature | Effect |
| --- | --- |
| Entity distance culling | Stops rendering entities past a cutoff (default 64 blocks; 32 for items, XP orbs, arrows and armor stands). Vanilla already culls most mobs at a similar range, so this mainly matters for very large entities. Large entities get extra range so dragons and ghasts never vanish. Glowing entities, the player and the camera's vehicle are never culled. |
| Block entity culling | Stops rendering chests, signs, banners, etc. past 48 blocks. Beacons and end portals are exempt. |
| Small entity budget | Draws at most 256 items, XP orbs, arrows and armor stands per frame, so a huge item pile can't tank the frame rate. |
| Adaptive culling | Watches frame time. While FPS is below your target (default 60) the culling distances and particle cap shrink, down to 50% by default; they recover when there is headroom. Paused while the window is unfocused or minimized. |
| Fast entity lookup (game logic) | Finds nearby entities by looking up the few sections that matter instead of scanning every loaded section in the world column. Identical results, same order. About -35% tick time in an item-heavy scene. |
| Fast entity collisions (game logic) | Skips collision scans of areas holding nothing collidable. Identical results. Falls back to vanilla for boats, minecarts and wind charges. |
| Particle limiter | Caps particles spawned per tick (default 150) so explosions and farms can't stall the frame. |
| Adaptive render distance | Off by default. While frames stay well below your target FPS it lowers render distance (2 chunks at a time, never below your minimum) and raises it back one chunk at a time when there is headroom, never above the value you chose. Changes are slow on purpose because every change reloads chunks. Your own value is restored when you disable it or quit. |
| Performance presets | Balanced, Performance and Potato bundles of vanilla video settings (render distance, biome blend, clouds, particles, weather, mipmaps, entity distance, shadows, leaves, transparency, vignette, AO, chunk fade-in). Applied only when you press the button or run `/argon preset <name>`; they only ever lower a setting, and can be undone for the session. |
| Translucency sorting | "Fast" turns off Sodium's translucent quad splitting, saving CPU on water and glass-heavy scenes at the cost of occasional sorting glitches. In memory only. |
| Sodium chunk threads | Optional in-memory override of Sodium's chunk builder thread count. Sodium's own config file is never touched. |

Gains depend on the scene. In software-rendered A/B tests, item piles, chest-heavy rooms and particle storms gained roughly 20-45%, while a large mob herd and ordinary generated terrain showed no measurable change. See [docs/BENCHMARK.md](docs/BENCHMARK.md) for the numbers and caveats.
Run `/argon status` to see how much work Argon has skipped.

## How much does it boost FPS?

There is no single percentage: it depends entirely on the scene. Measured in A/B tests (Argon off vs on, same world, Sodium 0.9.2, Minecraft 26.2, **software rendering with no GPU**, two separate runs):

| Scene | Fixed limits (run 1 / run 2) | Defaults with adaptive (run 1 / run 2) |
| --- | ---: | ---: |
| **Normal generated terrain** (forest, camera spinning) | **no change** (-4% to +1%) | **no change** |
| 630 dropped items near the camera | +20% / +42% | +32% / +45% |
| 793 chests, 50-69 blocks away | +27% / +28% | +23% / +31% |
| 300 particles per tick | +20% / +20% | +22% / +27% |
| 400 cows in view | within noise (-5% to +3%) | +3% / +9% |

- **In normal terrain Argon made no measurable difference.** There is little for it to skip, and the test was bound by software rasterization (about 13 FPS), so it says nothing about CPU-bound play on a real GPU.
- **In deliberately heavy scenes the gain was about +20% to +45%.** The size of the gain varied noticeably between the two runs, so read these as "roughly 20-40%", not exact.
- Most FPS gains in practice come from Sodium itself and from lowering render distance and video settings (see the presets), not from culling.

Full methodology and raw numbers: [docs/BENCHMARK.md](docs/BENCHMARK.md).

## Config

Argon's settings are in **Options > Video Settings** (Sodium's screen), under their own **Argon** tab with Culling, Adaptive, Chunks & Presets and Game Logic pages. They use Sodium's controls, performance-impact badges and Apply button, and options grey out when the feature they depend on is off. If [Mod Menu](https://modrinth.com/mod/modmenu) is installed, Argon's config button opens the same screen.

You can also edit `config/argon.json` (created on first launch) and run `/argon reload`.

```json
{
  "entityCulling": true,
  "entityDistance": 64,
  "smallEntityDistance": 32,
  "blockEntityCulling": true,
  "blockEntityDistance": 48,
  "fastEntityLookup": true,
  "fastEntityCollisions": true,
  "maxSmallEntitiesPerFrame": 256,
  "adaptiveCulling": true,
  "targetFps": 60,
  "adaptiveMinScale": 0.5,
  "particleLimiter": true,
  "maxParticlesPerTick": 150,
  "adaptiveRenderDistance": false,
  "minRenderDistance": 8,
  "sodiumTranslucencySorting": "DEFAULT",
  "sodiumChunkBuilderThreads": 0
}
```

## Commands

`/argon status`, `/argon reload`, `/argon reset`, `/argon preset <balanced|performance|potato|undo>`

## Versions and requirements

Download the jar whose name matches your Minecraft version (`argon-1.0.0+<minecraft>.jar`). Fabric API and Sodium are required; Mod Menu and Lithium are optional.

| Minecraft | Java | Fabric Loader | Sodium |
| --- | --- | --- | --- |
| 1.21.11 | 21+ | 0.17.3+ | 0.8.14+ |
| 26.1.2 | 25+ | 0.18.4+ | 0.9.2+ |
| 26.2 | 25+ | 0.18.4+ | 0.9.2+ |
| 26.3 | 25+ | 0.19.3+ | 0.9.2+ |

The Fabric Loader minimum is the one each version's Fabric API needs; Mod Menu needs 0.19.2+ on 26.2 and 26.3.

How each version was tested (headless, software rendering, no GPU):

| Minecraft | Automated in-game test suite | Notes |
| --- | --- | --- |
| 1.21.11 | Passes | Also passes on Fabric Loader 0.17.3. Built with Mojang mappings and remapped for the released game. |
| 26.1.2 | Could not run | Every Argon mixin applies (mixin audit), and a normal client session loaded a world and rendered it with Argon's hooks active. The automated suite crashes inside Sodium's terrain renderer under Fabric's test harness; the same crash happens with Argon fully disabled, so it is not caused by Argon. |
| 26.2 | Passes | All benchmarks in this README and in `docs/` were measured on 26.2. |
| 26.3 | Passes | 26.3 opens its window through SDL3; headless testing needs `SDL_VIDEO_DRIVER=offscreen` with Mesa EGL installed. |

## Lithium-style game logic (partial) and using Argon with Lithium

Argon includes two behavior-preserving optimizations in the spirit of [Lithium](https://modrinth.com/mod/lithium), for the integrated server in singleplayer and the client world. They are **not a replacement for Lithium**: on its own Lithium was faster in every scene we measured, while Argon's gains are concentrated in item-heavy worlds (about -35% tick time) with little change for plain mob AI. Argon is client-only, so none of this applies to dedicated servers; install Lithium there. No Lithium code is used in Argon. Numbers and method: [docs/BENCHMARK.md](docs/BENCHMARK.md).

**Running both is supported.** Lithium does not touch Argon's entity-section lookup, and Argon's collision shortcut returns the same results as the code it sits in front of, with or without Lithium (checked by a 4,000-query equivalence test run under Lithium). Measured on top of Lithium, each Argon option was neutral to slightly positive and never clearly harmful (see the benchmark doc), so nothing is auto-disabled.

How Argon behaves around other mods:

- **A conflict can't stop the game from starting.** Argon's game-logic mixins are optional. If another mod changes the same methods, that optimization is skipped instead of crashing, and the helper code falls back to vanilla's behavior if only part of it applied.
- **You can see what is active.** The log prints a line at startup and `/argon status` shows whether Lithium was detected and how many queries Argon's shortcuts have served. If an option is on but "0 served", something is blocking its hook.
- **Tested together:** Lithium 0.25.3, FerriteCore 9.0.0, ImmediatelyFast 1.16.5, Sodium Extra 0.9.4 and Krypton 0.3.1, all loaded at once, with Sodium, Fabric API and Mod Menu. (Entity Culling and BadOptimizations are listed for 26.2 on Modrinth but declare older Minecraft versions, so they could not be tested here.)

## What Argon cannot change

- **OpenGL context flags** (such as Sodium's no-error context) and the **graphics backend** are fixed when the window is created, so a mod cannot switch them while the game runs. Change them in Sodium's settings or the vanilla Video Settings and restart.
- Sodium's overrides here are applied in memory. If you press Apply in Sodium's own settings screen while an override is active, Sodium will save it.

## Why no shadow or idle-FPS options?

Vanilla already stops entity shadows past 16 blocks and throttles minimized or idle windows on every supported version, so Argon leaves those alone.

## Building

One codebase builds every version with [Stonecutter](https://stonecutter.kikugie.dev/). Shared code is in `src/`; the few version differences are marked with Stonecutter comments such as `//? if >=26.2 {`, and each version's dependency versions are in `versions/<minecraft>/gradle.properties`. The sources in `src/` are kept in their 26.2 form.

Build with JDK 25 (it also compiles the Java 21 jar for 1.21.11) and the included Gradle wrapper:

```
./gradlew build
```

Each version's jar is written to `versions/<minecraft>/build/libs/argon-<mod version>+<minecraft>.jar`; `./gradlew collectJars` also copies them all into `build/libs/<mod version>/`.

A client game test audits every mixin, loads a world, overflows the entity and particle budgets, checks Argon's counters and compares its game-logic shortcuts against vanilla. It needs a display (use `xvfb-run` on a headless machine):

```
./gradlew :26.2:runClientGameTest
```
