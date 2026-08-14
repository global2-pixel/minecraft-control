package com.robotcmd.client.mixin;

import com.robotcmd.client.SuggestionClient;
import com.robotcmd.client.SuggestionOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

	@Shadow
	protected EditBox input;

	@Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
	private void robotcmd$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		int key = event.key();
		if (key == GLFW.GLFW_KEY_TAB) {
			if (SuggestionClient.onTabPressed(this.input.getValue())) {
				cir.setReturnValue(true);
				cir.cancel();
			}
		} else if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
			if (SuggestionClient.onArrowPressed(key == GLFW.GLFW_KEY_UP ? -1 : 1)) {
				cir.setReturnValue(true);
				cir.cancel();
			}
		} else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			String selected = SuggestionClient.consumeOnEnter();
			if (selected != null) {
				this.input.setValue(SuggestionClient.prefix() + selected);
				this.input.moveCursorToEnd(false);
				cir.setReturnValue(true);
				cir.cancel();
			}
		}
	}

	@Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
	private void robotcmd$onMouseClicked(MouseButtonEvent event, boolean unknown, CallbackInfoReturnable<Boolean> cir) {
		if (event.button() != 0) {
			return;
		}
		int row = SuggestionOverlay.hitRow(SuggestionOverlay.computeBounds(this.input), event.x(), event.y());
		if (row >= 0) {
			String picked = SuggestionClient.consumeRow(row);
			if (picked != null) {
				this.input.setValue(SuggestionClient.prefix() + picked);
				this.input.moveCursorToEnd(false);
				cir.setReturnValue(true);
				cir.cancel();
			}
		}
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("RETURN"))
	private void robotcmd$onExtractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		SuggestionOverlay.render(extractor, this.input);
	}

	@Inject(method = "removed()V", at = @At("HEAD"))
	private void robotcmd$onRemoved(CallbackInfo ci) {
		SuggestionClient.clearPanel();
	}
}
