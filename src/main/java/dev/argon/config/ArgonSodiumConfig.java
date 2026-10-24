package dev.argon.config;

import dev.argon.ArgonClient;
import dev.argon.ArgonConfig;
import dev.argon.Presets;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.EnumOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Adds Argon's pages to Sodium's Video Settings screen, using Sodium's own widgets, impact badges and Apply flow. */
public class ArgonSodiumConfig implements ConfigEntryPoint {
	private static final ArgonConfig DEFAULTS = new ArgonConfig();

	private static final Identifier ENTITY_CULLING = id("entity_culling");
	private static final Identifier BLOCK_ENTITY_CULLING = id("block_entity_culling");
	private static final Identifier PARTICLE_LIMITER = id("particle_limiter");
	private static final Identifier ADAPTIVE_CULLING = id("adaptive_culling");
	private static final Identifier ADAPTIVE_RENDER_DISTANCE = id("adaptive_render_distance");

	private enum Sorting { DEFAULT, FAST }

	private enum PresetChoice { NONE, BALANCED, PERFORMANCE, POTATO }

	@Override
	public void registerConfigLate(ConfigBuilder builder) {
		ModOptionsBuilder mod = builder.registerOwnModOptions()
			.setColorTheme(builder.createColorTheme().setBaseThemeRGB(0x9B5DE5));

		mod.addPage(culling(builder));
		mod.addPage(adaptive(builder));
		mod.addPage(chunksAndPresets(builder));
		mod.addPage(gameLogic(builder));
	}

	private static OptionPageBuilder culling(ConfigBuilder b) {
		OptionGroupBuilder entities = b.createOptionGroup().setName(text("Entities"))
			.addOption(bool(b, ENTITY_CULLING, "Entity Culling",
				"Stop drawing entities past a distance. Vanilla already culls most mobs at a similar range, so this mostly affects very large entities.",
				OptionImpact.LOW, c -> c.entityCulling, (c, v) -> c.entityCulling = v))
			.addOption(integer(b, id("entity_distance"), "Entity Distance", "Maximum distance for normal entities (large ones get extra range).",
				OptionImpact.LOW, 16, 512, 8, blocks(), c -> c.entityDistance, (c, v) -> c.entityDistance = v).setEnabledProvider(onlyWhen(ENTITY_CULLING), ENTITY_CULLING))
			.addOption(integer(b, id("small_entity_distance"), "Small Entity Distance", "Maximum distance for items, XP orbs, arrows and armor stands.",
				OptionImpact.MEDIUM, 8, 128, 4, blocks(), c -> c.smallEntityDistance, (c, v) -> c.smallEntityDistance = v).setEnabledProvider(onlyWhen(ENTITY_CULLING), ENTITY_CULLING))
			.addOption(integer(b, id("small_entity_budget"), "Small Entity Budget", "Most items, XP orbs, arrows and armor stands drawn per frame. Protects against item piles. 0 disables the cap.",
				OptionImpact.HIGH, 0, 1000, 25, v -> v == 0 ? text("Off") : text(String.valueOf(v)), c -> c.maxSmallEntitiesPerFrame, (c, v) -> c.maxSmallEntitiesPerFrame = v));

		OptionGroupBuilder blockEntities = b.createOptionGroup().setName(text("Block Entities"))
			.addOption(bool(b, BLOCK_ENTITY_CULLING, "Block Entity Culling",
				"Stop drawing chests, signs, banners and similar past a distance. Beacons and end portals are exempt.",
				OptionImpact.MEDIUM, c -> c.blockEntityCulling, (c, v) -> c.blockEntityCulling = v))
			.addOption(integer(b, id("block_entity_distance"), "Block Entity Distance", "Vanilla draws them out to 64 blocks.",
				OptionImpact.MEDIUM, 16, 512, 8, blocks(), c -> c.blockEntityDistance, (c, v) -> c.blockEntityDistance = v).setEnabledProvider(onlyWhen(BLOCK_ENTITY_CULLING), BLOCK_ENTITY_CULLING));

		OptionGroupBuilder particles = b.createOptionGroup().setName(text("Particles"))
			.addOption(bool(b, PARTICLE_LIMITER, "Particle Limiter", "Cap how many particles can spawn per tick so explosions and farms can't stall a frame.",
				OptionImpact.MEDIUM, c -> c.particleLimiter, (c, v) -> c.particleLimiter = v))
			.addOption(integer(b, id("particles_per_tick"), "Particles Per Tick", "Spawn cap per client tick.",
				OptionImpact.MEDIUM, 10, 1000, 10, v -> text(String.valueOf(v)), c -> c.maxParticlesPerTick, (c, v) -> c.maxParticlesPerTick = v).setEnabledProvider(onlyWhen(PARTICLE_LIMITER), PARTICLE_LIMITER));

		return b.createOptionPage().setName(text("Culling")).addOptionGroup(entities).addOptionGroup(blockEntities).addOptionGroup(particles);
	}

