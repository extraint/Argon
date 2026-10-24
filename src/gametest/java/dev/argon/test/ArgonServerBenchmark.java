package dev.argon.test;

import dev.argon.ArgonConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.InactivityFpsLimit;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Measures server tick time (milliseconds per tick) in staged scenes.
 * Opt-in: {@code ARGON_BENCH=server ./gradlew runClientGameTest}. Prints {@code ARGON-MSPT} lines.
 * Client rendering is throttled so it competes as little as possible with the server thread for CPU.
 */
public class ArgonServerBenchmark implements FabricClientGameTest {
	private static final int SETTLE_TICKS = 100;
	private static final int MEASURE_TICKS = 100;
	private static final int ROUNDS = 5;
	private static final int FLUSH_TICKS = 100; // the tick-time ring holds 100 entries, so a full ring is all one mode
	private static final String NORMAL_SEED = "argon-bench";

	/**
	 * ARGON_BENCH_FORCE pins one mode for the whole run: on, off, lookup (only fast entity lookup) or
	 * collisions (only fast entity collisions). Unset alternates between off and on inside one launch.
	 */
	private static final String FORCE = System.getenv("ARGON_BENCH_FORCE");
	private static final Boolean FORCED = FORCE == null ? null : Boolean.TRUE;

	private record Scenario(String name, Runnable setup, Runnable teardown) {
	}

	private TestSingleplayerContext world;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"server".equals(System.getenv("ARGON_BENCH"))) {
			return;
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			runWorld(context, singleplayer, flatScenarios());
		}
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(state -> {
				state.setSeed(NORMAL_SEED);
				state.setWorldType(state.getNormalPresetList().get(0));
			})
			.create()) {
			runWorld(context, singleplayer, List.of(new Scenario("normal-terrain-idle", () -> { }, () -> { })));
		}
	}

	private void runWorld(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<Scenario> scenarios) {
		this.world = singleplayer;
		context.runOnClient(client -> {
			client.options.enableVsync().set(false);
			client.options.framerateLimit().set(10);
			client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
			client.options.renderDistance().set(10);
		});
		cmd("gamemode spectator @s");
		cmd("tp @s ~ ~ ~ 0 0");
		context.waitTicks(200);

		ArgonConfig.load();
		for (Scenario scenario : scenarios) {
			scenario.setup.run();
			context.waitTicks(SETTLE_TICKS);
			double[][] results = new double[2][ROUNDS];
			double[] worst = new double[2];

			for (int round = 0; round < ROUNDS; round++) {
				for (int i = 0; i < 2; i++) {
					int mode = FORCED != null ? 1 : (i + round) % 2; // 0 = Argon server optimizations off, 1 = on; order alternates
					if (FORCED != null && i == 1) {
						break; // forced single-mode run: one measurement per round
					}
					boolean lookup = mode == 1 && !"collisions".equals(FORCE) && !"off".equals(FORCE);
					boolean collisions = mode == 1 && !"lookup".equals(FORCE) && !"off".equals(FORCE);
					ArgonConfig.get().fastEntityLookup = lookup;
					ArgonConfig.get().fastEntityCollisions = collisions;
					context.waitTicks(FLUSH_TICKS);
					long[] times = world.getServer().computeOnServer(server -> server.getTickTimesNanos().clone());
					double mean = 0;
					for (long t : times) {
						mean += t;
						worst[mode] = Math.max(worst[mode], t / 1e6);
					}
					results[mode][round] = mean / times.length / 1e6;
					System.out.printf("ARGON-MSPT scenario=%s argon=%s round=%d mspt=%.3f%n", scenario.name, FORCE != null ? FORCE : (mode == 1 ? "on" : "off"), round, results[mode][round]);
				}
			}
			for (int mode = 0; mode < 2; mode++) {
				if (FORCED != null && mode != 1) {
					continue;
				}
				double[] sorted = results[mode].clone();
				java.util.Arrays.sort(sorted);
				System.out.printf("ARGON-MSPT-MEDIAN scenario=%s argon=%s mspt=%.3f max=%.2f%n", scenario.name, FORCE != null ? FORCE : (mode == 1 ? "on" : "off"), sorted[ROUNDS / 2], worst[mode]);
			}
			scenario.teardown.run();
			context.waitTicks(60);
		}
	}

	private List<Scenario> flatScenarios() {
		return List.of(
			new Scenario("mob-pen", this::spawnMobs, () -> cmd("kill @e[type=!minecraft:player]")),
			new Scenario("item-pile", this::spawnItems, () -> cmd("kill @e[type=!minecraft:player]")),
			new Scenario("spread-mobs", this::spawnSpreadMobs, () -> cmd("kill @e[type=!minecraft:player]")),
			new Scenario("hopper-field", () -> cmd("fill ~-10 ~ ~5 ~10 ~ ~25 minecraft:hopper"), () -> cmd("fill ~-10 ~ ~5 ~10 ~ ~25 minecraft:air"))
		);
	}

	private void spawnMobs() {
		Random random = new Random(7);
		String[] types = {"minecraft:cow", "minecraft:villager", "minecraft:chicken", "minecraft:pig"};
		for (int i = 0; i < 400; i++) {
			int dx = random.nextInt(41) - 20;
			int dz = random.nextInt(41) - 20;
			cmd("summon " + types[i % types.length] + " ~" + dx + " ~ ~" + dz + " {PersistenceRequired:1b,Silent:1b}");
		}
	}

	/** Mobs scattered over many chunks, so many entity sections exist at once, as in a lived-in world. */
	private void spawnSpreadMobs() {
		Random random = new Random(21);
		String[] types = {"minecraft:cow", "minecraft:sheep", "minecraft:pig", "minecraft:chicken"};
		for (int i = 0; i < 500; i++) {
			int dx = random.nextInt(281) - 140;
			int dz = random.nextInt(281) - 140;
			int dy = random.nextInt(12);
			cmd("summon " + types[i % types.length] + " ~" + dx + " ~" + dy + " ~" + dz + " {PersistenceRequired:1b,Silent:1b}");
		}
	}

	private void spawnItems() {
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
}
