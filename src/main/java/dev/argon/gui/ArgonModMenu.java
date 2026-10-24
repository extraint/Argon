package dev.argon.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;

/** Argon's settings live in Sodium's Video Settings screen, so Mod Menu's config button opens that. */
public class ArgonModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return VideoSettingsScreen::createScreen;
	}
}