	private static OptionPageBuilder adaptive(ConfigBuilder b) {
		OptionGroupBuilder target = b.createOptionGroup().setName(text("Target"))
			.addOption(integer(b, id("target_fps"), "Target FPS", "Adaptive features react when frames are slower than this. Don't set it above what your display or FPS limit allows.",
				OptionImpact.VARIES, 20, 360, 5, v -> text(v + " FPS"), c -> c.targetFps, (c, v) -> c.targetFps = v));

		OptionGroupBuilder culling = b.createOptionGroup().setName(text("Adaptive Culling"))
			.addOption(bool(b, ADAPTIVE_CULLING, "Adaptive Culling", "Shrink culling distances and the particle cap while FPS is below target, and restore them when there is headroom.",
				OptionImpact.VARIES, c -> c.adaptiveCulling, (c, v) -> c.adaptiveCulling = v))
			.addOption(integer(b, id("adaptive_min_scale"), "Minimum Scale", "How far distances may shrink, as a share of your configured values.",
				OptionImpact.VARIES, 20, 100, 5, v -> text(v + "%"), c -> (int) Math.round(c.adaptiveMinScale * 100), (c, v) -> c.adaptiveMinScale = v / 100.0)
				.setEnabledProvider(onlyWhen(ADAPTIVE_CULLING), ADAPTIVE_CULLING));

		OptionGroupBuilder renderDistance = b.createOptionGroup().setName(text("Adaptive Render Distance"))
			.addOption(bool(b, ADAPTIVE_RENDER_DISTANCE, "Adaptive Render Distance",
				"Lower render distance while FPS stays well below target, then raise it again (never above your own setting). Each change reloads chunks, so it moves slowly.",
				OptionImpact.HIGH, c -> c.adaptiveRenderDistance, (c, v) -> c.adaptiveRenderDistance = v))
			.addOption(integer(b, id("min_render_distance"), "Minimum Render Distance", "The adaptive system never goes below this.",
				OptionImpact.HIGH, 2, 32, 1, v -> text(v + " chunks"), c -> c.minRenderDistance, (c, v) -> c.minRenderDistance = v)
				.setEnabledProvider(onlyWhen(ADAPTIVE_RENDER_DISTANCE), ADAPTIVE_RENDER_DISTANCE));

		return b.createOptionPage().setName(text("Adaptive")).addOptionGroup(target).addOptionGroup(culling).addOptionGroup(renderDistance);
	}

	private static OptionPageBuilder chunksAndPresets(ConfigBuilder b) {
		EnumOptionBuilder<Sorting> sorting = b.createEnumOption(id("translucency_sorting"), Sorting.class)
			.setName(text("Translucency Sorting"))
			.setTooltip(text("Fast turns off Sodium's translucent quad splitting. Cheaper for water and glass heavy views, but may mis-sort overlapping translucent faces."))
			.setImpact(OptionImpact.MEDIUM)
			.setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
			.setDefaultValue(Sorting.DEFAULT)
			.setElementNameProvider(e -> text(e == Sorting.FAST ? "Fast" : "Default"))
			.setStorageHandler(ArgonSodiumConfig::afterSave)
			.setBinding(v -> ArgonConfig.get().sodiumTranslucencySorting = v.name(),
				() -> "FAST".equals(ArgonConfig.get().sodiumTranslucencySorting) ? Sorting.FAST : Sorting.DEFAULT);

		int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
		IntegerOptionBuilder threads = integer(b, id("chunk_threads"), "Chunk Builder Threads",
			"Override how many threads Sodium uses to build chunks. Auto leaves Sodium's own choice.",
			OptionImpact.HIGH, 0, cores, 1, v -> v == 0 ? text("Auto") : text(String.valueOf(v)), c -> c.sodiumChunkBuilderThreads, (c, v) -> c.sodiumChunkBuilderThreads = v);
		threads.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD);

		OptionGroupBuilder chunks = b.createOptionGroup().setName(text("Chunks")).addOption(sorting).addOption(threads);

