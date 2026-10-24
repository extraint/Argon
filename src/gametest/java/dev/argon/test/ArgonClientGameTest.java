package dev.argon.test;

import dev.argon.Adaptive;
import dev.argon.ArgonConfig;
import dev.argon.ArgonStats;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Loads a real world and checks that Argon's render hooks run and skip work without breaking the frame loop. */
public class ArgonClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		ArgonConfig.load();
		ArgonStats.reset();

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(40);
			context.takeScreenshot("argon-baseline");

			// More items than the per-frame small-entity budget, plus a particle burst above the per-tick cap.
			int items = ArgonConfig.get().maxSmallEntitiesPerFrame + 150;
			for (int i = 0; i < items; i++) {
				world.getServer().runCommand("execute as @p at @s run summon item ~ ~2 ~ {Item:{id:\"minecraft:stone\",count:1}}");
			}
			world.getServer().runCommand("execute as @p at @s run particle minecraft:explosion ~ ~2 ~ 3 3 3 0 2000 force");
			context.waitTicks(60);
			context.takeScreenshot("argon-stress");

			check(ArgonStats.ENTITIES_CULLED.sum() > 0, "expected the small-entity budget to cull some of " + items + " items");
			check(ArgonStats.PARTICLES_DROPPED.sum() > 0, "expected the particle limiter to drop some of a 2000-particle burst");
			double scale = Adaptive.scale();
			check(scale >= ArgonConfig.get().adaptiveMinScale - 1e-9 && scale <= 1.0, "adaptive scale out of range: " + scale);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
