package io.haque.vault_modifier_alerts.feature.expiry;

import io.haque.vault_modifier_alerts.VaultModifierAlerts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;

public final class AlertSoundPlayer {

	private static ResourceLocation missingSoundLogged;

	private AlertSoundPlayer() {
	}

	public static void play(String soundEventId, double volume, double pitch, ResourceLocation modifierId) {
		ResourceLocation soundId = ResourceLocation.tryParse(soundEventId);
		if (soundId == null) {
			logOnceMissing(soundEventId, null, modifierId);
			return;
		}
		SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
		if (soundEvent == null) {
			logOnceMissing(soundEventId, soundId, modifierId);
			return;
		}
		Minecraft.getInstance().getSoundManager()
				.play(SimpleSoundInstance.forUI(soundEvent, (float) volume, (float) pitch));
	}

	private static void logOnceMissing(String configured, ResourceLocation parsed, ResourceLocation modifierId) {
		if (missingSoundLogged != null) {
			return;
		}
		missingSoundLogged = parsed == null
			? ResourceLocation.tryParse("vault_modifier_alerts:invalid")
			: parsed;
		VaultModifierAlerts.LOGGER.error("[VMA] Sound event {} not registered; alert for {} was silent",
				configured, modifierId);
	}
}