package io.haque.vault_modifier_alerts.feature.reroll;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.reroll.AutoRerollEngine.StopReason;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.Candidate;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.OperationScope;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.RollRange;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout.Hit;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout.HitType;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout.Rect;
import iskallia.vault.client.gui.screen.block.VaultArtisanStationScreen;
import iskallia.vault.container.VaultArtisanStationContainer;
import iskallia.vault.gear.modification.GearModificationAction;
import iskallia.vault.init.ModConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Selection, threshold and dropdown state for the auto-reroll panel, plus its
 * rendering and input routing. The panel is added to the Artisan Station
 * screen as a real VH framework element (RerollPanelElement); all geometry
 * comes from {@link RerollPanelLayout} so the drawn rows and the click/scroll
 * routing share one source of truth.
 */
public final class RerollPanel {

	public enum DropdownMode {
		NONE, OPERATION, MODIFIER
	}

	private static final int BG_COLOR = 0xEE111111;
	private static final int BORDER_COLOR = 0xFF6B6B6B;
	private static final int GOLD_COLOR = 0xFFE3C38C;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int MUTED_COLOR = 0xFFA0A0A0;
	private static final int ACCENT_COLOR = 0xFF55FF55;
	private static final int WARN_COLOR = 0xFFFF5555;
	private static final int FIELD_BG_COLOR = 0xFF2E2E2E;
	private static final int FIELD_FOCUS_COLOR = 0xFF484848;
	private static final int HOVER_COLOR = 0xFF3A3A3A;
	private static final int HIGHLIGHT_COLOR = 0xFF543C1F;
	private static final int DISABLED_COLOR = 0xFF707070;
	private static final int MAX_INPUT_LENGTH = 6;

	private static final RerollPanel INSTANCE = new RerollPanel();

	private boolean visible = true;
	private int operationIndex;
	private int targetIndex;
	private String minInputText = "";
	private boolean thresholdEnabled;
	private double thresholdValue;
	private boolean minInputFocused;
	private DropdownMode dropdownMode = DropdownMode.NONE;
	private int dropdownScroll;
	private int dropdownMaxRows = RerollPanelLayout.DEFAULT_DROPDOWN_ROWS;
	private int lastX;
	private int lastY;
	private int lastW;
	private int lastH;
	private String pendingTooltip;
	private int tooltipX;
	private int tooltipY;

	private RerollPanel() {
	}

	public static RerollPanel getInstance() {
		return INSTANCE;
	}

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

	public int targetIndex() {
		return targetIndex;
	}

	public double thresholdValue() {
		return thresholdValue;
	}

	/** Selection snapshot used by the Start button and the /vma reroll command. */
	public record RerollSelection(ResourceLocation operationId, ResourceLocation targetId, boolean thresholdEnabled,
			double thresholdValue) {
	}

