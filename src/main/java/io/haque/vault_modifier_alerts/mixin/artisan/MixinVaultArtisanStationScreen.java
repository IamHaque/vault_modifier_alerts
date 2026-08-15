package io.haque.vault_modifier_alerts.mixin.artisan;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.VaultModifierAlerts;
import io.haque.vault_modifier_alerts.feature.reroll.ArtisanStationScreenAccessor;
import io.haque.vault_modifier_alerts.feature.reroll.AutoRerollEngine;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanel;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.render.spi.ITooltipRendererFactory;
import iskallia.vault.client.gui.framework.screen.AbstractElementContainerScreen;
import iskallia.vault.client.gui.screen.block.VaultArtisanStationScreen;
import iskallia.vault.container.VaultArtisanStationContainer;
import iskallia.vault.gear.modification.GearModificationAction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Artisan station integration for the Remastered VH 3.15 screen framework.
 * The screen does not override render() itself - everything is drawn through
 * framework elements - so the auto-reroll panel is added as a real framework
 * element from the constructor, exactly like the reference mods do.
 * - <init> RETURN: registers RerollPanelElement (click-routed by the framework).
 * - m_6305_ TAIL (require=0): re-draws the panel on top of the slot items the
 *   framework painted over it; best-effort only, the element pass still draws.
 * - attemptCraft HEAD: notifies the engine of any press (engine or manual).
 * - Duck interface ArtisanStationScreenAccessor exposes the private attemptCraft
 *   so the engine can trigger the exact button-press behaviour.
 * VH classes are not obfuscated, so remap=false and real method names are used.
 */
@Mixin(value = VaultArtisanStationScreen.class, remap = false)
public abstract class MixinVaultArtisanStationScreen extends AbstractElementContainerScreen<VaultArtisanStationContainer>
		implements ArtisanStationScreenAccessor {

	@Shadow
	private void attemptCraft(GearModificationAction action) {
	}

	private MixinVaultArtisanStationScreen(VaultArtisanStationContainer container, Inventory inventory,
			Component title, IElementRenderer elementRenderer,
			ITooltipRendererFactory<AbstractElementContainerScreen<VaultArtisanStationContainer>> tooltipRendererFactory) {
		super(container, inventory, title, elementRenderer, tooltipRendererFactory);
	}

	@Override
	@Unique
	public void vma$triggerAction(GearModificationAction action) {
		attemptCraft(action);
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void vma$addRerollPanel(VaultArtisanStationContainer container, Inventory inventory, Component title,
			CallbackInfo ci) {
		addElement(RerollPanelElement.create((VaultArtisanStationScreen) (Object) this));
	}

	/**
	 * Draws the panel again at the very end of the screen render, above the
	 * slot items and tooltips the framework painted over it. require=0: the
	 * element already draws the panel in the framework pass, so a failure here
	 * only loses the z-order improvement, never the panel itself.
	 */
	@Inject(method = "m_6305_", at = @At("TAIL"), require = 0)
	private void vma$renderPanelOnTop(PoseStack poseStack, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		try {
			RerollPanelElement element = RerollPanelElement.getInstance();
			RerollPanel panel = RerollPanel.getInstance();
			if (element != null && panel.isVisible()) {
				panel.draw((VaultArtisanStationScreen) (Object) this, poseStack, element.x(), element.y(),
						element.width(), element.height(), mouseX, mouseY);
			}
		} catch (Throwable t) {
			VaultModifierAlerts.LOGGER.warn("[VMA] Failed to re-draw auto-reroll panel on top", t);
		}
	}

	@Inject(method = "attemptCraft", at = @At("HEAD"))
	private void vma$onAttemptCraft(GearModificationAction action, CallbackInfo ci) {
		AutoRerollEngine.getInstance().onCraftTriggered(action);
	}
}
