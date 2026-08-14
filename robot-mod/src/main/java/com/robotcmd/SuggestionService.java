package com.robotcmd;

import com.google.gson.Gson;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Computes command suggestions on the robot's client and replies to the owner
 * via a private message. Server suggestions come from the robot's server
 * (RequestCommandCompletionsC2SPacket -> CommandSuggestionsS2CPacket mixin);
 * Baritone "#" suggestions come from the Baritone API via reflection.
 */
public final class SuggestionService {

	public static final String REPLY_TOKEN = "[RC-SUGG]";

	/** /msg command strings are limited to 256 chars by the protocol; stay well under. */
	private static final int MAX_MSG_COMMAND_LENGTH = 240;

	private static final long REQUEST_TIMEOUT_MS = 2500;
	private static final int MAX_SUGGESTIONS = 80;

	private static final AtomicInteger NEXT_COMPLETION_ID = new AtomicInteger(1);
	private static final Map<Integer, CompletableFuture<List<String>>> PENDING = new LinkedHashMap<>();
	private static final Gson GSON = new Gson();

	private SuggestionService() {
	}

	public static void requestSuggestions(String partial, String ownerName) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
		if (networkHandler == null || client.player == null) {
			return;
		}
		String trimmed = partial.trim();
		if (trimmed.isEmpty()) {
			return;
		}
		RobotCmdClient.LOGGER.info("[robotcmd] Suggestion request from '{}': {}", ownerName, trimmed);

		Thread worker = new Thread(() -> {
			try {
				List<String> server = requestServerSuggestions(client, networkHandler, trimmed);
				List<String> baritone = getBaritoneSuggestions(trimmed);
				sendReply(client, networkHandler, ownerName, merge(server, baritone));
			} catch (Exception e) {
				RobotCmdClient.LOGGER.error("[robotcmd] Suggestion request failed", e);
			}
		}, "robotcmd-suggest");
		worker.setDaemon(true);
		worker.start();
	}

	private static List<String> requestServerSuggestions(MinecraftClient client, ClientPlayNetworkHandler networkHandler, String partial) {
		int completionId = NEXT_COMPLETION_ID.getAndIncrement();
		CompletableFuture<List<String>> future = new CompletableFuture<>();
		PENDING.put(completionId, future);

		// ClientConnection.send is not thread-safe, so the request packet is sent on the client thread.
		CompletableFuture<Void> sent = new CompletableFuture<>();
		client.execute(() -> {
			try {
				networkHandler.sendPacket(new RequestCommandCompletionsC2SPacket(completionId, partial));
			} finally {
				sent.complete(null);
			}
		});
		try {
			sent.get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			return future.get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			return List.of();
		} finally {
			PENDING.remove(completionId);
		}
	}

	/** Called from the ClientPlayNetworkHandlerMixin on the client thread. */
	public static void onServerSuggestions(int completionId, List<String> suggestions) {
		CompletableFuture<List<String>> future = PENDING.remove(completionId);
		if (future != null) {
			future.complete(suggestions);
		}
	}

	private static List<String> getBaritoneSuggestions(String partial) {
		try {
			Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
			Object provider = apiClass.getMethod("getProvider").invoke(null);
			Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
			Object manager = baritone.getClass().getMethod("getCommandManager").invoke(baritone);

			// Baritone expects the prefix WITHOUT the leading '#', and its suggestions
			// come back without '#' — re-add it so the completed command works in chat.
			boolean hadPrefix = partial.startsWith("#");
			String stripped = hadPrefix ? partial.substring(1) : partial;
			Object result = manager.getClass().getMethod("tabComplete", String.class).invoke(manager, stripped);
			if (result instanceof Stream<?> stream) {
				List<String> out = new ArrayList<>();
				stream.forEach(item -> {
					String s = String.valueOf(item);
					if (hadPrefix && !s.startsWith("#")) {
						s = "#" + s;
					}
					out.add(s);
				});
				return out;
			}
		} catch (Exception e) {
			RobotCmdClient.LOGGER.info("[robotcmd] Baritone tabComplete unavailable: {}", e.toString());
		}
		return List.of();
	}

	private static List<String> merge(List<String> server, List<String> baritone) {
		List<String> merged = new ArrayList<>(server);
		for (String s : baritone) {
			if (!merged.contains(s)) {
				merged.add(s);
			}
		}
		return merged.size() > MAX_SUGGESTIONS ? merged.subList(0, MAX_SUGGESTIONS) : merged;
	}

	private static void sendReply(MinecraftClient client, ClientPlayNetworkHandler networkHandler, String ownerName, List<String> suggestions) {
		String prefix = "msg " + ownerName + " " + REPLY_TOKEN + " ";
		int maxPayload = MAX_MSG_COMMAND_LENGTH - prefix.length();
		client.execute(() -> {
			for (List<String> chunk : chunkJson(suggestions, maxPayload)) {
				String json = GSON.toJson(chunk);
				networkHandler.sendChatCommand(prefix + json);
				RobotCmdClient.recordReply(json);
			}
			networkHandler.sendChatCommand(prefix + "[]");
			RobotCmdClient.recordReply("[]");
		});
		RobotCmdClient.LOGGER.info("[robotcmd] Sent {} suggestions to '{}'", suggestions.size(), ownerName);
	}

	/** Splits the list so each chunk's JSON stays within the per-message payload limit. */
	private static List<List<String>> chunkJson(List<String> suggestions, int maxPayload) {
		List<List<String>> chunks = new ArrayList<>();
		List<String> current = new ArrayList<>();
		int currentLen = 2;
		for (String s : suggestions) {
			int itemLen = GSON.toJson(s).length() + 2;
			if (currentLen + itemLen > maxPayload && !current.isEmpty()) {
				chunks.add(current);
				current = new ArrayList<>();
				currentLen = 2;
			}
			if (itemLen > maxPayload) {
				s = s.substring(0, Math.max(1, maxPayload - 20)) + "...";
				itemLen = GSON.toJson(s).length() + 2;
			}
			current.add(s);
			currentLen += itemLen;
		}
		if (!current.isEmpty()) {
			chunks.add(current);
		}
		return chunks;
	}
}
