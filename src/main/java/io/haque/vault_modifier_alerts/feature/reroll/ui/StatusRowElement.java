package io.haque.vault_modifier_alerts.feature.reroll.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanel;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelState;
import iskallia.vault.client.gui.framework.element.ContainerElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.spatial.Spatials;
import iskallia.vault.client.gui.screen.block.VaultArtisanStationScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * Status row element for the auto-reroll panel. Delegates text/color
 * computation to {@link RerollPanel#computeStatusInfo} and renders the
 * truncated label with hover tooltip when the text overflows.
 */
public class StatusRowElement extends ContainerElement<StatusRowElement> {

	private Font font;

	public StatusRowElement(int x, int y, int width) {
		super(Spatials.size(width, RerollPanelLayout.ROW_H));
	}

	public void setFont(Font font) {
		this.font = font;
	}

	@Override
	public void render(IElementRenderer renderer, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		RerollPanel panel = RerollPanel.getInstance();
		if (!panel.isVisible() || font == null) {
			return;
		}
		VaultArtisanStationScreen screen = (VaultArtisanStationScreen) Minecraft.getInstance().screen;
		RerollPanel.StatusInfo info = panel.computeStatusInfo(panel.operations(screen));
		int maxChars = (width() - RerollPanelLayout.PAD_X * 2) / 7;
		String display = info.text();
		if (display.length() > maxChars) {
			display = RerollPanel.truncate(display, maxChars);
		}
		font.draw(poseStack, display, x() + RerollPanelLayout.PAD_X, y() + 3, info.color());
		if (mouseX >= x() && mouseX < x() + width() && mouseY >= y() && mouseY < y() + height()
				&& !info.full().equals(display)) {
			panel.hoverTooltip(info.full(), mouseX, mouseY);
		}
	}
}
