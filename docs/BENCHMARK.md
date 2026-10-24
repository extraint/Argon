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

# Server tick time: Argon vs vanilla vs Lithium

Argon's "Game Logic" options speed up code that runs in singleplayer's integrated server (and the client world). They are measured here as **milliseconds per server tick** (lower is better; 50 ms is the 20 TPS budget), from `ARGON_BENCH=server ./gradlew runClientGameTest`.

- Each figure is the median of **separate game launches** (3 for vanilla and Argon, 2 for Lithium), each the median of 5 windows of 100 ticks. Flipping the options inside one launch gave misleading results (it disturbs the JIT), so each mode gets its own JVM.
- Same caveats as above: no GPU, only 4 cores shared with the software renderer, staged scenes. Compare columns within this table only.
- "Vanilla" = Argon's game-logic options off. "Lithium" = Lithium 0.25.3 loaded, Argon's options off. Lithium is a separate mod and none of its code is used by Argon.

| Scene | Vanilla | Argon on | Lithium |
| --- | ---: | ---: | ---: |
| 400 mobs with AI (cows, villagers, chickens, pigs) in a pen | 11.7 | 11.6 (-1%) | 7.5 (-36%) |
| 630 dropped items | 7.1 | 4.7 (**-35%**) | 3.4 (-52%) |
| 500 mobs spread over ~280x280 blocks | 8.7 | 8.5 (-2%) | 6.0 (-31%) |
| 420 empty hoppers | 4.8 | 4.2 (-11%) | 3.3 (-31%) |
| Idle normal terrain | 4.9 | 4.7 (-5%) | 4.0 (-19%) |

**Reading it honestly:** Argon's two optimizations help a lot when many items are ticking, a little with hoppers and idle terrain, and not measurably with plain mob AI. Lithium is faster in every scene because it optimizes far more systems (AI, pathfinding, block collisions, chunk storage and more). Argon is not a replacement for Lithium; the two can be installed together.

## How correctness is checked

`ArgonClientGameTest` populates a world with boats, minecarts, shulkers, armor stands, cows, villagers, a dragon with its parts and more, across several sections including negative coordinates. For 4,000 random query boxes it requires Argon's entity lookup and collision queries to return **exactly the same results in the same order** as vanilla's code. This test caught a real mistake during development (boats and minecarts use a different collision rule), which is now handled by falling back to vanilla for them.

## Argon on top of Lithium

Same method as above, with Lithium 0.25.3 loaded in every run (ms per tick; n = separate launches; "+ both" is Argon's normal setting).

| Scene | Lithium only (n=4) | + Argon lookup (n=2) | + Argon collisions (n=2) | + both (n=3) |
| --- | ---: | ---: | ---: | ---: |
| Mob pen | 7.4 | 6.7 | 6.4 | 7.1 |
| Item pile | 3.4 | 3.4 | 3.1 | 3.1 |
| Spread mobs | 6.1 | 6.0 | 6.0 | 5.4 |
| Hopper field | 3.3 | 3.5 | 3.2 | 3.3 |
| Idle terrain | 4.0 | 3.6 | 3.5 | 3.7 |

With so few launches and about +/-5% noise, no single cell is conclusive. What the table does support: adding Argon on top of Lithium did not clearly make anything slower, and most cells moved slightly the right way, so Argon leaves both options on when Lithium is present. Lithium hooks the collision method from the inside (it redirects its entity fetch) while Argon replaces the method's start, so when Argon's shortcut runs, Lithium's version of that step does not; the equivalence test confirms the results are identical either way.
