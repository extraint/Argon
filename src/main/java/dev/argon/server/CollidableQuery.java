package dev.argon.server;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.function.Consumer;

/** Implemented on {@code LevelEntityGetterAdapter} by Argon's mixin. */
public interface CollidableQuery {
	void argon$getCollidable(AABB bounds, Consumer<Entity> output);
}
