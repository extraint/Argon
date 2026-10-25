package dev.argon.server;

import net.minecraft.world.entity.Entity;

/**
 * Decides which entity classes can ever be collided with, and when the shortcut in {@code getEntityCollisions} applies.
 * {@code Entity.canBeCollidedWith} returns a constant false, so an entity whose class (and superclasses below Entity)
 * never overrides it can never appear in a collision query. Anything uncertain is treated as collidable, which only
 * costs speed, never correctness.
 */
public final class Collidability {
	private static final ClassValue<Boolean> CAPABLE = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			return overridesCanBeCollidedWith(type);
		}
	};

	/** True when the class keeps {@code Entity.canCollideWith}, i.e. "other.canBeCollidedWith(this) and not riding together". */
	private static final ClassValue<Boolean> DEFAULT_RULE = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			return !overrides(type, CAN_COLLIDE_WITH);
		}
	};

	/** Method names as they exist at runtime: Mojang names on 26.x, intermediary names in a released 1.21.x game. */
	private static final String CAN_BE_COLLIDED_WITH = runtimeName("canBeCollidedWith", "method_30948");
	private static final String CAN_COLLIDE_WITH = runtimeName("canCollideWith", "method_30949");

	private Collidability() {
	}

	private static String runtimeName(String mojangName, String intermediaryName) {
		//? if <26 {
		/*return net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver()
			.mapMethodName("intermediary", "net.minecraft.class_1297", intermediaryName, "(Lnet/minecraft/class_1297;)Z");
		*///?} else
		return mojangName;
	}

	/**
	 * Checks the rules against vanilla entities whose behaviour is known. If anything is off (for example a method
	 * name could not be resolved), the collision shortcut must not be used.
	 */
	public static boolean verified() {
		return SelfCheck.OK;
	}

	private static final class SelfCheck {
		static final boolean OK = run();

		private static boolean run() {
			try {
				return CAPABLE.get(net.minecraft.world.entity.monster.Shulker.class)
					&& CAPABLE.get(net.minecraft.world.entity.vehicle.boat.AbstractBoat.class)
					&& !CAPABLE.get(net.minecraft.world.entity.animal.cow.Cow.class)
					&& !DEFAULT_RULE.get(net.minecraft.world.entity.vehicle.boat.AbstractBoat.class)
					&& DEFAULT_RULE.get(net.minecraft.world.entity.animal.cow.Cow.class);
			} catch (Throwable t) {
				return false;
			}
		}
	}

	/**
	 * Boats, minecarts and wind charges override {@code canCollideWith} to hit any pushable entity, not just collidable
	 * ones. The shortcut only holds when the querying entity (if any) uses the default rule.
	 */
	public static boolean sourceUsesDefaultRule(Object source) {
		return source == null || DEFAULT_RULE.get(source.getClass());
	}

	public static boolean mayCollide(Object entity) {
		return !(entity instanceof Entity) || CAPABLE.get(entity.getClass());
	}

	private static boolean overridesCanBeCollidedWith(Class<?> type) {
		return overrides(type, CAN_BE_COLLIDED_WITH);
	}

	/** Whether the class, or a superclass below Entity, declares {@code name(Entity)}. Unsure counts as yes. */
	private static boolean overrides(Class<?> type, String name) {
		try {
			for (Class<?> c = type; c != null && c != Entity.class; c = c.getSuperclass()) {
				try {
					c.getDeclaredMethod(name, Entity.class);
					return true;
				} catch (NoSuchMethodException ignored) {
					// keep walking up
				}
			}
			return false;
		} catch (Throwable t) {
			return true;
		}
	}
}
