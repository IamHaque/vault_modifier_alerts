package io.haque.vault_modifier_alerts.feature.reroll;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.reroll.AutoRerollEngine.StopReason;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.Candidate;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.OperationScope;
import iskallia.vault.client.gui.screen.block.VaultArtisanStationScreen;
import iskallia.vault.container.VaultArtisanStationContainer;
import iskallia.vault.gear.modification.GearModificationAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Selection and threshold state for the auto-reroll panel, plus its rendering.
 * The panel itself is added to the Artisan Station screen as a real VH
 * framework element (RerollPanelElement); this class holds the model the
 * widgets read and write (operation/target pick, min-value threshold with the
 * strict "never exceed the target's max roll" guard) and draws/click-routes the
 * raw panel UI the element delegates to.
 */
public final class RerollPanel {

	public static final int PANEL_WIDTH = 150;
	public static final int PANEL_HEIGHT = 122;

	private static final int TITLE_Y = 3;
	private static final int ROWS_Y = 15;
	private static final int ROW_H = 11;
	private static final int CHECKBOX_ROW_H = 13;
	private static final int BUTTON_ROW_H = 12;
	private static final int MAX_INPUT_LENGTH = 6;

	private static final int BG_COLOR = 0xC0101010;
	private static final int BORDER_COLOR = 0xFF6B6B6B;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int MUTED_COLOR = 0xFFA0A0A0;
	private static final int ACCENT_COLOR = 0xFF55FF55;
	private static final int WARN_COLOR = 0xFFFF5555;
	private static final int FIELD_BG_COLOR = 0xFF2E2E2E;
	private static final int FIELD_FOCUS_COLOR = 0xFF484848;

	private static final RerollPanel INSTANCE = new RerollPanel();

