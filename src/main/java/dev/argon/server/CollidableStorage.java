package dev.argon.server;

import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.phys.AABB;

/** Implemented on {@code EntitySectionStorage} by Argon's mixin. */
public interface CollidableStorage<T> {
	/**
	 * Same traversal as {@code getEntities(AABB, consumer)}, but sections with no collidable entities are skipped.
	 * Entities and order within the remaining sections are unchanged.
	 */
	void argon$getInSectionsWithCollidables(AABB bounds, AbortableIterationConsumer<T> output);
}
