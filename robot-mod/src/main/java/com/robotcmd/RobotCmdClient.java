package com.robotcmd;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-side mod running on the bot account.
 *
 * <p>The owner types in chat: {@code <botId> cmd <command>}
 * e.g. {@code MyBot cmd /give Steve diamond 64} or {@code MyBot cmd "say hello"}.
 * Only player names listed in the config (ownerNames) can trigger it.
 *
 * <p>Both the trigger detection and the result capture run on ChatHud/InGameHud
 * mixin hooks instead of chat events, so locally-rendered output (e.g. Baritone)
 * is captured too, not just server chat.
 */
public class RobotCmdClient implements ClientModInitializer {

	public static final Logger LOGGER = LoggerFactory.getLogger("robotcmd");

	/** Vanilla chat arrives as {@code <SenderName> message}. */
	static final Pattern SENDER_PATTERN = Pattern.compile("^<([^>]+)>\\s+(.+)$");

	/** Anti-loop / anti-spam guard. */
	private static final long COOLDOWN_MS = 1000;
	private static final long SUGGEST_COOLDOWN_MS = 300;

	private static long lastExecutedAt = 0;
	private static long lastSuggestAt = 0;

	private static boolean capturingResults = false;
	private static long captureDeadline = 0;

	@Override
	public void onInitializeClient() {
		RobotCmdConfig.load();
		RobotCommandRegistration.register();
		LOGGER.info("[robotcmd] Initialized.");
	}

	/** Called from ChatHudMixin for every message shown in the chat HUD. */
	public static void onChatMessage(Text message) {
		try {
			String text = message.getString();
			handleChatMessage(text);
			captureAndForwardResult(text);
		} catch (Exception e) {
			LOGGER.error("[robotcmd] Failed to process chat message", e);
		}
	}

	/** Called from InGameHudMixin for every action-bar (overlay) message. */
	public static void onOverlayMessage(Text message) {
		try {
			captureAndForwardResult(message.getString());
		} catch (Exception e) {
			LOGGER.error("[robotcmd] Failed to process overlay message", e);
		}
	}

	private static void handleChatMessage(String text) {
		Matcher senderMatcher = SENDER_PATTERN.matcher(text);
		if (!senderMatcher.matches()) {
			return;
		}
		String senderName = senderMatcher.group(1).trim();
		String content = senderMatcher.group(2).trim();

		// Never react to the bot's own echoed messages (prevents loops via /say etc.).
		if (senderName.equalsIgnoreCase(getSelfName())) {
			return;
		}

		String botId = resolveBotId();
		if (botId.isEmpty()) {
			return;
		}

		if (!isOwner(senderName)) {
			LOGGER.info("[robotcmd] Ignored command from non-owner '{}': {}", senderName, content);
			return;
		}

		// Match: <botId> cmds <partial>  ->  suggestion request, never executes
		String suggestRegex = "^" + Pattern.quote(botId) + "\\s+cmds\\s+(.+)$";
		Matcher suggestMatcher = Pattern.compile(suggestRegex, Pattern.CASE_INSENSITIVE).matcher(content);
		if (suggestMatcher.matches()) {
			long now = System.currentTimeMillis();
			if (now - lastSuggestAt >= SUGGEST_COOLDOWN_MS) {
				lastSuggestAt = now;
				SuggestionService.requestSuggestions(suggestMatcher.group(1), senderName);
			}
			return;
		}

		// Match: <botId> cmd <command>
		String regex = "^" + Pattern.quote(botId) + "\\s+cmd\\s+(.+)$";
		Matcher cmdMatcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(content);
		if (!cmdMatcher.matches()) {
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastExecutedAt < COOLDOWN_MS) {
			return;
		}
		lastExecutedAt = now;

		String command = cmdMatcher.group(1);
		if (!RobotInfoService.tryHandle(command)) {
			executeCommand(command, senderName);
		}
	}

	/** Broadcasts a message to the chat bar. Runs on the client thread (thread-safe). */
	public static void broadcastToChat(String text) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
		if (networkHandler == null) {
			return;
		}
		client.execute(() -> networkHandler.sendChatMessage(text));
	}

	private static boolean isOwner(String senderName) {
		List<String> owners = RobotCmdConfig.get().ownerNames;
		if (owners == null || owners.isEmpty()) {
			return false;
		}
		for (String owner : owners) {
			if (owner != null && !owner.isBlank() && owner.equalsIgnoreCase(senderName)) {
				return true;
			}
		}
		return false;
	}

	private static String resolveBotId() {
		String configured = RobotCmdConfig.get().botId;
		if (configured != null && !configured.isBlank()) {
			return configured.trim();
		}
		return getSelfName();
	}

	private static String getSelfName() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player != null ? client.player.getName().getString() : "";
	}

	private static void executeCommand(String rawCommand, String senderName) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
		if (networkHandler == null || client.player == null) {
			LOGGER.warn("[robotcmd] Not connected to a server, cannot execute: {}", rawCommand);
			return;
		}

		String command = stripQuotes(rawCommand);
		if (command.isEmpty()) {
			return;
		}

		if (command.startsWith("/")) {
			// /give ... -> sendChatCommand("give ...")
			networkHandler.sendChatCommand(command.substring(1));
		} else {
			// plain text -> the bot says it in chat
			networkHandler.sendChatMessage(command);
		}

		RobotCmdConfig config = RobotCmdConfig.get();
		capturingResults = config.broadcastResults;
		captureDeadline = System.currentTimeMillis() + Math.max(0, config.captureWindowMs);

		LOGGER.info("[robotcmd] Executed by '{}': {}", senderName, command);
	}

	/** Forwards command feedback (system messages, not player chat) to the chat bar. */
	private static void captureAndForwardResult(String text) {
		if (!capturingResults) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now >= captureDeadline) {
			capturingResults = false;
			return;
		}
		if (text.isBlank() || SENDER_PATTERN.matcher(text).matches()) {
			return; // player chat (including our own broadcast echo) is not feedback
		}
		sendToChat("[RobotCmd] " + text);
	}

	private static void sendToChat(String message) {
		ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
		if (networkHandler != null) {
			networkHandler.sendChatMessage(message);
		}
	}

	private static String stripQuotes(String cmd) {
		String trimmed = cmd.trim();
		if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
			return trimmed.substring(1, trimmed.length() - 1).trim();
		}
		return trimmed;
	}
}
