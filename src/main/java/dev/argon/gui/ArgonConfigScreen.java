package dev.argon.gui;

import dev.argon.ArgonClient;
import dev.argon.ArgonConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Edits {@link ArgonConfig} in place; changes are saved when the screen closes. */
public class ArgonConfigScreen extends Screen {
	private static final int MAX_COLUMN_WIDTH = 180;
	private static final int GAP = 10;
	private static final int ROW_HEIGHT = 24;

	private final Screen parent;
	private int columnWidth = MAX_COLUMN_WIDTH;

	public ArgonConfigScreen(Screen parent) {
		super(Component.literal("Argon Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ArgonConfig config = ArgonConfig.get();
		this.columnWidth = Math.min(MAX_COLUMN_WIDTH, (this.width - 2 * GAP - GAP) / 2);
		int left = this.width / 2 - columnWidth - GAP / 2;
		int right = this.width / 2 + GAP / 2;
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

		addSlider(left, y, "Sodium Chunk Threads", 0, Math.max(2, Runtime.getRuntime().availableProcessors()), config.sodiumChunkBuilderThreads,
			v -> v == 0 ? "Auto" : String.valueOf(v), v -> config.sodiumChunkBuilderThreads = v);
		addSlider(right, y, "Particles Per Tick", 10, 1000, config.maxParticlesPerTick, String::valueOf, v -> config.maxParticlesPerTick = v);

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
			.bounds(this.width / 2 - 100, this.height - 28, 200, 20)
			.build());
	}

	private void addToggle(int x, int y, String label, boolean initial, java.util.function.Consumer<Boolean> setter) {
		boolean[] state = {initial};
		addRenderableWidget(Button.builder(toggleText(label, initial), button -> {
			state[0] = !state[0];
			setter.accept(state[0]);
			button.setMessage(toggleText(label, state[0]));
		}).bounds(x, y, columnWidth, 20).build());
	}

	private static Component toggleText(String label, boolean on) {
		return Component.literal(label + ": ").append(on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
	}

	private void addSlider(int x, int y, String label, int min, int max, int initial, IntFunction<String> format, IntConsumer setter) {
		addRenderableWidget(new IntSlider(x, y, columnWidth, label, min, max, initial, format, setter));
	}

	@Override
	public void onClose() {
		ArgonConfig.get().sanitize();
		ArgonConfig.save();
		ArgonClient.applyTuning();
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
	}

	private static final class IntSlider extends AbstractSliderButton {
		private final String label;
		private final int min;
		private final int max;
		private final IntFunction<String> format;
		private final IntConsumer setter;

		IntSlider(int x, int y, int width, String label, int min, int max, int initial, IntFunction<String> format, IntConsumer setter) {
			super(x, y, width, 20, Component.empty(), (double) (Math.clamp(initial, min, max) - min) / (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.format = format;
			this.setter = setter;
			updateMessage();
		}

		private int intValue() {
			return min + (int) Math.round(this.value * (max - min));
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(label + ": " + format.apply(intValue())));
		}

		@Override
		protected void applyValue() {
			setter.accept(intValue());
		}
	}
}
