package com.robotcmd.client.mixin;

import com.robotcmd.client.SuggestionClient;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

	@Inject(method = "addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
	private void robotcmd$onPlayerMessage(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
		if (SuggestionClient.tryHandleReply(message.getString())) {
			ci.cancel();
		}
	}

	@Inject(method = "addServerSystemMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
	private void robotcmd$onServerSystemMessage(Component message, CallbackInfo ci) {
		if (SuggestionClient.tryHandleReply(message.getString())) {
			ci.cancel();
		}
	}

	@Inject(method = "addClientSystemMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
	private void robotcmd$onClientSystemMessage(Component message, CallbackInfo ci) {
		if (SuggestionClient.tryHandleReply(message.getString())) {
			ci.cancel();
		}
	}
}
