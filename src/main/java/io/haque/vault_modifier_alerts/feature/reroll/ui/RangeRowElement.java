package io.haque.vault_modifier_alerts.feature.reroll.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanel;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout;
import iskallia.vault.client.gui.framework.element.ContainerElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.render.spi.ITooltipRenderer;
import iskallia.vault.client.gui.framework.spatial.Spatials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Range row element for the auto-reroll panel. Renders the current target's
 * roll range live and shows the full text in a declarative tooltip when the
 * row truncates it. Replaces the hand-drawn {@code drawRangeRow()} and its
 * manual popover in {@link RerollPanel}.
 */
public class RangeRowElement extends ContainerElement<RangeRowElement> {

	private Font font;

	public RangeRowElement(int x, int y, int width) {
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
		ModifierCatalog.RollRange range = panel.currentTargetRange();
		String text = range.numeric() ? "Range: " + range.displayText() : "Range: ?";
		int maxChars = (width() - RerollPanelLayout.PAD_X * 2) / 7;
		String shown = text.length() > maxChars ? RerollPanel.truncate(text, maxChars) : text;
		font.draw(poseStack, shown, x() + RerollPanelLayout.PAD_X, y() + 3, RerollTokens.TEXT_MUTED);
	}

	@Override
	public boolean onHoverTooltip(ITooltipRenderer tooltipRenderer, PoseStack poseStack,
			int mouseX, int mouseY, TooltipFlag flag) {
		if (font == null) {
			return false;
		}
		if (mouseX < x() || mouseX >= x() + width() || mouseY < y() || mouseY >= y() + height()) {
			return false;
		}
		ModifierCatalog.RollRange range = RerollPanel.getInstance().currentTargetRange();
		String full = range.numeric() ? "Range: " + range.displayText() : "Range: ?";
		int maxChars = (width() - RerollPanelLayout.PAD_X * 2) / 7;
		if (full.length() <= maxChars) {
			return false;
		}
		tooltipRenderer.renderComponentTooltip(poseStack,
				List.of(new TextComponent(full)),
				mouseX, mouseY,
				iskallia.vault.client.gui.framework.render.TooltipDirection.LEFT);
		return true;
	}
}