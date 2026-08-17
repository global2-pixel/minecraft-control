package com.robotcmd;

import java.util.ArrayList;
import java.util.List;

/** Ring buffer of recent server chat messages, browsable in pages by the owner. */
public final class ChatLogService {

	private static final int MAX_ENTRIES = 300;
	private static final int MAX_LINE_LENGTH = 35;
	private static final int PAGE_SIZE = 5;

	private static final List<String> LOG = new ArrayList<>();

	private ChatLogService() {
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
