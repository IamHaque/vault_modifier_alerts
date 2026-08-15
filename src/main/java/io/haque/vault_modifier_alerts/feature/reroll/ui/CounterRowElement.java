package io.haque.vault_modifier_alerts.feature.reroll.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.reroll.AutoRerollEngine;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout;
import iskallia.vault.client.gui.framework.element.ContainerElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.spatial.Spatials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * Counter row element that displays "Potential reset x N" when the
 * auto-reroll engine is running and auto-reset potential is enabled.
 * Hidden otherwise.
 */
public class CounterRowElement extends ContainerElement<CounterRowElement> {

	private Font font;

	public CounterRowElement(int x, int y, int width) {
		super(Spatials.size(width, RerollPanelLayout.ROW_H));
	}

	public void setFont(Font font) {
		this.font = font;
	}

	@Override
	public void render(IElementRenderer renderer, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		AutoRerollEngine engine = AutoRerollEngine.getInstance();
		if (!engine.isRunning() || !VmaClientConfigs.isAutoResetPotentialEnabled() || font == null) {
			return;
		}
		int resets = engine.potentialResetsThisSession();
		font.draw(poseStack, "Potential reset x " + resets, x() + RerollPanelLayout.PAD_X, y() + 3,
				RerollTokens.TEXT_MUTED);
	}
}
