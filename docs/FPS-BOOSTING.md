# FPS boosting with Argon

Argon is a Fabric add-on (Minecraft 1.21.11, 26.1.2, 26.2 and 26.3) for [Sodium](https://modrinth.com/mod/sodium). Sodium does the heavy lifting (terrain rendering, chunk building, its own entity culling); Argon trims the work Sodium leaves alone and gives you one-click settings that cut GPU and CPU load.

**What to expect:** see the percentages below. In heavy scenes (item piles, chest-filled rooms, particle storms) software-rendered tests showed roughly 20-45% more FPS, and none in normal terrain. See [BENCHMARK.md](BENCHMARK.md) for the numbers and their limits. Presets and render distance are where large gains usually come from, because they remove work entirely.

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

Full methodology and raw numbers: [docs/BENCHMARK.md](BENCHMARK.md).

## Quick start

1. Install Fabric Loader, Fabric API, **Sodium** and the Argon jar for your Minecraft version (see the requirements table in the README).
2. Launch once, then open **Options > Video Settings** and pick the **Argon** tab on the left (or edit `config/argon.json`).
3. Leave the defaults on. They are safe and invisible in normal play.
4. Still too slow? Open the **Chunks & Presets** page, set **Apply Preset** to Performance and press Apply (see below).

## What each feature does

| Feature | Helps with | Cost to visuals |
| --- | --- | --- |
| Small entity budget | Piles of dropped items, XP orbs, arrows | Past 256 per frame the extras aren't drawn |
| Block entity culling | Chests, signs, banners 48+ blocks away | None you can see at that range |
| Particle limiter | Explosions, farms, big effects | Bursts above 150 particles/tick are trimmed |
| Entity distance culling | Very large entities far away | Mostly redundant with vanilla for normal mobs |
| Adaptive culling | Any scene that drops below your target FPS | Distances shrink down to 50% only while slow |
| Adaptive render distance (opt-in) | GPU- or chunk-bound scenes | Fewer chunks shown while FPS is low |
| Fast entity lookup / collisions | Tick lag (low TPS) in singleplayer, especially with many items | None; identical results to vanilla |
| Presets | Weak hardware | Lowers vanilla video settings (undoable) |
| Translucency sorting: Fast (opt-in) | Water and glass heavy views | Occasional sorting glitches |

## Presets

Applied only when you press the button or run `/argon preset <balanced|performance|potato>`. They only lower settings and never raise ones already cheaper than the preset. `/argon preset undo` reverts them for the session.

| Setting | Balanced | Performance | Potato |
| --- | --- | --- | --- |
| Render distance (max) | 16 | 12 | 6 |
| Biome blend (max) | 3 | 1 | 0 |
| Clouds | Fast | Off | Off |
| Particles | Decreased | Decreased | Minimal |
| Weather radius (max) | 5 | 3 | 3 |
| Mipmap levels (max) | 4 | 2 | 0 |
| Entity distance scaling (max) | 1.0 | 0.75 | 0.5 |
| Entity shadows | unchanged | off | off |
| Leaves, improved transparency, vignette | transparency/vignette off | all off | all off |
| Ambient occlusion | unchanged | unchanged | off |

## Tuning tips

- **Check the effect:** `/argon status` shows how many entities, block entities and particles Argon skipped, and the current adaptive scale.
- **Low FPS only in farms or storage rooms:** lower `blockEntityDistance` and `maxSmallEntitiesPerFrame` in `config/argon.json`, then `/argon reload`.
- **Low FPS everywhere:** apply the Performance preset, then consider Adaptive Render Distance with a realistic `targetFps`.
- **More CPU cores than Sodium uses:** set `sodiumChunkBuilderThreads` to a fixed value. Leave it at 0 for Sodium's automatic choice.
- **Don't set `targetFps` above what your monitor or limiter allows:** adaptive features would then think every frame is slow.

## Lag that isn't FPS

If the game stutters while FPS looks fine, the integrated server may be running slowly (many items, mobs, hoppers). The **Game Logic** page has two safe optimizations for that; they are on by default. For heavier worlds also install [Lithium](https://modrinth.com/mod/lithium), which optimizes much more and works alongside Argon (see the README for what was tested together).

## What Argon cannot do

- OpenGL context flags and the graphics backend are fixed when the window opens, so Argon can't change them at runtime.
- Argon can't make a GPU-bound scene faster than the GPU allows; it reduces the work submitted.
- Vanilla already drops entity shadows past 16 blocks and throttles minimized or idle windows, so Argon doesn't duplicate that.

## Verifying changes yourself

`ARGON_BENCH=1 ./gradlew runClientGameTest` runs the A/B benchmark (needs a display; use `xvfb-run` on a headless machine). Compare results only within one run on one machine.
