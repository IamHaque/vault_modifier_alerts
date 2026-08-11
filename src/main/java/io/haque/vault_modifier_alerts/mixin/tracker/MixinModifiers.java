package io.haque.vault_modifier_alerts.mixin.tracker;

import io.haque.vault_modifier_alerts.tracker.ModifierTracker;
import io.haque.vault_modifier_alerts.tracker.VaultModifierTimeAccessor;
import iskallia.vault.core.vault.Modifiers;
import iskallia.vault.core.vault.modifier.spi.ModifierContext;
import iskallia.vault.core.vault.modifier.spi.VaultModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

@Mixin(value = Modifiers.class, remap = false)
public abstract class MixinModifiers {

	@Redirect(method = "getDisplayGroup",
			at = @At(value = "INVOKE",
					target = "Liskallia/vault/core/vault/Modifiers$Entry;getModifier()Ljava/util/Optional;"))
	private Optional<VaultModifier<?>> vma$captureTimeLeft(Modifiers.Entry instance) {

		Optional<VaultModifier<?>> result = instance.getModifier();
		VaultModifier<?> modifier = result.orElse(null);
		if (modifier instanceof VaultModifierTimeAccessor accessor) {
			ModifierContext context = instance.getContext();
			Integer timeLeft = context != null ? context.getTimeLeft().orElse(null) : null;
			accessor.vma$setTimeLeft(timeLeft);
			ModifierTracker.getInstance().recordFrameEntry(modifier.getId(), timeLeft);
		}
		return result;
	}
}