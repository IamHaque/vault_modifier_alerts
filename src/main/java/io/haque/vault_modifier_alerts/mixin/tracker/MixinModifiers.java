package io.haque.vault_modifier_alerts.mixin.tracker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.haque.vault_modifier_alerts.tracker.ModifierTracker;
import io.haque.vault_modifier_alerts.tracker.VaultModifierTimeAccessor;
import iskallia.vault.core.vault.Modifiers;
import iskallia.vault.core.vault.modifier.spi.ModifierContext;
import iskallia.vault.core.vault.modifier.spi.VaultModifier;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(value = Modifiers.class, remap = false)
public abstract class MixinModifiers {

	@WrapOperation(method = "getDisplayGroup",
			at = @At(value = "INVOKE",
					target = "Liskallia/vault/core/vault/Modifiers$Entry;getModifier()Ljava/util/Optional;"))
	private static Optional<VaultModifier<?>> vma$captureTimeLeft(Modifiers.Entry instance,
			Operation<Optional<VaultModifier<?>>> original,
			@Local(name = "map") Object2IntMap<VaultModifier<?>> map) {

		Optional<VaultModifier<?>> result = original.call(instance);
		VaultModifier<?> modifier = result.orElse(null);
		if (modifier instanceof VaultModifierTimeAccessor accessor) {
			if (!map.containsKey(modifier)) {
				accessor.vma$setTimeLeft(null);
			}
			ModifierContext context = instance.getContext();
			if (context != null) {
				Integer timeLeft = context.getTimeLeft().orElse(null);
				if (timeLeft != null && (accessor.vma$getTimeLeft() == null
						|| accessor.vma$getTimeLeft() <= 0 || timeLeft < accessor.vma$getTimeLeft())) {
					accessor.vma$setTimeLeft(timeLeft);
				}
				ModifierTracker.getInstance().recordFrameEntry(modifier.getId(), accessor.vma$getTimeLeft());
			} else {
				accessor.vma$setTimeLeft(null);
				ModifierTracker.getInstance().recordFrameEntry(modifier.getId(), null);
			}
		}
		return result;
	}
}