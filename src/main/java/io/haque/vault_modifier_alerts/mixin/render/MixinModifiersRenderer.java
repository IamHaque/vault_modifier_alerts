package io.haque.vault_modifier_alerts.mixin.render;

import io.haque.vault_modifier_alerts.VaultModifierAlerts;
import io.haque.vault_modifier_alerts.feature.order.ModifierOrdering;
import iskallia.vault.core.vault.modifier.spi.VaultModifier;
import iskallia.vault.core.vault.overlay.ModifiersRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;

@Mixin(value = ModifiersRenderer.class, remap = false)
public abstract class MixinModifiersRenderer {

	@ModifyVariable(method = "renderVaultModifiers(Ljava/util/Map;Lcom/mojang/blaze3d/vertex/PoseStack;ZFLiskallia/vault/util/Alignment;Z)V",
			argsOnly = true, ordinal = 0, at = @At("HEAD"))
	private static Map<VaultModifier<?>, Integer> vma$reorderGroup(
			Map<VaultModifier<?>, Integer> group) {
		try {
			return ModifierOrdering.reorder(group);
		} catch (RuntimeException e) {
			VaultModifierAlerts.LOGGER.error("[VMA] HUD reorder failed; rendering vanilla order", e);
			return group;
		}
	}
}