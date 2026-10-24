package dev.argon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks frame time and derives a 0..1 multiplier for Argon's culling distances and particle cap.
 * While frames are slower than the target the multiplier drops; with headroom it recovers.
 */
public final class Adaptive {
	private static final long ADJUST_INTERVAL_NANOS = 500_000_000L;
	/** Single long frames (chunk loading, GC) shouldn't be read as sustained load. */
	private static final double MAX_SAMPLE_MS = 100.0;

	private static final Logger LOGGER = LoggerFactory.getLogger("Argon");
	private static final long SECOND = 1_000_000_000L;
	/** Chunk reloads cause hitches, so render distance changes slowly and only after sustained slow or fast frames. */
	private static final long SHRINK_AFTER = 6 * SECOND;
	private static final long GROW_AFTER = 15 * SECOND;
	private static final long CHANGE_COOLDOWN = 12 * SECOND;

	private static volatile double scale = 1.0;
	/** The render distance the user chose, and the last value Argon set; a mismatch means the user changed it. */
	private static int baseDistance = -1;
	private static int lastAppliedDistance = -1;
	private static long slowSince;
	private static long fastSince;
	private static long lastDistanceChange;
	private static long lastFrameNanos;
	private static long lastAdjustNanos;
	private static double averageMs;

	private Adaptive() {
	}

	/** Current multiplier; always 1.0 when adaptive culling is off. */
	public static double scale() {
		return ArgonConfig.get().adaptiveCulling ? scale : 1.0;
	}

	public static void reset() {
		scale = 1.0;
		averageMs = 0;
		lastFrameNanos = 0;
	}

	/** Called once per extracted frame, on the render thread. */
	public static void onFrame(Minecraft minecraft) {
		ArgonConfig config = ArgonConfig.get();
		long now = System.nanoTime();
		long previous = lastFrameNanos;
		lastFrameNanos = now;

		if (!config.adaptiveCulling && !config.adaptiveRenderDistance) {
			scale = 1.0;
			restoreRenderDistance(minecraft);
			return;
		}
		// Vanilla throttles unfocused or minimized windows to a few FPS; that says nothing about load.
		if (previous == 0 || !minecraft.isWindowActive() || minecraft.getWindow().isIconified()) {
			averageMs = 0;
			return;
		}

		double sampleMs = Math.min((now - previous) / 1_000_000.0, MAX_SAMPLE_MS);
		averageMs = averageMs == 0 ? sampleMs : averageMs + (sampleMs - averageMs) * 0.05;

		if (now - lastAdjustNanos < ADJUST_INTERVAL_NANOS) {
			return;
		}
		lastAdjustNanos = now;

		double targetMs = 1000.0 / config.targetFps;
		if (!config.adaptiveCulling) {
			scale = 1.0;
		} else if (averageMs > targetMs * 1.05) {
			scale = Math.max(config.adaptiveMinScale, scale * 0.92);
		} else if (averageMs < targetMs * 0.85) {
			scale = Math.min(1.0, scale * 1.04);
		}

		if (config.adaptiveRenderDistance) {
			adaptRenderDistance(minecraft, config, now, targetMs);
		} else {
			restoreRenderDistance(minecraft);
		}
	}

	private static void adaptRenderDistance(Minecraft minecraft, ArgonConfig config, long now, double targetMs) {
		OptionInstance<Integer> option = minecraft.options.renderDistance();
		int current = option.get();
		if (baseDistance == -1 || current != lastAppliedDistance) {
			// First run, or the user changed it themselves: treat their value as the ceiling.
			baseDistance = current;
			lastAppliedDistance = current;
			slowSince = 0;
			fastSince = 0;
		}

		boolean cooling = now - lastDistanceChange < CHANGE_COOLDOWN;
		if (averageMs > targetMs * 1.25) {
			fastSince = 0;
			slowSince = slowSince == 0 ? now : slowSince;
			if (!cooling && now - slowSince >= SHRINK_AFTER && current > config.minRenderDistance) {
				setDistance(option, Math.max(config.minRenderDistance, current - 2), now);
			}
		} else if (averageMs < targetMs * 0.6) {
			slowSince = 0;
			fastSince = fastSince == 0 ? now : fastSince;
			if (!cooling && now - fastSince >= GROW_AFTER && current < baseDistance) {
				setDistance(option, Math.min(baseDistance, current + 1), now);
			}
		} else {
			slowSince = 0;
			fastSince = 0;
		}
	}

	private static void setDistance(OptionInstance<Integer> option, int distance, long now) {
		option.values().validateValue(distance).ifPresent(option::set);
		LOGGER.info("Adaptive render distance: {} -> {} chunks (average frame {} ms)", lastAppliedDistance, option.get(), Math.round(averageMs));
		lastAppliedDistance = option.get();
		lastDistanceChange = now;
		slowSince = 0;
		fastSince = 0;
	}

	/** Puts the user's own render distance back (feature switched off, or the game is closing). */
	public static void restoreRenderDistance(Minecraft minecraft) {
		if (baseDistance != -1) {
			OptionInstance<Integer> option = minecraft.options.renderDistance();
			if (option.get() == lastAppliedDistance && lastAppliedDistance != baseDistance) {
				option.values().validateValue(baseDistance).ifPresent(option::set);
			}
			baseDistance = -1;
			lastAppliedDistance = -1;
		}
	}
}
