package com.robotcmd.mixin;

import com.mojang.brigadier.suggestion.Suggestion;
import com.robotcmd.SuggestionService;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

	@Inject(method = "onCommandSuggestions(Lnet/minecraft/network/packet/s2c/play/CommandSuggestionsS2CPacket;)V", at = @At("HEAD"))
	private void robotcmd$onCommandSuggestions(CommandSuggestionsS2CPacket packet, CallbackInfo ci) {
		List<String> suggestions = packet.getSuggestions().getList().stream()
			.map(Suggestion::getText)
			.toList();
		SuggestionService.onServerSuggestions(packet.getCompletionId(), suggestions);
	}
}
