package dev.argon.gui;

import dev.argon.ArgonConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Edits {@link ArgonConfig} in place; changes are saved when the screen closes. */
public class ArgonConfigScreen extends ArgonScreenBase {
	public ArgonConfigScreen(Screen parent) {
		super(Component.literal("Argon Settings"), parent);
	}

	@Override
	protected void init() {
		ArgonConfig config = ArgonConfig.get();
		layoutColumns();
		int y = 40;

		addToggle(left, y, "Entity Culling", config.entityCulling, v -> config.entityCulling = v);
		addToggle(right, y, "Block Entity Culling", config.blockEntityCulling, v -> config.blockEntityCulling = v);
		y += ROW_HEIGHT;

		addSlider(left, y, "Entity Distance", 16, 256, config.entityDistance, String::valueOf, v -> config.entityDistance = v);
		addSlider(right, y, "Block Entity Distance", 16, 256, config.blockEntityDistance, String::valueOf, v -> config.blockEntityDistance = v);
		y += ROW_HEIGHT;

		addSlider(left, y, "Small Entity Distance", 8, 128, config.smallEntityDistance, String::valueOf, v -> config.smallEntityDistance = v);
		addToggle(right, y, "Particle Limiter", config.particleLimiter, v -> config.particleLimiter = v);
		y += ROW_HEIGHT;

		addToggle(left, y, "Adaptive Culling", config.adaptiveCulling, v -> config.adaptiveCulling = v);
		addSlider(right, y, "Target FPS", 30, 240, config.targetFps, String::valueOf, v -> config.targetFps = v);
		y += ROW_HEIGHT;

		addSlider(left, y, "Small Entity Budget", 0, 1000, config.maxSmallEntitiesPerFrame, v -> v == 0 ? "Off" : String.valueOf(v), v -> config.maxSmallEntitiesPerFrame = v);
		addSlider(right, y, "Particles Per Tick", 10, 1000, config.maxParticlesPerTick, String::valueOf, v -> config.maxParticlesPerTick = v);
		y += ROW_HEIGHT;

		addSlider(left, y, "Sodium Chunk Threads", 0, Math.max(2, Runtime.getRuntime().availableProcessors()), config.sodiumChunkBuilderThreads,
			v -> v == 0 ? "Auto" : String.valueOf(v), v -> config.sodiumChunkBuilderThreads = v);

		y += ROW_HEIGHT;

		addRenderableWidget(Button.builder(Component.literal("Chunks & Rendering..."), button -> this.minecraft.setScreenAndShow(new ArgonRenderingScreen(this)))
			.bounds(this.width / 2 - 100, y + 4, 200, 20)
			.build());

		addDoneButton();
	}
}
