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
import net.minecraft.server.level.ParticleStatus;

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

			testPresets(context);
			testSodiumOverride(context);
			testAdaptiveRenderDistance(context);
			testScreens(context);
		}
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
		check(argon.pages().size() == 3, "expected 3 Argon pages in Sodium, found " + argon.pages().size());

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
