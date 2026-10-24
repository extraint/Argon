package dev.argon.mixin;

import dev.argon.ArgonConfig;
import dev.argon.ArgonStats;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
	@Unique
	private int argon$spawnedThisTick;

	@Inject(method = "tick", at = @At("HEAD"))
	private void argon$resetSpawnBudget(CallbackInfo ci) {
		this.argon$spawnedThisTick = 0;
	}

	// Callers already treat a null result as "particle not created".
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void argon$limitSpawnRate(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz, CallbackInfoReturnable<Particle> cir) {
		ArgonConfig config = ArgonConfig.get();
		if (config.particleLimiter && config.maxParticlesPerTick > 0 && ++this.argon$spawnedThisTick > config.maxParticlesPerTick) {
			ArgonStats.PARTICLES_DROPPED.increment();
			cir.setReturnValue(null);
		}
	}
}
