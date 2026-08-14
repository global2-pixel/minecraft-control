package com.robotcmd.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Extracts readable text from a chat Component without depending on lazy
 * translation resolution (Component.getString() returns only the translation
 * key for not-yet-decomposed translatable components).
 */
public final class ChatTextExtractor {

	private ChatTextExtractor() {
	}

	public static String extract(Component component) {
		StringBuilder sb = new StringBuilder();
		collect(component, sb);
		return sb.toString();
	}

	private static void collect(Component component, StringBuilder sb) {
		ComponentContents contents = component.getContents();
		if (contents instanceof PlainTextContents plain) {
			sb.append(plain.text());
		} else if (contents instanceof TranslatableContents translatable) {
			for (Object arg : translatable.getArgs()) {
				if (arg instanceof Component child) {
					collect(child, sb);
				} else if (arg != null) {
					sb.append(arg);
				}
			}
		}
		for (Component sibling : component.getSiblings()) {
			collect(sibling, sb);
		}
	}
}
