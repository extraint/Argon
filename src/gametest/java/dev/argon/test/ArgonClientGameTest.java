package dev.argon.test;

import dev.argon.Adaptive;
import dev.argon.ArgonConfig;
import dev.argon.ArgonClient;
import dev.argon.ArgonStats;
import dev.argon.Presets;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.QuadSplittingMode;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;
import java.util.Random;
import net.minecraft.server.level.ParticleStatus;

/** Loads a real world and checks that Argon's render hooks run and skip work without breaking the frame loop. */
public class ArgonClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getenv("ARGON_BENCH") != null) {
			return; // benchmark runs only measure
		}
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

			testServerOptimizations(context, world);
			testPresets(context);
			testSodiumOverride(context);
			testAdaptiveRenderDistance(context);
			testScreens(context);
		}
	}

	/**
	 * Argon's entity lookup and collision shortcuts must return exactly what vanilla's code returns, in the same
	 * order. Populate a world with many entity types (including collidable ones and a dragon with parts) across
	 * several sections, including negative coordinates, then compare both code paths on thousands of random boxes.
	 */
	private static void testServerOptimizations(ClientGameTestContext context, TestSingleplayerContext world) {
		String[] types = {"minecraft:cow", "minecraft:villager", "minecraft:oak_boat", "minecraft:shulker", "minecraft:armor_stand",
			"minecraft:minecart", "minecraft:item_frame", "minecraft:end_crystal", "minecraft:pufferfish", "minecraft:chicken"};
		Random placement = new Random(11);
		for (int i = 0; i < 240; i++) {
			int dx = placement.nextInt(121) - 60;
			int dz = placement.nextInt(121) - 60;
			int dy = placement.nextInt(40);
			world.getServer().runCommand("execute as @p at @s run summon " + types[i % types.length] + " ~" + dx + " ~" + dy + " ~" + dz);
		}
		world.getServer().runCommand("execute as @p at @s run summon minecraft:ender_dragon ~10 ~20 ~10");
		context.waitTicks(10);

		String result = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			ArgonConfig config = ArgonConfig.get();
			Entity anchor = level.players().isEmpty() ? null : level.players().get(0);
			if (anchor == null) {
				return "no player";
			}
			List<Entity> all = new java.util.ArrayList<>();
			level.getAllEntities().forEach(all::add);
			Random random = new Random(99);
			int compared = 0;
			int collidableHits = 0;

			for (int i = 0; i < 4000; i++) {
				double size = i % 50 == 0 ? 60 + random.nextInt(100) : 0.4 + random.nextDouble() * 10;
				double cx = anchor.getX() + random.nextInt(161) - 80;
				double cy = anchor.getY() + random.nextInt(60) - 10;
				double cz = anchor.getZ() + random.nextInt(161) - 80;
				AABB box = new AABB(cx, cy, cz, cx + size, cy + size * (0.3 + random.nextDouble()), cz + size);
				Entity source = random.nextInt(4) == 0 ? null : all.get(random.nextInt(all.size()));

				config.fastEntityLookup = false;
				config.fastEntityCollisions = false;
				List<Entity> expectedEntities = level.getEntities(source, box);
				List<AABB> expectedShapes = bounds(level.getEntityCollisions(source, box));

				config.fastEntityLookup = true;
				config.fastEntityCollisions = true;
				List<Entity> actualEntities = level.getEntities(source, box);
				List<AABB> actualShapes = bounds(level.getEntityCollisions(source, box));

				if (!expectedEntities.equals(actualEntities)) {
					return "entity lookup mismatch for " + box + ": expected " + expectedEntities.size() + " got " + actualEntities.size();
				}
				if (!expectedShapes.equals(actualShapes)) {
					return "collision mismatch for " + box + ": expected " + expectedShapes.size() + " got " + actualShapes.size();
				}
				compared++;
				if (!expectedShapes.isEmpty()) {
					collidableHits++;
				}
			}
			return "ok compared=" + compared + " queriesWithCollisions=" + collidableHits;
		});

		System.out.println("ARGON-EQUIVALENCE " + result);
		check(result.startsWith("ok"), "server optimizations diverged from vanilla: " + result);
		check(Integer.parseInt(result.replaceAll(".*queriesWithCollisions=", "")) > 50, "test did not exercise collisions: " + result);
		world.getServer().runCommand("kill @e[type=!minecraft:player]");
	}

	private static List<AABB> bounds(List<VoxelShape> shapes) {
		return shapes.stream().map(VoxelShape::bounds).toList();
	}

	private static void testPresets(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.renderDistance().set(16);
			Presets.apply(client, Presets.Preset.POTATO);
			check(client.options.renderDistance().get() <= 6, "Potato should cap render distance at 6");
			check(client.options.particles().get() == ParticleStatus.MINIMAL, "Potato should minimize particles");
			Presets.undo(client);
			check(client.options.renderDistance().get() == 16, "undo should restore render distance 16");
			check(!Presets.canUndo(), "nothing left to undo");

			// A preset must never raise a setting that is already cheaper than its target.
			client.options.renderDistance().set(4);
			Presets.apply(client, Presets.Preset.PERFORMANCE);
			check(client.options.renderDistance().get() == 4, "preset raised an already-low render distance");
			Presets.undo(client);
			client.options.renderDistance().set(16);
		});
	}

	private static void testSodiumOverride(ClientGameTestContext context) {
		QuadSplittingMode original = SodiumClientMod.options().performance.quadSplittingMode;
		ArgonConfig.get().sodiumTranslucencySorting = "FAST";
		ArgonClient.applyTuning();
		check(SodiumClientMod.options().performance.quadSplittingMode == QuadSplittingMode.OFF, "FAST should turn quad splitting off");
		ArgonConfig.get().sodiumTranslucencySorting = "DEFAULT";
		ArgonClient.applyTuning();
		check(SodiumClientMod.options().performance.quadSplittingMode == original, "DEFAULT should restore Sodium's own value");
	}

	private static void testAdaptiveRenderDistance(ClientGameTestContext context) {
		ArgonConfig config = ArgonConfig.get();
		config.adaptiveRenderDistance = true;
		config.minRenderDistance = 8;
		config.targetFps = 360; // unreachable, so frames always count as slow
		context.runOnClient(client -> client.options.renderDistance().set(16));
		context.waitTicks(500);

		int shrunk = context.computeOnClient(client -> client.options.renderDistance().get());
		check(shrunk < 16 && shrunk >= 8, "adaptive render distance should shrink within [8, 16), was " + shrunk);

		config.adaptiveRenderDistance = false;
		context.waitTicks(60);
		int restored = context.computeOnClient(client -> client.options.renderDistance().get());
		check(restored == 16, "render distance should return to the user's 16 when disabled, was " + restored);
	}

	/** Argon registers its pages with Sodium; check they exist, read back our values, and screenshot each. */
	private static void testScreens(ClientGameTestContext context) {
		ModOptions argon = ConfigManager.CONFIG.getModOptions().stream()
			.filter(mod -> "argon".equals(mod.configId()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Argon did not register its options with Sodium"));
		check(argon.pages().size() == 4, "expected 4 Argon pages in Sodium, found " + argon.pages().size());

		ArgonConfig config = ArgonConfig.get();
		config.entityDistance = 96;
		ConfigManager.CONFIG.resetAllOptionsFromBindings(); // Sodium caches values and reloads them from bindings
		check(ConfigManager.CONFIG.readIntOption(Identifier.fromNamespaceAndPath("argon", "entity_distance")) == 96,
			"Sodium should read Argon's entity distance through the binding");

		int index = 0;
		for (var page : argon.pages()) {
			OptionPage optionPage = (OptionPage) page;
			context.setScreen(() -> VideoSettingsScreen.createScreen(null, optionPage));
			context.waitTicks(3);
			context.takeScreenshot("argon-sodium-page-" + index++);
		}
		context.setScreen(() -> null);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
