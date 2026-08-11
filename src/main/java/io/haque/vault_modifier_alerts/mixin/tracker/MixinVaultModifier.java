package io.haque.vault_modifier_alerts.mixin.tracker;

import io.haque.vault_modifier_alerts.tracker.VaultModifierTimeAccessor;
import iskallia.vault.core.vault.modifier.spi.VaultModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(VaultModifier.class)
public abstract class MixinVaultModifier implements VaultModifierTimeAccessor {

	@Unique
	private Integer vma$ticksLeft;

	@Override
	public Integer vma$getTimeLeft() {
		return vma$ticksLeft;
	}

	@Override
	public void vma$setTimeLeft(Integer timeLeft) {
		vma$ticksLeft = timeLeft;
	}
}