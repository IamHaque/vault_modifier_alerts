package io.haque.vault_modifier_alerts.feature.reroll;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.reroll.ui.CounterRowElement;
import io.haque.vault_modifier_alerts.feature.reroll.ui.DropdownListElement;
import io.haque.vault_modifier_alerts.feature.reroll.ui.DropdownRowElement;
import io.haque.vault_modifier_alerts.feature.reroll.ui.StepperButtonElement;
import io.haque.vault_modifier_alerts.feature.reroll.ui.StartStopButtonElement;
import io.haque.vault_modifier_alerts.feature.reroll.ui.StatusRowElement;
import io.haque.vault_modifier_alerts.feature.reroll.ui.ToggleRowElement;
import iskallia.vault.client.gui.framework.element.ContainerElement;
import iskallia.vault.client.gui.framework.element.spi.AbstractSpatialElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.spatial.Spatials;
import iskallia.vault.client.gui.screen.block.VaultArtisanStationScreen;
import net.minecraft.util.Mth;

/**
 * The auto-reroll panel as a real VH framework element container. Anchored
 * left of the artisan station window (right side fallback when there is no
 * space); when neither side has room the panel shrinks toward
 * {@link RerollPanelLayout#MIN_WIDTH} instead of overlapping at full width.
 *
 * <p>This element extends {@link ContainerElement} so child elements (row
 * widgets, buttons, labels) can be added incrementally. The panel frame and
 * unconverted rows are still drawn by {@link RerollPanel#draw}; converted rows
 * are rendered as real framework children by the container's own render pass.</p>
 *
 * <p>The mixin re-draws the panel just before the screen's tooltip pass, above
 * slot items and tooltips. The second pass paints the same pixels, so the double
 * draw is invisible; it only matters when slot items overlap the panel.</p>
 */
