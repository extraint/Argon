package dev.argon;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.server.level.ParticleStatus;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Optional;

/**
 * One-shot bundles of vanilla video settings. A preset only ever lowers a setting: if yours is already cheaper than
 * the preset's value it is left alone. Applying is explicit (button or command); Argon never does it on its own.
 */
public final class Presets {
	public enum Preset {
		BALANCED(new Target(16, 3, CloudStatus.FAST, 64, ParticleStatus.DECREASED, 5, 4, 1.0, true, true, false, false, true, 1.0)),
		PERFORMANCE(new Target(12, 1, CloudStatus.OFF, 32, ParticleStatus.DECREASED, 3, 2, 0.75, false, false, false, false, true, 0.0)),
		POTATO(new Target(6, 0, CloudStatus.OFF, 8, ParticleStatus.MINIMAL, 3, 0, 0.5, false, false, false, false, false, 0.0));

		private final Target target;

		Preset(Target target) {
			this.target = target;
		}

		public String label() {
			String name = name().toLowerCase(Locale.ROOT);
			return Character.toUpperCase(name.charAt(0)) + name.substring(1);
		}

		public Preset next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public static Optional<Preset> byName(String name) {
			for (Preset preset : values()) {
				if (preset.name().equalsIgnoreCase(name)) {
					return Optional.of(preset);
				}
			}
			return Optional.empty();
		}
	}

	/** Booleans are the highest value allowed (true leaves your choice alone, false forces it off). */
	private record Target(int renderDistance, int biomeBlend, CloudStatus clouds, int cloudRange, ParticleStatus particles,
						  int weatherRadius, int mipmaps, double entityScaling, boolean shadows, boolean cutoutLeaves,
						  boolean improvedTransparency, boolean vignette, boolean ambientOcclusion, double chunkFadeIn) {
	}

	/** Restores run newest-first, so undoing after several applies returns to the original settings. */
	private static final Deque<Runnable> UNDO = new ArrayDeque<>();

	private Presets() {
	}

	public static boolean canUndo() {
		return !UNDO.isEmpty();
	}

	/** Applies the preset and returns how many settings changed. */
	public static int apply(Minecraft minecraft, Preset preset) {
		var options = minecraft.options;
		Target t = preset.target;
		int changed = 0;

		changed += lowerInt(options.renderDistance(), t.renderDistance);
		changed += lowerInt(options.biomeBlendRadius(), t.biomeBlend);
		changed += change(options.cloudStatus(), cheaper(options.cloudStatus().get(), t.clouds));
		changed += lowerInt(options.cloudRange(), t.cloudRange);
		changed += change(options.particles(), options.particles().get().ordinal() >= t.particles.ordinal() ? options.particles().get() : t.particles);
		changed += lowerInt(options.weatherRadius(), t.weatherRadius);
		changed += lowerInt(options.mipmapLevels(), t.mipmaps);
		changed += change(options.entityDistanceScaling(), Math.min(options.entityDistanceScaling().get(), t.entityScaling));
		changed += change(options.chunkSectionFadeInTime(), Math.min(options.chunkSectionFadeInTime().get(), t.chunkFadeIn));
		changed += change(options.entityShadows(), options.entityShadows().get() && t.shadows);
		changed += change(options.cutoutLeaves(), options.cutoutLeaves().get() && t.cutoutLeaves);
		changed += change(options.improvedTransparency(), options.improvedTransparency().get() && t.improvedTransparency);
		changed += change(options.vignette(), options.vignette().get() && t.vignette);
		changed += change(options.ambientOcclusion(), options.ambientOcclusion().get() && t.ambientOcclusion);

		options.save();
		return changed;
	}

	/** Reverts everything applied since the last undo. */
	public static void undo(Minecraft minecraft) {
		while (!UNDO.isEmpty()) {
			UNDO.removeLast().run();
		}
		minecraft.options.save();
	}

	private static CloudStatus cheaper(CloudStatus current, CloudStatus target) {
		return current.ordinal() <= target.ordinal() ? current : target;
	}

	private static int lowerInt(OptionInstance<Integer> option, int ceiling) {
		return change(option, Math.min(option.get(), ceiling));
	}

	/** Sets the option if the value differs and is valid for it; records how to put it back. */
	private static <T> int change(OptionInstance<T> option, T value) {
		T old = option.get();
		if (old.equals(value) || option.values().validateValue(value).isEmpty()) {
			return 0;
		}
		option.set(value);
		UNDO.addLast(() -> option.set(old));
		return 1;
	}
}
