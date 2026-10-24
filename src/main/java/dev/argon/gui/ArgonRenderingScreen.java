package dev.argon.gui;

import dev.argon.ArgonConfig;
import dev.argon.Presets;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Chunk, render distance and video-setting options. */
class ArgonRenderingScreen extends ArgonScreenBase {
	private Presets.Preset preset = Presets.Preset.PERFORMANCE;
	private Button cyclePreset;
	private Button applyPreset;
	private Button undoPreset;

	ArgonRenderingScreen(Screen parent) {
		super(Component.literal("Chunks & Rendering"), parent);
	}

	@Override
	protected void init() {
		layoutColumns();
		ArgonConfig config = ArgonConfig.get();
		int y = 40;

		addToggle(left, y, "Adaptive Render Distance", config.adaptiveRenderDistance, v -> config.adaptiveRenderDistance = v);
		addSlider(right, y, "Min Render Distance", 2, 32, config.minRenderDistance, String::valueOf, v -> config.minRenderDistance = v);
		y += ROW_HEIGHT;

		boolean fast = "FAST".equals(config.sodiumTranslucencySorting);
		addRenderableWidget(Button.builder(sortingText(fast), button -> {
			boolean nowFast = !"FAST".equals(config.sodiumTranslucencySorting);
			config.sodiumTranslucencySorting = nowFast ? "FAST" : "DEFAULT";
			button.setMessage(sortingText(nowFast));
		}).bounds(left, y, columnWidth, 20).build());
		y += ROW_HEIGHT + 12;

		cyclePreset = addRenderableWidget(Button.builder(presetText(), button -> {
			preset = preset.next();
			button.setMessage(presetText());
		}).bounds(left, y, columnWidth, 20).build());
		applyPreset = addRenderableWidget(Button.builder(Component.literal("Apply Preset"), button -> {
			int changed = Presets.apply(this.minecraft, preset);
			button.setMessage(Component.literal("Applied (" + changed + " lowered)"));
			undoPreset.active = Presets.canUndo();
		}).bounds(right, y, columnWidth, 20).build());
		y += ROW_HEIGHT;

		undoPreset = addRenderableWidget(Button.builder(Component.literal("Undo Preset"), button -> {
			Presets.undo(this.minecraft);
			button.active = false;
			applyPreset.setMessage(Component.literal("Apply Preset"));
		}).bounds(right, y, columnWidth, 20).build());
		undoPreset.active = Presets.canUndo();

		addDoneButton();
	}

	private static Component sortingText(boolean fast) {
		return Component.literal("Translucency Sorting: " + (fast ? "Fast" : "Default"));
	}

	private Component presetText() {
		return Component.literal("Preset: " + preset.label());
	}
}
