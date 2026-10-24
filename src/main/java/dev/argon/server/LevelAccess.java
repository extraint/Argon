package dev.argon.server;

import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.entity.Entity;

/** Exposes {@code Level.getEntities()}, which is protected. */
public interface LevelAccess {
	LevelEntityGetter<Entity> argon$entityGetter();
}
