package com.robotcmd;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Ring buffer of recent server chat messages, browsable in pages and persisted to a file. */
public final class ChatLogService {

	private static final int MAX_ENTRIES = 300;
	private static final int MAX_LINE_LENGTH = 35;
	private static final int PAGE_SIZE = 5;
	private static final long MAX_FILE_BYTES = 500_000;
	private static final int CHECK_INTERVAL = 50;

	private static final List<String> LOG = new ArrayList<>();
	private static Path logFile;
	private static java.io.BufferedWriter writer;
	private static int writesSinceCheck = 0;

	private ChatLogService() {
	}

	/** Loads the previous log from disk. Call once at startup. */
	public static void init() {
		logFile = FabricLoader.getInstance().getConfigDir().resolve("robotcmd/chatlog.txt");
		try {
			Files.createDirectories(logFile.getParent());
			if (Files.exists(logFile)) {
				List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
				int start = Math.max(0, lines.size() - MAX_ENTRIES);
				LOG.clear();
				LOG.addAll(lines.subList(start, lines.size()));
			}
			writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			RobotCmdClient.LOGGER.error("[robotcmd] Failed to init chat log", e);
		}
	}

	public static void record(String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		String line = text.length() <= MAX_LINE_LENGTH ? text : text.substring(0, MAX_LINE_LENGTH - 3) + "...";
		synchronized (LOG) {
			LOG.add(line);
			if (LOG.size() > MAX_ENTRIES) {
				LOG.remove(0);
			}
		}
		appendToFile(line);
	}

	private static void appendToFile(String line) {
		if (writer == null) {
			return;
		}
		try {
			writer.write(line);
			writer.newLine();
			writer.flush();
			if (++writesSinceCheck >= CHECK_INTERVAL && Files.size(logFile) > MAX_FILE_BYTES) {
				trimFile();
				writesSinceCheck = 0;
			}
		} catch (IOException e) {
			RobotCmdClient.LOGGER.error("[robotcmd] Failed to write chat log", e);
		}
	}

	/** Rewrites the file from the in-memory buffer to keep it bounded. */
	private static void trimFile() {
		try {
			writer.close();
			List<String> snapshot;
			synchronized (LOG) {
				snapshot = new ArrayList<>(LOG);
			}
			Files.write(logFile, snapshot, StandardCharsets.UTF_8);
			writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			RobotCmdClient.LOGGER.error("[robotcmd] Failed to trim chat log", e);
		}
	}

	/** Returns the 1-based page, newest entries first. */
	public static String page(int page) {
		synchronized (LOG) {
			int total = LOG.size();
			int totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
			if (page < 1 || page > totalPages) {
				return "[日志] 页数无效 (共 " + totalPages + " 页，用 /log 2 翻页)";
			}
			StringBuilder sb = new StringBuilder("[日志 p" + page + '/' + totalPages + "]");
			int end = Math.max(0, total - (page - 1) * PAGE_SIZE);
			int start = Math.max(0, end - PAGE_SIZE);
			for (int i = end - 1; i >= start; i--) {
				sb.append(" | ").append(LOG.get(i));
			}
			if (start == end) {
				sb.append(" | (无)");
			}
			return sb.toString();
		}
	}
}
