package io.haque.vault_modifier_alerts.feature.reroll.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.reroll.AutoRerollEngine;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanel;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelState;
import iskallia.vault.client.gui.framework.element.ContainerElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.spatial.Spatials;
import iskallia.vault.client.gui.screen.block.VaultArtisanStationScreen;
import iskallia.vault.gear.modification.GearModificationAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Dropdown list element for the auto-reroll panel. Renders header with
 * scroll triangles and a scrollable list of items. Replaces the hand-drawn
 * {@code drawDropdown()} in {@link RerollPanel}.
 */
public class DropdownListElement extends ContainerElement<DropdownListElement> {

	private VaultArtisanStationScreen screen;

	private DropdownListElement(VaultArtisanStationScreen screen) {
		super(Spatials.size(0, 0));
		this.screen = screen;
	}

	public static DropdownListElement create(VaultArtisanStationScreen screen) {
		DropdownListElement element = new DropdownListElement(screen);
		element.layout((screenSize, gui, parent, world) -> {
			RerollPanel panel = RerollPanel.getInstance();
			if (!panel.isVisible()) {
				return;
			}
			RerollPanelState state = RerollPanelState.getInstance();
			if (!state.isDropdownOpen()) {
				world.positionXY(0, 0);
				world.width(0);
				world.height(0);
				return;
			}
			RerollPanelLayout layout = panel.computeLayout(
					parent.x(), parent.y(), parent.width());
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
		RerollPanel panel = RerollPanel.getInstance();
		if (!panel.isVisible()) {
			return;
		}
		RerollPanelState state = RerollPanelState.getInstance();
		if (!state.isDropdownOpen()) {
			return;
		}
		RerollPanelLayout layout = panel.computeLayout(
				this.x(), this.y() - RerollPanelLayout.TITLE_H, this.width());

		Font font = Minecraft.getInstance().font;

		GuiComponent.fill(poseStack, x(), y(), x() + width(), y() + height(), RerollTokens.DROPDOWN_BG);
		GuiComponent.fill(poseStack, x(), y(), x() + width(), y() + 1, RerollTokens.ACCENT_GOLD());

		boolean operationDropdown = state.dropdownMode() == RerollPanelState.DropdownMode.OPERATION;
		boolean targetDropdown = state.dropdownMode() == RerollPanelState.DropdownMode.TARGETS;
		String header = operationDropdown ? "Operations" : (targetDropdown ? "Targets" : "Modifiers");
		int headerWidth = font.width(header);
		font.draw(poseStack, header, x() + width() / 2 - headerWidth / 2, y() + 3, RerollTokens.ACCENT_GOLD());

		int count = getItemCount();
		int visible = layout.dropdownVisibleItems;
		boolean scrollable = count > visible;
		boolean canScrollUp = scrollable && state.dropdownScroll() > 0;
		boolean canScrollDown = scrollable && state.dropdownScroll() < count - visible;
		drawTriangle(poseStack, x() + 7, y() + 6, true,
				canScrollUp ? RerollTokens.TEXT_DEFAULT() : RerollTokens.TEXT_DISABLED);
		drawTriangle(poseStack, x() + width() - 7, y() + 6, false,
				canScrollDown ? RerollTokens.TEXT_DEFAULT() : RerollTokens.TEXT_DISABLED);

		for (int slot = 0; slot < visible; slot++) {
			int index = state.dropdownScroll() + slot;
			if (index >= count) {
				break;
			}
			int itemY = y() + RerollPanelLayout.DROPDOWN_HEADER_H + slot * RerollPanelLayout.DROPDOWN_ITEM_H;
			renderItem(poseStack, index, itemY, mouseX, mouseY);
		}
	}

	private void renderItem(PoseStack poseStack, int index, int itemY, int mouseX, int mouseY) {
		RerollPanel panel = RerollPanel.getInstance();
		RerollPanelState state = RerollPanelState.getInstance();
		boolean operationDropdown = state.dropdownMode() == RerollPanelState.DropdownMode.OPERATION;
		boolean targetDropdown = state.dropdownMode() == RerollPanelState.DropdownMode.TARGETS;
		Font font = Minecraft.getInstance().font;

		String name = getItemName(index);
		String range = getItemRange(index);
		boolean current = operationDropdown ? index == state.operationIndex()
				: (targetDropdown ? index == state.focusedTarget() : false);
		boolean hovered = mouseX >= x() && mouseX < x() + width()
				&& mouseY >= itemY && mouseY < itemY + RerollPanelLayout.DROPDOWN_ITEM_H;
		boolean removeZone = targetDropdown && mouseX >= x() + width() - 16 && hovered;

		if (hovered) {
			GuiComponent.fill(poseStack, x(), itemY, x() + width(), itemY + RerollPanelLayout.DROPDOWN_ITEM_H,
					removeZone ? RerollTokens.DROPDOWN_REMOVE_HOVER : RerollTokens.ROW_HOVER);
		}

		int baseColor = current ? RerollTokens.ACCENT_GOLD()
				: (VmaClientConfigs.isAutoRerollEnabled() ? RerollTokens.TEXT_DEFAULT() : RerollTokens.TEXT_DISABLED);

		if (current || (!targetDropdown && !operationDropdown && isWatchedItem(index))) {
			font.draw(poseStack, targetDropdown || operationDropdown ? ">" : "*",
					x() + 2, itemY + 3, RerollTokens.ACCENT_GOLD());
		}

		if (targetDropdown && AutoRerollEngine.getInstance().isRunning() && index < state.targets().size()) {
			boolean met = AutoRerollEngine.getInstance().isMet(index);
			font.draw(poseStack, met ? "[x]" : "[ ]", x() + 2, itemY + 3,
					met ? RerollTokens.STATE_SUCCESS() : RerollTokens.STATE_DANGER());
		}

		int rangeWidth = range.isEmpty() ? 0 : font.width(range);
		int nameMax = x() + width() - RerollPanelLayout.PAD_X - rangeWidth - 8;
		String fullName = name;
		String shownName = RerollPanel.truncate(name, Math.max(8, (nameMax - x()) / 7));
		if (!shownName.equals(fullName) && hovered) {
			panel.hoverTooltip(fullName, mouseX, mouseY);
		}
		font.draw(poseStack, shownName, x() + 11, itemY + 3, baseColor);

		if (!range.isEmpty() && !removeZone) {
			int rangeX = x() + width() - RerollPanelLayout.PAD_X;
			font.draw(poseStack, range, rangeX - font.width(range), itemY + 3, RerollTokens.TEXT_MUTED);
		}

		if (removeZone) {
			font.draw(poseStack, "x", x() + width() - 12, itemY + 3, RerollTokens.STATE_DANGER());
		}
	}

	@Override
	public boolean onMouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !isVisible()) {
			return false;
		}
		RerollPanel panel = RerollPanel.getInstance();
		RerollPanelState state = RerollPanelState.getInstance();

		if (mouseY < y() + RerollPanelLayout.DROPDOWN_HEADER_H) {
			if (mouseX < x() + 14) {
				state.scrollDropdown(-1);
				return true;
			}
			if (mouseX >= x() + width() - 14) {
				state.scrollDropdown(1);
				return true;
			}
			return false;
		}

		RerollPanelLayout layout = panel.computeLayout(
				this.x(), this.y() - RerollPanelLayout.TITLE_H, this.width());
		int slot = (int) (mouseY - y() - RerollPanelLayout.DROPDOWN_HEADER_H) / RerollPanelLayout.DROPDOWN_ITEM_H;
		int count = getItemCount();
		int index = state.dropdownScroll() + slot;
		if (slot < 0 || slot >= layout.dropdownVisibleItems || index >= count) {
			return false;
		}

		boolean targetDropdown = state.dropdownMode() == RerollPanelState.DropdownMode.TARGETS;
		boolean inRemoveZone = targetDropdown && mouseX >= x() + width() - 16;

		if (targetDropdown) {
			if (inRemoveZone) {
				state.removeTarget(index);
			} else {
				state.focusTarget(index);
			}
		} else if (state.dropdownMode() == RerollPanelState.DropdownMode.OPERATION) {
			List<GearModificationAction> operations = panel.operations(screen);
			if (index >= 0 && index < operations.size()) {
				state.selectOperation(index);
				state.closeDropdown();
			}
		} else if (state.dropdownMode() == RerollPanelState.DropdownMode.MODIFIER) {
			ItemStack gear = RerollPanel.stationGear();
			List<GearModificationAction> operations = panel.operations(screen);
			int safeIndex = Math.min(state.operationIndex(), operations.size() - 1);
			List<ModifierCatalog.Candidate> cands = panel.candidates(gear, operations.get(safeIndex));
			if (index >= 0 && index < cands.size()) {
				state.toggleTarget(cands.get(index).id());
				state.closeDropdown();
			}
		}
		return true;
	}

