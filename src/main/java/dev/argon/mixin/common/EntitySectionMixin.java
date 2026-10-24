package dev.argon.mixin.common;

import dev.argon.server.Collidability;
import dev.argon.server.CollidableSection;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySection.class)
public abstract class EntitySectionMixin<T extends EntityAccess> implements CollidableSection {
	@Unique
	private int argon$collidable;

	@Inject(method = "add", at = @At("TAIL"))
	private void argon$countAdded(T entity, CallbackInfo ci) {
		if (Collidability.mayCollide(entity)) {
			this.argon$collidable++;
		}
	}

	@Inject(method = "remove", at = @At("RETURN"))
	private void argon$countRemoved(T entity, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && Collidability.mayCollide(entity)) {
			this.argon$collidable--;
		}
	}

	@Override
	public int argon$collidableCount() {
		return this.argon$collidable;
	}
}
