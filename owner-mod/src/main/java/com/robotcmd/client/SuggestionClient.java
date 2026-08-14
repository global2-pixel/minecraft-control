package com.robotcmd.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suggestion state for the owner's client.
 *
 * <p>Flow: the owner types {@code <botId> cmd <partial>} in chat and presses TAB;
 * this mod sends {@code <botId> cmds <partial>} as a chat message; the robot
 * replies with a private message containing {@code [RC-SUGG] <json array>};
 * the ChatComponent mixin parses the reply and this class stores the list.
 */
public final class SuggestionClient {

	private static final long REQUEST_COOLDOWN_MS = 300;

	private static String botId = "";
	private static String requestKeyword = "cmds";
	private static String replyToken = "[RC-SUGG]";

	private static List<String> suggestions = List.of();
	private static int selected = 0;
	private static String partial = "";
	private static long lastRequestAt = 0;

	private static final Gson GSON = new Gson();

	private SuggestionClient() {
	}

	public static void reloadConfig() {
		OwnerCmdConfig config = OwnerCmdConfig.get();
		botId = config.botId != null ? config.botId.trim() : "";
		requestKeyword = config.requestKeyword != null && !config.requestKeyword.isBlank()
			? config.requestKeyword.trim() : "cmds";
		replyToken = config.replyToken != null && !config.replyToken.isBlank()
			? config.replyToken : "[RC-SUGG]";
	}

	/** Returns true if TAB was consumed by the suggestion flow. */
	public static boolean onTabPressed(String inputText) {
		String partialText = matchPartial(inputText);
		if (partialText == null) {
			return false;
		}
		long now = System.currentTimeMillis();
		if (now - lastRequestAt < REQUEST_COOLDOWN_MS) {
			return true;
		}
		if (!suggestions.isEmpty() && partialText.equals(partial)) {
			selected = (selected + 1) % suggestions.size();
			return true;
		}
		suggestions = List.of();
		partial = partialText;
		selected = 0;
		lastRequestAt = now;
		sendRequest(partialText);
		return true;
	}

	/** Returns the selected suggestion if Enter should insert it, else null. */
	public static String consumeOnEnter() {
		if (suggestions.isEmpty()) {
			return null;
		}
		String picked = suggestions.get(selected);
		suggestions = List.of();
		partial = "";
		selected = 0;
		return picked;
	}

	/** Returns true if the chat line was a suggestion reply (should be hidden). */
	public static boolean tryHandleReply(String chatText) {
		int idx = chatText.indexOf(replyToken);
		if (idx < 0) {
			return false;
		}
		String json = chatText.substring(idx + replyToken.length()).trim();
		try {
			List<String> parsed = GSON.fromJson(json, new TypeToken<List<String>>() {
			}.getType());
			if (parsed != null && !parsed.isEmpty()) {
				suggestions = parsed;
				selected = 0;
			}
		} catch (Exception e) {
			OwnerCmdMod.LOGGER.warn("[ownercmd] Failed to parse suggestion reply: {}", json);
		}
		return true;
	}

	public static boolean shouldShow(String inputText) {
		return !suggestions.isEmpty() && matchPartial(inputText) != null;
	}

	public static List<String> suggestions() {
		return suggestions;
	}

	public static int selected() {
		return selected;
	}

	public static String prefix() {
		return botId + " cmd ";
	}

	private static String matchPartial(String inputText) {
		if (botId.isEmpty() || inputText == null) {
			return null;
		}
		String regex = "^" + Pattern.quote(botId) + "\\s+cmd\\s+(.+)$";
		Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(inputText);
		if (!matcher.matches()) {
			return null;
		}
		String partialText = matcher.group(1).trim();
		return partialText.isEmpty() ? null : partialText;
	}

	private static void sendRequest(String partialText) {
		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client.getConnection();
		if (connection != null) {
			connection.sendChat(botId + " " + requestKeyword + " " + partialText);
		}
	}
}
