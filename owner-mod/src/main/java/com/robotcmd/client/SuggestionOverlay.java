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

	public record PanelBounds(int x, int y, int width, int height, int lineHeight, int rows, boolean isList) {
	}

	private SuggestionOverlay() {
	}

	/** Panel geometry for the current state, or null if the panel is hidden. */
	public static PanelBounds computeBounds(EditBox input) {
		String state = SuggestionClient.panelState(input.getValue());
		if (state == null) {
			return null;
		}
		Font font = Minecraft.getInstance().font;
		boolean isList = "READY".equals(state);
		int lineHeight = font.lineHeight + 4;
		int rows = isList ? SuggestionClient.suggestions().size() : 1;
		int panelHeight = rows * lineHeight + PADDING * 2;
		int x = input.getX() + (input.getWidth() - PANEL_WIDTH) / 2;
		int y = Math.max(2, input.getY() - panelHeight - 4);
		return new PanelBounds(x, y, PANEL_WIDTH, panelHeight, lineHeight, rows, isList);
	}

	/** Row index under the mouse position, or -1 if outside the suggestion list. */
	public static int hitRow(PanelBounds bounds, double mouseX, double mouseY) {
		if (bounds == null || !bounds.isList) {
			return -1;
		}
		if (mouseX < bounds.x || mouseX > bounds.x + bounds.width
			|| mouseY < bounds.y || mouseY > bounds.y + bounds.height) {
			return -1;
		}
		int row = (int) ((mouseY - bounds.y - PADDING) / bounds.lineHeight);
		return row >= 0 && row < bounds.rows ? row : -1;
	}

	public static void render(GuiGraphicsExtractor extractor, EditBox input) {
		PanelBounds bounds = computeBounds(input);
		if (bounds == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;

		extractor.nextStratum();
		extractor.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, BG_COLOR);
		extractor.outline(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, OUTLINE_COLOR);

		if (!bounds.isList) {
			extractor.text(font, stateText(SuggestionClient.panelState(input.getValue())),
				bounds.x + 6, bounds.y + PADDING + 1, HINT_COLOR);
			return;
		}

		List<String> suggestions = SuggestionClient.suggestions();
		int selected = SuggestionClient.selected();
		for (int i = 0; i < suggestions.size(); i++) {
			int rowY = bounds.y + PADDING + i * bounds.lineHeight;
			if (i == selected) {
				extractor.fill(bounds.x + 1, rowY, bounds.x + bounds.width - 1, rowY + bounds.lineHeight, HIGHLIGHT_COLOR);
			}
			String text = suggestions.get(i);
			if (font.width(text) > bounds.width - 16) {
				text = font.plainSubstrByWidth(text, bounds.width - 16 - 3) + "...";
			}
			extractor.text(font, text, bounds.x + 6, rowY + 2, TEXT_COLOR);
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
