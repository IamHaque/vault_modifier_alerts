package io.haque.vault_modifier_alerts.feature.expiry;

import io.haque.vault_modifier_alerts.VaultModifierAlerts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;

public final class AlertSoundPlayer {

	private AlertSoundPlayer() {
	}

	public static void play(String soundEventId, double volume, double pitch, ResourceLocation modifierId) {
		ResourceLocation soundId = ResourceLocation.tryParse(soundEventId);
		if (soundId == null) {
			VaultModifierAlerts.LOGGER.debug("[VMA] Sound event ID parse failed: input='{}' modifierId={}",
					soundEventId, modifierId);
			logMissingSound(soundEventId, null, modifierId);
			return;
		}
		SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
		if (soundEvent == null) {
			VaultModifierAlerts.LOGGER.debug("[VMA] SoundEvent not registered: id='{}' modifierId={}",
					soundId, modifierId);
			logMissingSound(soundEventId, soundId, modifierId);
			return;
		}
		VaultModifierAlerts.LOGGER.debug("[VMA] Playing sound: event={} vol={} pitch={}",
				soundEvent, volume, pitch);
		Minecraft.getInstance().getSoundManager()
				.play(SimpleSoundInstance.forUI(soundEvent, (float) volume, (float) pitch));
	}

	private static void logMissingSound(String configured, ResourceLocation parsed, ResourceLocation modifierId) {
		VaultModifierAlerts.LOGGER.error("[VMA] Sound event {} not registered; alert for {} was silent",
				configured, modifierId);
	}
}