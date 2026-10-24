package dev.argon.server;

/** Implemented on {@code EntitySection} by Argon's mixin. */
public interface CollidableSection {
	/** How many entities currently in the section could ever be collided with (see {@link Collidability}). */
	int argon$collidableCount();
}
