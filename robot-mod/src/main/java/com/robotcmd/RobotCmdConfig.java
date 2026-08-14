package com.robotcmd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads/saves config/robotcmd.json.
 *
 * <p>ownerNames: player names allowed to trigger commands (the owner's own names).
 * botId:      the trigger id that must prefix "cmd". Empty string means the
 *             bot's own player name is used (recommended).
 * broadcastResults: re-send the command's server feedback to the chat bar
 *             so the owner (and everyone on the server) can see the result.
 * captureWindowMs: how long to keep capturing feedback after a command runs.
 */
public final class RobotCmdConfig {

	private static final String FILE_NAME = "robotcmd.json";

	public List<String> ownerNames = new ArrayList<>();
	public String botId = "";
	public boolean broadcastResults = true;
	public long captureWindowMs = 1500;

	private static RobotCmdConfig instance;

	public static RobotCmdConfig get() {
		return instance;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		if (!Files.exists(file)) {
			RobotCmdConfig cfg = new RobotCmdConfig();
			cfg.ownerNames.add("YourMinecraftName");
			save(file, cfg);
			instance = cfg;
			RobotCmdClient.LOGGER.warn(
				"[robotcmd] Created default config at {} - set ownerNames to your player name and restart!",
				file);
			return;
		}
		try {
			Gson gson = new Gson();
			RobotCmdConfig cfg = gson.fromJson(Files.readString(file), RobotCmdConfig.class);
			instance = cfg != null ? cfg : new RobotCmdConfig();
			RobotCmdClient.LOGGER.info(
				"[robotcmd] Config loaded. ownerNames={}, botId='{}'",
				instance.ownerNames, instance.botId);
		} catch (Exception e) {
			RobotCmdClient.LOGGER.error("[robotcmd] Failed to read config, using empty defaults", e);
			instance = new RobotCmdConfig();
		}
	}

	private static void save(Path file, RobotCmdConfig cfg) {
		try {
			Files.createDirectories(file.getParent());
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			Files.writeString(file, gson.toJson(cfg));
		} catch (IOException e) {
			RobotCmdClient.LOGGER.error("[robotcmd] Failed to write default config", e);
		}
	}
}
