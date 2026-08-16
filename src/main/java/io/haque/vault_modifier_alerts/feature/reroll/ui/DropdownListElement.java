package io.haque.vault_modifier_alerts.feature.reroll.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanel;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelState;
import iskallia.vault.client.gui.framework.element.VerticalScrollClipContainer;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.screen.layout.ScreenLayout;
import iskallia.vault.client.gui.framework.spatial.Padding;
import iskallia.vault.client.gui.framework.spatial.Spatials;
import iskallia.vault.client.gui.screen.block.VaultArtisanStationScreen;
import iskallia.vault.gear.modification.GearModificationAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Dropdown list element for the auto-reroll panel, backed by the host's
 * {@link VerticalScrollClipContainer}: the background, the clip region, the
 * elastic inner container and the scrollbar are all framework widgets, and
 * every visible item is a real per-row element
 * ({@link DropdownItemRowElement}). Replaces the hand-rolled item loop,
 * scroll triangles and scroll math that lived here before.
 */
public class DropdownListElement extends VerticalScrollClipContainer<DropdownListElement> {

	private final VaultArtisanStationScreen screen;
	private final List<DropdownItemRowElement> rows = new ArrayList<>();
	private RerollPanelState.DropdownMode lastMode;

	private DropdownListElement(VaultArtisanStationScreen screen) {
		super(Spatials.zero(), Padding.of(0, 0, RerollPanelLayout.DROPDOWN_HEADER_H, 0));
		this.screen = screen;
	}

	public static DropdownListElement create(VaultArtisanStationScreen screen) {
		DropdownListElement element = new DropdownListElement(screen);
		element.layout((screenSize, gui, parent, world) -> {
			if (!element.isVisible()) {
				world.positionXY(0, 0);
				world.width(0);
				world.height(0);
				return;
			}
			RerollPanelLayout layout = RerollPanel.getInstance().computeLayout(parent.x(), parent.y(), parent.width());
			world.positionXY(layout.x, layout.dropdownY);
			world.width(layout.width);
			world.height(layout.dropdownHeight);
		});
		return element;
	}

	@Override
	public void setVisible(boolean visible) {
	}

	@Override
	public boolean isVisible() {
		RerollPanelState state = RerollPanelState.getInstance();
		return RerollPanel.getInstance().isVisible() && state.isDropdownOpen();
	}

	@Override
	public void render(IElementRenderer renderer, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		if (!isVisible()) {
			lastMode = null;
			return;
		}
		RerollPanelState state = RerollPanelState.getInstance();
		if (state.dropdownMode() != lastMode) {
			lastMode = state.dropdownMode();
			verticalScrollBarElement.setValue(0f);
		}
		refreshRowsIfNeeded();
		verticalScrollBarElement.setVisible(verticalScrollBarElement.isEnabled());
		super.render(renderer, poseStack, mouseX, mouseY, partialTick);

		Font font = Minecraft.getInstance().font;
		String header = state.dropdownMode() == RerollPanelState.DropdownMode.OPERATION
				? "Operations"
				: (state.dropdownMode() == RerollPanelState.DropdownMode.TARGETS ? "Targets" : "Modifiers");
		font.draw(poseStack, header, x() + width() / 2f - font.width(header) / 2f, y() + 3, RerollTokens.ACCENT_GOLD());
		GuiComponent.fill(poseStack, x(), y(), x() + width(), y() + 1, RerollTokens.ACCENT_GOLD());
	}

