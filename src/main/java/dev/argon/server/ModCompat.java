package dev.argon.server;

import dev.argon.ArgonConfig;
import dev.argon.ArgonStats;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;

import java.util.Optional;

/** Detects mods that optimize the same code as Argon's game-logic options and reports what is actually active. */
public final class ModCompat {
	private ModCompat() {
	}

	/** The loaded Lithium version, if any. */
	public static Optional<String> lithiumVersion() {
		return FabricLoader.getInstance().getModContainer("lithium").map(mod -> mod.getMetadata().getVersion().getFriendlyString());
	}

	/**
	 * Whether the mixin classes that add Argon's helper interfaces were applied. Argon's game-logic mixins are
	 * optional so a conflicting mod can't stop the game from starting; whether the injections actually run is
	 * visible in the counters on {@code /argon status} ({@link ArgonStats}).
	 */
	public static boolean gameLogicMixinsLoaded() {
		try {
			return CollidableSection.class.isAssignableFrom(EntitySection.class)
				&& CollidableStorage.class.isAssignableFrom(EntitySectionStorage.class)
				&& CollidableQuery.class.isAssignableFrom(LevelEntityGetterAdapter.class);
		} catch (Throwable t) {
			return false;
		}
	}

	/** One-line summary for the log and {@code /argon status}. */
	public static String describe() {
		ArgonConfig config = ArgonConfig.get();
		String lithium = lithiumVersion().map(version -> "Lithium " + version + " detected, running alongside it; ").orElse("");
		if (!gameLogicMixinsLoaded()) {
			return lithium + "game-logic mixins NOT loaded (another mod may have interfered); those options are inactive";
		}
		return lithium + "game-logic mixins loaded; fast entity lookup " + (config.fastEntityLookup ? "on" : "off") + " ("
			+ ArgonStats.FAST_ENTITY_LOOKUPS.sum() + " served), fast collisions " + (config.fastEntityCollisions ? "on" : "off")
			+ " (" + ArgonStats.FAST_COLLISION_QUERIES.sum() + " served)"
			+ (Collidability.verified() ? "" : "; collision rules could not be verified, so fast collisions stay off");
	}
}
