package io.haque.vault_modifier_alerts.feature.reroll;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import iskallia.vault.client.gui.framework.element.spi.AbstractSpatialElement;
import iskallia.vault.client.gui.framework.element.spi.IGuiEventElement;
import iskallia.vault.client.gui.framework.element.spi.IRenderedElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.spatial.Spatials;
import iskallia.vault.client.gui.screen.block.VaultArtisanStationScreen;
import net.minecraft.util.Mth;

/**
 * The auto-reroll panel as a real VH framework element. Anchored left of the
 * artisan station window (right side fallback when there is no space), drawn
 * and click-routed natively by the framework's element pipeline. The model
 * (state, rendering, hit logic) lives in {@link RerollPanel}.
 */
public final class RerollPanelElement extends AbstractSpatialElement<RerollPanelElement>
		implements IRenderedElement, IGuiEventElement {

	private static final int MARGIN = 22;

	private final VaultArtisanStationScreen screen;

	private RerollPanelElement(VaultArtisanStationScreen screen) {
		super(Spatials.size(RerollPanel.PANEL_WIDTH, RerollPanel.PANEL_HEIGHT));
		this.screen = screen;
	}

	public static RerollPanelElement create(VaultArtisanStationScreen screen) {
		RerollPanelElement element = new RerollPanelElement(screen);
		element.layout((screenSize, gui, parent, world) -> {
			int x = gui.left() - MARGIN - RerollPanel.PANEL_WIDTH;
			if (x < 0) {
				x = gui.right() + MARGIN;
			}
			x = Mth.clamp(x, 0, Math.max(0, screenSize.width() - RerollPanel.PANEL_WIDTH - MARGIN));
			int y = Mth.clamp(gui.top() + 16, 4, Math.max(4, screenSize.height() - RerollPanel.PANEL_HEIGHT - 4));
			world.positionXY(x, y);
		});
		return element;
	}

	@Override
	public void setVisible(boolean visible) {
		RerollPanel.getInstance().setVisible(visible);
	}

	@Override
	public boolean isVisible() {
		return RerollPanel.getInstance().isVisible() && VmaClientConfigs.isAutoRerollEnabled();
	}

	@Override
	public void render(IElementRenderer renderer, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		RerollPanel panel = RerollPanel.getInstance();
		if (!panel.isVisible() || !VmaClientConfigs.isAutoRerollEnabled()) {
			return;
		}
		panel.draw(screen, poseStack, x(), y(), width(), height(), mouseX, mouseY);
	}

	@Override
	public boolean onMouseClicked(double mouseX, double mouseY, int button) {
		RerollPanel panel = RerollPanel.getInstance();
		if (!panel.isVisible() || !VmaClientConfigs.isAutoRerollEnabled()) {
			return false;
		}
		if (!panel.hitTest(x(), y(), width(), height(), (int) mouseX, (int) mouseY)) {
			return false;
		}
		return panel.handleClick(screen, x(), y(), width(), height(), (int) mouseX, (int) mouseY, button);
	}
}