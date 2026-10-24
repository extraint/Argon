package dev.argon;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;

/** Kept in its own class so Sodium is only touched when we actually tune it. */
final class SodiumTuning {
	private SodiumTuning() {
	}

	/** Applies in memory only; Sodium's own options file is never modified. */
	static void apply(ArgonConfig config) {
		if (config.sodiumChunkBuilderThreads > 0) {
			SodiumClientMod.options().performance.chunkBuilderThreads = config.sodiumChunkBuilderThreads;
		}
	}
}
