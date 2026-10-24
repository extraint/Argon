# Argon

A client-side Fabric mod for **Minecraft 26.2** that adds extra performance work on top of [Sodium](https://modrinth.com/mod/sodium).
Sodium already speeds up terrain rendering and chunk building; Argon targets the parts it leaves alone.

**New here?** Read the [FPS boosting guide](docs/FPS-BOOSTING.md) for what each option does, preset values and tuning tips.

## What it does

| Feature | Effect |
| --- | --- |
| Entity distance culling | Stops rendering entities past a cutoff (default 64 blocks; 32 for items, XP orbs, arrows and armor stands). Vanilla already culls most mobs at a similar range, so this mainly matters for very large entities. Large entities get extra range so dragons and ghasts never vanish. Glowing entities, the player and the camera's vehicle are never culled. |
| Block entity culling | Stops rendering chests, signs, banners, etc. past 48 blocks. Beacons and end portals are exempt. |
| Small entity budget | Draws at most 256 items, XP orbs, arrows and armor stands per frame, so a huge item pile can't tank the frame rate. |
| Adaptive culling | Watches frame time. While FPS is below your target (default 60) the culling distances and particle cap shrink, down to 50% by default; they recover when there is headroom. Paused while the window is unfocused or minimized. |
| Particle limiter | Caps particles spawned per tick (default 150) so explosions and farms can't stall the frame. |
| Adaptive render distance | Off by default. While frames stay well below your target FPS it lowers render distance (2 chunks at a time, never below your minimum) and raises it back one chunk at a time when there is headroom, never above the value you chose. Changes are slow on purpose because every change reloads chunks. Your own value is restored when you disable it or quit. |
| Performance presets | Balanced, Performance and Potato bundles of vanilla video settings (render distance, biome blend, clouds, particles, weather, mipmaps, entity distance, shadows, leaves, transparency, vignette, AO, chunk fade-in). Applied only when you press the button or run `/argon preset <name>`; they only ever lower a setting, and can be undone for the session. |
| Translucency sorting | "Fast" turns off Sodium's translucent quad splitting, saving CPU on water and glass-heavy scenes at the cost of occasional sorting glitches. In memory only. |
| Sodium chunk threads | Optional in-memory override of Sodium's chunk builder thread count. Sodium's own config file is never touched. |

Gains depend on the scene. In a software-rendered A/B test, item piles, chest-heavy rooms and particle storms gained roughly 20-30% and a large mob herd did not change; ordinary terrain was not measured. See [docs/BENCHMARK.md](docs/BENCHMARK.md) for the numbers and caveats.
Run `/argon status` to see how much work Argon has skipped.

## How much does it boost FPS?

There is no single percentage: it depends entirely on the scene. Measured in an A/B test (Argon off vs on, same world, Sodium 0.9.2, Minecraft 26.2, **software rendering with no GPU**):

| Scene | Fixed limits | Defaults (with adaptive) |
| --- | ---: | ---: |
| 630 dropped items near the camera | **+20%** | **+32%** |
| 793 chests, 50-69 blocks away | **+27%** | **+23%** |
| 300 particles per tick | **+20%** | **+22%** |
| 400 cows in view | no real change (-5% to +3%, within noise) | no real change |

- **Best case measured: about +20% to +30%**, in deliberately heavy scenes.
- **Normal play (ordinary terrain, few entities): not measured**, and expected to be small, because the features only skip work that is rare in normal scenes.
- Run-to-run spread was about +/-10%, so treat these as rough. A real GPU may give different results.
- Most of the "big" FPS gains in practice come from Sodium itself and from lowering render distance and video settings (see the presets), not from culling.

Full methodology and raw numbers: [docs/BENCHMARK.md](docs/BENCHMARK.md).

## Config

With [Mod Menu](https://modrinth.com/mod/modmenu) installed, open Argon's settings screen from the mod list (optional; Argon works without it). Changes save when you press Done.

You can also edit `config/argon.json` (created on first launch) and run `/argon reload`.

```json
{
  "entityCulling": true,
  "entityDistance": 64,
  "smallEntityDistance": 32,
  "blockEntityCulling": true,
  "blockEntityDistance": 48,
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

## Requirements

Minecraft 26.2, Fabric Loader 0.20.1+, Fabric API, Sodium 0.9.2+, Java 25.

## What Argon cannot change

- **OpenGL context flags** (such as Sodium's no-error context) and the **graphics backend** are fixed when the window is created, so a mod cannot switch them while the game runs. Change them in Sodium's settings or the vanilla Video Settings and restart.
- Sodium's overrides here are applied in memory. If you press Apply in Sodium's own settings screen while an override is active, Sodium will save it.

## Why no shadow or idle-FPS options?

Vanilla 26.2 already stops entity shadows past 16 blocks and throttles minimized or idle windows, so Argon leaves those alone.

## Building

Minecraft 26.x needs JDK 25 and Gradle 9.1+ (the wrapper is included):

```
./gradlew build
```

The jar is written to `build/libs/argon-<version>.jar`.

A client game test loads a world, overflows the entity and particle budgets, and checks Argon's counters. It needs a display (use `xvfb-run` on a headless machine):

```
./gradlew runClientGameTest
```
