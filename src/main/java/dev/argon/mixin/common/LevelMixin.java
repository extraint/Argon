package dev.argon.mixin.common;

import dev.argon.server.LevelAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Level.class)
public interface LevelMixin extends LevelAccess {
	@Invoker("getEntities")
	@Override
	LevelEntityGetter<Entity> argon$entityGetter();
}
