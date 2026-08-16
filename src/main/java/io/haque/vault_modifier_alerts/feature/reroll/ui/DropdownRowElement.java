package io.haque.vault_modifier_alerts.feature.reroll.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.reroll.AutoRerollEngine;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanel;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelState;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.RollTarget;
import iskallia.vault.client.gui.framework.element.spi.AbstractSpatialElement;
import iskallia.vault.client.gui.framework.element.spi.IGuiEventElement;
import iskallia.vault.client.gui.framework.element.spi.IRenderedElement;
import iskallia.vault.client.gui.framework.render.TooltipDirection;
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
 * A real framework element for the panel's dropdown rows (Focus, Modifier,
 * Targets). Draws the row chrome (hover/open background, label, value,
 * chevron) and routes clicks declaratively, replacing the hand-drawn
 * {@code drawRow()}/{@code drawModifierRow()}/{@code drawTargetsRow()} in
 * {@link RerollPanel}. The Targets row keeps its clear and stop-condition
 * chip zones on the right edge, with declarative tooltips.
 */
public class DropdownRowElement extends AbstractSpatialElement<DropdownRowElement>
		implements IRenderedElement, IGuiEventElement {

	private final VaultArtisanStationScreen screen;
	private final RerollPanelState.DropdownMode mode;

	public DropdownRowElement(int x, int y, int width, VaultArtisanStationScreen screen,
			RerollPanelState.DropdownMode mode) {
		super(Spatials.positionXY(x, y).size(width, RerollPanelLayout.ROW_H));
		this.screen = screen;
		this.mode = mode;
	}

	@Override
	public void setVisible(boolean visible) {
	}

	@Override
	public boolean isVisible() {
		return true;
	}

	private RerollPanel panel() {
		return RerollPanel.getInstance();
	}

	private RerollPanelState state() {
		return RerollPanelState.getInstance();
	}

	private boolean rowsAvailable() {
		return !panel().operations(screen).isEmpty();
	}

	@Override
	public void render(IElementRenderer renderer, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		if (!rowsAvailable()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		RerollPanelState state = state();
		boolean enabled = VmaClientConfigs.isAutoRerollEnabled();
		boolean open = state.isDropdownOpen() && state.dropdownMode() == mode;
		int x = x();
		int y = y();
		int w = width();
		int h = height();
		boolean hovered = containsMouse(mouseX, mouseY);
		boolean chipHovered = hovered && mouseX >= x + w - 24;
		boolean clearHovered = hovered && mouseX >= x + w - 44 && mouseX < x + w - 26;
		boolean rowHovered = hovered && !chipHovered && !clearHovered;

		if (open) {
			GuiComponent.fill(poseStack, x, y, x + w, y + h, RerollTokens.ROW_OPEN);
		} else if (mode == RerollPanelState.DropdownMode.TARGETS ? rowHovered : hovered) {
			GuiComponent.fill(poseStack, x, y, x + w, y + h, RerollTokens.ROW_HOVER);
		}

		int color = enabled ? RerollTokens.TEXT_DEFAULT() : RerollTokens.TEXT_DISABLED;
		String label = mode == RerollPanelState.DropdownMode.OPERATION ? "Focus"
				: (mode == RerollPanelState.DropdownMode.MODIFIER ? "Modifier" : "Targets");
		font.draw(poseStack, label, x + RerollPanelLayout.PAD_X, y + 3, RerollTokens.TEXT_MUTED);

		String value = fullValue();
		if (mode == RerollPanelState.DropdownMode.TARGETS) {
			int maxChars = (w - 62 - 50 - RerollPanelLayout.PAD_X) / 7;
			if (value.length() > maxChars) {
				value = RerollPanel.truncate(value, maxChars);
			}
		}
		font.draw(poseStack, value, x + 62, y + 3, color);

		font.draw(poseStack, "\u2304", x + w - 9, y + 3, color);

		if (mode == RerollPanelState.DropdownMode.TARGETS) {
			GuiComponent.fill(poseStack, x + w - 44, y, x + w - 26, y + h,
					clearHovered ? RerollTokens.ROW_HOVER : RerollTokens.DROPDOWN_BG);
			int xColor = clearHovered ? RerollTokens.STATE_DANGER()
					: (enabled ? RerollTokens.TEXT_MUTED : RerollTokens.TEXT_DISABLED);
			font.draw(poseStack, "x", x + w - 35, y + 3, xColor);
			GuiComponent.fill(poseStack, x + w - 24, y, x + w, y + h,
					chipHovered ? RerollTokens.ROW_HOVER : RerollTokens.DROPDOWN_BG);
			int chipColor = chipHovered ? RerollTokens.ACCENT_GOLD()
					: (enabled ? RerollTokens.TEXT_MUTED : RerollTokens.TEXT_DISABLED);
			String condition = state.stopCondition() == AutoRerollEngine.StopCondition.ANY ? "any" : "all";
			font.draw(poseStack, condition, x + w - 22, y + 3, chipColor);
		}
	}

	@Override
	public boolean onMouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !containsMouse(mouseX, mouseY) || !rowsAvailable()) {
			return false;
		}
		RerollPanelState state = state();
		state.loseMinFocus();
		switch (mode) {
			case OPERATION -> state.toggleDropdown(RerollPanelState.DropdownMode.OPERATION,
					panel().operations(screen).size());
			case MODIFIER -> state.toggleDropdown(RerollPanelState.DropdownMode.MODIFIER,
					modifierCount());
			case TARGETS -> {
				int x = x();
				int w = width();
				if (mouseX >= x + w - 24) {
					state.closeDropdown();
					state.setStopCondition(state.stopCondition() == AutoRerollEngine.StopCondition.ANY
							? AutoRerollEngine.StopCondition.ALL
							: AutoRerollEngine.StopCondition.ANY);
				} else if (mouseX >= x + w - 44) {
					state.closeDropdown();
					AutoRerollEngine engine = AutoRerollEngine.getInstance();
					if (engine.isRunning()) {
						engine.stop(AutoRerollEngine.StopReason.STOPPED, false);
					}
					engine.cancelResume();
					state.targets().clear();
					state.focusTarget(-1);
					state.resetMinInputText();
				} else {
					state.toggleDropdown(RerollPanelState.DropdownMode.TARGETS, state.targets().size());
				}
			}
		}
		return true;
	}

	@Override
	public boolean onHoverTooltip(ITooltipRenderer renderer, PoseStack poseStack,
			int mouseX, int mouseY, TooltipFlag flag) {
		if (!rowsAvailable() || !containsMouse(mouseX, mouseY)) {
			return false;
		}
		if (mode == RerollPanelState.DropdownMode.TARGETS) {
			int x = x();
			int w = width();
			if (mouseX >= x + w - 44 && mouseX < x + w - 24) {
				renderer.renderComponentTooltip(poseStack,
						List.of(new TextComponent("Clear all targets")),
						mouseX, mouseY, TooltipDirection.LEFT);
				return true;
			}
		}
		String full = fullValue();
		Font font = Minecraft.getInstance().font;
		boolean overflow = x() + 62 + font.width(full) > x() + width() - RerollPanelLayout.PAD_X;
		if (overflow) {
			renderer.renderComponentTooltip(poseStack,
					List.of(new TextComponent(full)),
					mouseX, mouseY, TooltipDirection.LEFT);
			return true;
		}
		return false;
	}

	private int modifierCount() {
		List<GearModificationAction> operations = panel().operations(screen);
		ItemStack gear = RerollPanel.stationGear();
		if (operations.isEmpty() || gear.isEmpty()) {
			return 0;
		}
		int safeIndex = Math.min(state().operationIndex(), operations.size() - 1);
		return panel().candidates(gear, operations.get(safeIndex)).size();
	}

	private String fullValue() {
		RerollPanelState state = state();
		switch (mode) {
			case OPERATION: {
				List<GearModificationAction> operations = panel().operations(screen);
				int safeIndex = Math.min(state.operationIndex(), operations.size() - 1);
				return panel().displayOperationName(operations.get(safeIndex));
			}
			case MODIFIER:
				return state.targets().isEmpty() ? "add a modifier..." : "+ add modifier";
			case TARGETS: {
				RollTarget focused = state.focusedTarget() >= 0 && state.focusedTarget() < state.targets().size()
						? state.targets().get(state.focusedTarget())
						: null;
				if (focused == null) {
					return state.targets().isEmpty() ? "none" : "?";
				}
				String value = panel().targetName(focused.id());
				if (focused.thresholdEnabled()) {
					value += " >=" + RerollPanel.formatDisplay(focused.thresholdValue(),
							panel().currentTargetRange().percent());
				}
				return value;
			}
		}
		return "";
	}
}
