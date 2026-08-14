package com.robotcmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

/**
 * Registers client-side commands on the robot's client:
 * /status, /mybag, /drop <item> [count].
 * The owner triggers them remotely via "MyBot cmd /status" etc.
 */
public final class RobotCommandRegistration {

	private RobotCommandRegistration() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("status").executes(ctx -> {
				ctx.getSource().sendFeedback(Text.literal(RobotInfoService.status()));
				return 1;
			}));
			dispatcher.register(ClientCommandManager.literal("mybag").executes(ctx -> {
				ctx.getSource().sendFeedback(Text.literal(RobotInfoService.bag()));
				return 1;
			}));
			dispatcher.register(ClientCommandManager.literal("drop")
				.then(ClientCommandManager.argument("item", StringArgumentType.word())
					.executes(ctx -> {
						ctx.getSource().sendFeedback(Text.literal(
							RobotInfoService.drop(StringArgumentType.getString(ctx, "item"))));
						return 1;
					})
					.then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1))
						.executes(ctx -> {
							ctx.getSource().sendFeedback(Text.literal(
								RobotInfoService.drop(StringArgumentType.getString(ctx, "item")
									+ " " + IntegerArgumentType.getInteger(ctx, "count"))));
							return 1;
						}))));
		});
	}
}
