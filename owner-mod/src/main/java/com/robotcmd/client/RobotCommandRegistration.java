package com.robotcmd.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * Client-side shortcuts that translate to robot requests through the chat
 * channel: /status -> "MyBot cmd status", /mybag -> "MyBot cmd bag",
 * /drop <item> [count] -> "MyBot cmd drop <item> <count>".
 */
public final class RobotCommandRegistration {

	private RobotCommandRegistration() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommands.literal("status").executes(ctx -> {
				send("status");
				return 1;
			}));
			dispatcher.register(ClientCommands.literal("mybag").executes(ctx -> {
				send("bag");
				return 1;
			}));
			dispatcher.register(ClientCommands.literal("drop")
				.then(ClientCommands.argument("item", StringArgumentType.word())
					.executes(ctx -> {
						send("drop " + StringArgumentType.getString(ctx, "item"));
						return 1;
					})
					.then(ClientCommands.argument("count", IntegerArgumentType.integer(1))
						.executes(ctx -> {
							send("drop " + StringArgumentType.getString(ctx, "item")
								+ " " + IntegerArgumentType.getInteger(ctx, "count"));
							return 1;
						}))));
		});
	}

	private static void send(String internalCommand) {
		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client.getConnection();
		if (connection != null) {
			connection.sendChat(SuggestionClient.prefix() + internalCommand);
		}
	}
}
