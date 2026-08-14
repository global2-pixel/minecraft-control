package com.robotcmd;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
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

	/** Private-request wrapper used by the owner's tab-completion. */
	private static final String REQ_TOKEN = "[RC-REQ]";

	/** Our own messages always carry one of these tokens; an echo containing one must not be re-forwarded. */
	private static final String[] PROTOCOL_TOKENS = {"[RC-SUGG]", "[RC-REQ]", "[RobotCmd]"};

	/** Texts we recently sent via /msg; their echoes must not be re-forwarded. */
	private static final java.util.ArrayDeque<String> RECENT_REPLIES = new java.util.ArrayDeque<>(8);

	/** Vanilla chat arrives as {@code <SenderName> message}. */
	static final Pattern SENDER_PATTERN = Pattern.compile("^<([^>]+)>\\s+(.+)$");

	/** Private messages arrive as {@code Name whispers to you: ...} (wrapper text and colon style are localized). */
	private static final Pattern WHISPER_PATTERN = Pattern.compile("^([A-Za-z0-9_]+)[^:：]+[：:]\\s*(.+)$");

	/** Anti-loop / anti-spam guard. */
	private static final long COOLDOWN_MS = 1000;
	private static final long SUGGEST_COOLDOWN_MS = 300;

	private static long lastExecutedAt = 0;
	private static long lastSuggestAt = 0;

	private static boolean capturingResults = false;
	private static long captureDeadline = 0;
	private static String replyTarget = "";

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
			handleChatMessage(message, text);
			captureAndForwardResult(message);
		} catch (Exception e) {
			LOGGER.error("[robotcmd] Failed to process chat message", e);
		}
	}

	/** Called from InGameHudMixin for every action-bar (overlay) message. */
	public static void onOverlayMessage(Text message) {
		try {
			captureAndForwardResult(message);
		} catch (Exception e) {
			LOGGER.error("[robotcmd] Failed to process overlay message", e);
		}
	}

	private static void handleChatMessage(Text message, String text) {
		String senderName = extractSenderFromClickEvent(message);
		String argsContent = extractContentFromArgs(message);
		String content;

		int reqIdx = text.indexOf(REQ_TOKEN);
		if (reqIdx >= 0) {
			// private request: content from the clean component args (no decorative suffix) or after the token
			String raw = argsContent != null ? argsContent : text;
			int tokenIdx = raw.indexOf(REQ_TOKEN);
			content = tokenIdx >= 0 ? raw.substring(tokenIdx + REQ_TOKEN.length()).trim() : raw.trim();
			if (senderName == null) {
				Matcher whisperMatcher = WHISPER_PATTERN.matcher(text);
				if (whisperMatcher.matches()) {
					senderName = whisperMatcher.group(1).trim();
				}
			}
			if (senderName == null) {
				LOGGER.warn("[robotcmd] Could not identify sender of private request, ignoring: {}", text);
				return;
			}
		} else {
			Matcher senderMatcher = SENDER_PATTERN.matcher(text);
			if (senderMatcher.matches()) {
				if (senderName == null) {
					senderName = senderMatcher.group(1).trim();
				}
				content = argsContent != null ? argsContent : senderMatcher.group(2).trim();
			} else {
				if (senderName == null) {
					Matcher whisperMatcher = WHISPER_PATTERN.matcher(text);
					if (whisperMatcher.matches()) {
						senderName = whisperMatcher.group(1).trim();
					}
				}
				content = argsContent;
				if (senderName == null || content == null) {
					return;
				}
			}
		}

		content = stripDecorativeSuffix(content);

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

		// Normalize: strip the optional botId prefix, then match the keyword-first form.
		// Chat messages arrive as "<botId> cmd ..."; token-wrapped private requests
		// arrive as "cmd ..." (already addressed to the bot via /msg).
		String normalized = content;
		if (normalized.regionMatches(true, 0, botId, 0, botId.length())) {
			normalized = normalized.substring(botId.length()).trim();
		}

		// Match: cmds <partial>  ->  suggestion request, never executes
		Matcher suggestMatcher = Pattern.compile("^cmds\\s+(.+)$", Pattern.CASE_INSENSITIVE).matcher(normalized);
		if (suggestMatcher.matches()) {
			long now = System.currentTimeMillis();
			if (now - lastSuggestAt >= SUGGEST_COOLDOWN_MS) {
				lastSuggestAt = now;
				SuggestionService.requestSuggestions(suggestMatcher.group(1), senderName);
			}
			return;
		}

		// Match: cmd <command>
		Matcher cmdMatcher = Pattern.compile("^cmd\\s+(.+)$", Pattern.CASE_INSENSITIVE).matcher(normalized);
		if (!cmdMatcher.matches()) {
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastExecutedAt < COOLDOWN_MS) {
			return;
		}
		lastExecutedAt = now;

		String command = cmdMatcher.group(1);
		if (!RobotInfoService.tryHandle(command, senderName)) {
			executeCommand(command, senderName);
		}
	}

	/** Walks the message tree for the "/msg <name>" suggestion click event (the real username). */
	private static String extractSenderFromClickEvent(Text message) {
		Style style = message.getStyle();
		if (style != null && style.getClickEvent() != null) {
			ClickEvent clickEvent = style.getClickEvent();
			if (clickEvent.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
				String value = clickEvent.getValue();
				if (value != null && value.startsWith("/msg ")) {
					String name = value.substring(5).trim();
					if (!name.isEmpty() && !name.contains(" ")) {
						return name;
					}
				}
			}
		}
		for (Text sibling : message.getSiblings()) {
			String sender = extractSenderFromClickEvent(sibling);
			if (sender != null) {
				return sender;
			}
		}
		return null;
	}

	/** Content from the translatable args (chat/whisper types are [sender, content]). */
	private static String extractContentFromArgs(Text message) {
		if (message.getContent() instanceof TranslatableTextContent translatable) {
			Object[] args = translatable.getArgs();
			if (args.length >= 2 && args[args.length - 1] instanceof Text content) {
				return content.getString();
			}
		}
		for (Text sibling : message.getSiblings()) {
			String content = extractContentFromArgs(sibling);
			if (content != null) {
				return content;
			}
		}
		return null;
	}

	/** Sends a private message to the owner. Runs on the client thread (thread-safe). */
	public static void replyTo(String ownerName, String text) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
		if (networkHandler == null) {
			return;
		}
		client.execute(() -> {
			networkHandler.sendChatCommand("msg " + ownerName + " " + text);
			recordReply(text);
		});
	}

	/** Remembers a /msg we sent so its echo is not captured as command feedback. */
	public static void recordReply(String text) {
		if (text == null || text.isEmpty()) {
			return;
		}
		synchronized (RECENT_REPLIES) {
			RECENT_REPLIES.addLast(text);
			while (RECENT_REPLIES.size() > 8) {
				RECENT_REPLIES.removeFirst();
			}
		}
	}

	private static boolean isOurEcho(String text) {
		synchronized (RECENT_REPLIES) {
			for (String sent : RECENT_REPLIES) {
				if (text.contains(sent)) {
					return true;
				}
			}
		}
		return false;
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
		} else if (command.startsWith("#")) {
			// Baritone commands go to public chat so Baritone can process them
			networkHandler.sendChatMessage(command);
		} else {
			// plain text -> private reply to the requester instead of public chat
			replyTo(senderName, command);
		}

		RobotCmdConfig config = RobotCmdConfig.get();
		capturingResults = config.broadcastResults;
		captureDeadline = System.currentTimeMillis() + Math.max(0, config.captureWindowMs);
		replyTarget = senderName;

		LOGGER.info("[robotcmd] Executed by '{}': {}", senderName, command);
	}

	/** Forwards command feedback (system messages, not player chat) via /msg to the requester. */
	private static void captureAndForwardResult(Text message) {
		if (!capturingResults) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now >= captureDeadline) {
			capturingResults = false;
			return;
		}
		String text = message.getString();
		if (text.isBlank() || SENDER_PATTERN.matcher(text).matches()) {
			return; // player chat is not command feedback
		}
		for (String token : PROTOCOL_TOKENS) {
			if (text.contains(token)) {
				return; // our own echo/forwarded reply, would loop if re-sent
			}
		}
		if (isOurEcho(text)) {
			return; // echo of a /msg we just sent
		}
		if (replyTarget.isEmpty()) {
			return;
		}
		replyTo(replyTarget, "[RobotCmd] " + text);
	}

	/** Strips the neko-style decorative suffix some resource packs append to chat formats. */
	private static String stripDecorativeSuffix(String s) {
		return s.replaceAll("(?:喵～)+$", "").trim();
	}

	private static String stripQuotes(String cmd) {
		String trimmed = cmd.trim();
		if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
			return trimmed.substring(1, trimmed.length() - 1).trim();
		}
		return trimmed;
	}
}
