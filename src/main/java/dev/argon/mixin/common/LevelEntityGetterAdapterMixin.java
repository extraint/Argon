package dev.argon.mixin.common;

import dev.argon.server.CollidableQuery;
import dev.argon.server.CollidableStorage;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Consumer;

@Mixin(LevelEntityGetterAdapter.class)
public abstract class LevelEntityGetterAdapterMixin<T extends EntityAccess> implements CollidableQuery {
	@Shadow
	@Final
	private EntitySectionStorage<T> sectionStorage;

	@Override
	@SuppressWarnings("unchecked")
	public void argon$getCollidable(AABB bounds, Consumer<Entity> output) {
		((CollidableStorage<T>) this.sectionStorage).argon$getInSectionsWithCollidables(bounds,
			AbortableIterationConsumer.forConsumer(entity -> output.accept((Entity) entity)));
	}
}
