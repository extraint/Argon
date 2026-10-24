# Argon

A client-side Fabric mod for **Minecraft 26.2** that adds extra performance work on top of [Sodium](https://modrinth.com/mod/sodium).
Sodium already speeds up terrain rendering and chunk building; Argon targets the parts it leaves alone.

## What it does

| Feature | Effect |
| --- | --- |
| Entity distance culling | Stops rendering entities past a cutoff (default 64 blocks; 32 for items, XP orbs, arrows and armor stands). Vanilla already culls most mobs at a similar range, so this mainly matters for very large entities. Large entities get extra range so dragons and ghasts never vanish. Glowing entities, the player and the camera's vehicle are never culled. |
| Block entity culling | Stops rendering chests, signs, banners, etc. past 48 blocks. Beacons and end portals are exempt. |
| Small entity budget | Draws at most 256 items, XP orbs, arrows and armor stands per frame, so a huge item pile can't tank the frame rate. |
| Adaptive culling | Watches frame time. While FPS is below your target (default 60) the culling distances and particle cap shrink, down to 50% by default; they recover when there is headroom. Paused while the window is unfocused or minimized. |
| Particle limiter | Caps particles spawned per tick (default 150) so explosions and farms can't stall the frame. |
| Sodium chunk threads | Optional in-memory override of Sodium's chunk builder thread count. Sodium's own config file is never touched. |

Gains depend on the scene. In a software-rendered A/B test, item piles, chest-heavy rooms and particle storms gained roughly 20-30%, a large mob herd did not change, and ordinary terrain is unaffected. See [docs/BENCHMARK.md](docs/BENCHMARK.md) for the numbers and caveats.
Run `/argon status` to see how much work Argon has skipped.

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
  "sodiumChunkBuilderThreads": 0
}
```

## Commands

`/argon status`, `/argon reload`, `/argon reset`

## Requirements

Minecraft 26.2, Fabric Loader 0.20.1+, Fabric API, Sodium 0.9.2+, Java 25.

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
