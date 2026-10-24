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
			return !overrides(type, "canCollideWith");
		}
	};

	private Collidability() {
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
		return overrides(type, "canBeCollidedWith");
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
