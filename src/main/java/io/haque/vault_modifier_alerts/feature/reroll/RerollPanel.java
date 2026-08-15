package io.haque.vault_modifier_alerts.feature.reroll;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.reroll.AutoRerollEngine.StopReason;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.Candidate;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.OperationScope;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.RollRange;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.RollTarget;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout.Hit;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout.HitType;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout.Rect;
import iskallia.vault.client.gui.screen.block.VaultArtisanStationScreen;
import iskallia.vault.container.VaultArtisanStationContainer;
import iskallia.vault.gear.crafting.VaultGearCraftingHelper;
import iskallia.vault.gear.modification.GearModificationAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
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
		NONE, OPERATION, MODIFIER, TARGETS
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
	private final List<RollTarget> targets = new ArrayList<>();
	private int focusedTarget = -1;
	private AutoRerollEngine.StopCondition stopCondition = AutoRerollEngine.StopCondition.ANY;
	private String minInputText = "";
	private boolean minInputFocused;
	private DropdownMode dropdownMode = DropdownMode.NONE;
	private int dropdownScroll;
	private int dropdownMaxRows = RerollPanelLayout.DEFAULT_DROPDOWN_ROWS;
	private int lastX;
	private int lastY;
	private int lastW;
	private int lastH;
	private int currentWidth = RerollPanelLayout.WIDTH;
	private ItemStack lastSeenGear = ItemStack.EMPTY;
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

	private double thresholdValue() {
		RollTarget target = focused();
		return target == null ? 0.0 : target.thresholdValue();
	}

	/** Selection snapshot used by the Start button and the /vma reroll command. */
	public record RerollSelection(ResourceLocation operationId, List<RollTarget> targets,
			AutoRerollEngine.StopCondition stopCondition) {
	}

	/** @return the current panel selection, or null if no station screen / no target chosen. */
	public RerollSelection currentSelection() {
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.screen instanceof VaultArtisanStationScreen screen)) {
			return null;
		}
		List<GearModificationAction> operations = operations(screen);
		if (operations.isEmpty()) {
			return null;
		}
		clampSelections(operations, stationGear());
		if (targets.isEmpty()) {
			return null;
		}
		return new RerollSelection(operations.get(operationIndex).modification().getRegistryName(),
				List.copyOf(targets), stopCondition);
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
		minInputFocused = false;
	}

	/**
	 * Toggles a candidate from the picker in or out of the watch list. Adding
	 * appends with no threshold and focuses it; removing shifts focus to a
	 * remaining target.
	 */
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

	/**
	 * Commits the field text. A valid number is always kept: it is clamped to
	 * the focused target's roll range when that range is known, and used as-is
	 * when the range could not be read (the engine compares "at least X" either
	 * way).
	 */
	public void commitMinInput() {
		RollTarget target = focused();
		if (target == null) {
			return;
		}
		if (minInputText.isEmpty()) {
			setFocusedThreshold(false, 0.0);
			return;
		}
		RollRange range = currentTargetRange();
		String text = minInputText.endsWith(".") ? minInputText.substring(0, minInputText.length() - 1) : minInputText;
		Double parsed = safeParseOrNull(text);
		if (parsed == null) {
			minInputText = "";
			setFocusedThreshold(false, 0.0);
			return;
		}
		setFocusedThreshold(true, range.numeric() ? Mth.clamp(parsed, range.min(), range.max()) : parsed);
		minInputText = formatDisplay(thresholdValue(), false);
	}

	private RollTarget focused() {
		return focusedTarget >= 0 && focusedTarget < targets.size() ? targets.get(focusedTarget) : null;
	}

	private void setFocusedThreshold(boolean enabled, double value) {
		RollTarget target = focused();
		if (target != null) {
			targets.set(focusedTarget, new RollTarget(target.id(), enabled, value));
		}
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
		currentWidth = width;
		updateDropdownCapacity(screen, x, y);
		List<GearModificationAction> operations = operations(screen);
		if (operations.isEmpty()) {
			dropdownMode = DropdownMode.NONE;
		}
		ItemStack gear = stationGear();
		if (lastSeenGear.isEmpty() && !gear.isEmpty()) {
			resetSelection();
		}
		lastSeenGear = gear.copy();
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
		drawModifierRow(poseStack, layout, mouseX, mouseY);
		drawTargetsRow(poseStack, layout, x, width, mouseX, mouseY);
		drawMinRow(poseStack, layout, x, width, mouseX, mouseY);
		drawRangeRow(poseStack, layout, x, width, mouseX, mouseY);
		drawPotentialRow(poseStack, layout, x, width, operation);
		drawToggleRow(poseStack, layout, x, "Auto-reroll", enabled, layout.rerollToggleY, mouseX, mouseY);
		drawToggleRow(poseStack, layout, x, "Auto-reset potential",
				VmaClientConfigs.isAutoResetPotentialEnabled(), layout.resetToggleY, mouseX, mouseY);
		drawButton(poseStack, layout, engine, canStart(), mouseX, mouseY);
		drawStatus(poseStack, layout, candidates.isEmpty(), mouseX, mouseY);
		drawCounterRow(poseStack, layout, engine);

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
			case TARGETS_ROW -> {
				loseMinFocus();
				toggleDropdown(DropdownMode.TARGETS, targets.size());
			}
			case TARGETS_CHIP -> {
				loseMinFocus();
				closeDropdown();
				stopCondition = stopCondition == AutoRerollEngine.StopCondition.ANY
						? AutoRerollEngine.StopCondition.ALL : AutoRerollEngine.StopCondition.ANY;
			}
			case TARGETS_CLEAR -> {
				loseMinFocus();
				closeDropdown();
				if (AutoRerollEngine.getInstance().isRunning()) {
					AutoRerollEngine.getInstance().stop(StopReason.STOPPED, false);
				}
				targets.clear();
				focusedTarget = -1;
				minInputText = "";
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
				} else if (canStart()) {
					RerollSelection selection = currentSelection();
					if (selection != null) {
						engine.start(selection.operationId(), selection.targets(), selection.stopCondition());
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
						toggleTarget(targets.get(realIndex).id());
						closeDropdown();
					}
				} else if (dropdownMode == DropdownMode.TARGETS && realIndex >= 0 && realIndex < this.targets.size()) {
					Rect itemRect = layout.dropdownItem(hit.index());
					if (itemRect != null && mouseX >= itemRect.x() + itemRect.width() - 16) {
						removeTarget(realIndex);
					} else {
						focusTarget(realIndex);
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
		if (dropdownMode == DropdownMode.TARGETS) {
			return targets.size();
		}
		int safeIndex = Math.min(operationIndex, operations.size() - 1);
		return candidates(stationGear(), operations.get(safeIndex)).size();
	}

	private void clampDropdownScroll(int operationCount, int candidateCount) {
		int count = dropdownMode == DropdownMode.OPERATION ? operationCount
				: (dropdownMode == DropdownMode.TARGETS ? targets.size()
						: (dropdownMode == DropdownMode.MODIFIER ? candidateCount : 0));
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
		int available = screen.height - (y + new RerollPanelLayout(x, y, currentWidth, false, 0, 0, 0).baseHeight
				+ RerollPanelLayout.DROPDOWN_HEADER_H);
		dropdownMaxRows = Mth.clamp(available / RerollPanelLayout.DROPDOWN_ITEM_H,
				RerollPanelLayout.MIN_DROPDOWN_ROWS, RerollPanelLayout.MAX_DROPDOWN_ROWS);
	}

	private RerollPanelLayout computeLayout(int x, int y) {
		int count = dropdownCount();
		return new RerollPanelLayout(x, y, currentWidth, dropdownMode != DropdownMode.NONE, count, dropdownScroll,
				dropdownMaxRows);
	}

	/**
	 * Panel width that fits beside the station window: the full width when
	 * either side has room, otherwise the larger side's room clamped to
	 * {@link RerollPanelLayout#MIN_WIDTH}.
	 */
	public static int computeWidth(int screenWidth, int guiLeft, int guiRight) {
		int leftRoom = guiLeft - RerollPanelLayout.MARGIN;
		int rightRoom = screenWidth - (guiRight + RerollPanelLayout.MARGIN);
		if (leftRoom >= RerollPanelLayout.WIDTH || rightRoom >= RerollPanelLayout.WIDTH) {
			return RerollPanelLayout.WIDTH;
		}
		return Mth.clamp(Math.max(leftRoom, rightRoom), RerollPanelLayout.MIN_WIDTH, RerollPanelLayout.WIDTH);
	}

	/** Clears every gear-specific selection (targets, thresholds, focus, operation, open dropdown). */
	private void resetSelection() {
		targets.clear();
		focusedTarget = -1;
		minInputText = "";
		minInputFocused = false;
		operationIndex = 0;
		closeDropdown();
	}

	private boolean canStart() {
		return VmaClientConfigs.isAutoRerollEnabled() && !targets.isEmpty();
	}

	/** True when the cursor lies inside the panel's horizontal range and the given row band. */
	private static boolean rowHovered(RerollPanelLayout layout, int mouseX, int mouseY, int y, int h) {
		return mouseX >= layout.x && mouseX < layout.x + layout.width && mouseY >= y && mouseY < y + h;
	}

	// ------------------------------------------------------------------ draw

	private void drawPanelFrame(PoseStack poseStack, RerollPanelLayout layout) {
		GuiComponent.fill(poseStack, layout.x, layout.y, layout.x + layout.width,
				layout.y + layout.totalHeight, BG_COLOR);
		GuiComponent.fill(poseStack, layout.x, layout.y, layout.x + layout.width, layout.y + 2, GOLD_COLOR);
		GuiComponent.fill(poseStack, layout.x, layout.y + layout.totalHeight - 1, layout.x + layout.width,
				layout.y + layout.totalHeight, BORDER_COLOR);
		GuiComponent.fill(poseStack, layout.x, layout.y + 2, layout.x + 1, layout.y + layout.totalHeight, BORDER_COLOR);
		GuiComponent.fill(poseStack, layout.x + layout.width - 1, layout.y + 2,
				layout.x + layout.width, layout.y + layout.totalHeight, BORDER_COLOR);
	}

	/**
	 * A selectable row: label left, one-line value, dropdown marker right.
	 * Highlighted while its dropdown is open, hovered otherwise.
	 */
	private void drawRow(PoseStack poseStack, RerollPanelLayout layout, String label, String value, int y, int mouseX,
			int mouseY, boolean open) {
		boolean hovered = rowHovered(layout, mouseX, mouseY, y, RerollPanelLayout.ROW_H);
		if (open) {
			GuiComponent.fill(poseStack, layout.x, y, layout.x + layout.width,
					y + RerollPanelLayout.ROW_H, HIGHLIGHT_COLOR);
		} else if (hovered) {
			GuiComponent.fill(poseStack, layout.x, y, layout.x + layout.width,
					y + RerollPanelLayout.ROW_H, HOVER_COLOR);
		}
		int color = VmaClientConfigs.isAutoRerollEnabled() ? TEXT_COLOR : DISABLED_COLOR;
		drawString(poseStack, label, layout.x + RerollPanelLayout.PAD_X, y + 3, MUTED_COLOR);
		drawString(poseStack, value, layout.x + 62, y + 3, color);
		drawTriangle(poseStack, layout.x + layout.width - 8, y + 6, false, color);
		if (hovered && layout.x + 62 + font().width(value) > layout.x + layout.width
				- RerollPanelLayout.PAD_X) {
			hoverTooltip(value, mouseX, mouseY);
		}
	}

	/**
	 * Modifier row: the "add a target" picker. The dropdown lists every
	 * rollable modifier; already-watched ones carry a checkmark and click toggles.
	 */
	private void drawModifierRow(PoseStack poseStack, RerollPanelLayout layout, int mouseX, int mouseY) {
		boolean hovered = rowHovered(layout, mouseX, mouseY, layout.modifierY, RerollPanelLayout.ROW_H);
		if (dropdownMode == DropdownMode.MODIFIER) {
			GuiComponent.fill(poseStack, layout.x, layout.modifierY, layout.x + layout.width,
					layout.modifierY + RerollPanelLayout.ROW_H, HIGHLIGHT_COLOR);
		} else if (hovered) {
			GuiComponent.fill(poseStack, layout.x, layout.modifierY, layout.x + layout.width,
					layout.modifierY + RerollPanelLayout.ROW_H, HOVER_COLOR);
		}
		int color = VmaClientConfigs.isAutoRerollEnabled() ? TEXT_COLOR : DISABLED_COLOR;
		drawString(poseStack, "Modifier", layout.x + RerollPanelLayout.PAD_X, layout.modifierY + 3, MUTED_COLOR);
		String placeholder = targets.isEmpty() ? "add a modifier..." : "+ add modifier";
		drawString(poseStack, placeholder, layout.x + 62, layout.modifierY + 3, color);
		drawTriangle(poseStack, layout.x + layout.width - 8, layout.modifierY + 6, false, color);
	}

	/**
	 * Targets row: the focused watch-list target, its count, and the clickable
	 * stop-condition chip ("any"/"all") on the right.
	 */
	private void drawTargetsRow(PoseStack poseStack, RerollPanelLayout layout, int x, int width, int mouseX,
			int mouseY) {
		boolean hovered = rowHovered(layout, mouseX, mouseY, layout.targetsY, RerollPanelLayout.ROW_H);
		boolean chipHovered = hovered && mouseX >= x + width - 24;
		boolean clearHovered = hovered && mouseX >= x + width - 44 && mouseX < x + width - 26;
		boolean addHovered = hovered && !chipHovered && !clearHovered;
		if (dropdownMode == DropdownMode.TARGETS) {
			GuiComponent.fill(poseStack, layout.x, layout.targetsY, layout.x + layout.width,
					layout.targetsY + RerollPanelLayout.ROW_H, HIGHLIGHT_COLOR);
		} else if (addHovered) {
			GuiComponent.fill(poseStack, layout.x, layout.targetsY, layout.x + layout.width,
					layout.targetsY + RerollPanelLayout.ROW_H, HOVER_COLOR);
		}
		drawString(poseStack, "Targets", layout.x + RerollPanelLayout.PAD_X, layout.targetsY + 3, MUTED_COLOR);
		String value;
		RollTarget focused = focused();
		if (focused == null) {
			value = targets.isEmpty() ? "none" : "?";
		} else {
			value = targetName(focused.id());
			if (focused.thresholdEnabled()) {
				value += " >=" + formatDisplay(focused.thresholdValue(), currentTargetRange().percent());
			}
		}
		String full = value;
		int maxChars = (layout.width - 62 - 50 - RerollPanelLayout.PAD_X) / 7;
		boolean truncated = value.length() > maxChars;
		if (truncated) {
			value = truncate(value, maxChars);
		}
		int color = VmaClientConfigs.isAutoRerollEnabled() ? TEXT_COLOR : DISABLED_COLOR;
		drawString(poseStack, value, layout.x + 62, layout.targetsY + 3, color);
		if (hovered && !chipHovered && !clearHovered && truncated) {
			hoverTooltip(full, mouseX, mouseY);
		}
		GuiComponent.fill(poseStack, x + width - 44, layout.targetsY, x + width - 26,
				layout.targetsY + RerollPanelLayout.ROW_H, clearHovered ? HOVER_COLOR : 0xF0181818);
		drawCentered(poseStack, "x", x + width - 35, layout.targetsY + 3,
				clearHovered ? WARN_COLOR : (VmaClientConfigs.isAutoRerollEnabled() ? MUTED_COLOR : DISABLED_COLOR));
		if (clearHovered) {
			hoverTooltip("Clear all targets", mouseX, mouseY);
		}
		GuiComponent.fill(poseStack, x + width - 24, layout.targetsY, x + width,
				layout.targetsY + RerollPanelLayout.ROW_H,
				chipHovered ? HOVER_COLOR : 0xF0181818);
		drawString(poseStack, stopCondition == AutoRerollEngine.StopCondition.ANY ? "any" : "all",
				x + width - 22, layout.targetsY + 3,
				chipHovered ? GOLD_COLOR : (VmaClientConfigs.isAutoRerollEnabled() ? MUTED_COLOR : DISABLED_COLOR));
	}

	/** Last row: how many times crafting potential was auto-reset during the run. */
	private void drawCounterRow(PoseStack poseStack, RerollPanelLayout layout, AutoRerollEngine engine) {
		if (!engine.isRunning() || !VmaClientConfigs.isAutoResetPotentialEnabled()) {
			return;
		}
		int resets = engine.potentialResetsThisSession();
		drawString(poseStack, "Potential reset x " + resets, layout.x + RerollPanelLayout.PAD_X, layout.counterY + 3,
				MUTED_COLOR);
	}

	private void drawMinRow(PoseStack poseStack, RerollPanelLayout layout, int x, int width, int mouseX, int mouseY) {
		RollRange range = currentTargetRange();
		boolean numeric = range.numeric();
		boolean enabled = VmaClientConfigs.isAutoRerollEnabled();
		boolean hasTarget = focused() != null;
		boolean hoveredDec = rowHovered(layout, mouseX, mouseY, layout.minY, RerollPanelLayout.ROW_H)
				&& mouseX < x + 16;
		boolean hoveredInc = rowHovered(layout, mouseX, mouseY, layout.minY, RerollPanelLayout.ROW_H)
				&& mouseX >= x + width - 16;
		boolean hoveredField = rowHovered(layout, mouseX, mouseY, layout.minY, RerollPanelLayout.ROW_H)
				&& mouseX >= layout.minFieldLeft() && mouseX < layout.minFieldRight();
		boolean hasThreshold = hasTarget && focused().thresholdEnabled();
		int buttonColor = enabled ? (hoveredDec ? TEXT_COLOR : MUTED_COLOR) : DISABLED_COLOR;
		GuiComponent.fill(poseStack, x + 2, layout.minY + 2, x + 14, layout.minY + 12,
				enabled ? (hoveredDec ? HOVER_COLOR : 0xFF303030) : 0xFF222222);
		GuiComponent.fill(poseStack, x + width - 14, layout.minY + 2, x + width - 2, layout.minY + 12,
				enabled ? (hoveredInc ? HOVER_COLOR : 0xFF303030) : 0xFF222222);
		drawCentered(poseStack, "-", x + 8, layout.minY + 3, buttonColor);
		drawCentered(poseStack, "+", x + width - 8, layout.minY + 3, buttonColor);
		drawString(poseStack, "Min", x + 22, layout.minY + 3, MUTED_COLOR);
		if (minInputFocused || hasThreshold) {
			GuiComponent.fill(poseStack, layout.minFieldLeft(), layout.minY, layout.minFieldRight(),
					layout.minY + RerollPanelLayout.ROW_H, minInputFocused ? FIELD_FOCUS_COLOR : FIELD_BG_COLOR);
		}
		String shown;
		if (!hasTarget) {
			shown = "-";
		} else if (minInputFocused) {
			shown = minInputText + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
		} else if (hasThreshold) {
			shown = formatDisplay(focused().thresholdValue(), numeric && range.percent());
		} else {
			shown = "any";
		}
		drawString(poseStack, shown, layout.minFieldLeft() + 2, layout.minY + 3, hasTarget
				? (hoveredField && !minInputFocused ? GOLD_COLOR : TEXT_COLOR) : DISABLED_COLOR);
	}

	private void drawRangeRow(PoseStack poseStack, RerollPanelLayout layout, int x, int width, int mouseX, int mouseY) {
		RollRange range = currentTargetRange();
		String text = range.numeric() ? "Range: " + range.displayText() : "Range: ?";
		String full = text;
		int maxChars = (layout.width - RerollPanelLayout.PAD_X * 2) / 7;
		if (text.length() > maxChars) {
			text = truncate(text, maxChars);
			if (rowHovered(layout, mouseX, mouseY, layout.rangeY, RerollPanelLayout.ROW_H)) {
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
		int cost = potentialCost(gear, operation);
		String left = "Potential " + potential + "/" + max;
		String right = cost > 0 && potential >= cost ? "~" + potential / cost + " rolls" : "";
		int color = potential > 0 ? (VmaClientConfigs.isAutoRerollEnabled() ? TEXT_COLOR : DISABLED_COLOR) : WARN_COLOR;
		drawString(poseStack, left, x + RerollPanelLayout.PAD_X, layout.potentialY + 3, color);
		if (!right.isEmpty()) {
			drawRight(poseStack, right, x + width - RerollPanelLayout.PAD_X, layout.potentialY + 3, MUTED_COLOR);
		}
	}

	private void drawToggleRow(PoseStack poseStack, RerollPanelLayout layout, int x, String label, boolean enabled,
			int y, int mouseX, int mouseY) {
		boolean hovered = rowHovered(layout, mouseX, mouseY, y, RerollPanelLayout.ROW_H);
		if (hovered) {
			GuiComponent.fill(poseStack, layout.x, y, layout.x + layout.width,
					y + RerollPanelLayout.ROW_H, HOVER_COLOR);
		}
		drawString(poseStack, "[" + (enabled ? "x" : " ") + "]", x + RerollPanelLayout.PAD_X, y + 3,
				enabled ? ACCENT_COLOR : DISABLED_COLOR);
		drawString(poseStack, label, x + 30, y + 3, enabled ? TEXT_COLOR : DISABLED_COLOR);
	}

	private void drawButton(PoseStack poseStack, RerollPanelLayout layout, AutoRerollEngine engine, boolean canStart,
			int mouseX, int mouseY) {
		boolean running = engine.isRunning();
		boolean hovered = rowHovered(layout, mouseX, mouseY, layout.buttonY, RerollPanelLayout.BUTTON_H);
		if (running || canStart) {
			GuiComponent.fill(poseStack, layout.x + RerollPanelLayout.PAD_X, layout.buttonY,
					layout.x + layout.width - RerollPanelLayout.PAD_X,
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
		drawCentered(poseStack, label, layout.x + layout.width / 2, layout.buttonY + 3, color);
	}

	private void drawStatus(PoseStack poseStack, RerollPanelLayout layout, boolean noCandidates, int mouseX, int mouseY) {
		AutoRerollEngine engine = AutoRerollEngine.getInstance();
		String text;
		int color;
		String full;
		if (engine.isRunning()) {
			text = runningStatus(engine);
			color = ACCENT_COLOR;
			full = targetDetail(engine);
		} else if (engine.stopReason() != null) {
			StringBuilder suffix = new StringBuilder();
			if (engine.rolls() > 0) {
				suffix.append(" - ").append(engine.rolls()).append(" rolls");
			}
			int resets = engine.potentialResetsThisSession();
			if (resets > 0) {
				suffix.append(suffix.length() == 0 ? " - " : ", ").append(resets)
						.append(" potential reset").append(resets == 1 ? "" : "s");
			}
			boolean success = engine.stopReason() == StopReason.SUCCESS;
			String reason = success && engine.stopCondition() == AutoRerollEngine.StopCondition.ALL
					? "all targets met" : stopReasonText(engine.stopReason());
			text = "Stopped: " + reason + suffix;
			color = success ? ACCENT_COLOR : WARN_COLOR;
			full = text;
		} else if (!VmaClientConfigs.isAutoRerollEnabled()) {
			text = "Auto-reroll disabled";
			color = DISABLED_COLOR;
			full = text;
		} else if (stationGear().isEmpty()) {
			text = "No gear in station";
			color = MUTED_COLOR;
			full = text;
		} else if (noCandidates) {
			text = "No rollable modifiers";
			color = MUTED_COLOR;
			full = text;
		} else if (targets.isEmpty()) {
			text = "Add a target modifier";
			color = MUTED_COLOR;
			full = text;
		} else if (targets.size() == 1 && focused() != null && focused().thresholdEnabled()) {
			text = "Ready : goal at least " + formatDisplay(focused().thresholdValue(), currentTargetRange().percent());
			color = ACCENT_COLOR;
			full = text;
		} else if (targets.size() == 1) {
			text = "Ready : any roll";
			color = ACCENT_COLOR;
			full = text;
		} else {
			text = "Ready : " + targets.size() + " targets";
			color = ACCENT_COLOR;
			full = targetDetail(engine);
		}
		int maxChars = (layout.width - RerollPanelLayout.PAD_X * 2) / 7;
		if (text.length() > maxChars) {
			text = truncate(text, maxChars);
		}
		if (rowHovered(layout, mouseX, mouseY, layout.statusY, RerollPanelLayout.ROW_H) && !full.equals(text)) {
			hoverTooltip(full, mouseX, mouseY);
		}
		drawString(poseStack, text, layout.x + RerollPanelLayout.PAD_X, layout.statusY + 3, color);
	}

	/** Status text while a run is in progress: live value vs threshold, or met/passing counts. */
	private String runningStatus(AutoRerollEngine engine) {
		String base = "Rolling #" + engine.rolls();
		if (targets.size() <= 1) {
			RollTarget target = focused();
			double value = engine.currentValue(0);
			if (target != null && value > 0) {
				boolean percent = currentTargetRange().percent();
				if (target.thresholdEnabled()) {
					return base + " - " + formatDisplay(value, percent) + " >="
							+ formatDisplay(target.thresholdValue(), percent)
							+ (engine.isMet(0) ? " [x]" : " [ ]");
				}
				return base + " - " + formatDisplay(value, percent);
			}
			return base;
		}
		int met = engine.metCount();
		String label = engine.stopCondition() == AutoRerollEngine.StopCondition.ALL ? " met" : " passing";
		return base + " - " + met + "/" + targets.size() + label;
	}

	/** Per-target detail line: name, current value, threshold and pass state for every watched target. */
	private String targetDetail(AutoRerollEngine engine) {
		if (targets.isEmpty()) {
			return "";
		}
		StringBuilder detail = new StringBuilder();
		for (int i = 0; i < targets.size(); i++) {
			RollTarget target = targets.get(i);
			if (i > 0) {
				detail.append("  ");
			}
			detail.append(targetName(target.id()));
			double value = engine.currentValue(i);
			boolean percent = rollRangeFor(target).percent();
			if (target.thresholdEnabled()) {
				detail.append(" ").append(value > 0 ? formatDisplay(value, percent) : "-").append(" >=")
						.append(formatDisplay(target.thresholdValue(), percent));
				if (engine.isRunning()) {
					detail.append(engine.isMet(i) ? " [x]" : " [ ]");
				}
			} else if (value > 0) {
				detail.append(" ").append(formatDisplay(value, percent));
				if (engine.isRunning()) {
					detail.append(" [x]");
				}
			}
		}
		return detail.toString();
	}

	private void drawDropdown(PoseStack poseStack, RerollPanelLayout layout, List<GearModificationAction> operations,
			List<Candidate> candidates, int mouseX, int mouseY) {
		boolean operationDropdown = dropdownMode == DropdownMode.OPERATION;
		boolean targetDropdown = dropdownMode == DropdownMode.TARGETS;
		int count;
		List<String> names;
		if (operationDropdown) {
			names = operations.stream().map(this::displayOperationName).toList();
			count = operations.size();
		} else if (targetDropdown) {
			names = targets.stream().map(t -> targetName(t.id())).toList();
			count = targets.size();
		} else {
			names = candidates.stream().map(Candidate::displayName).toList();
			count = candidates.size();
		}
		int visible = layout.dropdownVisibleItems;
		if (visible == 0) {
			return;
		}
		GuiComponent.fill(poseStack, layout.x, layout.dropdownY, layout.x + layout.width,
				layout.dropdownY + layout.dropdownHeight, 0xF0181818);
		GuiComponent.fill(poseStack, layout.x, layout.dropdownY, layout.x + layout.width,
				layout.dropdownY + 1, GOLD_COLOR);
		String header = operationDropdown ? "Operations" : (targetDropdown ? "Targets" : "Modifiers");
		drawCentered(poseStack, header, layout.x + layout.width / 2, layout.dropdownY + 3, GOLD_COLOR);
		boolean scrollable = count > visible;
		drawTriangle(poseStack, layout.x + 7, layout.dropdownY + 6, true,
				scrollable && dropdownScroll > 0 ? TEXT_COLOR : DISABLED_COLOR);
		drawTriangle(poseStack, layout.x + layout.width - 7, layout.dropdownY + 6, false,
				scrollable && dropdownScroll < count - visible ? TEXT_COLOR : DISABLED_COLOR);

		for (int slot = 0; slot < visible; slot++) {
			int index = dropdownScroll + slot;
			if (index >= count) {
				break;
			}
			Rect rect = layout.dropdownItem(slot);
			boolean removeZone = targetDropdown && mouseX >= rect.x() + rect.width() - 16 && mouseY >= rect.y()
					&& mouseY < rect.y() + rect.height();
			boolean hovered = mouseX >= rect.x() && mouseX < rect.x() + rect.width() && mouseY >= rect.y()
					&& mouseY < rect.y() + rect.height();
			boolean current = operationDropdown ? index == operationIndex
					: (targetDropdown ? index == focusedTarget : false);
			if (hovered) {
				GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(),
						removeZone ? 0xFF4A1F1F : HOVER_COLOR);
			}
			String name = names.get(index);
			String range = "";
			if (!operationDropdown && !targetDropdown && index < candidates.size()) {
				RollRange rollRange = rollRangeOf(candidates.get(index));
				range = rollRange.displayText();
			}
			if (operationDropdown && index < operations.size()) {
				int cost = potentialCost(stationGear(), operations.get(index));
				if (cost > 0) {
					range = cost + " potential";
				}
			}
			if (targetDropdown) {
				RollTarget target = targets.get(index);
				range = target.thresholdEnabled() ? "min " + formatDisplay(target.thresholdValue(), false) : "any";
			}
			int baseColor = current ? GOLD_COLOR : (VmaClientConfigs.isAutoRerollEnabled() ? TEXT_COLOR : DISABLED_COLOR);
			int rangeX = rect.x() + rect.width() - RerollPanelLayout.PAD_X;
			int rangeWidth = range.isEmpty() ? 0 : font().width(range);
			int nameMax = rect.x() + rect.width() - RerollPanelLayout.PAD_X - rangeWidth - 8;
			if (current || (!targetDropdown && !operationDropdown && isWatched(candidates.get(index).id()))) {
				drawString(poseStack, targetDropdown || operationDropdown ? ">" : "*", rect.x() + 2, rect.y() + 3,
						GOLD_COLOR);
			}
			if (targetDropdown && AutoRerollEngine.getInstance().isRunning() && index < targets.size()) {
				boolean met = AutoRerollEngine.getInstance().isMet(index);
				drawString(poseStack, met ? "[x]" : "[ ]", rect.x() + 2, rect.y() + 3,
						met ? ACCENT_COLOR : WARN_COLOR);
			}
			String fullName = name;
			String shownName = truncate(name, Math.max(8, (nameMax - rect.x()) / 7));
			if (!shownName.equals(fullName) && hovered) {
				hoverTooltip(fullName, mouseX, mouseY);
			}
			drawString(poseStack, shownName, rect.x() + 11, rect.y() + 3, baseColor);
			if (!range.isEmpty() && !(targetDropdown && removeZone)) {
				drawRight(poseStack, range, rangeX, rect.y() + 3, MUTED_COLOR);
			}
			if (removeZone) {
				drawCentered(poseStack, "x", rect.x() + rect.width() - 8, rect.y() + 3, WARN_COLOR);
			}
		}
	}

	private boolean isWatched(ResourceLocation id) {
		for (RollTarget target : targets) {
			if (target.id().equals(id)) {
				return true;
			}
		}
		return false;
	}

	/** Display name of a watched target: from the rollable candidates, else humanized id. */
	private String targetName(ResourceLocation id) {
		if (id == null) {
			return "?";
		}
		ItemStack gear = stationGear();
		if (!gear.isEmpty() && Minecraft.getInstance().screen instanceof VaultArtisanStationScreen screen) {
			List<GearModificationAction> operations = operations(screen);
			if (!operations.isEmpty()) {
				int safeIndex = Math.min(operationIndex, operations.size() - 1);
				for (Candidate candidate : candidates(gear, operations.get(safeIndex))) {
					if (candidate.id().equals(id)) {
						return candidate.displayName();
					}
				}
			}
		}
		return ModifierCatalog.humanizeId(ModifierCatalog.stripModPrefix(id.getPath()));
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
		if (dropdownMode != DropdownMode.NONE || focused() == null) {
			return;
		}
		if (!minInputFocused) {
			minInputText = focused().thresholdEnabled() ? formatDisplay(focused().thresholdValue(), false) : "";
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
		if (focused() == null) {
			return;
		}
		RollRange range = currentTargetRange();
		double value;
		if (range.numeric()) {
			value = Mth.clamp(thresholdValue() + delta, range.min(), range.max());
		} else {
			value = Math.max(0.0, thresholdValue() + delta);
		}
		setFocusedThreshold(true, value);
		minInputText = formatDisplay(thresholdValue(), false);
		minInputFocused = false;
	}

	private double currentStep() {
		double step = currentTargetRange().step();
		return step > 0.0 ? step : 1.0;
	}

	// ------------------------------------------------------------------ model

	public RollRange currentTargetRange() {
		return rollRangeFor(focused());
	}

	private RollRange rollRangeFor(RollTarget target) {
		if (target == null) {
			return new RollRange(0, 0, 0, false, false);
		}
		ItemStack gear = stationGear();
		if (gear.isEmpty() || !(Minecraft.getInstance().screen instanceof VaultArtisanStationScreen screen)) {
			return new RollRange(0, 0, 0, false, false);
		}
		List<GearModificationAction> operations = operations(screen);
		if (operations.isEmpty()) {
			return new RollRange(0, 0, 0, false, false);
		}
		GearModificationAction operation = operations.get(Math.min(operationIndex, operations.size() - 1));
		return ModifierCatalog.rollRange(gear, target.id(),
				ModifierCatalog.scopeOfOperation(operation.modification().getRegistryName()));
	}

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

	// ------------------------------------------------------------------ misc

	private static ItemStack stationGear() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen instanceof VaultArtisanStationScreen screen) {
			return ((VaultArtisanStationContainer) screen.getMenu()).getGearInputSlot().getItem();
		}
		return ItemStack.EMPTY;
	}

	private static int potentialCost(ItemStack gear, GearModificationAction operation) {
		try {
			if (gear == null || gear.isEmpty() || operation == null || operation.modification() == null) {
				return 0;
			}
			Player player = Minecraft.getInstance().player;
			if (player == null) {
				return 0;
			}
			return VaultGearCraftingHelper.getReducedPotential(gear, player, operation.modification());
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
			case EVALUATION_ERROR -> "evaluation error";
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