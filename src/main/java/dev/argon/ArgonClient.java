package dev.argon;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.argon.server.ModCompat;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
//?} else
/*import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;*/
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArgonClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("Argon");

	@Override
	public void onInitializeClient() {
		ArgonConfig.load();
		applyTuning();
		// Sodium may not have loaded its options yet when this entrypoint runs (it depends on mod load order).
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			applyTuning();
			LOGGER.info("Argon: {}", ModCompat.describe());
		});

		// Don't leave a temporarily lowered render distance in options.txt.
		ClientLifecycleEvents.CLIENT_STOPPING.register(Adaptive::restoreRenderDistance);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> registerCommands(dispatcher));
	}

	/** Pushes settings that live outside Argon (currently Sodium's thread count) after a load or edit. */
	public static void applyTuning() {
		try {
			SodiumTuning.apply(ArgonConfig.get());
		} catch (IllegalStateException e) {
			LOGGER.debug("Sodium's options are not available yet; Argon will apply its overrides once the client has started");
		} catch (Throwable t) {
			LOGGER.warn("Could not apply Sodium tuning; continuing without it", t);
		}
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("argon")
			.then(literal("status").executes(context -> {
				context.getSource().sendFeedback(Component.literal(
					"Argon skipped this session: " + ArgonStats.ENTITIES_CULLED.sum() + " entity renders, "
						+ ArgonStats.BLOCK_ENTITIES_CULLED.sum() + " block entity renders, "
						+ ArgonStats.PARTICLES_DROPPED.sum() + " particles. Adaptive culling scale: "
						+ Math.round(Adaptive.scale() * 100) + "%"));
				context.getSource().sendFeedback(Component.literal(ModCompat.describe()));
				return 1;
			}))
			.then(literal("reload").executes(context -> {
				Adaptive.restoreRenderDistance(context.getSource().getClient());
				ArgonConfig.load();
				Adaptive.reset();
				applyTuning();
				context.getSource().sendFeedback(Component.literal("Argon config reloaded"));
				return 1;
			}))
			.then(presetCommand())
			.then(literal("reset").executes(context -> {
				ArgonStats.reset();
				context.getSource().sendFeedback(Component.literal("Argon counters reset"));
				return 1;
			})));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> presetCommand() {
		LiteralArgumentBuilder<FabricClientCommandSource> preset = literal("preset");
		for (Presets.Preset option : Presets.Preset.values()) {
			preset.then(literal(option.name().toLowerCase(java.util.Locale.ROOT)).executes(context -> {
				int changed = Presets.apply(context.getSource().getClient(), option);
				context.getSource().sendFeedback(Component.literal(option.label() + " preset applied (" + changed + " settings lowered). Use /argon preset undo to revert."));
				return 1;
			}));
		}
		return preset.then(literal("undo").executes(context -> {
			Presets.undo(context.getSource().getClient());
			context.getSource().sendFeedback(Component.literal("Preset changes reverted"));
			return 1;
		}));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
		//? if >=26.1 {
		return ClientCommands.literal(name);
		//?} else
		/*return ClientCommandManager.literal(name);*/
	}
}
