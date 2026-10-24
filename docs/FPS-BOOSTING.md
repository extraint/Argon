# FPS boosting with Argon

Argon is a Fabric 26.2 add-on for [Sodium](https://modrinth.com/mod/sodium). Sodium does the heavy lifting (terrain rendering, chunk building, its own entity culling); Argon trims the work Sodium leaves alone and gives you one-click settings that cut GPU and CPU load.

**What to expect:** in ordinary terrain Argon changes little. In heavy scenes (item piles, chest-filled rooms, particle storms) a software-rendered test showed roughly 20-30% more FPS. See [BENCHMARK.md](BENCHMARK.md) for the numbers and their limits. Presets and render distance are where large gains usually come from, because they remove work entirely.

## Quick start

1. Install Minecraft 26.2 with Fabric Loader 0.20.1+, Fabric API, **Sodium 0.9.2+** and Argon (Java 25).
2. Launch once, then open Argon from Mod Menu (or edit `config/argon.json`).
3. Leave the defaults on. They are safe and invisible in normal play.
4. Still too slow? Open **Chunks & Rendering...** and apply the **Performance** preset (see below).

## What each feature does

| Feature | Helps with | Cost to visuals |
| --- | --- | --- |
| Small entity budget | Piles of dropped items, XP orbs, arrows | Past 256 per frame the extras aren't drawn |
| Block entity culling | Chests, signs, banners 48+ blocks away | None you can see at that range |
| Particle limiter | Explosions, farms, big effects | Bursts above 150 particles/tick are trimmed |
| Entity distance culling | Very large entities far away | Mostly redundant with vanilla for normal mobs |
| Adaptive culling | Any scene that drops below your target FPS | Distances shrink down to 50% only while slow |
| Adaptive render distance (opt-in) | GPU- or chunk-bound scenes | Fewer chunks shown while FPS is low |
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

## What Argon cannot do

- OpenGL context flags and the graphics backend are fixed when the window opens, so Argon can't change them at runtime.
- Argon can't make a GPU-bound scene faster than the GPU allows; it reduces the work submitted.
- Vanilla 26.2 already drops entity shadows past 16 blocks and throttles minimized or idle windows, so Argon doesn't duplicate that.

## Verifying changes yourself

`ARGON_BENCH=1 ./gradlew runClientGameTest` runs the A/B benchmark (needs a display; use `xvfb-run` on a headless machine). Compare results only within one run on one machine.
