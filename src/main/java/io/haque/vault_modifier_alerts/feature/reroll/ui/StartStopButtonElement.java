package io.haque.vault_modifier_alerts.feature.reroll.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelState;
import io.haque.vault_modifier_alerts.feature.reroll.AutoRerollEngine;
import iskallia.vault.client.atlas.TextureAtlasRegion;
import iskallia.vault.client.gui.framework.element.ButtonElement;
import iskallia.vault.client.gui.framework.render.NineSlice;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.spatial.Spatials;
import net.minecraft.client.gui.Font;

/**
 * The start/stop button for the auto-reroll panel, rendered as a real
 * {@link ButtonElement} with a centered text label overlaid on the atlas
 * texture. The label changes between "Start" and "Stop" depending on
 * engine state, and the button is visually disabled when neither action
 * is available.
 *
 * <p>The host's {@link ButtonElement#render} blits the button texture at its
 * native 16px size, so this class overrides render to draw the texture
 * 9-sliced across the element's full size instead.</p>
 */
public class StartStopButtonElement extends ButtonElement<StartStopButtonElement> {

	private static final NineSlice.Slices SLICES = NineSlice.slice(4, 4, 4, 4);

	private final RerollPanelState state;
	private Font font;

	public StartStopButtonElement(int x, int y, int width, int height, RerollPanelState state, Runnable onClick) {
		super(Spatials.size(width, height), RerollTokens.START_BUTTON_TEXTURES, onClick);
		this.state = state;
		setDisabled(() -> !state.canStart() && !AutoRerollEngine.getInstance().isRunning());
		tooltip(() -> {
			if (AutoRerollEngine.getInstance().isRunning()) {
				return new net.minecraft.network.chat.TextComponent("Stop the auto-reroll run");
			}
			if (state.canStart()) {
				return new net.minecraft.network.chat.TextComponent("Start auto-rerolling the focused operation");
			}
			return new net.minecraft.network.chat.TextComponent("Select at least one target modifier to enable Start");
		});
	}

	public void setFont(Font font) {
		this.font = font;
	}

	@Override
	public void render(IElementRenderer renderer, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		if (!isVisible()) {
			return;
		}
		TextureAtlasRegion region = textures.selectTexture(isDisabled(), containsMouse(mouseX, mouseY), clickHeld);
		renderer.render(NineSlice.TextureRegion.of(region.atlas(), region.resourceLocation(), SLICES),
				poseStack, worldSpatial);
		if (font == null) {
			return;
		}
		boolean running = AutoRerollEngine.getInstance().isRunning();
		String label = running ? "Stop" : "Start";
		int textWidth = font.width(label);
		int centerX = x() + width() / 2;
		int textY = y() + (height() - 8) / 2;
		int color;
		if (running) {
			color = RerollTokens.STATE_DANGER();
		} else if (state.canStart()) {
			color = RerollTokens.STATE_SUCCESS();
		} else {
			color = RerollTokens.TEXT_DISABLED;
		}
		font.draw(poseStack, label, centerX - textWidth / 2.0f, textY, color);
	}
}
