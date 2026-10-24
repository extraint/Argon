package dev.argon.mixin.common;

import com.google.common.collect.ImmutableList;
import dev.argon.ArgonConfig;
import dev.argon.ArgonStats;
import dev.argon.server.CollidableQuery;
import dev.argon.server.Collidability;
import dev.argon.server.LevelAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(EntityGetter.class)
public interface EntityGetterMixin {
	/**
	 * Same result as the original, without scanning sections that hold nothing collidable. An entity whose class
	 * never overrides {@code canBeCollidedWith} is rejected by the original predicate anyway, so skipping it cannot
	 * change the output. That reasoning needs the querying entity to use the default collision rule, so boats,
	 * minecarts and wind charges take the original path. The dragon-part loop and the result construction mirror the original.
	 */
	@Inject(method = "getEntityCollisions", at = @At("HEAD"), cancellable = true)
	private void argon$fastEntityCollisions(@Nullable Entity source, AABB testArea, CallbackInfoReturnable<List<VoxelShape>> cir) {
		if (!ArgonConfig.get().fastEntityCollisions || !Collidability.sourceUsesDefaultRule(source) || !((Object) this instanceof Level level)) {
			return;
		}
		if (!(level instanceof LevelAccess access)) {
			return;
		}
		LevelEntityGetter<Entity> getter = access.argon$entityGetter();
		if (!(getter instanceof LevelEntityGetterAdapter<?>) || !(getter instanceof CollidableQuery query)) {
			return;
		}

		ArgonStats.FAST_COLLISION_QUERIES.increment();
		if (testArea.getSize() < 1.0E-7) {
			cir.setReturnValue(List.of());
			return;
		}

		Predicate<Entity> canCollide = source == null
			? EntitySelector.CAN_BE_COLLIDED_WITH
			: entity -> !entity.isSpectator() && source.canCollideWith(entity);
		AABB area = testArea.inflate(1.0E-7);

		List<Entity> colliding = new ArrayList<>();
		query.argon$getCollidable(area, entity -> {
			if (entity != source && canCollide.test(entity)) {
				colliding.add(entity);
			}
		});
		for (EnderDragonPart part : level.dragonParts()) {
			if (part != source && part.parentMob != source && canCollide.test(part) && area.intersects(part.getBoundingBox())) {
				colliding.add(part);
			}
		}

		if (colliding.isEmpty()) {
			cir.setReturnValue(List.of());
			return;
		}

		ImmutableList.Builder<VoxelShape> shapes = ImmutableList.builderWithExpectedSize(colliding.size());
		for (Entity entity : colliding) {
			shapes.add(Shapes.create(entity.getBoundingBox()));
		}
		cir.setReturnValue(shapes.build());
	}
}
