package io.haque.vault_modifier_alerts.feature.downed;

import io.haque.vault_modifier_alerts.VaultModifierAlerts;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.expiry.AlertSoundPlayer;
import iskallia.vault.client.DownedClientData;
import iskallia.vault.client.DownedClientData.DownedTeammateInfo;
import iskallia.vault.core.vault.ClientVaults;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DownedAlertEngine {

	private static final DownedAlertEngine INSTANCE = new DownedAlertEngine();

	private boolean inVault;
	private boolean wasLocalDowned;
	private final Set<UUID> knownTeammates = new HashSet<>();

	private DownedAlertEngine() {
	}

	public static DownedAlertEngine getInstance() {
		return INSTANCE;
	}

	public void evaluate() {
		if (!VmaClientConfigs.isDownedAlertsEnabled()) {
			return;
		}
		boolean inVaultNow = ClientVaults.getActive() != null && ClientVaults.getActive().isPresent();
		if (inVaultNow != inVault) {
			resetSession(inVaultNow);
			return;
		}
		if (!inVaultNow) {
			return;
		}
		boolean localDowned = DownedClientData.isLocalPlayerDowned();
		if (localDowned && !wasLocalDowned) {
			fireLocal();
		}
		wasLocalDowned = localDowned;
		Map<UUID, DownedTeammateInfo> teammates = DownedClientData.getDownedTeammates();
		for (Map.Entry<UUID, DownedTeammateInfo> entry : teammates.entrySet()) {
			if (knownTeammates.add(entry.getKey())) {
				fireTeammate(entry.getKey(), entry.getValue());
			}
		}
		knownTeammates.retainAll(teammates.keySet());
	}

	private void resetSession(boolean nowInVault) {
		inVault = nowInVault;
		wasLocalDowned = false;
		knownTeammates.clear();
	}

	private static void fireLocal() {
		if (!VmaClientConfigs.isAlertSoundEnabled()) {
			if (VmaClientConfigs.isDebugLogging()) {
				VaultModifierAlerts.LOGGER.debug(
						"[VMA] Local player knocked down; sound suppressed (alertSoundEnabled=false)");
			}
			return;
		}
		if (!VmaClientConfigs.isLocalPlayerDownedSoundEnabled()) {
			if (VmaClientConfigs.isDebugLogging()) {
				VaultModifierAlerts.LOGGER.debug(
						"[VMA] Local player knocked down; sound suppressed (localPlayerDownedSoundEnabled=false)");
			}
			return;
		}
		play();
		if (VmaClientConfigs.isDebugLogging()) {
			VaultModifierAlerts.LOGGER.debug("[VMA] Local player knocked down; alert fired");
		}
	}

	private static void fireTeammate(UUID uuid, DownedTeammateInfo info) {
		if (!VmaClientConfigs.isAlertSoundEnabled()) {
			if (VmaClientConfigs.isDebugLogging()) {
				VaultModifierAlerts.LOGGER.debug(
						"[VMA] Teammate {} knocked down; sound suppressed (alertSoundEnabled=false)",
						info == null ? uuid : info.playerName());
			}
			return;
		}
		if (!VmaClientConfigs.isTeammateDownedSoundEnabled()) {
			if (VmaClientConfigs.isDebugLogging()) {
				VaultModifierAlerts.LOGGER.debug(
						"[VMA] Teammate {} knocked down; sound suppressed (teammateDownedSoundEnabled=false)",
						info == null ? uuid : info.playerName());
			}
			return;
		}
		play();
		if (VmaClientConfigs.isDebugLogging()) {
			VaultModifierAlerts.LOGGER.debug("[VMA] Teammate {} knocked down; alert fired",
					info == null ? uuid : info.playerName());
		}
	}

	private static void play() {
		AlertSoundPlayer.play(VmaClientConfigs.downedSoundEvent(), VmaClientConfigs.DOWNED_VOLUME.get(),
				VmaClientConfigs.DOWNED_PITCH.get(), null);
	}
}
