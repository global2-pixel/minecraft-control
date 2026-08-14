package com.robotcmd;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Internal robot commands triggered through the chat channel:
 * {@code status} (health/hunger/equipment), {@code bag} (inventory list),
 * {@code drop <item> [count]} (throw items). Replies are sent via /msg.
 */
public final class RobotInfoService {

	private static final int MAX_REPLY_LENGTH = 180;

	private RobotInfoService() {
	}

	/** Returns true if the command was internal (already handled). Results go via /msg. */
	public static boolean tryHandle(String command, String ownerName) {
		String trimmed = command.trim();
		if (trimmed.startsWith("/")) {
			trimmed = trimmed.substring(1).trim();
		}
		if (trimmed.isEmpty()) {
			return false;
		}
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.equals("status") || lower.equals("hp")) {
			RobotCmdClient.replyTo(ownerName, status());
			return true;
		}
		if (lower.equals("bag") || lower.equals("mybag") || lower.equals("inv") || lower.equals("inventory")) {
			RobotCmdClient.replyTo(ownerName, bag());
			return true;
		}
		if (lower.equals("drop") || lower.startsWith("drop ")) {
			RobotCmdClient.replyTo(ownerName, drop(trimmed.substring(4).trim()));
			return true;
		}
		return false;
	}

	public static String status() {
		ClientPlayerEntity player = currentPlayer();
		if (player == null) {
			return "[状态] 未连接服务器";
		}
		PlayerInventory inventory = player.getInventory();
		return "[状态] 生命 " + (int) player.getHealth() + '/' + (int) player.getMaxHealth()
			+ " | 饱食 " + player.getHungerManager().getFoodLevel()
			+ " 饱和 " + String.format(Locale.ROOT, "%.1f", player.getHungerManager().getSaturationLevel())
			+ " | 护甲 " + player.getArmor()
			+ " | 手持 " + itemName(player.getMainHandStack())
			+ " 副手 " + itemName(player.getOffHandStack())
			+ " | 头盔 " + itemName(inventory.armor.get(3))
			+ " 胸甲 " + itemName(inventory.armor.get(2))
			+ " 护腿 " + itemName(inventory.armor.get(1))
			+ " 靴子 " + itemName(inventory.armor.get(0));
	}

	public static String bag() {
		ClientPlayerEntity player = currentPlayer();
		if (player == null) {
			return "[背包] 未连接服务器";
		}
		PlayerInventory inventory = player.getInventory();
		Map<String, Integer> counts = new LinkedHashMap<>();
		int usedSlots = 0;
		for (ItemStack stack : inventory.main) {
			if (!stack.isEmpty()) {
				counts.merge(itemName(stack), stack.getCount(), Integer::sum);
				usedSlots++;
			}
		}
		if (counts.isEmpty()) {
			return "[背包] 空的";
		}
		StringBuilder sb = new StringBuilder("[背包] (" + usedSlots + '/' + inventory.main.size() + "格) ");
		boolean first = true;
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			if (!first) {
				sb.append(", ");
			}
			first = false;
			sb.append(entry.getKey()).append(" x").append(entry.getValue());
		}
		return truncate(sb.toString());
	}

	public static String drop(String argument) {
		ClientPlayerEntity player = currentPlayer();
		if (player == null) {
			return "[掉落] 未连接服务器";
		}
		argument = argument.trim();
		if (argument.isEmpty()) {
			return "[掉落] 用法: drop <物品> [数量]";
		}
		String[] parts = argument.split("\\s+");
		String query = parts[0].toLowerCase(Locale.ROOT);
		int wanted = 1;
		if (parts.length > 1) {
			try {
				wanted = Math.max(1, Integer.parseInt(parts[1]));
			} catch (NumberFormatException e) {
				return "[掉落] 数量无效: " + parts[1];
			}
		}

		PlayerInventory inventory = player.getInventory();
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerInteractionManager interactionManager = client.interactionManager;
		if (interactionManager == null) {
			return "[掉落] 无法执行";
		}

		int containerSlot = findContainerSlot(inventory, query);
		if (containerSlot < 0) {
			return "[掉落] 背包里找不到: " + parts[0];
		}

		// Move the item into the currently selected hotbar slot (SWAP keeps the displaced stack in the backpack)
		int selectedContainerSlot = 36 + inventory.selectedSlot;
		if (containerSlot != selectedContainerSlot) {
			interactionManager.clickSlot(player.playerScreenHandler.syncId, containerSlot,
				inventory.selectedSlot, SlotActionType.SWAP, player);
		}

		ItemStack inHand = inventory.getStack(inventory.selectedSlot);
		if (inHand.isEmpty()) {
			return "[掉落] 未能拿起物品: " + parts[0];
		}
		String name = itemName(inHand);
		int available = inHand.getCount();
		int toDrop = Math.min(wanted, available);
		if (toDrop >= available) {
			sendAction(player, PlayerActionC2SPacket.Action.DROP_ALL_ITEMS);
		} else {
			for (int i = 0; i < toDrop; i++) {
				sendAction(player, PlayerActionC2SPacket.Action.DROP_ITEM);
			}
		}
		String suffix = toDrop < wanted ? "（数量不足，实际只有 " + available + "）" : "";
		return "[掉落] 已扔出 " + toDrop + "x " + name + suffix;
	}

	/** Returns the container slot id of the first matching stack (hotbar 36-44, main 9-35, offhand 45), or -1. */
	private static int findContainerSlot(PlayerInventory inventory, String query) {
		for (int i = 0; i < 36; i++) {
			ItemStack stack = inventory.main.get(i);
			if (!stack.isEmpty() && matches(stack, query)) {
				return i <= 8 ? 36 + i : i;
			}
		}
		ItemStack offHand = inventory.offHand.get(0);
		if (!offHand.isEmpty() && matches(offHand, query)) {
			return 45;
		}
		return -1;
	}

	private static void sendAction(ClientPlayerEntity player, PlayerActionC2SPacket.Action action) {
		player.networkHandler.sendPacket(new PlayerActionC2SPacket(action, BlockPos.ORIGIN, Direction.DOWN));
	}

	private static boolean matches(ItemStack stack, String query) {
		Identifier id = Registries.ITEM.getId(stack.getItem());
		String idPath = id.getPath().toLowerCase(Locale.ROOT);
		String name = stack.getName().getString().toLowerCase(Locale.ROOT);
		return idPath.equals(query) || idPath.contains(query)
			|| name.equals(query) || name.contains(query);
	}

	private static String itemName(ItemStack stack) {
		return stack == null || stack.isEmpty() ? "空" : stack.getName().getString();
	}

	private static ClientPlayerEntity currentPlayer() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player;
	}

	private static String truncate(String text) {
		return text.length() <= MAX_REPLY_LENGTH ? text : text.substring(0, MAX_REPLY_LENGTH - 3) + "...";
	}
}
