package dev.argon;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArgonClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("Argon");

	@Override
	public void onInitializeClient() {
		ArgonConfig.load();
		try {
			SodiumTuning.apply(ArgonConfig.get());
		} catch (Throwable t) {
			LOGGER.warn("Could not apply Sodium tuning; continuing without it", t);
		}

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> registerCommands(dispatcher));
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("argon")
			.then(ClientCommands.literal("status").executes(context -> {
				context.getSource().sendFeedback(Component.literal(
					"Argon skipped this session: " + ArgonStats.ENTITIES_CULLED.sum() + " entity renders, "
						+ ArgonStats.BLOCK_ENTITIES_CULLED.sum() + " block entity renders, "
						+ ArgonStats.PARTICLES_DROPPED.sum() + " particles"));
				return 1;
			}))
			.then(ClientCommands.literal("reload").executes(context -> {
				ArgonConfig.load();
				context.getSource().sendFeedback(Component.literal("Argon config reloaded"));
				return 1;
			}))
			.then(ClientCommands.literal("reset").executes(context -> {
				ArgonStats.reset();
				context.getSource().sendFeedback(Component.literal("Argon counters reset"));
				return 1;
			})));
	}
}
