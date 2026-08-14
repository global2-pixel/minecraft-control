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

	private SuggestionOverlay() {
	}

	public static void render(GuiGraphicsExtractor extractor, EditBox input) {
		if (!SuggestionClient.shouldShow(input.getValue())) {
			return;
		}
		List<String> suggestions = SuggestionClient.suggestions();
		if (suggestions.isEmpty()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int selected = SuggestionClient.selected();

		int lineHeight = font.lineHeight + 4;
		int panelHeight = suggestions.size() * lineHeight + PADDING * 2;
		int x = input.getX() + (input.getWidth() - PANEL_WIDTH) / 2;
		int y = input.getY() - panelHeight - 4;

		extractor.fill(x, y, x + PANEL_WIDTH, y + panelHeight, BG_COLOR);
		extractor.outline(x, y, x + PANEL_WIDTH, y + panelHeight, OUTLINE_COLOR);

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
}
