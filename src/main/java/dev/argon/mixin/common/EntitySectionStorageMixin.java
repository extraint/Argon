package dev.argon.mixin.common;

import dev.argon.ArgonConfig;
import dev.argon.ArgonStats;
import dev.argon.server.CollidableSection;
import dev.argon.server.CollidableStorage;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionStorageMixin<T extends EntityAccess> implements CollidableStorage<T> {
	/** Boxes spanning more section cells than this use the original traversal. */
	private static final int MAX_DIRECT_CELLS = 128;
	/** Section coordinates must fit the packed key (22 bits for x/z, 20 for y) for key order to match. */
	private static final int XZ_LIMIT = 1 << 21;
	private static final int Y_LIMIT = 1 << 19;

	@Shadow
	@Final
	private Long2ObjectMap<EntitySection<T>> sections;

	@Shadow
	public abstract void forEachAccessibleNonEmptySection(AABB bb, AbortableIterationConsumer<EntitySection<T>> output);

	/**
	 * Vanilla walks, for every x in the box, the whole tree slab of every section at that x in the world and filters
	 * by y and z, so a query costs time proportional to the number of loaded sections. For a small box the same
	 * sections can be looked up directly. They are visited in the same order as the tree would yield them:
	 * ascending x, then z, then y, with z and y compared as unsigned packed fields (non-negative values first).
	 */
	@Inject(method = "forEachAccessibleNonEmptySection", at = @At("HEAD"), cancellable = true)
	private void argon$directLookup(AABB bb, AbortableIterationConsumer<EntitySection<T>> output, CallbackInfo ci) {
		if (!ArgonConfig.get().fastEntityLookup) {
			return;
		}

		int xMin = SectionPos.posToSectionCoord(bb.minX - 2.0);
		int yMin = SectionPos.posToSectionCoord(bb.minY - 4.0);
		int zMin = SectionPos.posToSectionCoord(bb.minZ - 2.0);
		int xMax = SectionPos.posToSectionCoord(bb.maxX + 2.0);
		int yMax = SectionPos.posToSectionCoord(bb.maxY + 0.0);
		int zMax = SectionPos.posToSectionCoord(bb.maxZ + 2.0);

		if (xMin > xMax || yMin > yMax || zMin > zMax
			|| xMin < -XZ_LIMIT || xMax >= XZ_LIMIT || zMin < -XZ_LIMIT || zMax >= XZ_LIMIT || yMin < -Y_LIMIT || yMax >= Y_LIMIT
			|| (long) (xMax - xMin + 1) * (yMax - yMin + 1) * (zMax - zMin + 1) > MAX_DIRECT_CELLS) {
			return; // let the original code handle empty, huge or out-of-range boxes
		}

		ci.cancel();
		ArgonStats.FAST_ENTITY_LOOKUPS.increment();

		for (int x = xMin; x <= xMax; x++) {
			for (int zSegment = 0; zSegment < 2; zSegment++) {
				int zFrom = segmentFrom(zMin, zMax, zSegment);
				int zTo = segmentTo(zMin, zMax, zSegment);
				for (int z = zFrom; z <= zTo; z++) {
					for (int ySegment = 0; ySegment < 2; ySegment++) {
						int yFrom = segmentFrom(yMin, yMax, ySegment);
						int yTo = segmentTo(yMin, yMax, ySegment);
						for (int y = yFrom; y <= yTo; y++) {
							EntitySection<T> section = this.sections.get(SectionPos.asLong(x, y, z));
							if (section != null && !section.isEmpty() && section.getStatus().isAccessible() && output.accept(section).shouldAbort()) {
								return;
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Packed fields sort as unsigned, so non-negative coordinates come before negative ones. A range that straddles
	 * zero is therefore walked as two segments: [0, max] then [min, -1]. Other ranges are a single segment.
	 */
	private static int segmentFrom(int min, int max, int segment) {
		if (min >= 0 || max < 0) {
			return segment == 0 ? min : 1;
		}
		return segment == 0 ? 0 : min;
	}

	private static int segmentTo(int min, int max, int segment) {
		if (min >= 0 || max < 0) {
			return segment == 0 ? max : 0;
		}
		return segment == 0 ? max : -1;
	}

	@Override
	public void argon$getInSectionsWithCollidables(AABB bounds, AbortableIterationConsumer<T> output) {
		this.forEachAccessibleNonEmptySection(bounds, section -> {
			// If the section mixin is missing (another mod interfered), assume it may hold something collidable.
			if (!(section instanceof CollidableSection counted) || counted.argon$collidableCount() > 0) {
				return section.getEntities(bounds, output);
			}
			//? if >=26.3 {
			/*return net.minecraft.util.Continuation.CONTINUE;
			*///?} else
			return AbortableIterationConsumer.Continuation.CONTINUE;
		});
	}
}