	/** @return the current panel selection, or null if no station screen / no valid choice. */
	public RerollSelection currentSelection() {
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.screen instanceof VaultArtisanStationScreen screen)) {
			return null;
		}
		List<GearModificationAction> operations = operations(screen);
		if (operations.isEmpty()) {
			return null;
		}
		ItemStack gear = stationGear();
		clampSelections(operations, gear);
		GearModificationAction operation = operations.get(operationIndex);
		ResourceLocation targetId = currentTargetId(gear, operation);
		if (targetId == null) {
			return null;
		}
		return new RerollSelection(operation.modification().getRegistryName(), targetId, thresholdEnabled,
				thresholdValue);
	}

	/** All re-roll operations the station offers for the selected tab scope. */
	public List<GearModificationAction> operations(VaultArtisanStationScreen screen) {
		List<GearModificationAction> result = new ArrayList<>();
		for (GearModificationAction action : ((VaultArtisanStationContainer) screen.getMenu())
				.getModificationActions()) {
			OperationScope scope = ModifierCatalog.scopeOfOperation(action.modification().getRegistryName());
			if (scope != null) {
				result.add(action);
			}
		}
		return result;
	}

	public List<Candidate> candidates(ItemStack gear, GearModificationAction operation) {
		return ModifierCatalog.candidates(gear,
				ModifierCatalog.scopeOfOperation(operation.modification().getRegistryName()));
	}

	public void selectOperation(int index) {
		if (index == operationIndex) {
			return;
		}
		operationIndex = index;
		targetIndex = 0;
		resetMinInput();
	}

	public void selectTarget(int index) {
		targetIndex = index;
		resetMinInput();
	}

	/**
	 * Commits the field text. A valid number is always kept: it is clamped to
	 * the target's roll range when that range is known, and used as-is when the
	 * range could not be read (the engine compares "at least X" either way).
	 */
	public void commitMinInput() {
		if (minInputText.isEmpty()) {
			thresholdEnabled = false;
			thresholdValue = 0;
			return;
		}
		RollRange range = currentTargetRange();
		String text = minInputText.endsWith(".") ? minInputText.substring(0, minInputText.length() - 1) : minInputText;
		Double parsed = safeParseOrNull(text);
		if (parsed == null) {
			minInputText = "";
			thresholdEnabled = false;
			thresholdValue = 0;
			return;
		}
		thresholdEnabled = true;
		thresholdValue = range.numeric() ? Mth.clamp(parsed, range.min(), range.max()) : parsed;
		minInputText = formatDisplay(thresholdValue, false);
	}

	public void resetMinInput() {
		minInputText = "";
		thresholdEnabled = false;
		thresholdValue = 0;
	}

	/**
	 * Typed-character support for the min-value field, fed by the screen-level
	 * char event (the framework does not route chars to elements we own).
	 *
	 * @return true when the character was consumed by the field
	 */
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

	/**
	 * Key support fed by the screen-level key event. First priority is an open
	 * dropdown (Escape closes, arrows scroll), then the focused min-value field
	 * (Backspace edits, Enter/KP-Enter and Escape commit, Escape also drops
	 * focus).
	 *
	 * @return true when the key was consumed
	 */
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

	/**
	 * Key support for the focused min-value field. Backspace edits, Enter and
	 * Escape commit (Escape also drops focus), everything else passes through.
	 *
	 * @return true when the key was consumed by the field
	 */
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

	/** Total panel height including an open dropdown; used by the framework element. */
	public int currentHeight() {
		return computeLayout(0, 0).totalHeight;
	}

	/** Where the panel was last drawn (full rect including any open dropdown). */
	public RerollPanelLayout.Rect bounds() {
		return new RerollPanelLayout.Rect(lastX, lastY, lastW, lastH);
	}

	/**
	 * Draws the whole panel at the given rect. Called by the framework element.
	 */
	public void draw(VaultArtisanStationScreen screen, PoseStack poseStack, int x, int y, int width, int height,
			int mouseX, int mouseY) {
		lastX = x;
		lastY = y;
		lastW = width;
		lastH = height;
		if (width != RerollPanelLayout.WIDTH) {
			width = RerollPanelLayout.WIDTH;
		}
		updateDropdownCapacity(screen, x, y);
		List<GearModificationAction> operations = operations(screen);
		if (operations.isEmpty()) {
			dropdownMode = DropdownMode.NONE;
		}
		ItemStack gear = stationGear();
		clampSelections(operations, gear);
		AutoRerollEngine engine = AutoRerollEngine.getInstance();
		GearModificationAction operation = operations.isEmpty() ? null : operations.get(operationIndex);
		List<Candidate> candidates = operation == null ? List.of() : candidates(gear, operation);
		clampDropdownScroll(operations.size(), candidates.size());
		RerollPanelLayout layout = computeLayout(x, y);
		pendingTooltip = null;

		drawPanelFrame(poseStack, layout);
		drawCentered(poseStack, "Auto-Reroll", x + width / 2, y + 4, GOLD_COLOR);

		if (operation == null) {
			drawString(poseStack, "No re-roll actions", x + RerollPanelLayout.PAD_X, layout.focusY + 3, WARN_COLOR);
			drawStatus(poseStack, layout, candidates.isEmpty(), mouseX, mouseY);
			drawTooltip(poseStack);
			return;
		}

		boolean enabled = VmaClientConfigs.isAutoRerollEnabled();
		drawRow(poseStack, layout, "Focus", displayOperationName(operation), layout.focusY, mouseX, mouseY,
				dropdownMode == DropdownMode.OPERATION);
		drawModifierRow(poseStack, layout, candidates, mouseX, mouseY);
		drawMinRow(poseStack, layout, x, width, mouseX, mouseY);
		drawRangeRow(poseStack, layout, x, width, mouseX, mouseY);
		drawPotentialRow(poseStack, layout, x, width, operation);
		drawToggleRow(poseStack, layout, x, "Auto-reroll", enabled, layout.rerollToggleY, mouseX, mouseY);
		drawToggleRow(poseStack, layout, x, "Auto-reset potential",
				VmaClientConfigs.isAutoResetPotentialEnabled(), layout.resetToggleY, mouseX, mouseY);
		drawButton(poseStack, layout, engine, canStart(candidates), mouseX, mouseY);
		drawStatus(poseStack, layout, candidates.isEmpty(), mouseX, mouseY);

		if (dropdownMode != DropdownMode.NONE) {
			drawDropdown(poseStack, layout, operations, candidates, mouseX, mouseY);
		}
		drawTooltip(poseStack);
	}

	/** @return true when the point lies inside the panel rect (no other meaning). */
	public boolean hitTest(int x, int y, int width, int height, int mouseX, int mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/** @return true if the click was consumed by the panel. */
	public boolean handleClick(VaultArtisanStationScreen screen, int x, int y, int width, int height, int mouseX,
			int mouseY, int button) {
		if (button != 0 || !hitTest(x, y, width, height, mouseX, mouseY)) {
			return false;
		}
		List<GearModificationAction> operations = operations(screen);
		if (operations.isEmpty()) {
			dropdownMode = DropdownMode.NONE;
			return true;
		}
		ItemStack gear = stationGear();
		clampSelections(operations, gear);
		int operationCount = operations.size();
		int candidateCount = candidates(gear, operations.get(operationIndex)).size();
		clampDropdownScroll(operationCount, candidateCount);
		RerollPanelLayout layout = computeLayout(x, y);
		Hit hit = layout.regionAt(mouseX, mouseY);

		switch (hit.type()) {
			case FOCUS_ROW -> {
				loseMinFocus();
				toggleDropdown(DropdownMode.OPERATION, operationCount);
			}
			case MODIFIER_ROW -> {
				loseMinFocus();
				toggleDropdown(DropdownMode.MODIFIER, candidateCount);
			}
			case MIN_DEC -> stepMin(layout, -currentStep());
			case MIN_INC -> stepMin(layout, currentStep());
			case MIN_FIELD -> toggleMinFocus();
			case REROLL_TOGGLE -> {
				loseMinFocus();
				closeDropdown();
				boolean enabled = !VmaClientConfigs.isAutoRerollEnabled();
				VmaClientConfigs.setAutoRerollEnabled(enabled);
				if (!enabled && AutoRerollEngine.getInstance().isRunning()) {
					AutoRerollEngine.getInstance().stop(StopReason.STOPPED, false);
				}
			}
			case RESET_TOGGLE -> {
				loseMinFocus();
				VmaClientConfigs.setAutoResetPotential(!VmaClientConfigs.isAutoResetPotentialEnabled());
			}
			case START_BUTTON -> {
				loseMinFocus();
				AutoRerollEngine engine = AutoRerollEngine.getInstance();
				if (engine.isRunning()) {
					engine.stop(StopReason.STOPPED, false);
				} else if (canStart(candidates(gear, operations.get(operationIndex)))) {
					RerollSelection selection = currentSelection();
					if (selection != null) {
						engine.start(selection.operationId(), selection.targetId(), selection.thresholdEnabled(),
								selection.thresholdValue());
					}
				}
			}
			case DROPDOWN_UP -> scrollDropdown(-1);
			case DROPDOWN_DOWN -> scrollDropdown(1);
			case DROPDOWN_ITEM -> {
				int realIndex = dropdownScroll + hit.index();
				if (dropdownMode == DropdownMode.OPERATION && realIndex >= 0 && realIndex < operations.size()) {
					selectOperation(realIndex);
					closeDropdown();
				} else if (dropdownMode == DropdownMode.MODIFIER) {
					int safeIndex = Math.min(operationIndex, operations.size() - 1);
					List<Candidate> targets = candidates(gear, operations.get(safeIndex));
					if (realIndex >= 0 && realIndex < targets.size()) {
						selectTarget(realIndex);
						closeDropdown();
					}
				}
			}
			case NONE -> {
				if (isDropdownOpen()) {
					closeDropdown();
				} else {
					loseMinFocus();
				}
			}
			default -> loseMinFocus();
		}
		return true;
	}

	/**
	 * Mouse-wheel routing for an open dropdown. Called from the framework
	 * element when the wheel is over the panel.
	 *
	 * @return true when the scroll was consumed
	 */
	public boolean handleScroll(double delta) {
		if (dropdownMode == DropdownMode.NONE) {
			return false;
		}
		scrollDropdown(delta > 0 ? -1 : 1);
		return true;
	}

	private void scrollDropdown(int delta) {
		if (dropdownMode == DropdownMode.NONE) {
			return;
		}
		int count = dropdownCount();
		int max = Math.max(0, count - dropdownMaxRows);
		dropdownScroll = Mth.clamp(dropdownScroll + delta, 0, max);
	}

	/** The number of entries in the currently open dropdown (0 when none). */
	private int dropdownCount() {
		if (dropdownMode == DropdownMode.NONE) {
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.screen instanceof VaultArtisanStationScreen screen)) {
			return 0;
		}
		List<GearModificationAction> operations = operations(screen);
		if (operations.isEmpty()) {
			return 0;
		}
		if (dropdownMode == DropdownMode.OPERATION) {
			return operations.size();
		}
		int safeIndex = Math.min(operationIndex, operations.size() - 1);
		return candidates(stationGear(), operations.get(safeIndex)).size();
	}

	private void clampDropdownScroll(int operationCount, int candidateCount) {
		int count = dropdownMode == DropdownMode.OPERATION ? operationCount
				: (dropdownMode == DropdownMode.MODIFIER ? candidateCount : 0);
		int max = Math.max(0, count - dropdownMaxRows);
		dropdownScroll = Mth.clamp(dropdownScroll, 0, max);
	}

	private void toggleDropdown(DropdownMode mode, int count) {
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

	/** Closes the open dropdown, if any. Exposed for screen-level event routing. */
	public void closeDropdown() {
		dropdownMode = DropdownMode.NONE;
		dropdownScroll = 0;
	}

	private void updateDropdownCapacity(VaultArtisanStationScreen screen, int x, int y) {
		int available = screen.height - (y + new RerollPanelLayout(x, y, false, 0, 0, 0).baseHeight
				+ RerollPanelLayout.DROPDOWN_HEADER_H);
		dropdownMaxRows = Mth.clamp(available / RerollPanelLayout.DROPDOWN_ITEM_H,
				RerollPanelLayout.MIN_DROPDOWN_ROWS, RerollPanelLayout.MAX_DROPDOWN_ROWS);
	}

	private RerollPanelLayout computeLayout(int x, int y) {
		int count = dropdownCount();
		return new RerollPanelLayout(x, y, dropdownMode != DropdownMode.NONE, count, dropdownScroll, dropdownMaxRows);
	}

	private boolean canStart(List<Candidate> candidates) {
		return VmaClientConfigs.isAutoRerollEnabled() && !candidates.isEmpty();
	}

	// ------------------------------------------------------------------ draw

	private void drawPanelFrame(PoseStack poseStack, RerollPanelLayout layout) {
		GuiComponent.fill(poseStack, layout.x, layout.y, layout.x + RerollPanelLayout.WIDTH,
				layout.y + layout.totalHeight, BG_COLOR);
		GuiComponent.fill(poseStack, layout.x, layout.y, layout.x + RerollPanelLayout.WIDTH, layout.y + 2, GOLD_COLOR);
		GuiComponent.fill(poseStack, layout.x, layout.y + layout.totalHeight - 1, layout.x + RerollPanelLayout.WIDTH,
				layout.y + layout.totalHeight, BORDER_COLOR);
		GuiComponent.fill(poseStack, layout.x, layout.y + 2, layout.x + 1, layout.y + layout.totalHeight, BORDER_COLOR);
		GuiComponent.fill(poseStack, layout.x + RerollPanelLayout.WIDTH - 1, layout.y + 2,
				layout.x + RerollPanelLayout.WIDTH, layout.y + layout.totalHeight, BORDER_COLOR);
	}

	/**
	 * A selectable row: label left, one-line value, dropdown marker right.
	 * Highlighted while its dropdown is open, hovered otherwise.
	 */
	private void drawRow(PoseStack poseStack, RerollPanelLayout layout, String label, String value, int y, int mouseX,
			int mouseY, boolean open) {
		boolean hovered = mouseY >= y && mouseY < y + RerollPanelLayout.ROW_H;
		if (open) {
			GuiComponent.fill(poseStack, layout.x, y, layout.x + RerollPanelLayout.WIDTH,
					y + RerollPanelLayout.ROW_H, HIGHLIGHT_COLOR);
		} else if (hovered) {
			GuiComponent.fill(poseStack, layout.x, y, layout.x + RerollPanelLayout.WIDTH,
					y + RerollPanelLayout.ROW_H, HOVER_COLOR);
		}
		int color = VmaClientConfigs.isAutoRerollEnabled() ? TEXT_COLOR : DISABLED_COLOR;
		drawString(poseStack, label, layout.x + RerollPanelLayout.PAD_X, y + 3, MUTED_COLOR);
		drawString(poseStack, value, layout.x + 62, y + 3, color);
		drawTriangle(poseStack, layout.x + RerollPanelLayout.WIDTH - 8, y + 6, false, color);
		if (hovered && layout.x + 62 + font().width(value) > layout.x + RerollPanelLayout.WIDTH
				- RerollPanelLayout.PAD_X) {
			hoverTooltip(value, mouseX, mouseY);
		}
	}

	/** Modifier row: shows only the modifier name (the "Mod Added Ability Level" prefix is gone). */
	private void drawModifierRow(PoseStack poseStack, RerollPanelLayout layout, List<Candidate> candidates, int mouseX,
			int mouseY) {
		boolean hovered = mouseY >= layout.modifierY && mouseY < layout.modifierY + RerollPanelLayout.ROW_H;
		if (dropdownMode == DropdownMode.MODIFIER) {
			GuiComponent.fill(poseStack, layout.x, layout.modifierY, layout.x + RerollPanelLayout.WIDTH,
					layout.modifierY + RerollPanelLayout.ROW_H, HIGHLIGHT_COLOR);
		} else if (hovered) {
			GuiComponent.fill(poseStack, layout.x, layout.modifierY, layout.x + RerollPanelLayout.WIDTH,
					layout.modifierY + RerollPanelLayout.ROW_H, HOVER_COLOR);
		}
		int color = VmaClientConfigs.isAutoRerollEnabled() ? TEXT_COLOR : DISABLED_COLOR;
		drawString(poseStack, "Modifier", layout.x + RerollPanelLayout.PAD_X, layout.modifierY + 3, MUTED_COLOR);
		String name;
		if (candidates.isEmpty()) {
			name = "none rollable";
		} else {
			name = candidates.get(targetIndex).displayName();
			int maxChars = (RerollPanelLayout.WIDTH - 62 - RerollPanelLayout.PAD_X * 2) / 7;
			if (name.length() > maxChars) {
				String full = name;
				name = truncate(name, maxChars);
				if (hovered) {
					hoverTooltip(full, mouseX, mouseY);
				}
			}
		}
		drawString(poseStack, name, layout.x + 62, layout.modifierY + 3, color);
		drawTriangle(poseStack, layout.x + RerollPanelLayout.WIDTH - 8, layout.modifierY + 6, false, color);
	}

	private void drawMinRow(PoseStack poseStack, RerollPanelLayout layout, int x, int width, int mouseX, int mouseY) {
		RollRange range = currentTargetRange();
		boolean numeric = range.numeric();
		boolean enabled = VmaClientConfigs.isAutoRerollEnabled();
		boolean hoveredDec = mouseY >= layout.minY && mouseY < layout.minY + RerollPanelLayout.ROW_H
				&& mouseX < x + 16;
		boolean hoveredInc = mouseY >= layout.minY && mouseY < layout.minY + RerollPanelLayout.ROW_H
				&& mouseX >= x + width - 16;
		boolean hoveredField = mouseY >= layout.minY && mouseY < layout.minY + RerollPanelLayout.ROW_H
				&& mouseX >= layout.minFieldLeft() && mouseX < layout.minFieldRight();
		int arrowColor = enabled ? MUTED_COLOR : DISABLED_COLOR;
		drawString(poseStack, "-", x + 5, layout.minY + 3, hoveredDec ? TEXT_COLOR : arrowColor);
		drawString(poseStack, "Min", x + 22, layout.minY + 3, MUTED_COLOR);
		drawString(poseStack, "+", x + 196, layout.minY + 3, hoveredInc ? TEXT_COLOR : arrowColor);
		if (minInputFocused || thresholdEnabled) {
			GuiComponent.fill(poseStack, layout.minFieldLeft(), layout.minY, layout.minFieldRight(),
					layout.minY + RerollPanelLayout.ROW_H, minInputFocused ? FIELD_FOCUS_COLOR : FIELD_BG_COLOR);
		}
		String shown;
		if (minInputFocused) {
			shown = minInputText + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
		} else if (thresholdEnabled) {
			shown = formatDisplay(thresholdValue, numeric && range.percent());
		} else {
			shown = "any";
		}
		drawString(poseStack, shown, layout.minFieldLeft() + 2, layout.minY + 3,
				hoveredField && !minInputFocused ? GOLD_COLOR : TEXT_COLOR);
	}

	private void drawRangeRow(PoseStack poseStack, RerollPanelLayout layout, int x, int width, int mouseX, int mouseY) {
		RollRange range = currentTargetRange();
		String text = range.numeric() ? "Range: " + range.displayText() : "Range: ?";
		String full = text;
		int maxChars = (RerollPanelLayout.WIDTH - RerollPanelLayout.PAD_X * 2) / 7;
		if (text.length() > maxChars) {
			text = truncate(text, maxChars);
			if (mouseY >= layout.rangeY && mouseY < layout.rangeY + RerollPanelLayout.ROW_H) {
				hoverTooltip(full, mouseX, mouseY);
			}
		}
		drawString(poseStack, text, x + RerollPanelLayout.PAD_X, layout.rangeY + 3, MUTED_COLOR);
	}

	private void drawPotentialRow(PoseStack poseStack, RerollPanelLayout layout, int x, int width,
			GearModificationAction operation) {
		ItemStack gear = stationGear();
		int potential = ModifierCatalog.craftingPotential(gear);
		int max = ModifierCatalog.maxCraftingPotential(gear);
		int cost = potentialCost(operation);
		String left = "Potential " + potential + "/" + max;
		String right = cost > 0 && potential > 0 ? "~" + Math.max(1, potential / cost) + " rolls" : "";
		int color = potential > 0 ? (VmaClientConfigs.isAutoRerollEnabled() ? TEXT_COLOR : DISABLED_COLOR) : WARN_COLOR;
		drawString(poseStack, left, x + RerollPanelLayout.PAD_X, layout.potentialY + 3, color);
		if (!right.isEmpty()) {
			drawRight(poseStack, right, x + width - RerollPanelLayout.PAD_X, layout.potentialY + 3, MUTED_COLOR);
		}
	}

	private void drawToggleRow(PoseStack poseStack, RerollPanelLayout layout, int x, String label, boolean enabled,
			int y, int mouseX, int mouseY) {
		boolean hovered = mouseY >= y && mouseY < y + RerollPanelLayout.ROW_H;
		if (hovered) {
			GuiComponent.fill(poseStack, layout.x, y, layout.x + RerollPanelLayout.WIDTH,
					y + RerollPanelLayout.ROW_H, HOVER_COLOR);
		}
		drawString(poseStack, "[" + (enabled ? "x" : " ") + "]", x + RerollPanelLayout.PAD_X, y + 3,
				enabled ? ACCENT_COLOR : DISABLED_COLOR);
		drawString(poseStack, label, x + 30, y + 3, enabled ? TEXT_COLOR : DISABLED_COLOR);
	}

	private void drawButton(PoseStack poseStack, RerollPanelLayout layout, AutoRerollEngine engine, boolean canStart,
			int mouseX, int mouseY) {
		boolean running = engine.isRunning();
		boolean hovered = mouseY >= layout.buttonY && mouseY < layout.buttonY + RerollPanelLayout.BUTTON_H;
		if (running || canStart) {
			GuiComponent.fill(poseStack, layout.x + RerollPanelLayout.PAD_X, layout.buttonY,
					layout.x + RerollPanelLayout.WIDTH - RerollPanelLayout.PAD_X,
					layout.buttonY + RerollPanelLayout.BUTTON_H, hovered ? HOVER_COLOR : 0xFF303030);
		}
		String label = running ? "Stop" : "Start";
		int color;
		if (running) {
			color = WARN_COLOR;
		} else if (canStart) {
			color = hovered ? GOLD_COLOR : ACCENT_COLOR;
		} else {
			color = DISABLED_COLOR;
		}
		drawCentered(poseStack, label, layout.x + RerollPanelLayout.WIDTH / 2, layout.buttonY + 3, color);
	}

	private void drawStatus(PoseStack poseStack, RerollPanelLayout layout, boolean noCandidates, int mouseX, int mouseY) {
		AutoRerollEngine engine = AutoRerollEngine.getInstance();
		String text;
		int color;
		if (engine.isRunning()) {
			StringBuilder value = new StringBuilder("Rolling... #" + engine.rolls());
			if (engine.lastTargetValue() > 0) {
				boolean percent = currentTargetRange().percent();
				value.append(" (").append(formatDisplay(engine.lastTargetValue(), percent)).append(")");
			}
			text = value.toString();
			color = ACCENT_COLOR;
		} else if (engine.stopReason() != null) {
			String suffix = engine.rolls() > 0 ? " · " + engine.rolls() + " rolls" : "";
			text = "Stopped: " + stopReasonText(engine.stopReason()) + suffix;
			color = WARN_COLOR;
		} else if (!VmaClientConfigs.isAutoRerollEnabled()) {
			text = "Auto-reroll disabled";
			color = DISABLED_COLOR;
		} else if (stationGear().isEmpty()) {
			text = "No gear in station";
			color = MUTED_COLOR;
		} else if (noCandidates) {
			text = "No rollable modifiers";
			color = MUTED_COLOR;
		} else if (thresholdEnabled) {
			text = "Ready · min " + formatDisplay(thresholdValue, currentTargetRange().percent());
			color = ACCENT_COLOR;
		} else {
			text = "Ready · any roll";
			color = ACCENT_COLOR;
		}
		String full = text;
		int maxChars = (RerollPanelLayout.WIDTH - RerollPanelLayout.PAD_X * 2) / 7;
		if (text.length() > maxChars) {
			text = truncate(text, maxChars);
			if (mouseY >= layout.statusY && mouseY < layout.statusY + RerollPanelLayout.ROW_H) {
				hoverTooltip(full, mouseX, mouseY);
			}
		}
		drawString(poseStack, text, layout.x + RerollPanelLayout.PAD_X, layout.statusY + 3, color);
	}

	private void drawDropdown(PoseStack poseStack, RerollPanelLayout layout, List<GearModificationAction> operations,
			List<Candidate> candidates, int mouseX, int mouseY) {
		boolean operationDropdown = dropdownMode == DropdownMode.OPERATION;
		List<String> names = operationDropdown ? operations.stream().map(this::displayOperationName).toList()
				: candidates.stream().map(Candidate::displayName).toList();
		int count = names.size();
		int visible = layout.dropdownVisibleItems;
		if (visible == 0) {
			return;
		}
		GuiComponent.fill(poseStack, layout.x, layout.dropdownY, layout.x + RerollPanelLayout.WIDTH,
				layout.dropdownY + layout.dropdownHeight, 0xF0181818);
		GuiComponent.fill(poseStack, layout.x, layout.dropdownY, layout.x + RerollPanelLayout.WIDTH,
				layout.dropdownY + 1, GOLD_COLOR);
		String header = operationDropdown ? "Operations" : "Modifiers";
		drawCentered(poseStack, header, layout.x + RerollPanelLayout.WIDTH / 2, layout.dropdownY + 3, GOLD_COLOR);
		boolean scrollable = count > visible;
		drawTriangle(poseStack, layout.x + 7, layout.dropdownY + 6, true,
				scrollable && dropdownScroll > 0 ? TEXT_COLOR : DISABLED_COLOR);
		drawTriangle(poseStack, layout.x + RerollPanelLayout.WIDTH - 7, layout.dropdownY + 6, false,
				scrollable && dropdownScroll < count - visible ? TEXT_COLOR : DISABLED_COLOR);

		for (int slot = 0; slot < visible; slot++) {
			int index = dropdownScroll + slot;
			if (index >= count) {
				break;
			}
			Rect rect = layout.dropdownItem(slot);
			boolean hovered = mouseX >= rect.x() && mouseX < rect.x() + rect.width() && mouseY >= rect.y()
					&& mouseY < rect.y() + rect.height();
			boolean current = operationDropdown ? index == operationIndex : index == targetIndex;
			if (hovered) {
				GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(),
						HOVER_COLOR);
			}
			String name = names.get(index);
			String range = "";
			if (!operationDropdown && index < candidates.size()) {
				RollRange rollRange = rollRangeOf(candidates.get(index));
				range = rollRange.displayText();
			}
			if (operationDropdown && index < operations.size()) {
				int cost = potentialCost(operations.get(index));
				if (cost > 0) {
					range = cost + " potential";
				}
			}
			int baseColor = current ? GOLD_COLOR : (VmaClientConfigs.isAutoRerollEnabled() ? TEXT_COLOR : DISABLED_COLOR);
			int rangeX = rect.x() + rect.width() - RerollPanelLayout.PAD_X;
			int rangeWidth = range.isEmpty() ? 0 : font().width(range);
			int nameMax = rect.x() + rect.width() - RerollPanelLayout.PAD_X - rangeWidth - 8;
			if (current) {
				drawString(poseStack, ">", rect.x() + 2, rect.y() + 3, GOLD_COLOR);
			}
			String fullName = name;
			String shownName = truncate(name, Math.max(8, (nameMax - rect.x()) / 7));
			if (!shownName.equals(fullName) && hovered) {
				hoverTooltip(fullName, mouseX, mouseY);
			}
			drawString(poseStack, shownName, rect.x() + 11, rect.y() + 3, baseColor);
			if (!range.isEmpty()) {
				drawRight(poseStack, range, rangeX, rect.y() + 3, MUTED_COLOR);
			}
		}
	}

	private RollRange rollRangeOf(Candidate candidate) {
		ItemStack gear = stationGear();
		if (gear.isEmpty() || !(Minecraft.getInstance().screen instanceof VaultArtisanStationScreen screen)) {
			return new RollRange(0, 0, 0, false, false);
		}
		List<GearModificationAction> operations = operations(screen);
		if (operations.isEmpty()) {
			return new RollRange(0, 0, 0, false, false);
		}
		int safeIndex = Math.min(operationIndex, operations.size() - 1);
		return ModifierCatalog.rollRange(gear, candidate.id(),
				ModifierCatalog.scopeOfOperation(operations.get(safeIndex).modification().getRegistryName()));
	}

	// ----------------------------------------------------------------- input

	private void toggleMinFocus() {
		if (dropdownMode != DropdownMode.NONE) {
			return;
		}
		if (!minInputFocused) {
			minInputText = thresholdEnabled ? formatDisplay(thresholdValue, false) : "";
			minInputFocused = true;
		} else {
			commitMinInput();
			minInputFocused = false;
		}
	}

	private void loseMinFocus() {
		if (minInputFocused) {
			commitMinInput();
			minInputFocused = false;
		}
	}

	private void stepMin(RerollPanelLayout layout, double delta) {
		RollRange range = currentTargetRange();
		thresholdEnabled = true;
		if (range.numeric()) {
			thresholdValue = Mth.clamp(thresholdValue + delta, range.min(), range.max());
		} else {
			thresholdValue = Math.max(0.0, thresholdValue + delta);
		}
		minInputText = formatDisplay(thresholdValue, false);
		minInputFocused = false;
	}

	private double currentStep() {
		double step = currentTargetRange().step();
		return step > 0.0 ? step : 1.0;
	}

	// ------------------------------------------------------------------ model

	public RollRange currentTargetRange() {
		ItemStack gear = stationGear();
		if (gear.isEmpty() || !(Minecraft.getInstance().screen instanceof VaultArtisanStationScreen screen)) {
			return new RollRange(0, 0, 0, false, false);
		}
		List<GearModificationAction> operations = operations(screen);
		if (operations.isEmpty()) {
			return new RollRange(0, 0, 0, false, false);
		}
		GearModificationAction operation = operations.get(Math.min(operationIndex, operations.size() - 1));
		List<Candidate> candidates = candidates(gear, operation);
		if (candidates.isEmpty() || targetIndex >= candidates.size()) {
			return new RollRange(0, 0, 0, false, false);
		}
		return ModifierCatalog.rollRange(gear, candidates.get(targetIndex).id(),
				ModifierCatalog.scopeOfOperation(operation.modification().getRegistryName()));
	}

	public void clampSelections(List<GearModificationAction> operations, ItemStack gear) {
		if (operations.isEmpty()) {
			operationIndex = 0;
			targetIndex = 0;
			return;
		}
		if (operationIndex >= operations.size()) {
			operationIndex = 0;
			targetIndex = 0;
		}
		GearModificationAction operation = operations.get(operationIndex);
		List<Candidate> candidates = candidates(gear, operation);
		if (candidates.isEmpty()) {
			targetIndex = 0;
			return;
		}
		if (targetIndex >= candidates.size()) {
			targetIndex = 0;
		}
	}

	private ResourceLocation currentTargetId(ItemStack gear, GearModificationAction operation) {
		List<Candidate> candidates = candidates(gear, operation);
		if (candidates.isEmpty()) {
			return null;
		}
		if (targetIndex >= candidates.size()) {
			targetIndex = 0;
		}
		return candidates.get(targetIndex).id();
	}

	// ------------------------------------------------------------------ misc

	private static ItemStack stationGear() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen instanceof VaultArtisanStationScreen screen) {
			return ((VaultArtisanStationContainer) screen.getMenu()).getGearInputSlot().getItem();
		}
		return ItemStack.EMPTY;
	}

	private static int potentialCost(GearModificationAction operation) {
		try {
			if (operation == null || operation.modification() == null
					|| ModConfigs.VAULT_GEAR_MODIFICATION_CONFIG == null) {
				return 0;
			}
			return ModConfigs.VAULT_GEAR_MODIFICATION_CONFIG.getPotentialUsed(operation.modification());
		} catch (Exception ignored) {
			return 0;
		}
	}

	private String displayOperationName(GearModificationAction operation) {
		try {
			if (operation != null && operation.modification() != null && operation.modification().getDisplayStack() != null) {
				String name = operation.modification().getDisplayStack().getHoverName().getString();
				if (name != null && !name.isBlank()) {
					return name;
				}
			}
		} catch (Exception ignored) {
		}
		ResourceLocation id = operation == null || operation.modification() == null ? null
				: operation.modification().getRegistryName();
		return id == null ? "?" : ModifierCatalog.humanizeId(id.getPath());
	}

	public static String stopReasonText(StopReason reason) {
		return switch (reason) {
			case SUCCESS -> "target rolled";
			case NO_GEAR -> "gear removed";
			case OUT_OF_MATERIALS -> "no materials";
			case OUT_OF_POTENTIAL -> "out of potential";
			case INVALID_TARGET -> "target not rollable";
			case MAX_ROLLS -> "max rolls";
			case TIMEOUT -> "no roll detected";
			case SCREEN_CLOSED -> "station closed";
			case STOPPED -> "stopped";
		};
	}

	public static String truncate(String text, int maxChars) {
		return text.length() <= maxChars ? text : text.substring(0, Math.max(1, maxChars - 1)) + "\u2026";
	}

	public static String formatDisplay(double value, boolean percent) {
		String number;
		if (value == Math.rint(value)) {
			number = String.valueOf((long) value);
		} else {
			number = String.format("%.2f", value);
			number = number.replaceAll("0$", "").replaceAll("\\.$", "");
		}
		return number + (percent ? "%" : "");
	}

	private static Double safeParseOrNull(String text) {
		try {
			return Double.parseDouble(text);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	// ----------------------------------------------------------------- tooltip

	/** Records a hover popover for text that does not fit its row (full text on hover). */
	private void hoverTooltip(String fullText, int mouseX, int mouseY) {
		if (fullText == null || fullText.isEmpty()) {
			return;
		}
		pendingTooltip = fullText;
		tooltipX = mouseX;
		tooltipY = mouseY;
	}

	/** Draws the recorded popover (racing the cursor, clamped to the screen) after the panel frame. */
	private void drawTooltip(PoseStack poseStack) {
		if (pendingTooltip == null) {
			return;
		}
		net.minecraft.client.gui.Font font = font();
		List<String> lines = wrapText(pendingTooltip, 190);
		int boxWidth = 0;
		for (String line : lines) {
			boxWidth = Math.max(boxWidth, font.width(line));
		}
		boxWidth += 8;
		int boxHeight = lines.size() * font.lineHeight + 8;
		int screenWidth = Minecraft.getInstance().screen.width;
		int screenHeight = Minecraft.getInstance().screen.height;
		int boxX = Math.min(tooltipX + 8, Math.max(2, screenWidth - boxWidth - 4));
		int boxY = tooltipY + 10;
		if (boxY + boxHeight > screenHeight - 4) {
			boxY = Math.max(2, tooltipY - boxHeight - 6);
		}
		GuiComponent.fill(poseStack, boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xF0101010);
		GuiComponent.fill(poseStack, boxX, boxY, boxX + boxWidth, boxY + 1, BORDER_COLOR);
		GuiComponent.fill(poseStack, boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, BORDER_COLOR);
		GuiComponent.fill(poseStack, boxX, boxY, boxX + 1, boxY + boxHeight, BORDER_COLOR);
		GuiComponent.fill(poseStack, boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, BORDER_COLOR);
		int lineY = boxY + 4;
		for (String line : lines) {
			drawString(poseStack, line, boxX + 3, lineY, TEXT_COLOR);
			lineY += font.lineHeight;
		}
	}

	/** Word-wraps {@code text} so no line is wider than {@code maxWidth} pixels. */
	private static List<String> wrapText(String text, int maxWidth) {
		List<String> lines = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return lines;
		}
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			if (!line.isEmpty() && font().width(line + " " + word) > maxWidth) {
				lines.add(line.toString());
				line.setLength(0);
			}
			if (line.isEmpty() && font().width(word) > maxWidth) {
				String rest = word;
				while (font().width(rest) > maxWidth) {
					int cut = Math.max(1, rest.length() - 1);
					while (cut > 1 && font().width(rest.substring(0, cut)) > maxWidth) {
						cut--;
					}
					lines.add(rest.substring(0, cut));
					rest = rest.substring(cut);
				}
				line.append(rest);
			} else {
				if (!line.isEmpty()) {
					line.append(' ');
				}
				line.append(word);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	private static net.minecraft.client.gui.Font font() {
		return Minecraft.getInstance().font;
	}

	private static void drawString(PoseStack poseStack, String text, int x, int y, int color) {
		font().draw(poseStack, text, x, y, color);
	}

	private static void drawCentered(PoseStack poseStack, String text, int centerX, int y, int color) {
		font().draw(poseStack, text, centerX - font().width(text) / 2.0F, y, color);
	}

	private static void drawRight(PoseStack poseStack, String text, int rightX, int y, int color) {
		font().draw(poseStack, text, rightX - font().width(text), y, color);
	}

	/** Small 5x3 triangle, used for the dropdown marker and scroll arrows. */
	private static void drawTriangle(PoseStack poseStack, int centerX, int topY, boolean up, int color) {
		for (int i = 0; i < 3; i++) {
			int rowY = up ? topY + (2 - i) : topY + i;
			GuiComponent.fill(poseStack, centerX - i, rowY, centerX + i + 1, rowY + 1, color);
		}
	}
}