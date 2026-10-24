# Benchmark: Argon off vs on

Produced by `ARGON_BENCH=1 ./gradlew runClientGameTest` (see `src/gametest/java/dev/argon/test/ArgonBenchmark.java`).

**Read this before quoting the numbers.**

- Run in a container with **no GPU**: Mesa llvmpipe software rendering at 854x480, Sodium 0.9.2, Minecraft 26.2.
  Absolute FPS is meaningless for real hardware, and a real GPU may shift the relative gains in either direction.
- "Off" means every Argon feature disabled at runtime. The mod is still loaded, so its hook overhead is included (it is negligible).
- Each cell is the median of 3 rounds (6 s measured after a 3 s settle), modes rotated each round.
  Within one run the spread is about +/-10%. **Between two separate runs the gain itself moved a lot** (the item pile showed +20% in run 1 and +42% in run 2), so quote ranges, not single numbers.
- **Static** = Argon's fixed limits only. **Full** = defaults, including adaptive culling (target 60 FPS).
- Run 2 pinned render distance to 12; run 1 used the test world's default. Terrain scenes are far more expensive per frame than the flat ones.

## Normal terrain (run 2)

A generated world (seed `argon-bench`, default world type) at render distance 12. The camera sits at spawn in forest and spins 4 degrees per tick, so all directions are measured equally. Natural trees and terrain, no staged entities.

| Mode | Median FPS | Rounds | vs Off |
| --- | ---: | --- | ---: |
| Off | 12.8 | 12, 13, 13 | - |
| Static | 12.3 | 12, 13, 12 | -3.8% (noise) |
| Full | 12.9 | 13, 13, 13 | +0.7% (noise) |

**No measurable difference.** There is almost nothing for Argon to skip in ordinary terrain, and this scene was limited by software rasterization of leaves and terrain, which Argon does not touch. On a real GPU the frame cost shifts toward the CPU, so this result does not prove there is never a gain there, but it gives no evidence of one.

## Staged heavy scenes on a superflat world

| Scene | Off | Static | Full | Static vs Off | Full vs Off |
| --- | ---: | ---: | ---: | ---: | ---: |
| 400 cows in view | 50.1 / 48.1 | 47.6 / 49.6 | 51.7 / 52.6 | -5% / +3% | +3% / +9% |
| 630 dropped items near the camera | 66.2 / 53.6 | 79.5 / 76.3 | 87.3 / 77.7 | +20% / +42% | +32% / +45% |
| 793 chests, 50-69 blocks away | 82.6 / 74.4 | 104.5 / 95.3 | 101.6 / 97.3 | +27% / +28% | +23% / +31% |
| 300 particles/tick for the whole run | 65.0 / 60.7 | 77.9 / 72.6 | 79.1 / 77.1 | +20% / +20% | +22% / +27% |

Each cell is "run 1 / run 2".

## What this says

- The wins come from the **small-entity budget** (items), the **block entity cutoff** (chests between 48 and the vanilla 64 blocks) and the **particle cap**, in scenes built to stress exactly those.
- **Entity distance culling does little for normal mobs**: vanilla already stops drawing an entity beyond roughly 64 blocks times its size, so the herd is essentially unchanged. It only matters for very large entities.
- **Adaptive culling** only acts when FPS is below target. It engaged (scale 0.5) in the cow and normal-terrain scenes, where it produced little or no measurable gain, and stayed at 1.0 in the others because FPS was above 60.
- Gains of roughly 20-45% appear only in the stress scenes above; in normal terrain there was none. Do not expect "drastic" improvements in normal play; Sodium provides most of the big wins.
