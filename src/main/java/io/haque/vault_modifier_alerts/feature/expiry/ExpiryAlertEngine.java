package io.haque.vault_modifier_alerts.feature.expiry;

import io.haque.vault_modifier_alerts.VaultModifierAlerts;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.tracker.ModifierTracker;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public final class ExpiryAlertEngine {

	private static final ExpiryAlertEngine INSTANCE = new ExpiryAlertEngine();

	private ExpiryAlertEngine() {
	}

	public static ExpiryAlertEngine getInstance() {
		return INSTANCE;
	}

	public void evaluate() {
		ModifierTracker tracker = ModifierTracker.getInstance();
		Map<ResourceLocation, Integer> newSnapshot = tracker.consumeFrame();
		for (String watchedId : VmaClientConfigs.watchedModifiers()) {
			ResourceLocation id = ResourceLocation.tryParse(watchedId);
			if (id == null) {
				continue;
			}
			boolean prevActive = isActive(tracker.getLastSnapshot(), id);
			boolean currActive = isActive(newSnapshot, id);
			if (prevActive && !currActive) {
				fire(id);
			} else if (currActive) {
				tracker.reArm(id);
			}
		}
		tracker.setLastSnapshot(newSnapshot);
	}

	private static void fire(ResourceLocation id) {
		ModifierTracker tracker = ModifierTracker.getInstance();
		if (tracker.isFired(id)) {
			return;
		}
		tracker.markFired(id);
		AlertSoundPlayer.play(VmaClientConfigs.SOUND_EVENT.get(), VmaClientConfigs.VOLUME.get(),
				VmaClientConfigs.PITCH.get(), id);
		if (VmaClientConfigs.isDebugLogging()) {
			VaultModifierAlerts.LOGGER.debug("[VMA] Temporal modifier {} expired; alert fired", id);
		}
	}

	private static boolean isActive(Map<ResourceLocation, Integer> snapshot, ResourceLocation id) {
		Integer timeLeft = snapshot.get(id);
		return timeLeft != null && timeLeft > 0;
	}
}