package io.haque.vault_modifier_alerts.feature.reroll.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout;
import iskallia.vault.client.gui.framework.ScreenTextures;
import iskallia.vault.client.gui.framework.element.spi.AbstractSpatialElement;
import iskallia.vault.client.gui.framework.element.spi.IGuiEventElement;
import iskallia.vault.client.gui.framework.element.spi.IRenderedElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.spatial.Spatials;
import iskallia.vault.client.atlas.TextureAtlasRegion;
import net.minecraft.client.gui.GuiComponent;

import java.util.function.Supplier;

/**
 * A toggle row element using the host's atlas toggle icons
 * ({@code BUTTON_TOGGLE_ON}/{@code BUTTON_TOGGLE_OFF}) on the left,
 * label on the right. Replaces the hand-drawn {@code drawToggleRow()}
 * in {@link io.haque.vault_modifier_alerts.feature.reroll.RerollPanel}.
 */
public class ToggleRowElement extends AbstractSpatialElement<ToggleRowElement>
		implements IRenderedElement, IGuiEventElement {

	private final String label;
	private final Supplier<Boolean> state;
	private final Runnable onToggle;

	public ToggleRowElement(int x, int y, int width, String label, Supplier<Boolean> state, Runnable onToggle) {
		super(Spatials.positionXY(x, y).size(width, RerollPanelLayout.ROW_H));
		this.label = label;
		this.state = state;
		this.onToggle = onToggle;
	}

	@Override
	public void setVisible(boolean visible) {
	}

	@Override
	public boolean isVisible() {
		return true;
	}

	@Override
	public void render(IElementRenderer renderer, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		boolean enabled = state.get();
		boolean hovered = containsMouse(mouseX, mouseY);
		if (hovered) {
			GuiComponent.fill(poseStack, x() + 1, y(), x() + width() - 1,
					y() + height(), RerollTokens.ROW_HOVER);
		}
		TextureAtlasRegion icon = enabled
				? (hovered ? ScreenTextures.BUTTON_TOGGLE_ON_HOVER : ScreenTextures.BUTTON_TOGGLE_ON)
				: (hovered ? ScreenTextures.BUTTON_TOGGLE_OFF_HOVER : ScreenTextures.BUTTON_TOGGLE_OFF);
		icon.blit(poseStack,
				Spatials.positionXY(x() + RerollPanelLayout.PAD_X, y() + 1),
				Spatials.size(12, 12));
		net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
		font.draw(poseStack, label, x() + 26, y() + 3,
				enabled ? RerollTokens.TEXT_DEFAULT() : RerollTokens.TEXT_DISABLED);
	}

	@Override
	public boolean onMouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && containsMouse(mouseX, mouseY)) {
			onToggle.run();
			return true;
		}
		return false;
	}
}