		EnumOptionBuilder<PresetChoice> preset = b.createEnumOption(id("apply_preset"), PresetChoice.class)
			.setName(text("Apply Preset"))
			.setTooltip(text("Lowers vanilla video settings (render distance, clouds, particles, shadows and more). Never raises a setting. Pick one and press Apply; "
				+ "avoid changing other video options in the same Apply."))
			.setImpact(OptionImpact.VARIES)
			.setDefaultValue(PresetChoice.NONE)
			.setElementNameProvider(e -> text(e == PresetChoice.NONE ? "None" : label(e)))
			.setStorageHandler(ArgonSodiumConfig::afterSave)
			.setBinding(v -> {
				if (v != PresetChoice.NONE) {
					Presets.apply(Minecraft.getInstance(), Presets.Preset.valueOf(v.name()));
				}
			}, () -> PresetChoice.NONE);

		BooleanOptionBuilder undo = b.createBooleanOption(id("undo_preset"))
			.setName(text("Undo Presets"))
			.setTooltip(text("Revert every setting changed by presets applied this session."))
			.setImpact(OptionImpact.VARIES)
			.setDefaultValue(false)
			.setStorageHandler(ArgonSodiumConfig::afterSave)
			.setBinding(v -> {
				if (v) {
					Presets.undo(Minecraft.getInstance());
				}
			}, () -> false);

		OptionGroupBuilder presets = b.createOptionGroup().setName(text("Presets")).addOption(preset).addOption(undo);

		return b.createOptionPage().setName(text("Chunks & Presets")).addOptionGroup(chunks).addOptionGroup(presets);
	}

	private static OptionPageBuilder gameLogic(ConfigBuilder b) {
		OptionGroupBuilder entities = b.createOptionGroup().setName(text("Entities"))
			.addOption(bool(b, id("fast_entity_lookup"), "Fast Entity Lookup",
				"Finds nearby entities with direct section lookups instead of scanning every loaded section in the world column. Same results in the same order as vanilla.",
				OptionImpact.MEDIUM, c -> c.fastEntityLookup, (c, v) -> c.fastEntityLookup = v))
			.addOption(bool(b, id("fast_entity_collisions"), "Fast Entity Collisions",
				"Skips entity-collision scans of areas that contain nothing collidable (only boats, shulkers and a few others can be). Same results as vanilla. Mostly helps worlds with many items or mobs.",
				OptionImpact.MEDIUM, c -> c.fastEntityCollisions, (c, v) -> c.fastEntityCollisions = v));

		return b.createOptionPage().setName(text("Game Logic")).addOptionGroup(entities);
	}

	private static void afterSave() {
		ArgonConfig.get().sanitize();
		ArgonConfig.save();
		ArgonClient.applyTuning();
	}

	private static BooleanOptionBuilder bool(ConfigBuilder b, Identifier id, String name, String tooltip, OptionImpact impact,
											 java.util.function.Function<ArgonConfig, Boolean> getter, java.util.function.BiConsumer<ArgonConfig, Boolean> setter) {
		return b.createBooleanOption(id)
			.setName(text(name))
			.setTooltip(text(tooltip))
			.setImpact(impact)
			.setDefaultValue(getter.apply(DEFAULTS))
			.setStorageHandler(ArgonSodiumConfig::afterSave)
			.setBinding(v -> setter.accept(ArgonConfig.get(), v), () -> getter.apply(ArgonConfig.get()));
	}

	private static IntegerOptionBuilder integer(ConfigBuilder b, Identifier id, String name, String tooltip, OptionImpact impact, int min, int max, int step,
												net.caffeinemc.mods.sodium.api.config.option.ControlValueFormatter formatter,
												java.util.function.Function<ArgonConfig, Integer> getter, java.util.function.BiConsumer<ArgonConfig, Integer> setter) {
		return b.createIntegerOption(id)
			.setName(text(name))
			.setTooltip(text(tooltip))
			.setImpact(impact)
			.setRange(min, max, step)
			.setValueFormatter(formatter)
			.setDefaultValue(getter.apply(DEFAULTS))
			.setStorageHandler(ArgonSodiumConfig::afterSave)
			.setBinding(v -> setter.accept(ArgonConfig.get(), v), () -> getter.apply(ArgonConfig.get()));
	}

	private static java.util.function.Function<ConfigState, Boolean> onlyWhen(Identifier toggle) {
		return state -> state.readBooleanOption(toggle);
	}

	private static net.caffeinemc.mods.sodium.api.config.option.ControlValueFormatter blocks() {
		return v -> text(v + " blocks");
	}

	private static String label(Enum<?> e) {
		String name = e.name().toLowerCase(java.util.Locale.ROOT);
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private static Component text(String s) {
		return Component.literal(s);
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("argon", path);
	}
}
