package dev.argon;

import net.minecraft.client.Minecraft;

/**
 * Tracks frame time and derives a 0..1 multiplier for Argon's culling distances and particle cap.
 * While frames are slower than the target the multiplier drops; with headroom it recovers.
 */
public final class Adaptive {
	private static final long ADJUST_INTERVAL_NANOS = 500_000_000L;
	/** Single long frames (chunk loading, GC) shouldn't be read as sustained load. */
	private static final double MAX_SAMPLE_MS = 100.0;

	private static volatile double scale = 1.0;
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

		if (!config.adaptiveCulling) {
			scale = 1.0;
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
		if (averageMs > targetMs * 1.05) {
			scale = Math.max(config.adaptiveMinScale, scale * 0.92);
		} else if (averageMs < targetMs * 0.85) {
			scale = Math.min(1.0, scale * 1.04);
		}
	}
}
