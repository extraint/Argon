# Benchmark: Argon off vs on

Produced by `ARGON_BENCH=1 ./gradlew runClientGameTest` (see `src/gametest/java/dev/argon/test/ArgonBenchmark.java`).

**Read this before quoting the numbers.**

- Run in a container with **no GPU**: Mesa llvmpipe software rendering at 854x480, Sodium 0.9.2, Minecraft 26.2.
  Absolute FPS is meaningless for real hardware, and a real GPU may shift the relative gains in either direction.
- "Off" means every Argon feature disabled at runtime. The mod is still loaded, so its hook overhead is included (it is negligible).
- Scenes are deliberately heavy and synthetic. In ordinary terrain Argon changes nothing measurable.
- Each cell is the median of 3 rounds (6 s measured after a 3 s settle), modes rotated each round. Round-to-round spread is about +/-10%, so gaps under ~10% are noise.
- **Static** = Argon's fixed limits only. **Full** = defaults, including adaptive culling (target 60 FPS).

| Scene | Off | Static | Full | Static vs Off |
| --- | ---: | ---: | ---: | ---: |
| 400 cows in view | 50.1 | 47.6 | 51.7 | within noise |
| 630 dropped items near the camera | 66.2 | 79.5 | 87.3 | +20% |
| 793 chests, 50-69 blocks away | 82.6 | 104.5 | 101.6 | +27% |
| 300 particles/tick for the whole run | 65.0 | 77.9 | 79.1 | +20% |

## What this says

- The wins come from the **small-entity budget** (items), the **block entity cutoff** (chests between 48 and the vanilla 64 blocks) and the **particle cap**.
- **Entity distance culling does little for normal mobs**: vanilla already stops drawing an entity beyond roughly 64 blocks times its size, so the herd is unchanged. It only matters for very large entities.
- **Adaptive culling** only acts when FPS is below target. It engaged (scale 0.5) in the cow scene, where it produced no measurable gain, and stayed at 1.0 elsewhere because FPS was above 60.
- Gains of 20-30% appear in the stress scenes above. Do not expect "drastic" improvements in normal play; Sodium provides most of the big wins.
