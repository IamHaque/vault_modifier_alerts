package io.haque.vault_modifier_alerts.feature.reroll;

import iskallia.vault.gear.modification.GearModificationAction;

/**
 * Duck interface implemented by MixinVaultArtisanStationScreen so the auto-reroll
 * engine can trigger the station's private attemptCraft (the exact button-press
 * behaviour) without reflection. Same pattern as tracker.VaultModifierTimeAccessor.
 */
public interface ArtisanStationScreenAccessor {

	void vma$triggerAction(GearModificationAction action);
}