	private boolean visible = true;
	private int operationIndex;
	private int targetIndex;
	private String minInputText = "";
	private boolean thresholdEnabled;
	private double thresholdValue;
	private boolean minInputFocused;

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
		}
	}

	public void toggleVisible() {
		visible = !visible;
		if (!visible) {
			minInputFocused = false;
		}
	}

	public boolean isMinInputFocused() {
		return minInputFocused;
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
	 * Commits the field text: clamps to the target's roll range.
	 */
	public void commitMinInput() {
		if (minInputText.isEmpty()) {
			thresholdEnabled = false;
			thresholdValue = 0;
			return;
		}
		ModifierCatalog.RollRange range = currentTargetRange();
		if (!range.numeric()) {
			minInputText = "";
			thresholdEnabled = false;
			thresholdValue = 0;
			return;
		}
		String text = minInputText.endsWith(".") ? minInputText.substring(0, minInputText.length() - 1) : minInputText;
		Double parsed = safeParseOrNull(text);
		if (parsed == null) {
			minInputText = "";
			thresholdEnabled = false;
			thresholdValue = 0;
			return;
		}
		thresholdEnabled = true;
		thresholdValue = Mth.clamp(parsed, range.min(), range.max());
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
	 * Key support for the focused min-value field, fed by the screen-level key
	 * event. Backspace edits, Enter/KP-Enter and Escape commit (Escape also
	 * drops focus), everything else passes through.
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

	/** Draws the whole panel at the given rect. Called by the framework element. */
	public void draw(VaultArtisanStationScreen screen, PoseStack poseStack, int x, int y, int width, int height,
			int mouseX, int mouseY) {
		GuiComponent.fill(poseStack, x, y, x + width, y + height, BG_COLOR);
		GuiComponent.fill(poseStack, x, y, x + width, y + 1, BORDER_COLOR);
		GuiComponent.fill(poseStack, x, y + height - 1, x + width, y + height, BORDER_COLOR);
		GuiComponent.fill(poseStack, x, y, x + 1, y + height, BORDER_COLOR);
		GuiComponent.fill(poseStack, x + width - 1, y, x + width, y + height, BORDER_COLOR);

		drawCentered(poseStack, "Auto-Reroll", x + width / 2, y + TITLE_Y, ACCENT_COLOR);

		List<GearModificationAction> operations = operations(screen);
		if (operations.isEmpty()) {
			drawString(poseStack, "No re-roll actions", x + 6, y + ROWS_Y, WARN_COLOR);
			return;
		}
		ItemStack gear = stationGear();
		clampSelections(operations, gear);
		AutoRerollEngine engine = AutoRerollEngine.getInstance();
		GearModificationAction operation = operations.get(operationIndex);
		List<Candidate> candidates = candidates(gear, operation);
		boolean blink = !minInputFocused || (System.currentTimeMillis() / 500) % 2 == 0;

		int rowY = y + ROWS_Y;
		drawSelectRow(poseStack, "Op: ", truncate(operation.modification().getDisplayStack().getHoverName().getString(),
				16), x, width, rowY);
		rowY += ROW_H;
		if (candidates.isEmpty()) {
			drawString(poseStack, "Tgt: none rollable", x + 6, rowY, WARN_COLOR);
		} else {
			drawSelectRow(poseStack, "Tgt: ", truncate(candidates.get(targetIndex).displayName(), 16), x, width,
					rowY);
		}
		rowY += ROW_H;
		drawMinRow(poseStack, x, width, rowY);
		rowY += ROW_H;
		drawString(poseStack, "Potential: " + ModifierCatalog.craftingPotential(gear) + "/"
				+ ModifierCatalog.maxCraftingPotential(gear), x + 6, rowY, MUTED_COLOR);
		rowY += ROW_H;
		boolean autoReset = VmaClientConfigs.isAutoResetPotentialEnabled();
		drawString(poseStack, "[" + (autoReset ? "x" : " ") + "] Auto-reset", x + 6, rowY, TEXT_COLOR);
		rowY += CHECKBOX_ROW_H;

		boolean running = engine.isRunning();
		String label = running ? "Stop" : "Start";
		int labelColor = running ? WARN_COLOR : ACCENT_COLOR;
		if (running || !candidates.isEmpty()) {
			GuiComponent.fill(poseStack, x + 6, rowY, x + width - 6, rowY + BUTTON_ROW_H, 0xFF303030);
			drawCentered(poseStack, label, x + width / 2, rowY + 1, labelColor);
		}
		rowY += BUTTON_ROW_H;

		if (running) {
			drawString(poseStack, "Rolling... (" + engine.rolls() + ")", x + 6, rowY, ACCENT_COLOR);
		} else if (engine.stopReason() != null) {
			drawString(poseStack, "Stopped: " + stopReasonText(engine.stopReason()), x + 6, rowY, WARN_COLOR);
		} else {
			drawString(poseStack, "Idle", x + 6, rowY, MUTED_COLOR);
		}
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
			return true;
		}
		ItemStack gear = stationGear();
		clampSelections(operations, gear);
		AutoRerollEngine engine = AutoRerollEngine.getInstance();

		int rowY = y + ROWS_Y;
		if (insideRow(rowY, ROW_H, mouseY)) {
			loseMinFocus();
			if (mouseX < x + 14) {
				selectOperation((operationIndex - 1 + operations.size()) % operations.size());
			} else if (mouseX >= x + width - 14) {
				selectOperation((operationIndex + 1) % operations.size());
			}
			return true;
		}
		rowY += ROW_H;
		GearModificationAction operation = operations.get(operationIndex);
		if (insideRow(rowY, ROW_H, mouseY)) {
			loseMinFocus();
			List<Candidate> candidates = candidates(gear, operation);
			if (!candidates.isEmpty()) {
				if (mouseX < x + 14) {
					selectTarget((targetIndex - 1 + candidates.size()) % candidates.size());
				} else if (mouseX >= x + width - 14) {
					selectTarget((targetIndex + 1) % candidates.size());
				}
			}
			return true;
		}
		rowY += ROW_H;
		if (insideRow(rowY, ROW_H, mouseY)) {
			ModifierCatalog.RollRange range = currentTargetRange();
			if (range.numeric() && mouseX < x + 10) {
				stepMin(-range.step());
			} else if (range.numeric() && mouseX >= x + width - 10) {
				stepMin(range.step());
			} else if (range.numeric()) {
				toggleMinFocus();
			}
			return true;
		}
		rowY += ROW_H;
		loseMinFocus();
		rowY += ROW_H;
		if (insideRow(rowY, CHECKBOX_ROW_H, mouseY)) {
			VmaClientConfigs.setAutoResetPotential(!VmaClientConfigs.isAutoResetPotentialEnabled());
			return true;
		}
		rowY += CHECKBOX_ROW_H;
		boolean hasTargets = !candidates(gear, operation).isEmpty();
		if (insideRow(rowY, BUTTON_ROW_H, mouseY) && (engine.isRunning() || hasTargets)) {
			if (engine.isRunning()) {
				engine.stop(StopReason.STOPPED, false);
			} else {
				RerollSelection selection = currentSelection();
				if (selection != null) {
					engine.start(selection.operationId(), selection.targetId(), selection.thresholdEnabled(),
							selection.thresholdValue());
				}
			}
			return true;
		}
		return true;
	}

	private void drawSelectRow(PoseStack poseStack, String prefix, String value, int x, int width, int y) {
		drawString(poseStack, "<", x + 1, y, MUTED_COLOR);
		drawString(poseStack, prefix + value, x + 16, y, TEXT_COLOR);
		drawString(poseStack, ">", x + width - 8, y, MUTED_COLOR);
	}

	private void drawMinRow(PoseStack poseStack, int x, int width, int y) {
		ModifierCatalog.RollRange range = currentTargetRange();
		boolean numeric = range.numeric();
		if (numeric) {
			drawString(poseStack, "<", x + 1, y, MUTED_COLOR);
			drawString(poseStack, ">", x + width - 8, y, MUTED_COLOR);
		}
		drawString(poseStack, "Min:", x + 12, y, TEXT_COLOR);
		int fieldLeft = x + 30;
		int fieldRight = x + width - 14;
		if (numeric && (minInputFocused || thresholdEnabled)) {
			GuiComponent.fill(poseStack, fieldLeft, y - 1, fieldRight, y + ROW_H - 1,
					minInputFocused ? FIELD_FOCUS_COLOR : FIELD_BG_COLOR);
		}
		String shown;
		if (minInputFocused) {
			shown = minInputText;
			if ((System.currentTimeMillis() / 500) % 2 == 0) {
				shown += "_";
			}
		} else if (thresholdEnabled) {
			shown = formatDisplay(thresholdValue, numeric && range.percent());
		} else {
			shown = "any";
		}
		drawString(poseStack, shown, fieldLeft + 2, y, TEXT_COLOR);
	}

	private void toggleMinFocus() {
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

	private void stepMin(double delta) {
		ModifierCatalog.RollRange range = currentTargetRange();
		if (!range.numeric()) {
			return;
		}
		thresholdEnabled = true;
		thresholdValue = Mth.clamp(thresholdValue + delta, range.min(), range.max());
		minInputText = formatDisplay(thresholdValue, false);
		minInputFocused = false;
	}

	private static boolean insideRow(int rowY, int rowHeight, int mouseY) {
		return mouseY >= rowY && mouseY < rowY + rowHeight;
	}

	public ModifierCatalog.RollRange currentTargetRange() {
		ItemStack gear = stationGear();
		if (gear.isEmpty() || !(Minecraft.getInstance().screen instanceof VaultArtisanStationScreen screen)) {
			return new ModifierCatalog.RollRange(0, 0, 0, false, false);
		}
		List<GearModificationAction> operations = operations(screen);
		if (operations.isEmpty()) {
			return new ModifierCatalog.RollRange(0, 0, 0, false, false);
		}
		GearModificationAction operation = operations.get(Math.min(operationIndex, operations.size() - 1));
		List<Candidate> candidates = candidates(gear, operation);
		if (candidates.isEmpty() || targetIndex >= candidates.size()) {
			return new ModifierCatalog.RollRange(0, 0, 0, false, false);
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

	private static ItemStack stationGear() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen instanceof VaultArtisanStationScreen screen) {
			return ((VaultArtisanStationContainer) screen.getMenu()).getGearInputSlot().getItem();
		}
		return ItemStack.EMPTY;
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
		return text.length() <= maxChars ? text : text.substring(0, maxChars - 1) + "\u2026";
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

	private static void drawString(PoseStack poseStack, String text, int x, int y, int color) {
		Minecraft.getInstance().font.draw(poseStack, text, x, y, color);
	}

	private static void drawCentered(PoseStack poseStack, String text, int centerX, int y, int color) {
		Minecraft.getInstance().font.draw(poseStack, text, centerX - Minecraft.getInstance().font.width(text) / 2.0F,
				y, color);
	}
}