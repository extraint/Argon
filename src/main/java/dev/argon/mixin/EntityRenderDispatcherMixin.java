package dev.argon.mixin;

import dev.argon.ArgonStats;
import dev.argon.Culling;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	// Runs once per extracted frame, before any shouldRender call for that frame.
	@Inject(method = "prepare", at = @At("HEAD"))
	private void argon$beginFrame(Camera camera, Entity cameraEntity, CallbackInfo ci) {
		Culling.beginFrame();
	}

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	//? if >=26.3 {
	/*private void argon$cullDistantEntities(Entity entity, Frustum frustum, double camX, double camY, double camZ, float partialTick, CallbackInfoReturnable<Boolean> cir) {
	*///?} else
	private void argon$cullDistantEntities(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
		if (Culling.shouldCullEntity(entity, camX, camY, camZ)) {
			ArgonStats.ENTITIES_CULLED.increment();
			cir.setReturnValue(false);
		}
	}
}
