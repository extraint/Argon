package dev.argon;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.QuadSplittingMode;

/**
 * Kept in its own class so Sodium is only touched when we actually tune it.
 * Changes are made in memory; Sodium's own options file is not written by Argon. If you press Apply in Sodium's
 * own settings screen while an override is active, Sodium will save the overridden values.
 */
final class SodiumTuning {
	private static Integer originalThreads;
	private static QuadSplittingMode originalSplitting;

	private SodiumTuning() {
	}

	static void apply(ArgonConfig config) {
		SodiumOptions.PerformanceSettings performance = SodiumClientMod.options().performance;
		if (originalThreads == null) {
			// Remember Sodium's own values so an override can be switched off again without a restart.
			originalThreads = performance.chunkBuilderThreads;
			originalSplitting = performance.quadSplittingMode;
		}

		performance.chunkBuilderThreads = config.sodiumChunkBuilderThreads > 0 ? config.sodiumChunkBuilderThreads : originalThreads;
		performance.quadSplittingMode = "FAST".equals(config.sodiumTranslucencySorting) ? QuadSplittingMode.OFF : originalSplitting;
	}
}
