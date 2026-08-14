package com.robotcmd.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads/saves config/ownercmd.json.
 *
 * <p>botId: the robot's in-game name (the trigger id, e.g. "MyBot").
 * requestKeyword: keyword sent for suggestion requests (robot replies instead of executing).
 * replyToken: token used by the robot's private-message replies.
 */
public final class OwnerCmdConfig {

	private static final String FILE_NAME = "ownercmd.json";

	public String botId = "MyBot";
	public String requestKeyword = "cmds";
	public String replyToken = "[RC-SUGG]";

	private static OwnerCmdConfig instance;

	public static OwnerCmdConfig get() {
		return instance;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		if (!Files.exists(file)) {
			OwnerCmdConfig cfg = new OwnerCmdConfig();
			save(file, cfg);
			instance = cfg;
			OwnerCmdMod.LOGGER.warn(
				"[ownercmd] Created default config at {} - set botId to the robot's player name and restart!",
				file);
			return;
		}
		try {
			Gson gson = new Gson();
			OwnerCmdConfig cfg = gson.fromJson(Files.readString(file), OwnerCmdConfig.class);
			instance = cfg != null ? cfg : new OwnerCmdConfig();
			OwnerCmdMod.LOGGER.info("[ownercmd] Config loaded. botId='{}'", instance.botId);
		} catch (Exception e) {
			OwnerCmdMod.LOGGER.error("[ownercmd] Failed to read config, using defaults", e);
			instance = new OwnerCmdConfig();
		}
	}

	private static void save(Path file, OwnerCmdConfig cfg) {
		try {
			Files.createDirectories(file.getParent());
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			Files.writeString(file, gson.toJson(cfg));
		} catch (IOException e) {
			OwnerCmdMod.LOGGER.error("[ownercmd] Failed to write default config", e);
		}
	}
}
