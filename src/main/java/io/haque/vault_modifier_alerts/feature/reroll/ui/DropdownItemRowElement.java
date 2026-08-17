package io.haque.vault_modifier_alerts.feature.reroll.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.reroll.AutoRerollEngine;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanel;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelState;
import iskallia.vault.client.gui.framework.element.spi.AbstractSpatialElement;
import iskallia.vault.client.gui.framework.element.spi.IGuiEventElement;
import iskallia.vault.client.gui.framework.element.spi.IRenderedElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.render.spi.ITooltipRenderer;
import iskallia.vault.client.gui.framework.spatial.Spatials;
import iskallia.vault.client.gui.screen.block.VaultArtisanStationScreen;
import iskallia.vault.gear.modification.GearModificationAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * One item row inside the dropdown's clipped list. Data (name, range, markers,
 * current/watched state) is queried live from {@link RerollPanelState} and
 * {@link RerollPanel} every frame, so rows stay correct as targets change or
 * the engine runs without being rebuilt.
 */
final class DropdownItemRowElement extends AbstractSpatialElement<DropdownItemRowElement>
		implements IRenderedElement, IGuiEventElement {

	private final DropdownListElement dropdown;
	private final VaultArtisanStationScreen screen;
	private final int index;

	DropdownItemRowElement(DropdownListElement dropdown, VaultArtisanStationScreen screen, int index) {
		super(Spatials.zero());
		this.dropdown = dropdown;
		this.screen = screen;
		this.index = index;
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
		RerollPanelState state = RerollPanelState.getInstance();
		boolean operationDropdown = state.dropdownMode() == RerollPanelState.DropdownMode.OPERATION;
		boolean targetDropdown = state.dropdownMode() == RerollPanelState.DropdownMode.TARGETS;
		Font font = Minecraft.getInstance().font;

		String name = dropdown.itemName(index);
		String range = dropdown.itemRange(index);
		boolean current = operationDropdown ? index == state.operationIndex()
				: (targetDropdown ? index == state.focusedTarget() : false);
		boolean hovered = containsMouse(mouseX, mouseY);
		boolean removeZone = targetDropdown && hovered && mouseX >= x() + width() - 16;

		if (hovered) {
			GuiComponent.fill(poseStack, x(), y(), x() + width(), y() + height(),
					removeZone ? RerollTokens.DROPDOWN_REMOVE_HOVER : RerollTokens.ROW_HOVER());
		}

		int baseColor = current ? RerollTokens.ACCENT_GOLD()
				: (VmaClientConfigs.isAutoRerollEnabled() ? RerollTokens.TEXT_DEFAULT() : RerollTokens.TEXT_DISABLED());

		if (current || (!operationDropdown && !targetDropdown && dropdown.isWatchedItem(index))) {
			font.draw(poseStack, operationDropdown || targetDropdown ? ">" : "*",
					x() + 2, y() + RerollPanelLayout.TEXT_BASELINE_OFFSET, RerollTokens.ACCENT_GOLD());
		}

		if (targetDropdown && AutoRerollEngine.getInstance().isRunning() && index < state.targets().size()) {
			boolean met = AutoRerollEngine.getInstance().isMet(index);
			font.draw(poseStack, met ? "[x]" : "[ ]", x() + 2, y() + RerollPanelLayout.TEXT_BASELINE_OFFSET,
					met ? RerollTokens.STATE_SUCCESS() : RerollTokens.STATE_DANGER());
		}

		int rangeWidth = range.isEmpty() ? 0 : font.width(range);
		int nameMax = x() + width() - RerollPanelLayout.PAD_X - rangeWidth - 8;
		String shownName = RerollPanel.truncate(name, Math.max(8, (nameMax - x()) / 7));
		font.draw(poseStack, shownName, x() + 11, y() + RerollPanelLayout.TEXT_BASELINE_OFFSET, baseColor);

		if (!range.isEmpty() && !removeZone) {
			int rangeX = x() + width() - RerollPanelLayout.PAD_X;
			font.draw(poseStack, range, rangeX - font.width(range), y() + RerollPanelLayout.TEXT_BASELINE_OFFSET, RerollTokens.TEXT_MUTED());
		}

		if (removeZone) {
			font.draw(poseStack, "x", x() + width() - 12, y() + RerollPanelLayout.TEXT_BASELINE_OFFSET, RerollTokens.STATE_DANGER());
		}
	}

	@Override
	public boolean onMouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !dropdown.isVisible()) {
			return false;
		}
		RerollPanelState state = RerollPanelState.getInstance();
		boolean targetDropdown = state.dropdownMode() == RerollPanelState.DropdownMode.TARGETS;
		boolean inRemoveZone = targetDropdown && mouseX >= x() + width() - 16;

		if (targetDropdown) {
			if (inRemoveZone) {
				state.removeTarget(index);
			} else {
				state.focusTarget(index);
			}
		} else if (state.dropdownMode() == RerollPanelState.DropdownMode.OPERATION) {
			List<GearModificationAction> operations = RerollPanel.getInstance().operations(screen);
			if (index >= 0 && index < operations.size()) {
				state.selectOperation(index);
				state.closeDropdown();
			}
		} else if (state.dropdownMode() == RerollPanelState.DropdownMode.MODIFIER) {
			ItemStack gear = RerollPanel.stationGear();
			List<GearModificationAction> operations = RerollPanel.getInstance().operations(screen);
			int safeIndex = Math.min(state.operationIndex(), operations.size() - 1);
			List<ModifierCatalog.Candidate> cands = RerollPanel.getInstance().candidates(gear, operations.get(safeIndex));
			if (index >= 0 && index < cands.size()) {
				state.toggleTarget(cands.get(index).id());
				state.closeDropdown();
			}
		}
		return true;
	}

	@Override
	public boolean onHoverTooltip(ITooltipRenderer renderer, PoseStack poseStack,
			int mouseX, int mouseY, TooltipFlag flag) {
		if (!dropdown.isVisible()) {
			return false;
		}
		String fullName = dropdown.itemName(index);
		Font font = Minecraft.getInstance().font;
		String range = dropdown.itemRange(index);
		int rangeWidth = range.isEmpty() ? 0 : font.width(range);
		int nameMax = x() + width() - RerollPanelLayout.PAD_X - rangeWidth - 8;
		String shownName = RerollPanel.truncate(fullName, Math.max(8, (nameMax - x()) / 7));
		if (!shownName.equals(fullName)) {
			renderer.renderComponentTooltip(poseStack,
					List.of(new TextComponent(fullName)),
					mouseX, mouseY,
					iskallia.vault.client.gui.framework.render.TooltipDirection.LEFT);
			return true;
		}
		return false;
	}
}