	@Override
	public boolean onMouseScrolled(double mouseX, double mouseY, double delta) {
		if (!isVisible()) {
			return false;
		}
		if (mouseX >= x() && mouseX < x() + width() && mouseY >= y() && mouseY < y() + height()) {
			RerollPanelState state = RerollPanelState.getInstance();
			if (delta > 0) {
				state.scrollDropdown(-1);
			} else if (delta < 0) {
				state.scrollDropdown(1);
			}
			return true;
		}
		return false;
	}

	private int getItemCount() {
		RerollPanelState state = RerollPanelState.getInstance();
		RerollPanel panel = RerollPanel.getInstance();
		if (state.dropdownMode() == RerollPanelState.DropdownMode.OPERATION) {
			return panel.operations(screen).size();
		} else if (state.dropdownMode() == RerollPanelState.DropdownMode.TARGETS) {
			return state.targets().size();
		} else {
			List<GearModificationAction> operations = panel.operations(screen);
			ItemStack gear = RerollPanel.stationGear();
			if (operations.isEmpty() || gear.isEmpty()) {
				return 0;
			}
			int safeIndex = Math.min(state.operationIndex(), operations.size() - 1);
			return panel.candidates(gear, operations.get(safeIndex)).size();
		}
	}

	private String getItemName(int index) {
		RerollPanelState state = RerollPanelState.getInstance();
		RerollPanel panel = RerollPanel.getInstance();
		if (state.dropdownMode() == RerollPanelState.DropdownMode.OPERATION) {
			return panel.displayOperationName(panel.operations(screen).get(index));
		} else if (state.dropdownMode() == RerollPanelState.DropdownMode.TARGETS) {
			return panel.targetName(state.targets().get(index).id());
		} else {
			List<GearModificationAction> operations = panel.operations(screen);
			ItemStack gear = RerollPanel.stationGear();
			int safeIndex = Math.min(state.operationIndex(), operations.size() - 1);
			List<ModifierCatalog.Candidate> cands = panel.candidates(gear, operations.get(safeIndex));
			return cands.get(index).displayName();
		}
	}

	private String getItemRange(int index) {
		RerollPanelState state = RerollPanelState.getInstance();
		RerollPanel panel = RerollPanel.getInstance();
		boolean operationDropdown = state.dropdownMode() == RerollPanelState.DropdownMode.OPERATION;
		boolean targetDropdown = state.dropdownMode() == RerollPanelState.DropdownMode.TARGETS;

		if (targetDropdown) {
			var target = state.targets().get(index);
			return target.thresholdEnabled()
					? "min " + RerollPanel.formatDisplay(target.thresholdValue(), false)
					: "any";
		}
		if (operationDropdown) {
			int cost = panel.potentialCost(RerollPanel.stationGear(), panel.operations(screen).get(index));
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

	private boolean isWatchedItem(int index) {
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

	private static void drawTriangle(PoseStack poseStack, int centerX, int topY, boolean up, int color) {
		for (int i = 0; i < 3; i++) {
			int rowY = up ? topY + (2 - i) : topY + i;
			GuiComponent.fill(poseStack, centerX - i, rowY, centerX + i + 1, rowY + 1, color);
		}
	}
}
