package dev.argon.mixin;

import dev.argon.ArgonStats;
import dev.argon.Culling;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
	// A null state means "nothing to render", which is what the method already returns for entities without a renderer.
	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
	private <E extends BlockEntity, S extends BlockEntityRenderState> void argon$cullDistantBlockEntities(E blockEntity, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumbling, boolean flag, CallbackInfoReturnable<S> cir) {
		if (Culling.shouldCullBlockEntity(blockEntity)) {
			ArgonStats.BLOCK_ENTITIES_CULLED.increment();
			cir.setReturnValue(null);
		}
	}
}