public final class RerollPanelElement extends ContainerElement<RerollPanelElement> {

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
			String side = VmaClientConfigs.rerollPanelSide();
			int x;
			if ("LEFT".equals(side)) {
				x = gui.left() - MARGIN - width;
			} else if ("RIGHT".equals(side)) {
				x = gui.right() + MARGIN;
			} else {
				// AUTO: left-first, fallback right
				x = gui.left() - MARGIN - width;
				if (x < 0) {
					x = gui.right() + MARGIN;
				}
			}
			x = Mth.clamp(x, 0, Math.max(0, screenSize.width() - width - MARGIN));
			int y = Mth.clamp(gui.top() + 16, 4, Math.max(4, screenSize.height() - panelHeight - 4));
			world.positionXY(x, y);
			if (width != instance.width()) {
				instance.setWidth(width);
			}
		});

		// Min row steppers (row 4) — the field itself stays state-driven
		// (framework does not route keyboard input to owned elements)
		int minY = RerollPanelLayout.TITLE_H + 3 * RerollPanelLayout.ROW_H;
		RerollPanel panel = RerollPanel.getInstance();
		RerollPanelState minState = RerollPanelState.getInstance();
		StepperButtonElement decButton = new StepperButtonElement(
				0, minY, 12, 12, "-",
				() -> minState.stepMin(-minState.currentStep()));
		decButton.layout((screenSize, gui, parent, world) -> {
			world.positionXY(parent.x() + 2, parent.y() + minY + 2);
		});
		decButton.setDisabled(() -> !VmaClientConfigs.isAutoRerollEnabled()
				|| !panel.currentTargetRange().numeric());
		instance.addElement(decButton);

		StepperButtonElement incButton = new StepperButtonElement(
				0, minY, 12, 12, "+",
				() -> minState.stepMin(minState.currentStep()));
		incButton.layout((screenSize, gui, parent, world) -> {
			world.positionXY(parent.x() + parent.width() - 14, parent.y() + minY + 2);
		});
		incButton.setDisabled(() -> !VmaClientConfigs.isAutoRerollEnabled()
				|| !panel.currentTargetRange().numeric());
		instance.addElement(incButton);

		// Dropdown rows (rows 1-3: Focus, Modifier, Targets)
		DropdownRowElement focusRow = new DropdownRowElement(
				0, RerollPanelLayout.TITLE_H, RerollPanelLayout.WIDTH, screen,
				RerollPanelState.DropdownMode.OPERATION);
		anchorRow(focusRow, RerollPanelLayout.TITLE_H);
		instance.addElement(focusRow);

		DropdownRowElement modifierRow = new DropdownRowElement(
				0, RerollPanelLayout.TITLE_H + RerollPanelLayout.ROW_H, RerollPanelLayout.WIDTH, screen,
				RerollPanelState.DropdownMode.MODIFIER);
		anchorRow(modifierRow, RerollPanelLayout.TITLE_H + RerollPanelLayout.ROW_H);
		instance.addElement(modifierRow);

		DropdownRowElement targetsRow = new DropdownRowElement(
				0, RerollPanelLayout.TITLE_H + 2 * RerollPanelLayout.ROW_H, RerollPanelLayout.WIDTH, screen,
				RerollPanelState.DropdownMode.TARGETS);
		anchorRow(targetsRow, RerollPanelLayout.TITLE_H + 2 * RerollPanelLayout.ROW_H);
		instance.addElement(targetsRow);

		// Auto-reroll toggle (row 8, 0-indexed: title + 6 rows above)
		int rerollToggleY = RerollPanelLayout.TITLE_H + 6 * RerollPanelLayout.ROW_H;
		ToggleRowElement rerollToggle = new ToggleRowElement(
				0, rerollToggleY, RerollPanelLayout.WIDTH,
				"Auto-reroll",
				VmaClientConfigs::isAutoRerollEnabled,
				() -> {
					RerollPanelState.getInstance().loseMinFocus();
					RerollPanelState.getInstance().closeDropdown();
					boolean enabled = !VmaClientConfigs.isAutoRerollEnabled();
					VmaClientConfigs.setAutoRerollEnabled(enabled);
					if (!enabled) {
						AutoRerollEngine.getInstance().cancelResume();
						if (AutoRerollEngine.getInstance().isRunning()) {
							AutoRerollEngine.getInstance().stop(AutoRerollEngine.StopReason.STOPPED, false);
						}
					}
				});
		anchorRow(rerollToggle, rerollToggleY);
		instance.addElement(rerollToggle);

		// Auto-reset toggle (row 9)
		int resetToggleY = RerollPanelLayout.TITLE_H + 7 * RerollPanelLayout.ROW_H;
		ToggleRowElement resetToggle = new ToggleRowElement(
				0, resetToggleY, RerollPanelLayout.WIDTH,
				"Auto-reset potential",
				VmaClientConfigs::isAutoResetPotentialEnabled,
				() -> {
					RerollPanelState.getInstance().loseMinFocus();
					VmaClientConfigs.setAutoResetPotential(!VmaClientConfigs.isAutoResetPotentialEnabled());
				});
		anchorRow(resetToggle, resetToggleY);
		instance.addElement(resetToggle);

		// Start/stop button (row 10)
		int buttonY = RerollPanelLayout.TITLE_H + 8 * RerollPanelLayout.ROW_H;
		RerollPanelState state = RerollPanelState.getInstance();
		StartStopButtonElement startButton = new StartStopButtonElement(
				0, buttonY, RerollPanelLayout.WIDTH, RerollPanelLayout.BUTTON_H,
				state,
				() -> {
					state.loseMinFocus();
					AutoRerollEngine engine = AutoRerollEngine.getInstance();
					if (engine.isRunning()) {
						engine.stop(AutoRerollEngine.StopReason.STOPPED, false);
					} else if (state.canStart()) {
						RerollPanel.RerollSelection selection = RerollPanel.getInstance().currentSelection();
						if (selection != null) {
							engine.start(selection.operationId(), selection.targets(), selection.stopCondition());
						}
					}
				});
		startButton.setFont(net.minecraft.client.Minecraft.getInstance().font);
		startButton.layout((screenSize, gui, parent, world) -> {
			world.positionXY(parent.x(), parent.y() + buttonY);
			world.width(parent.width());
		});
		instance.addElement(startButton);

		// Status row (row 11)
		int statusY = buttonY + RerollPanelLayout.BUTTON_H;
		StatusRowElement statusRow = new StatusRowElement(0, statusY, RerollPanelLayout.WIDTH);
		statusRow.setFont(net.minecraft.client.Minecraft.getInstance().font);
		anchorRow(statusRow, statusY);
		instance.addElement(statusRow);

		// Counter row (row 12)
		int counterY = statusY + RerollPanelLayout.ROW_H;
		CounterRowElement counterRow = new CounterRowElement(0, counterY, RerollPanelLayout.WIDTH);
		counterRow.setFont(net.minecraft.client.Minecraft.getInstance().font);
		anchorRow(counterRow, counterY);
		instance.addElement(counterRow);

		// Dropdown list
		DropdownListElement dropdown = DropdownListElement.create(screen);
		instance.addElement(dropdown);

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
		panel.draw(screen, poseStack, x(), y(), width(), height(), mouseX, mouseY);
		super.render(renderer, poseStack, mouseX, mouseY, partialTick);
		syncSize(panel);
	}

	@Override
	public boolean onMouseClicked(double mouseX, double mouseY, int button) {
		RerollPanel panel = RerollPanel.getInstance();
		if (!panel.isVisible()) {
			return false;
		}
		syncSize(panel);
		if (super.onMouseClicked(mouseX, mouseY, button)) {
			return true;
		}
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

	/**
	 * Anchors a full-width child row to the panel's own resolved position at
	 * the given local (panel-relative) Y offset. All simple full-width rows
	 * (toggles, buttons, labels) must use this instead of calling
	 * {@code world.positionXY(...)} directly: the framework only pre-translates
	 * the world spatial by the parent, and the layout lambda then overwrites it,
	 * so rows must fold {@code parent.x()/parent.y()} in themselves or they
	 * render pinned to the top-left of the screen.
	 */
	private static void anchorRow(AbstractSpatialElement<?> child, int localY) {
		child.layout((screenSize, gui, parent, world) -> {
			world.positionXY(parent.x(), parent.y() + localY);
			world.width(parent.width());
		});
	}
}
