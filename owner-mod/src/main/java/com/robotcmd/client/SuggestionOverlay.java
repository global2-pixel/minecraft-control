package com.robotcmd.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

import java.util.List;

/** Draws the suggestion panel above the chat input. */
public final class SuggestionOverlay {

	private static final int PANEL_WIDTH = 260;
	private static final int PADDING = 4;
	private static final int BG_COLOR = 0xC0101010;
	private static final int OUTLINE_COLOR = 0xFF505050;
	private static final int HIGHLIGHT_COLOR = 0xC03366FF;
	private static final int TEXT_COLOR = 0xFFE0E0E0;
	private static final int HINT_COLOR = 0xFFA0A0A0;

	private SuggestionOverlay() {
	}

	public static void render(GuiGraphicsExtractor extractor, EditBox input) {
		String state = SuggestionClient.panelState(input.getValue());
		if (state == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;

		boolean isList = "READY".equals(state);
		int lineHeight = font.lineHeight + 4;
		int rows = isList ? SuggestionClient.suggestions().size() : 1;
		int panelHeight = rows * lineHeight + PADDING * 2;
		int x = input.getX() + (input.getWidth() - PANEL_WIDTH) / 2;
		int y = Math.max(2, input.getY() - panelHeight - 4);

		extractor.nextStratum();
		extractor.fill(x, y, x + PANEL_WIDTH, y + panelHeight, BG_COLOR);
		extractor.outline(x, y, x + PANEL_WIDTH, y + panelHeight, OUTLINE_COLOR);

		if (!isList) {
			extractor.text(font, stateText(state), x + 6, y + PADDING + 1, HINT_COLOR);
			return;
		}

		List<String> suggestions = SuggestionClient.suggestions();
		int selected = SuggestionClient.selected();
		for (int i = 0; i < suggestions.size(); i++) {
			int rowY = y + PADDING + i * lineHeight;
			if (i == selected) {
				extractor.fill(x + 1, rowY, x + PANEL_WIDTH - 1, rowY + lineHeight, HIGHLIGHT_COLOR);
			}
			String text = suggestions.get(i);
			if (font.width(text) > PANEL_WIDTH - 16) {
				text = font.plainSubstrByWidth(text, PANEL_WIDTH - 16 - 3) + "...";
			}
			extractor.text(font, text, x + 6, rowY + 2, TEXT_COLOR);
		}
	}

	private static String stateText(String state) {
		return switch (state) {
			case "WAITING" -> "请求补全中...";
			case "EMPTY" -> "（无匹配补全）";
			case "TIMEOUT" -> "（请求超时，机器人未回复）";
			default -> "";
		};
	}
}
