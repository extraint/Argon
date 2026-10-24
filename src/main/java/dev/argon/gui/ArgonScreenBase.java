package dev.argon.gui;

import dev.argon.ArgonClient;
import dev.argon.ArgonConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Shared two-column layout and widgets for Argon's settings screens. */
abstract class ArgonScreenBase extends Screen {
	private static final int MAX_COLUMN_WIDTH = 180;
	static final int GAP = 10;
	static final int ROW_HEIGHT = 24;

	protected final Screen parent;
	protected int columnWidth = MAX_COLUMN_WIDTH;
	protected int left;
	protected int right;

	protected ArgonScreenBase(Component title, Screen parent) {
		super(title);
		this.parent = parent;
	}

	/** Call first in {@code init()}. */
	protected void layoutColumns() {
		this.columnWidth = Math.min(MAX_COLUMN_WIDTH, (this.width - 3 * GAP) / 2);
		this.left = this.width / 2 - columnWidth - GAP / 2;
		this.right = this.width / 2 + GAP / 2;
	}

	protected void addDoneButton() {
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
			.bounds(this.width / 2 - 100, this.height - 28, 200, 20)
			.build());
	}

	protected void addToggle(int x, int y, String label, boolean initial, Consumer<Boolean> setter) {
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

	protected void addSlider(int x, int y, String label, int min, int max, int initial, IntFunction<String> format, IntConsumer setter) {
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
