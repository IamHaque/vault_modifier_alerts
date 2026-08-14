package io.haque.vault_modifier_alerts.mixin.artisan;

import io.haque.vault_modifier_alerts.VaultModifierAlerts;
import io.haque.vault_modifier_alerts.feature.reroll.ArtisanStationScreenAccessor;
import io.haque.vault_modifier_alerts.feature.reroll.AutoRerollEngine;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanel;
import iskallia.vault.client.gui.screen.block.VaultArtisanStationScreen;
import iskallia.vault.gear.modification.GearModificationAction;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * F3 auto-reroll integration points on the Artisan Station screen (VH classes are
 * not obfuscated, so remap=false and real method names are used):
 * - m_6305_ render TAIL: draws the side panel after everything else.
 * - m_7933_ mouseClicked HEAD (cancellable): routes clicks on the panel rect to
 *   RerollPanel so the station GUI is never obstructed.
 * - attemptCraft HEAD: notifies the engine of any press (engine or manual).
 * - Duck interface ArtisanStationScreenAccessor exposes the private attemptCraft
 *   so the engine can trigger the exact button-press behaviour.
 */
@Mixin(value = VaultArtisanStationScreen.class, remap = false)
public abstract class MixinVaultArtisanStationScreen implements ArtisanStationScreenAccessor {

	@Shadow
	private void attemptCraft(GearModificationAction action) {
	}

	@Override
	@Unique
	public void vma$triggerAction(GearModificationAction action) {
		attemptCraft(action);
	}

	@Inject(method = "attemptCraft", at = @At("HEAD"))
	private void vma$onAttemptCraft(GearModificationAction action, CallbackInfo ci) {
		AutoRerollEngine.getInstance().onCraftTriggered(action);
	}

	@Inject(method = "m_6305_", at = @At("TAIL"))
	private void vma$renderPanel(PoseStack poseStack, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		try {
			RerollPanel.getInstance().render((VaultArtisanStationScreen) (Object) this, poseStack, mouseX, mouseY);
		} catch (Throwable t) {
			VaultModifierAlerts.LOGGER.warn("[VMA] Failed to render auto-reroll panel", t);
		}
	}

	@Inject(method = "m_7933_", at = @At("HEAD"), cancellable = true)
	private void vma$handlePanelClick(int mouseX, int mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		try {
			if (RerollPanel.getInstance().handleClick((VaultArtisanStationScreen) (Object) this, mouseX, mouseY, button)) {
				cir.setReturnValue(true);
			}
		} catch (Throwable t) {
			VaultModifierAlerts.LOGGER.warn("[VMA] Failed to handle auto-reroll panel click", t);
		}
	}
}