package io.haque.vault_modifier_alerts.feature.reroll;

import com.mojang.blaze3d.vertex.PoseStack;
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
 * and click-routed natively by the framework's element pipeline. The panel
 * grows when a selection dropdown is open, so the element's own height is
 * re-synced (with a layout pass) whenever that happens. All model, rendering
 * and hit logic lives in {@link RerollPanel}.
 */
public final class RerollPanelElement extends AbstractSpatialElement<RerollPanelElement>
		implements IRenderedElement, IGuiEventElement {

	private static final int MARGIN = 22;

	private final VaultArtisanStationScreen screen;
	private int lastHeight;

	private RerollPanelElement(VaultArtisanStationScreen screen) {
		super(Spatials.size(RerollPanelLayout.WIDTH, RerollPanel.getInstance().currentHeight()));
		this.screen = screen;
		this.lastHeight = height();
	}

	public static RerollPanelElement create(VaultArtisanStationScreen screen) {
		RerollPanelElement element = new RerollPanelElement(screen);
		element.layout((screenSize, gui, parent, world) -> {
			int width = RerollPanelLayout.WIDTH;
			int panelHeight = RerollPanel.getInstance().currentHeight();
			int x = gui.left() - MARGIN - width;
			if (x < 0) {
				x = gui.right() + MARGIN;
			}
			x = Mth.clamp(x, 0, Math.max(0, screenSize.width() - width - MARGIN));
			int y = Mth.clamp(gui.top() + 16, 4, Math.max(4, screenSize.height() - panelHeight - 4));
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
		return RerollPanel.getInstance().isVisible();
	}

	@Override
	public void render(IElementRenderer renderer, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		RerollPanel panel = RerollPanel.getInstance();
		if (!panel.isVisible()) {
			return;
		}
		syncHeight(panel);
		panel.draw(screen, poseStack, x(), y(), width(), height(), mouseX, mouseY);
	}

	@Override
	public boolean onMouseClicked(double mouseX, double mouseY, int button) {
		RerollPanel panel = RerollPanel.getInstance();
		if (!panel.isVisible()) {
			return false;
		}
		syncHeight(panel);
		if (!panel.hitTest(x(), y(), width(), height(), (int) mouseX, (int) mouseY)) {
			return false;
		}
		return panel.handleClick(screen, x(), y(), width(), height(), (int) mouseX, (int) mouseY, button);
	}

	private void syncHeight(RerollPanel panel) {
		int height = panel.currentHeight();
		if (height != lastHeight) {
			lastHeight = height;
			setHeight(height);
			screen.requestLayout();
		}
	}
}