package dev.argon;

import java.util.concurrent.atomic.LongAdder;

/** Running totals of work Argon has skipped this session, shown by {@code /argon status}. */
public final class ArgonStats {
	public static final LongAdder ENTITIES_CULLED = new LongAdder();
	public static final LongAdder BLOCK_ENTITIES_CULLED = new LongAdder();
	public static final LongAdder PARTICLES_DROPPED = new LongAdder();

	private ArgonStats() {
	}

	public static void reset() {
		ENTITIES_CULLED.reset();
		BLOCK_ENTITIES_CULLED.reset();
		PARTICLES_DROPPED.reset();
	}
}
