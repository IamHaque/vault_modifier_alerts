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
 * artisan station window (right side fallback when there is no space); when
 * neither side has room the panel shrinks toward
 * {@link RerollPanelLayout#MIN_WIDTH} instead of overlapping at full width.
 *
 * The framework renders elements <em>before</em> slot items, so this element
 * does NOT draw itself - the mixin draws the panel at the TAIL of the screen
 * render, above slot items and tooltips. The element stays registered so the
 * framework keeps routing clicks to {@link RerollPanel#handleClick}, and its
 * height/width re-sync drives the framework layout pass. All model, rendering
 * and hit logic lives in {@link RerollPanel}.
 */
public final class RerollPanelElement extends AbstractSpatialElement<RerollPanelElement>
		implements IRenderedElement, IGuiEventElement {

	private static final int MARGIN = RerollPanelLayout.MARGIN;

	private static RerollPanelElement instance;

	private final VaultArtisanStationScreen screen;
	private int lastHeight;
	private int lastWidth;

	private RerollPanelElement(VaultArtisanStationScreen screen) {
		super(Spatials.size(RerollPanelLayout.WIDTH, RerollPanel.getInstance().currentHeight()));
		this.screen = screen;
		this.lastHeight = height();
		this.lastWidth = width();
	}

	public static RerollPanelElement create(VaultArtisanStationScreen screen) {
		instance = new RerollPanelElement(screen);
		instance.layout((screenSize, gui, parent, world) -> {
			int width = RerollPanel.computeWidth(screenSize.width(), gui.left(), gui.right());
			int panelHeight = RerollPanel.getInstance().currentHeight();
			int x = gui.left() - MARGIN - width;
			if (x < 0) {
				x = gui.right() + MARGIN;
			}
			x = Mth.clamp(x, 0, Math.max(0, screenSize.width() - width - MARGIN));
			int y = Mth.clamp(gui.top() + 16, 4, Math.max(4, screenSize.height() - panelHeight - 4));
			world.positionXY(x, y);
			if (width != instance.width()) {
				instance.setWidth(width);
			}
		});
		return instance;
	}

	/** The element registered on the current station screen (null until one is open). */
	public static RerollPanelElement getInstance() {
		return instance;
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
		syncSize(panel);
	}

	@Override
	public boolean onMouseClicked(double mouseX, double mouseY, int button) {
		RerollPanel panel = RerollPanel.getInstance();
		if (!panel.isVisible()) {
			return false;
		}
		syncSize(panel);
		if (!panel.hitTest(x(), y(), width(), height(), (int) mouseX, (int) mouseY)) {
			return false;
		}
		return panel.handleClick(screen, x(), y(), width(), height(), (int) mouseX, (int) mouseY, button);
	}

	private void syncSize(RerollPanel panel) {
		int height = panel.currentHeight();
		if (height != lastHeight || width() != lastWidth) {
			lastHeight = height;
			lastWidth = width();
			setHeight(height);
			screen.requestLayout();
		}
	}
}
