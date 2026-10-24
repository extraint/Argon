package dev.argon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** User-editable settings, stored in {@code config/argon.json}. All distances are in blocks. */
public final class ArgonConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("Argon");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("argon.json");

	private static volatile ArgonConfig current = new ArgonConfig();

	/** Skip rendering entities beyond {@link #entityDistance} (plus a margin for large entities). */
	public boolean entityCulling = true;
	public int entityDistance = 64;
	/** Cheap, numerous entities (items, XP orbs, arrows, armor stands) get a shorter cutoff. */
	public int smallEntityDistance = 32;

	/** Skip rendering block entities (chests, signs, banners, ...) beyond {@link #blockEntityDistance}. */
	public boolean blockEntityCulling = true;
	public int blockEntityDistance = 48;

	/** Cap how many particles may be spawned per client tick. 0 disables the cap. */
	public boolean particleLimiter = true;
	public int maxParticlesPerTick = 150;

	/** Override Sodium's chunk builder thread count. 0 leaves Sodium's automatic choice alone. */
	public int sodiumChunkBuilderThreads = 0;

	public static ArgonConfig get() {
		return current;
	}

	public static void load() {
		ArgonConfig loaded = new ArgonConfig();
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				ArgonConfig parsed = GSON.fromJson(reader, ArgonConfig.class);
				if (parsed != null) {
					loaded = parsed;
				}
			} catch (Exception e) {
				LOGGER.warn("Could not read {}, using defaults", PATH, e);
			}
		}
		loaded.sanitize();
		current = loaded;
		save();
	}

	public static void save() {
		try (Writer writer = Files.newBufferedWriter(PATH)) {
			GSON.toJson(current, writer);
		} catch (IOException e) {
			LOGGER.warn("Could not write {}", PATH, e);
		}
	}

	public void sanitize() {
		entityDistance = Math.clamp(entityDistance, 16, 512);
		smallEntityDistance = Math.clamp(smallEntityDistance, 8, entityDistance);
		blockEntityDistance = Math.clamp(blockEntityDistance, 16, 512);
		maxParticlesPerTick = Math.clamp(maxParticlesPerTick, 0, 10_000);
		sodiumChunkBuilderThreads = Math.clamp(sodiumChunkBuilderThreads, 0, 64);
	}
}
