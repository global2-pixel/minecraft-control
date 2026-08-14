package com.robotcmd.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OwnerCmdMod implements ClientModInitializer {

	public static final Logger LOGGER = LoggerFactory.getLogger("ownercmd");

	@Override
	public void onInitializeClient() {
		OwnerCmdConfig.load();
		SuggestionClient.reloadConfig();
		RobotCommandRegistration.register();
		LOGGER.info("[ownercmd] Initialized.");
	}
}
