package io.haque.vault_modifier_alerts.feature.reroll;

import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.RollRange;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.RollTarget;
import iskallia.vault.client.gui.screen.block.VaultArtisanStationScreen;
import iskallia.vault.gear.modification.GearModificationAction;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure selection, threshold and dropdown state for the auto-reroll panel.
 * Holds no drawing code — all rendering lives in {@link RerollPanel}.
 * Screen-dependent model queries ({@link #operations}, {@link #candidates},
 * {@link #stationGear}) are delegated back to {@link RerollPanel} so this
 * class avoids any PoseStack / GuiComponent dependency.
 */
public final class RerollPanelState {

	public enum DropdownMode {
		NONE, OPERATION, MODIFIER, TARGETS
	}

	private static final int MAX_INPUT_LENGTH = 6;

	private static final RerollPanelState INSTANCE = new RerollPanelState();

	private RerollPanel panel;

	private boolean visible = true;
	private int operationIndex;
	private final List<RollTarget> targets = new ArrayList<>();
	private int focusedTarget = -1;
	private AutoRerollEngine.StopCondition stopCondition = AutoRerollEngine.StopCondition.ANY;
	private String minInputText = "";
	private boolean minInputFocused;
	private DropdownMode dropdownMode = DropdownMode.NONE;
	private int dropdownScroll;
	private int dropdownMaxRows = RerollPanelLayout.DEFAULT_DROPDOWN_ROWS;

	private RerollPanelState() {
	}

	public static RerollPanelState getInstance() {
		return INSTANCE;
	}

	/**
	 * Called once by {@link RerollPanel} to establish the back-reference for
	 * screen-dependent queries.
	 */
	void setPanel(RerollPanel panel) {
		this.panel = panel;
	}

	// --------------------------------------------------------------- visibility

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
		if (!visible) {
			minInputFocused = false;
			closeDropdown();
		}
	}

	public void toggleVisible() {
		visible = !visible;
		if (!visible) {
			minInputFocused = false;
			closeDropdown();
		}
	}

	// --------------------------------------------------------------- queries

	public boolean isMinInputFocused() {
		return minInputFocused;
	}

	public boolean isDropdownOpen() {
		return dropdownMode != DropdownMode.NONE;
	}

	public DropdownMode dropdownMode() {
		return dropdownMode;
	}

	public int operationIndex() {
		return operationIndex;
	}

	public List<RollTarget> targets() {
		return targets;
	}

	public int focusedTarget() {
		return focusedTarget;
	}

	public AutoRerollEngine.StopCondition stopCondition() {
		return stopCondition;
	}

	public String minInputText() {
		return minInputText;
	}

	public int dropdownScroll() {
		return dropdownScroll;
	}

	public int dropdownMaxRows() {
		return dropdownMaxRows;
	}

	// --------------------------------------------------------------- selection
	// mutations

	public void selectOperation(int index) {
		if (index == operationIndex) {
			return;
		}
		operationIndex = index;
		minInputFocused = false;
	}

	public void toggleTarget(ResourceLocation id) {
		for (int i = 0; i < targets.size(); i++) {
			if (targets.get(i).id().equals(id)) {
				targets.remove(i);
				if (focusedTarget == i) {
					focusedTarget = targets.isEmpty() ? -1 : Math.min(i, targets.size() - 1);
				} else if (focusedTarget > i) {
					focusedTarget--;
				}
				minInputFocused = false;
				return;
			}
		}
		targets.add(new RollTarget(id, false, 0.0));
		focusedTarget = targets.size() - 1;
		minInputFocused = false;
	}

	public void focusTarget(int index) {
		if (index >= 0 && index < targets.size()) {
			focusedTarget = index;
		}
		minInputFocused = false;
	}

	public void removeTarget(int index) {
		if (index < 0 || index >= targets.size()) {
			return;
		}
		targets.remove(index);
		if (focusedTarget == index) {
			focusedTarget = targets.isEmpty() ? -1 : Math.min(index, targets.size() - 1);
		} else if (focusedTarget > index) {
			focusedTarget--;
		}
		minInputFocused = false;
	}

	// --------------------------------------------------------------- min-input

	public void commitMinInput() {
		RollTarget target = focused();
		if (target == null) {
			return;
		}
		if (minInputText.isEmpty()) {
			setFocusedThreshold(false, 0.0);
			return;
		}
		RollRange range = panel.currentTargetRange();
		String text = minInputText.endsWith(".") ? minInputText.substring(0, minInputText.length() - 1) : minInputText;
		Double parsed = safeParseOrNull(text);
		if (parsed == null) {
			minInputText = "";
			setFocusedThreshold(false, 0.0);
			return;
		}
		double clamped = range.numeric() ? Mth.clamp(parsed, range.min(), range.max()) : parsed;
		setFocusedThreshold(true, clamped);
		minInputText = RerollPanel.formatDisplay(thresholdValue(), false);
	}

	public boolean acceptChar(char c) {
		if (!minInputFocused) {
			return false;
		}
		if (Character.isDigit(c)) {
			if (minInputText.length() < MAX_INPUT_LENGTH) {
				minInputText += c;
			}
			return true;
		}
		if (c == '.' && minInputText.indexOf('.') < 0) {
			minInputText += c;
			return true;
		}
		return false;
	}

	public boolean onKeyPressed(int keyCode) {
		if (dropdownMode != DropdownMode.NONE) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				closeDropdown();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_KP_8) {
				scrollDropdown(-1);
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_KP_2) {
				scrollDropdown(1);
				return true;
			}
			return false;
		}
		return inputKey(keyCode);
	}

	public boolean inputKey(int keyCode) {
		if (!minInputFocused) {
			return false;
		}
		if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
			if (!minInputText.isEmpty()) {
				minInputText = minInputText.substring(0, minInputText.length() - 1);
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			commitMinInput();
			minInputFocused = false;
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			commitMinInput();
			minInputFocused = false;
			return true;
		}
		return false;
	}

	// --------------------------------------------------------------- dropdown

	public void closeDropdown() {
		dropdownMode = DropdownMode.NONE;
		dropdownScroll = 0;
	}

	public void scrollDropdown(int delta) {
		if (dropdownMode == DropdownMode.NONE) {
			return;
		}
		int count = dropdownCount();
		int max = Math.max(0, count - dropdownMaxRows);
		dropdownScroll = Mth.clamp(dropdownScroll + delta, 0, max);
	}

	public void toggleDropdown(DropdownMode mode, int count) {
		if (dropdownMode == mode) {
			closeDropdown();
			return;
		}
		if (count <= 0) {
			closeDropdown();
			return;
		}
		dropdownMode = mode;
		dropdownScroll = 0;
		clampDropdownScroll(count, count);
	}

	public void clampDropdownScroll(int operationCount, int candidateCount) {
		int count = dropdownMode == DropdownMode.OPERATION ? operationCount
				: (dropdownMode == DropdownMode.TARGETS ? targets.size()
						: (dropdownMode == DropdownMode.MODIFIER ? candidateCount : 0));
		int max = Math.max(0, count - dropdownMaxRows);
		dropdownScroll = Mth.clamp(dropdownScroll, 0, max);
	}

	// --------------------------------------------------------------- focus / step

	public void toggleMinFocus() {
		if (dropdownMode != DropdownMode.NONE || focused() == null) {
			return;
		}
		if (!panel.currentTargetRange().numeric()) {
			return;
		}
		if (!minInputFocused) {
			minInputText = focused().thresholdEnabled()
					? RerollPanel.formatDisplay(focused().thresholdValue(), false)
					: "";
			minInputFocused = true;
		} else {
			commitMinInput();
			minInputFocused = false;
		}
	}

	public void loseMinFocus() {
		if (minInputFocused) {
			commitMinInput();
			minInputFocused = false;
		}
	}

	public void stepMin(double delta) {
		if (focused() == null) {
			return;
		}
		RollRange range = panel.currentTargetRange();
		if (!range.numeric()) {
			return;
		}
		double value = Mth.clamp(thresholdValue() + delta, range.min(), range.max());
		setFocusedThreshold(true, value);
		minInputText = RerollPanel.formatDisplay(thresholdValue(), false);
		minInputFocused = false;
	}

	public double currentStep() {
		double step = panel.currentTargetRange().step();
		return step > 0.0 ? step : 1.0;
	}

	// --------------------------------------------------------------- clamp / reset

	public void clampSelections(List<GearModificationAction> operations, ItemStack gear) {
		if (operations.isEmpty()) {
			operationIndex = 0;
			focusedTarget = -1;
			return;
		}
		if (operationIndex >= operations.size()) {
			operationIndex = 0;
		}
		if (focusedTarget >= targets.size()) {
			focusedTarget = targets.isEmpty() ? -1 : targets.size() - 1;
		}
	}

	public void resetSelection() {
		AutoRerollEngine.getInstance().cancelResume();
		targets.clear();
		focusedTarget = -1;
		minInputText = "";
		minInputFocused = false;
		operationIndex = 0;
		closeDropdown();
	}

	public boolean canStart() {
		return VmaClientConfigs.isAutoRerollEnabled() && !targets.isEmpty() && panel.currentSelection() != null;
	}

	// --------------------------------------------------------------- model queries
	// (via panel back-ref)

	public int dropdownCount() {
		if (dropdownMode == DropdownMode.NONE) {
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.screen instanceof VaultArtisanStationScreen screen)) {
			return 0;
		}
		List<GearModificationAction> ops = panel.operations(screen);
		if (ops.isEmpty()) {
			return 0;
		}
		if (dropdownMode == DropdownMode.OPERATION) {
			return ops.size();
		}
		if (dropdownMode == DropdownMode.TARGETS) {
			return targets.size();
		}
		int safeIndex = Math.min(operationIndex, ops.size() - 1);
		return panel.candidates(RerollPanel.stationGear(), ops.get(safeIndex)).size();
	}

	public void updateDropdownCapacity(VaultArtisanStationScreen screen, int x, int y, int currentWidth) {
		int available = screen.height - (y + new RerollPanelLayout(x, y, currentWidth, false, 0, 0, 0).baseHeight
				+ RerollPanelLayout.DROPDOWN_HEADER_H);
		dropdownMaxRows = Mth.clamp(available / RerollPanelLayout.DROPDOWN_ITEM_H,
				RerollPanelLayout.MIN_DROPDOWN_ROWS, RerollPanelLayout.MAX_DROPDOWN_ROWS);
	}

	// --------------------------------------------------------------- internal
	// helpers

	private RollTarget focused() {
		return focusedTarget >= 0 && focusedTarget < targets.size() ? targets.get(focusedTarget) : null;
	}

	private void setFocusedThreshold(boolean enabled, double value) {
		RollTarget target = focused();
		if (target != null) {
			targets.set(focusedTarget, new RollTarget(target.id(), enabled, value));
		}
	}

	private double thresholdValue() {
		RollTarget target = focused();
		return target == null ? 0.0 : target.thresholdValue();
	}

	/** Package-private setter for the stop-condition chip toggle. */
	public void setStopCondition(AutoRerollEngine.StopCondition condition) {
		this.stopCondition = condition;
	}

	/** Clears the focused target's threshold (right-click clear convention). */
	public void clearFocusedThreshold() {
		setFocusedThreshold(false, 0.0);
		minInputText = "";
	}

	/** Package-private reset for the min-input text (used by clear-targets). */
	public void resetMinInputText() {
		this.minInputText = "";
	}

	private static Double safeParseOrNull(String text) {
		try {
			return Double.parseDouble(text);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}
}