	@Override
	public boolean onMouseClicked(double mouseX, double mouseY, int button) {
		if (!isVisible()) {
			return false;
		}
		return super.onMouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean onMouseScrolled(double mouseX, double mouseY, double delta) {
		if (!isVisible()) {
			return false;
		}
		return super.onMouseScrolled(mouseX, mouseY, delta);
	}

	/**
	 * Keyboard scrolling (Up/Down arrows): steps one row per keypress by
	 * re-deriving the scrollbar's normalized value from the current item count
	 * and the number of rows that fit the open dropdown.
	 */
	public void scrollDropdownBy(int delta) {
		if (!isVisible()) {
			return;
		}
		int count = itemCount();
		int visible = (height() - RerollPanelLayout.DROPDOWN_HEADER_H) / RerollPanelLayout.DROPDOWN_ITEM_H;
		int range = count - visible;
		if (range <= 0) {
			return;
		}
		float value = Mth.clamp(verticalScrollBarElement.getValue() + delta / (float) range, 0f, 1f);
		verticalScrollBarElement.setValue(value);
		ScreenLayout.requestLayout();
	}

	// --------------------------------------------------------------- row
	// management

	private void refreshRowsIfNeeded() {
		int count = itemCount();
		if (count == rows.size()) {
			return;
		}
		for (DropdownItemRowElement row : rows) {
			removeElement(row);
		}
		rows.clear();
		for (int index = 0; index < count; index++) {
			final int rowIndex = index;
			DropdownItemRowElement row = new DropdownItemRowElement(this, screen, rowIndex);
			row.layout((screenSize, gui, parent, world) -> {
				world.positionXY(parent.x(), parent.y() + rowIndex * RerollPanelLayout.DROPDOWN_ITEM_H);
				world.width(innerWidth());
				world.height(RerollPanelLayout.DROPDOWN_ITEM_H);
			});
			addElement(row);
			rows.add(row);
		}
		ScreenLayout.requestLayout();
	}

	// --------------------------------------------------------------- item data
	// (queried live by the row elements each frame)

	int itemCount() {
		RerollPanelState state = RerollPanelState.getInstance();
		RerollPanel panel = RerollPanel.getInstance();
		if (state.dropdownMode() == RerollPanelState.DropdownMode.OPERATION) {
			return panel.operations(screen).size();
		} else if (state.dropdownMode() == RerollPanelState.DropdownMode.TARGETS) {
			return state.targets().size();
		}
		List<GearModificationAction> operations = panel.operations(screen);
		ItemStack gear = RerollPanel.stationGear();
		if (operations.isEmpty() || gear.isEmpty()) {
			return 0;
		}
		int safeIndex = Math.min(state.operationIndex(), operations.size() - 1);
		return panel.candidates(gear, operations.get(safeIndex)).size();
	}

	String itemName(int index) {
		RerollPanelState state = RerollPanelState.getInstance();
		RerollPanel panel = RerollPanel.getInstance();
		if (state.dropdownMode() == RerollPanelState.DropdownMode.OPERATION) {
			return panel.displayOperationName(panel.operations(screen).get(index));
		} else if (state.dropdownMode() == RerollPanelState.DropdownMode.TARGETS) {
			return panel.targetName(state.targets().get(index).id());
		}
		List<GearModificationAction> operations = panel.operations(screen);
		ItemStack gear = RerollPanel.stationGear();
		int safeIndex = Math.min(state.operationIndex(), operations.size() - 1);
		List<ModifierCatalog.Candidate> cands = panel.candidates(gear, operations.get(safeIndex));
		return cands.get(index).displayName();
	}

	String itemRange(int index) {
		RerollPanelState state = RerollPanelState.getInstance();
		RerollPanel panel = RerollPanel.getInstance();
		boolean targetDropdown = state.dropdownMode() == RerollPanelState.DropdownMode.TARGETS;

		if (targetDropdown) {
			var target = state.targets().get(index);
			return target.thresholdEnabled()
					? "min " + RerollPanel.formatDisplay(target.thresholdValue(), false)
					: "any";
		}
		if (state.dropdownMode() == RerollPanelState.DropdownMode.OPERATION) {
			int cost = RerollPanel.potentialCost(RerollPanel.stationGear(), panel.operations(screen).get(index));
			return cost > 0 ? cost + " potential" : "";
		}
		List<GearModificationAction> operations = panel.operations(screen);
		ItemStack gear = RerollPanel.stationGear();
		int safeIndex = Math.min(state.operationIndex(), operations.size() - 1);
		List<ModifierCatalog.Candidate> cands = panel.candidates(gear, operations.get(safeIndex));
		if (index < cands.size()) {
			return panel.rollRangeOf(cands.get(index)).displayText();
		}
		return "";
	}

	boolean isWatchedItem(int index) {
		RerollPanelState state = RerollPanelState.getInstance();
		RerollPanel panel = RerollPanel.getInstance();
		if (state.dropdownMode() != RerollPanelState.DropdownMode.MODIFIER) {
			return false;
		}
		List<GearModificationAction> operations = panel.operations(screen);
		ItemStack gear = RerollPanel.stationGear();
		int safeIndex = Math.min(state.operationIndex(), operations.size() - 1);
		List<ModifierCatalog.Candidate> cands = panel.candidates(gear, operations.get(safeIndex));
		return index < cands.size() && panel.isWatched(cands.get(index).id());
	}
}