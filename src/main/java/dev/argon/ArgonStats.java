package dev.argon;

import java.util.concurrent.atomic.LongAdder;

/** Running totals of work Argon has skipped this session, shown by {@code /argon status}. */
public final class ArgonStats {
	public static final LongAdder ENTITIES_CULLED = new LongAdder();
	public static final LongAdder BLOCK_ENTITIES_CULLED = new LongAdder();
	public static final LongAdder PARTICLES_DROPPED = new LongAdder();
	/** Queries answered by Argon's game-logic shortcuts. Zero while the options are on means a hook is not running. */
	public static final LongAdder FAST_ENTITY_LOOKUPS = new LongAdder();
	public static final LongAdder FAST_COLLISION_QUERIES = new LongAdder();
	/** Extracted frames; lets tests derive FPS. */
	public static final LongAdder FRAMES = new LongAdder();

	private ArgonStats() {
	}

	public static void reset() {
		ENTITIES_CULLED.reset();
		BLOCK_ENTITIES_CULLED.reset();
		PARTICLES_DROPPED.reset();
	}
}
