package dev.argon.test;

import dev.argon.Adaptive;
import dev.argon.ArgonConfig;
import dev.argon.ArgonStats;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.InactivityFpsLimit;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A/B frame-rate comparison with Argon's features toggled at runtime in one world.
 * Opt-in: {@code ARGON_BENCH=1 ./gradlew runClientGameTest}. Results are printed as {@code ARGON-BENCH} lines.
 * Numbers are only comparable within one run on one machine.
 */
public class ArgonBenchmark implements FabricClientGameTest {
	private static final int SETTLE_TICKS = 60;
	private static final int MEASURE_TICKS = 120;
	private static final int ROUNDS = 3;
	private static final int RENDER_DISTANCE = 12;
	private static final int WORLD_WARMUP_TICKS = 300;
	private static final String NORMAL_SEED = "argon-bench";

	private enum Mode { OFF, STATIC, FULL }

	private record Scenario(String name, Runnable setup, Runnable perTick, Runnable teardown) {
	}

	private TestSingleplayerContext world;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"1".equals(System.getenv("ARGON_BENCH"))) {
			return;
		}
		ArgonConfig.load();

		// Heavy synthetic scenes on a superflat world.
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			runWorld(context, singleplayer, flatScenarios());
		}

		// Ordinary generated terrain with its natural trees, animals and structures: the case most players actually play.
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(state -> {
				state.setSeed(NORMAL_SEED);
				state.setWorldType(state.getNormalPresetList().get(0));
			})
			.create()) {
			runWorld(context, singleplayer, normalScenarios());
		}
	}

	private void runWorld(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<Scenario> scenarios) {
		this.world = singleplayer;
		context.runOnClient(client -> {
			client.options.enableVsync().set(false);
			client.options.framerateLimit().set(260);
			client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
			client.options.renderDistance().set(RENDER_DISTANCE);
		});
		cmd("gamemode spectator @s");
		cmd("tp @s ~ ~ ~ 0 0");
		// Let the surrounding chunks generate and mesh before measuring anything.
		context.waitTicks(WORLD_WARMUP_TICKS);
		context.takeScreenshot("bench-world-" + scenarios.get(0).name);

		for (Scenario scenario : scenarios) {
			scenario.setup.run();
			context.waitTicks(60);
			List<List<Double>> results = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

			for (int round = 0; round < ROUNDS; round++) {
				for (int i = 0; i < Mode.values().length; i++) {
					Mode mode = Mode.values()[(i + round) % Mode.values().length];
					double fps = measure(context, scenario, mode);
					results.get(mode.ordinal()).add(fps);
					System.out.printf("ARGON-BENCH scenario=%s mode=%s round=%d fps=%.2f scale=%.2f%n",
						scenario.name, mode, round, fps, Adaptive.scale());
				}
			}
			for (Mode mode : Mode.values()) {
				System.out.printf("ARGON-BENCH-MEDIAN scenario=%s mode=%s fps=%.2f%n", scenario.name, mode, median(results.get(mode.ordinal())));
			}
			scenario.teardown.run();
			context.waitTicks(40);
		}
	}

	private double measure(ClientGameTestContext context, Scenario scenario, Mode mode) {
		apply(mode);
		Adaptive.reset();
		for (int i = 0; i < SETTLE_TICKS; i++) {
			tick(context, scenario);
		}
		long frames = ArgonStats.FRAMES.sum();
		long start = System.nanoTime();
		for (int i = 0; i < MEASURE_TICKS; i++) {
			tick(context, scenario);
		}
		long elapsed = System.nanoTime() - start;
		return (ArgonStats.FRAMES.sum() - frames) * 1e9 / elapsed;
	}

	private void tick(ClientGameTestContext context, Scenario scenario) {
		if (scenario.perTick != null) {
			scenario.perTick.run();
		}
		context.waitTick();
	}

	private static void apply(Mode mode) {
		ArgonConfig config = ArgonConfig.get();
		boolean on = mode != Mode.OFF;
		config.entityCulling = on;
		config.blockEntityCulling = on;
		config.particleLimiter = on;
		config.maxSmallEntitiesPerFrame = on ? 256 : 0;
		config.adaptiveCulling = mode == Mode.FULL;
	}

	private List<Scenario> flatScenarios() {
		return List.of(
			new Scenario("cow-herd", this::spawnCows, null, () -> cmd("kill @e[type=!minecraft:player]")),
			new Scenario("item-pile", this::spawnItems, null, () -> cmd("kill @e[type=!minecraft:player]")),
			new Scenario("chest-room", () -> cmd("fill ~-30 ~ ~50 ~30 ~ ~62 minecraft:chest"), null,
				() -> cmd("fill ~-30 ~ ~50 ~30 ~ ~62 minecraft:air")),
			new Scenario("particle-storm", () -> { }, () -> cmd("particle minecraft:flame ~ ~1 ~8 5 2 5 0.01 300 force"), () -> { })
		);
	}

	/** The camera spins 4 degrees per tick so every direction (and whatever is in it) is sampled equally. */
	private List<Scenario> normalScenarios() {
		return List.of(new Scenario("normal-terrain", () -> { }, () -> cmd("tp @s ~ ~ ~ ~4 0"), () -> { }));
	}

	private void spawnCows() {
		Random random = new Random(42);
		for (int i = 0; i < 400; i++) {
			int dx = random.nextInt(61) - 30;
			int dz = 6 + random.nextInt(55);
			cmd("summon minecraft:cow ~" + dx + " ~ ~" + dz + " {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
		}
	}

	private void spawnItems() {
		// Grid spacing of 1 block keeps items from merging into stacks.
		for (int layer = 0; layer < 2; layer++) {
			for (int x = -10; x <= 10; x++) {
				for (int z = 2; z <= 16; z++) {
					cmd("summon minecraft:item ~" + x + " ~" + (layer * 1.5) + " ~" + z
						+ " {Item:{id:\"minecraft:stone\",count:1},Age:-32768s,PickupDelay:32767s}");
				}
			}
		}
	}

	private void cmd(String command) {
		this.world.getServer().runCommand("execute as @p at @s run " + command);
	}

	private static double median(List<Double> values) {
		List<Double> sorted = values.stream().sorted().toList();
		return sorted.get(sorted.size() / 2);
	}
}
