package io.haque.vault_modifier_alerts.feature.reroll.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import iskallia.vault.client.atlas.TextureAtlasRegion;
import iskallia.vault.client.gui.framework.element.ButtonElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.spatial.Spatials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * A small stepper button ("-" / "+") rendered as a real {@link ButtonElement}
 * with the host's 16px button texture and a centered glyph, replacing the
 * hand-drawn fills in {@code drawMinRow()}. Disabled when the focused target's
 * range is non-numeric or auto-reroll is off; the state-level guards in
 * {@link io.haque.vault_modifier_alerts.feature.reroll.RerollPanelState}
 * still backstop the click path.
 *
 * <p>The host's {@link ButtonElement#render} blits the texture at its native
 * 16px size (which overflows this 12px element), so render is overridden to
 * stretch the texture across the element's own bounds.</p>
 */
public class StepperButtonElement extends ButtonElement<StepperButtonElement> {

	private final String glyph;

	public StepperButtonElement(int x, int y, int width, int height, String glyph, Runnable onClick) {
		super(Spatials.size(width, height), RerollTokens.START_BUTTON_TEXTURES, onClick);
		this.glyph = glyph;
	}

	@Override
	public void render(IElementRenderer renderer, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		if (!isVisible()) {
			return;
		}
		TextureAtlasRegion region = textures.selectTexture(isDisabled(), containsMouse(mouseX, mouseY), clickHeld);
		renderer.render(region, poseStack, worldSpatial, worldSpatial);
		Font font = Minecraft.getInstance().font;
		int color = isDisabled() ? RerollTokens.TEXT_DISABLED() : RerollTokens.TEXT_DEFAULT();
		int textWidth = font.width(glyph);
		font.draw(poseStack, glyph, x() + width() / 2 - textWidth / 2.0f, y() + (height() - 8) / 2, color);
	}
}
