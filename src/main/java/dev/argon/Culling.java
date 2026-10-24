package dev.argon;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicInteger;

/** Distance-culling decisions shared by the render mixins. */
public final class Culling {
	/** Extra blocks of range granted per block of entity size, so huge entities (dragons, ghasts) stay visible. */
	private static final double SIZE_MARGIN = 16.0;

	private static final AtomicInteger SMALL_ENTITIES_DRAWN = new AtomicInteger();

	private Culling() {
	}

	/** Resets per-frame budgets and feeds the adaptive scaler. */
	public static void beginFrame() {
		SMALL_ENTITIES_DRAWN.set(0);
		ArgonStats.FRAMES.increment();
		Adaptive.onFrame(Minecraft.getInstance());
	}

	private static double scaled(int distance, int floor) {
		return Math.max(Math.min(floor, distance), distance * Adaptive.scale());
	}

	public static boolean shouldCullEntity(Entity entity, double camX, double camY, double camZ) {
		ArgonConfig config = ArgonConfig.get();
		if (!config.entityCulling || isExempt(entity)) {
			return false;
		}

		AABB box = entity.getBoundingBox();
		double size = Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize()));
		boolean small = isSmall(entity);
		double base = small ? scaled(config.smallEntityDistance, 8) : scaled(config.entityDistance, 16);
		double limit = base + size * SIZE_MARGIN;

		if (entity.distanceToSqr(camX, camY, camZ) > limit * limit) {
			return true;
		}
		// Piles of items or arrows: draw the first N per frame and skip the rest.
		return small && config.maxSmallEntitiesPerFrame > 0 && SMALL_ENTITIES_DRAWN.incrementAndGet() > config.maxSmallEntitiesPerFrame;
	}

	public static boolean shouldCullBlockEntity(BlockEntity blockEntity) {
		ArgonConfig config = ArgonConfig.get();
		if (!config.blockEntityCulling) {
			return false;
		}
		// Beams and portals are meant to be seen from far away.
		if (blockEntity instanceof BeaconBlockEntity || blockEntity instanceof TheEndPortalBlockEntity) {
			return false;
		}

		Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		double limit = scaled(config.blockEntityDistance, 16);

		return blockEntity.getBlockPos().distToCenterSqr(camera) > limit * limit;
	}

	private static boolean isExempt(Entity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity cameraEntity = minecraft.getCameraEntity();

		return entity == minecraft.player
			|| entity == cameraEntity
			|| (cameraEntity != null && entity.isPassengerOfSameVehicle(cameraEntity))
			|| entity.isCurrentlyGlowing();
	}

	private static boolean isSmall(Entity entity) {
		return entity instanceof ItemEntity
			|| entity instanceof ExperienceOrb
			|| entity instanceof Projectile
			|| entity instanceof ArmorStand;
	}
}
