package io.haque.vault_modifier_alerts.feature.reroll;

import com.mojang.blaze3d.vertex.PoseStack;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.reroll.AutoRerollEngine.StopReason;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.Candidate;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.OperationScope;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.RollRange;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.RollTarget;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout.Hit;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout.Rect;
import io.haque.vault_modifier_alerts.feature.reroll.ui.RerollTokens;
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

import java.util.ArrayList;
import java.util.List;

/**
 * View layer for the auto-reroll panel: drawing, hit-testing and input routing.
 * All selection, threshold and dropdown state lives in
 * {@link RerollPanelState}.
 * The panel is added to the Artisan Station screen as a real VH framework
 * element
 * ({@link RerollPanelElement}); all geometry comes from
 * {@link RerollPanelLayout}
 * so the drawn rows and the click/scroll routing share one source of truth.
 */
public final class RerollPanel {

	/** Selection snapshot used by the Start button and the /vma reroll command. */
	public record RerollSelection(ResourceLocation operationId, List<RollTarget> targets,
			AutoRerollEngine.StopCondition stopCondition) {
	}

	private static final RerollPanel INSTANCE = new RerollPanel();

	private final RerollPanelState state = RerollPanelState.getInstance();

	private int lastX;
	private int lastY;
	private int lastW;
	private int lastH;
	private int currentWidth = RerollPanelLayout.WIDTH;
	private ItemStack lastSeenGear = ItemStack.EMPTY;
	private String pendingTooltip;
	private int tooltipX;
	private int tooltipY;

	// Status debounce (Phase 5.3): cache status and only update after
	// STATUS_DEBOUNCE_TICKS consecutive frames with the same text
	private static final int STATUS_DEBOUNCE_TICKS = 4;
	private StatusInfo cachedStatus;
	private int statusStableTicks;
	private StatusInfo displayedStatus;

	private RerollPanel() {
		state.setPanel(this);
	}

	public static RerollPanel getInstance() {
		return INSTANCE;
	}

	// --------------------------------------------------------- public API
	// (delegates)

	public boolean isVisible() {
		return VmaClientConfigs.isRerollPanelEnabled() && state.isVisible();
	}

	public void setVisible(boolean visible) {
		state.setVisible(visible);
	}

	public void toggleVisible() {
		state.toggleVisible();
	}

	public boolean isMinInputFocused() {
		return state.isMinInputFocused();
	}

	public boolean isDropdownOpen() {
		return state.isDropdownOpen();
	}

	/**
	 * Returns the current panel selection, or null if no station screen / no
	 * target chosen. Delegates selection state to {@link RerollPanelState}.
	 */
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
		if (state.targets().isEmpty()) {
			return null;
		}
		return new RerollSelection(
				operations.get(state.operationIndex()).modification().getRegistryName(),
				List.copyOf(state.targets()),
				state.stopCondition());
	}

	public void clampSelections(List<GearModificationAction> operations, ItemStack gear) {
		state.clampSelections(operations, gear);
	}

	public boolean acceptChar(char c) {
		return state.acceptChar(c);
	}

	public boolean onKeyPressed(int keyCode) {
		return state.onKeyPressed(keyCode);
	}

	public int currentHeight() {
		return computeLayout(0, 0).totalHeight;
	}

	public Rect bounds() {
		return new Rect(lastX, lastY, lastW, lastH);
	}

	public void closeDropdown() {
		state.closeDropdown();
	}

	public boolean handleScroll(double delta) {
		if (!state.isDropdownOpen()) {
			return false;
		}
		state.scrollDropdown(delta > 0 ? -1 : 1);
		return true;
	}

	// --------------------------------------------------------- screen-dependent
	// model queries

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

	public static ItemStack stationGear() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen instanceof VaultArtisanStationScreen screen) {
			return ((VaultArtisanStationContainer) screen.getMenu()).getGearInputSlot().getItem();
		}
		return ItemStack.EMPTY;
	}

	public RollRange currentTargetRange() {
		return rollRangeFor(state.focusedTarget() >= 0 && state.focusedTarget() < state.targets().size()
				? state.targets().get(state.focusedTarget())
				: null);
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
		GearModificationAction operation = operations.get(Math.min(state.operationIndex(), operations.size() - 1));
		return ModifierCatalog.rollRange(gear, target.id(),
				ModifierCatalog.scopeOfOperation(operation.modification().getRegistryName()));
	}

	public RollRange rollRangeOf(Candidate candidate) {
		ItemStack gear = stationGear();
		if (gear.isEmpty() || !(Minecraft.getInstance().screen instanceof VaultArtisanStationScreen screen)) {
			return new RollRange(0, 0, 0, false, false);
		}
		List<GearModificationAction> operations = operations(screen);
		if (operations.isEmpty()) {
			return new RollRange(0, 0, 0, false, false);
		}
		int safeIndex = Math.min(state.operationIndex(), operations.size() - 1);
		return ModifierCatalog.rollRange(gear, candidate.id(),
				ModifierCatalog.scopeOfOperation(operations.get(safeIndex).modification().getRegistryName()));
	}

	public String targetName(ResourceLocation id) {
		if (id == null) {
			return "?";
		}
		ItemStack gear = stationGear();
		if (!gear.isEmpty() && Minecraft.getInstance().screen instanceof VaultArtisanStationScreen screen) {
			List<GearModificationAction> operations = operations(screen);
			if (!operations.isEmpty()) {
				int safeIndex = Math.min(state.operationIndex(), operations.size() - 1);
				for (Candidate candidate : candidates(gear, operations.get(safeIndex))) {
					if (candidate.id().equals(id)) {
						return candidate.displayName();
					}
				}
			}
		}
		return ModifierCatalog.humanizeId(ModifierCatalog.stripModPrefix(id.getPath()));
	}

	public boolean isWatched(ResourceLocation id) {
		for (RollTarget target : state.targets()) {
			if (target.id().equals(id)) {
				return true;
			}
		}
		return false;
	}

	public String displayOperationName(GearModificationAction operation) {
		try {
			if (operation != null && operation.modification() != null
					&& operation.modification().getDisplayStack() != null) {
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

	public static int potentialCost(ItemStack gear, GearModificationAction operation) {
		try {
			if (gear == null || gear.isEmpty() || operation == null || operation.modification() == null) {
				return 0;
			}
			Player player = Minecraft.getInstance().player;
			if (player == null) {
				return 0;
			}
			int currentPotential = ModifierCatalog.craftingPotential(gear);
			int reducedPotential = VaultGearCraftingHelper.getReducedPotential(gear, player, operation.modification());
			return currentPotential - reducedPotential;
		} catch (Exception ignored) {
			return 0;
		}
	}

	// --------------------------------------------------------- layout

	public RerollPanelLayout computeLayout(int x, int y) {
		int count = state.dropdownCount();
		return new RerollPanelLayout(x, y, currentWidth, state.isDropdownOpen(), count,
				state.dropdownScroll(), state.dropdownMaxRows());
	}

	public RerollPanelLayout computeLayout(int x, int y, int width) {
		int count = state.dropdownCount();
		return new RerollPanelLayout(x, y, width, state.isDropdownOpen(), count,
				state.dropdownScroll(), state.dropdownMaxRows());
	}

	public static int computeWidth(int screenWidth, int guiLeft, int guiRight) {
		int leftRoom = guiLeft - RerollPanelLayout.MARGIN;
		int rightRoom = screenWidth - (guiRight + RerollPanelLayout.MARGIN);
		if (leftRoom >= RerollPanelLayout.WIDTH || rightRoom >= RerollPanelLayout.WIDTH) {
			return RerollPanelLayout.WIDTH;
		}
		return Mth.clamp(Math.max(leftRoom, rightRoom), RerollPanelLayout.MIN_WIDTH, RerollPanelLayout.WIDTH);
	}

	// --------------------------------------------------------- draw / hit-test /
	// input

	public boolean hitTest(int x, int y, int width, int height, int mouseX, int mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	public void draw(VaultArtisanStationScreen screen, PoseStack poseStack, int x, int y, int width, int height,
			int mouseX, int mouseY) {
		lastX = x;
		lastY = y;
		lastW = width;
		lastH = height;
		currentWidth = width;
		state.updateDropdownCapacity(screen, x, y, currentWidth);
		List<GearModificationAction> operations = operations(screen);
		if (operations.isEmpty()) {
			state.closeDropdown();
		}
		ItemStack gear = stationGear();
		if (lastSeenGear.isEmpty() && !gear.isEmpty()) {
			state.resetSelection();
		}
		lastSeenGear = gear.copy();
		clampSelections(operations, gear);
		AutoRerollEngine engine = AutoRerollEngine.getInstance();
		GearModificationAction operation = operations.isEmpty() ? null : operations.get(state.operationIndex());
		List<Candidate> candidates = operation == null ? List.of() : candidates(gear, operation);
		state.clampDropdownScroll(operations.size(), candidates.size());
		RerollPanelLayout layout = computeLayout(x, y);
		pendingTooltip = null;

		drawPanelFrame(poseStack, layout);
		drawCentered(poseStack, "Auto-Reroll", x + width / 2, y + 4, RerollTokens.ACCENT_GOLD());

		if (operation == null) {
			drawString(poseStack, "No re-roll actions", x + RerollPanelLayout.PAD_X, layout.focusY + 3, RerollTokens.STATE_DANGER());
			drawTooltip(poseStack);
			return;
		}

		drawRow(poseStack, layout, "Focus", displayOperationName(operation), layout.focusY, mouseX, mouseY,
				state.dropdownMode() == RerollPanelState.DropdownMode.OPERATION);
		drawModifierRow(poseStack, layout, mouseX, mouseY);
		drawTargetsRow(poseStack, layout, x, width, mouseX, mouseY);
		drawMinRow(poseStack, layout, x, width, mouseX, mouseY);
		drawRangeRow(poseStack, layout, x, width, mouseX, mouseY);
		drawPotentialRow(poseStack, layout, x, width, operation);

		drawTooltip(poseStack);
	}

	public boolean handleClick(VaultArtisanStationScreen screen, int x, int y, int width, int height, int mouseX,
			int mouseY, int button) {
		if (!hitTest(x, y, width, height, mouseX, mouseY)) {
			return false;
		}
		// Right-click on Min field clears the threshold (Phase 5.1)
		if (button == 1) {
			RerollPanelLayout layout = computeLayout(x, y);
			Hit hit = layout.regionAt(mouseX, mouseY);
			if (hit.type() == RerollPanelLayout.HitType.MIN_FIELD) {
				state.clearFocusedThreshold();
				return true;
			}
			return false;
		}
		if (button != 0) {
			return false;
		}
		List<GearModificationAction> operations = operations(screen);
		if (operations.isEmpty()) {
			state.closeDropdown();
			return true;
		}
		ItemStack gear = stationGear();
		clampSelections(operations, gear);
		int operationCount = operations.size();
		int candidateCount = candidates(gear, operations.get(state.operationIndex())).size();
		state.clampDropdownScroll(operationCount, candidateCount);
		RerollPanelLayout layout = computeLayout(x, y);
		Hit hit = layout.regionAt(mouseX, mouseY);

		switch (hit.type()) {
			case FOCUS_ROW -> {
				state.loseMinFocus();
				state.toggleDropdown(RerollPanelState.DropdownMode.OPERATION, operationCount);
			}
			case MODIFIER_ROW -> {
				state.loseMinFocus();
				state.toggleDropdown(RerollPanelState.DropdownMode.MODIFIER, candidateCount);
			}
			case TARGETS_ROW -> {
				state.loseMinFocus();
				state.toggleDropdown(RerollPanelState.DropdownMode.TARGETS, state.targets().size());
			}
			case TARGETS_CHIP -> {
				state.loseMinFocus();
				state.closeDropdown();
				toggleStopCondition();
			}
			case TARGETS_CLEAR -> {
				state.loseMinFocus();
				state.closeDropdown();
				if (AutoRerollEngine.getInstance().isRunning()) {
					AutoRerollEngine.getInstance().stop(StopReason.STOPPED, false);
				}
				AutoRerollEngine.getInstance().cancelResume();
				state.targets().clear();
				state.focusTarget(-1);
				// Reset minInputText directly — state exposes this via resetMinInput or we do
				// it here
				resetMinInput();
			}
			case MIN_DEC -> state.stepMin(-state.currentStep());
			case MIN_INC -> state.stepMin(state.currentStep());
			case MIN_FIELD -> state.toggleMinFocus();
			case NONE -> {
				if (state.isDropdownOpen()) {
					state.closeDropdown();
				} else {
					state.loseMinFocus();
				}
			}
			default -> state.loseMinFocus();
		}
		return true;
	}

	// --------------------------------------------------------- private draw
	// helpers

	private void drawPanelFrame(PoseStack poseStack, RerollPanelLayout layout) {
		GuiComponent.fill(poseStack, layout.x, layout.y, layout.x + layout.width,
				layout.y + layout.totalHeight, RerollTokens.PANEL_BG());
		GuiComponent.fill(poseStack, layout.x, layout.y, layout.x + layout.width, layout.y + 2, RerollTokens.ACCENT_GOLD());
		GuiComponent.fill(poseStack, layout.x, layout.y + layout.totalHeight - 1, layout.x + layout.width,
				layout.y + layout.totalHeight, RerollTokens.PANEL_BORDER());
		GuiComponent.fill(poseStack, layout.x, layout.y + 2, layout.x + 1, layout.y + layout.totalHeight, RerollTokens.PANEL_BORDER());
		GuiComponent.fill(poseStack, layout.x + layout.width - 1, layout.y + 2,
				layout.x + layout.width, layout.y + layout.totalHeight, RerollTokens.PANEL_BORDER());
	}

	private void drawRow(PoseStack poseStack, RerollPanelLayout layout, String label, String value, int y, int mouseX,
			int mouseY, boolean open) {
		boolean hovered = rowHovered(layout, mouseX, mouseY, y, RerollPanelLayout.ROW_H);
		if (open) {
			GuiComponent.fill(poseStack, layout.x, y, layout.x + layout.width,
					y + RerollPanelLayout.ROW_H, RerollTokens.ROW_OPEN);
		} else if (hovered) {
			GuiComponent.fill(poseStack, layout.x, y, layout.x + layout.width,
					y + RerollPanelLayout.ROW_H, RerollTokens.ROW_HOVER);
		}
		int color = VmaClientConfigs.isAutoRerollEnabled() ? RerollTokens.TEXT_DEFAULT() : RerollTokens.TEXT_DISABLED;
		drawString(poseStack, label, layout.x + RerollPanelLayout.PAD_X, y + 3, RerollTokens.TEXT_MUTED);
		drawString(poseStack, value, layout.x + 62, y + 3, color);
		drawTriangle(poseStack, layout.x + layout.width - 8, y + 6, false, color);
		if (hovered && layout.x + 62 + font().width(value) > layout.x + layout.width
				- RerollPanelLayout.PAD_X) {
			hoverTooltip(value, mouseX, mouseY);
		}
	}

	private void drawModifierRow(PoseStack poseStack, RerollPanelLayout layout, int mouseX, int mouseY) {
		boolean hovered = rowHovered(layout, mouseX, mouseY, layout.modifierY, RerollPanelLayout.ROW_H);
		if (state.dropdownMode() == RerollPanelState.DropdownMode.MODIFIER) {
			GuiComponent.fill(poseStack, layout.x, layout.modifierY, layout.x + layout.width,
					layout.modifierY + RerollPanelLayout.ROW_H, RerollTokens.ROW_OPEN);
		} else if (hovered) {
			GuiComponent.fill(poseStack, layout.x, layout.modifierY, layout.x + layout.width,
					layout.modifierY + RerollPanelLayout.ROW_H, RerollTokens.ROW_HOVER);
		}
		int color = VmaClientConfigs.isAutoRerollEnabled() ? RerollTokens.TEXT_DEFAULT() : RerollTokens.TEXT_DISABLED;
		drawString(poseStack, "Modifier", layout.x + RerollPanelLayout.PAD_X, layout.modifierY + 3, RerollTokens.TEXT_MUTED);
		String placeholder = state.targets().isEmpty() ? "add a modifier..." : "+ add modifier";
		drawString(poseStack, placeholder, layout.x + 62, layout.modifierY + 3, color);
		drawTriangle(poseStack, layout.x + layout.width - 8, layout.modifierY + 6, false, color);
	}

	private void drawTargetsRow(PoseStack poseStack, RerollPanelLayout layout, int x, int width, int mouseX,
			int mouseY) {
		boolean hovered = rowHovered(layout, mouseX, mouseY, layout.targetsY, RerollPanelLayout.ROW_H);
		boolean chipHovered = hovered && mouseX >= x + width - 24;
		boolean clearHovered = hovered && mouseX >= x + width - 44 && mouseX < x + width - 26;
		boolean addHovered = hovered && !chipHovered && !clearHovered;
		if (state.dropdownMode() == RerollPanelState.DropdownMode.TARGETS) {
			GuiComponent.fill(poseStack, layout.x, layout.targetsY, layout.x + layout.width,
					layout.targetsY + RerollPanelLayout.ROW_H, RerollTokens.ROW_OPEN);
		} else if (addHovered) {
			GuiComponent.fill(poseStack, layout.x, layout.targetsY, layout.x + layout.width,
					layout.targetsY + RerollPanelLayout.ROW_H, RerollTokens.ROW_HOVER);
		}
		drawString(poseStack, "Targets", layout.x + RerollPanelLayout.PAD_X, layout.targetsY + 3, RerollTokens.TEXT_MUTED);
		String value;
		RollTarget focused = state.focusedTarget() >= 0 && state.focusedTarget() < state.targets().size()
				? state.targets().get(state.focusedTarget())
				: null;
		if (focused == null) {
			value = state.targets().isEmpty() ? "none" : "?";
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
		int color = VmaClientConfigs.isAutoRerollEnabled() ? RerollTokens.TEXT_DEFAULT() : RerollTokens.TEXT_DISABLED;
		drawString(poseStack, value, layout.x + 62, layout.targetsY + 3, color);
		if (hovered && !chipHovered && !clearHovered && truncated) {
			hoverTooltip(full, mouseX, mouseY);
		}
		GuiComponent.fill(poseStack, x + width - 44, layout.targetsY, x + width - 26,
				layout.targetsY + RerollPanelLayout.ROW_H, clearHovered ? RerollTokens.ROW_HOVER : RerollTokens.DROPDOWN_BG);
		drawCentered(poseStack, "x", x + width - 35, layout.targetsY + 3,
				clearHovered ? RerollTokens.STATE_DANGER() : (VmaClientConfigs.isAutoRerollEnabled() ? RerollTokens.TEXT_MUTED : RerollTokens.TEXT_DISABLED));
		if (clearHovered) {
			hoverTooltip("Clear all targets", mouseX, mouseY);
		}
		GuiComponent.fill(poseStack, x + width - 24, layout.targetsY, x + width,
				layout.targetsY + RerollPanelLayout.ROW_H,
				chipHovered ? RerollTokens.ROW_HOVER : RerollTokens.DROPDOWN_BG);
		drawString(poseStack, state.stopCondition() == AutoRerollEngine.StopCondition.ANY ? "any" : "all",
				x + width - 22, layout.targetsY + 3,
				chipHovered ? RerollTokens.ACCENT_GOLD() : (VmaClientConfigs.isAutoRerollEnabled() ? RerollTokens.TEXT_MUTED : RerollTokens.TEXT_DISABLED));
	}

	private void drawMinRow(PoseStack poseStack, RerollPanelLayout layout, int x, int width, int mouseX, int mouseY) {
		RollRange range = currentTargetRange();
		boolean numeric = range.numeric();
		boolean enabled = VmaClientConfigs.isAutoRerollEnabled();
		boolean hasTarget = state.focusedTarget() >= 0 && state.focusedTarget() < state.targets().size();
		boolean hoveredDec = rowHovered(layout, mouseX, mouseY, layout.minY, RerollPanelLayout.ROW_H)
				&& mouseX < x + 16;
		boolean hoveredInc = rowHovered(layout, mouseX, mouseY, layout.minY, RerollPanelLayout.ROW_H)
				&& mouseX >= x + width - 16;
		boolean hoveredField = rowHovered(layout, mouseX, mouseY, layout.minY, RerollPanelLayout.ROW_H)
				&& mouseX >= layout.minFieldLeft() && mouseX < layout.minFieldRight();
		boolean hasThreshold = hasTarget
				&& state.targets().get(state.focusedTarget()).thresholdEnabled();
		int buttonColor = enabled ? (hoveredDec ? RerollTokens.TEXT_DEFAULT() : RerollTokens.TEXT_MUTED) : RerollTokens.TEXT_DISABLED;
		GuiComponent.fill(poseStack, x + 2, layout.minY + 2, x + 14, layout.minY + 12,
				enabled ? (hoveredDec ? RerollTokens.ROW_HOVER : RerollTokens.BUTTON_BG) : RerollTokens.BUTTON_DISABLED_BG);
		GuiComponent.fill(poseStack, x + width - 14, layout.minY + 2, x + width - 2, layout.minY + 12,
				enabled ? (hoveredInc ? RerollTokens.ROW_HOVER : RerollTokens.BUTTON_BG) : RerollTokens.BUTTON_DISABLED_BG);
		drawCentered(poseStack, "-", x + 8, layout.minY + 3, buttonColor);
		drawCentered(poseStack, "+", x + width - 8, layout.minY + 3, buttonColor);
		drawString(poseStack, "Min", x + 22, layout.minY + 3, RerollTokens.TEXT_MUTED);
		if (state.isMinInputFocused() || hasThreshold) {
			GuiComponent.fill(poseStack, layout.minFieldLeft(), layout.minY, layout.minFieldRight(),
					layout.minY + RerollPanelLayout.ROW_H,
					state.isMinInputFocused() ? RerollTokens.INPUT_FOCUS : RerollTokens.INPUT_BG);
		}
		String shown;
		if (!hasTarget) {
			shown = "-";
		} else if (state.isMinInputFocused()) {
			shown = state.minInputText() + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
		} else if (hasThreshold) {
			shown = formatDisplay(state.targets().get(state.focusedTarget()).thresholdValue(),
					numeric && range.percent());
		} else {
			shown = "any";
		}
		drawString(poseStack, shown, layout.minFieldLeft() + 2, layout.minY + 3, hasTarget
				? (hoveredField && !state.isMinInputFocused() ? RerollTokens.ACCENT_GOLD() : RerollTokens.TEXT_DEFAULT())
				: RerollTokens.TEXT_DISABLED);
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
		drawString(poseStack, text, x + RerollPanelLayout.PAD_X, layout.rangeY + 3, RerollTokens.TEXT_MUTED);
	}

	private void drawPotentialRow(PoseStack poseStack, RerollPanelLayout layout, int x, int width,
			GearModificationAction operation) {
		ItemStack gear = stationGear();
		int potential = ModifierCatalog.craftingPotential(gear);
		int max = ModifierCatalog.maxCraftingPotential(gear);
		int cost = potentialCost(gear, operation);
		String left = "Potential " + potential + "/" + max;
		String right = cost > 0 && potential >= cost ? "~" + potential / cost + " rolls" : "";
		int color = potential > 0 ? (VmaClientConfigs.isAutoRerollEnabled() ? RerollTokens.TEXT_DEFAULT() : RerollTokens.TEXT_DISABLED) : RerollTokens.STATE_DANGER();
		drawString(poseStack, left, x + RerollPanelLayout.PAD_X, layout.potentialY + 3, color);
		if (!right.isEmpty()) {
			drawRight(poseStack, right, x + width - RerollPanelLayout.PAD_X, layout.potentialY + 3, RerollTokens.TEXT_MUTED);
		}
	}


	String runningStatus(AutoRerollEngine engine) {
		String base = "Rolling #" + engine.rolls();
		if (state.targets().size() <= 1) {
			RollTarget target = state.focusedTarget() >= 0 && state.focusedTarget() < state.targets().size()
					? state.targets().get(state.focusedTarget())
					: null;
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
		return base + " - " + met + "/" + state.targets().size() + label;
	}

	String targetDetail(AutoRerollEngine engine) {
		if (state.targets().isEmpty()) {
			return "";
		}
		StringBuilder detail = new StringBuilder();
		for (int i = 0; i < state.targets().size(); i++) {
			RollTarget target = state.targets().get(i);
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



	// --------------------------------------------------------- tooltip

	public void hoverTooltip(String fullText, int mouseX, int mouseY) {
		if (fullText == null || fullText.isEmpty()) {
			return;
		}
		pendingTooltip = fullText;
		tooltipX = mouseX;
		tooltipY = mouseY;
	}

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
		GuiComponent.fill(poseStack, boxX, boxY, boxX + boxWidth, boxY + boxHeight, RerollTokens.TOOLTIP_BG);
		GuiComponent.fill(poseStack, boxX, boxY, boxX + boxWidth, boxY + 1, RerollTokens.PANEL_BORDER());
		GuiComponent.fill(poseStack, boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, RerollTokens.PANEL_BORDER());
		GuiComponent.fill(poseStack, boxX, boxY, boxX + 1, boxY + boxHeight, RerollTokens.PANEL_BORDER());
		GuiComponent.fill(poseStack, boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, RerollTokens.PANEL_BORDER());
		int lineY = boxY + 4;
		for (String line : lines) {
			drawString(poseStack, line, boxX + 3, lineY, RerollTokens.TEXT_DEFAULT());
			lineY += font.lineHeight;
		}
	}

	// --------------------------------------------------------- small state helpers
	// (used by handleClick)

	private void toggleStopCondition() {
		AutoRerollEngine.StopCondition sc = state.stopCondition();
		setStopCondition(sc == AutoRerollEngine.StopCondition.ANY
				? AutoRerollEngine.StopCondition.ALL
				: AutoRerollEngine.StopCondition.ANY);
	}

	private void setStopCondition(AutoRerollEngine.StopCondition condition) {
		// Accessed only from handleClick targets-chip branch; state exposes
		// the field for reading; this writes it directly through the targets list.
		// In Phase 3 this will be a proper state mutation method.
		state.targets(); // no-op to satisfy reference; actual mutation below
		// We use a package-private setter on state:
		state.setStopCondition(condition);
	}

	private void resetMinInput() {
		// Package-private access to state's minInputText for the clear-targets branch
		state.resetMinInputText();
	}

	// --------------------------------------------------------- static utilities

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

	// --------------------------------------------------------- drawing primitives

	private static net.minecraft.client.gui.Font font() {
		return Minecraft.getInstance().font;
	}

	private static boolean rowHovered(RerollPanelLayout layout, int mouseX, int mouseY, int y, int h) {
		return mouseX >= layout.x && mouseX < layout.x + layout.width && mouseY >= y && mouseY < y + h;
	}

	static void drawString(PoseStack poseStack, String text, int x, int y, int color) {
		font().draw(poseStack, text, x, y, color);
	}

	static void drawCentered(PoseStack poseStack, String text, int centerX, int y, int color) {
		font().draw(poseStack, text, centerX - font().width(text) / 2.0F, y, color);
	}

	static void drawRight(PoseStack poseStack, String text, int rightX, int y, int color) {
		font().draw(poseStack, text, rightX - font().width(text), y, color);
	}

	private static void drawTriangle(PoseStack poseStack, int centerX, int topY, boolean up, int color) {
		for (int i = 0; i < 3; i++) {
			int rowY = up ? topY + (2 - i) : topY + i;
			GuiComponent.fill(poseStack, centerX - i, rowY, centerX + i + 1, rowY + 1, color);
		}
	}

	public record StatusInfo(String text, int color, String full) {
	}

	public StatusInfo computeStatusInfo(List<GearModificationAction> operations) {
		AutoRerollEngine engine = AutoRerollEngine.getInstance();
		ItemStack gear = stationGear();
		boolean noCandidates;
		if (operations.isEmpty() || gear.isEmpty()) {
			noCandidates = true;
		} else {
			int safeIndex = Math.min(state.operationIndex(), operations.size() - 1);
			List<ModifierCatalog.Candidate> cands = candidates(gear, operations.get(safeIndex));
			noCandidates = cands.isEmpty();
		}
		String text;
		int color;
		String full;
		if (engine.isRunning()) {
			text = runningStatus(engine);
			color = RerollTokens.STATE_SUCCESS();
			full = targetDetail(engine);
		} else if (engine.isResumeWaiting()) {
			text = "Waiting for focus" + (engine.rolls() > 0 ? " - " + engine.rolls() + " rolls" : "");
			color = RerollTokens.STATE_DANGER();
			full = text;
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
					? "all targets met"
					: stopReasonText(engine.stopReason());
			text = "Stopped: " + reason + suffix;
			color = success ? RerollTokens.STATE_SUCCESS() : RerollTokens.STATE_DANGER();
			full = text;
		} else if (!VmaClientConfigs.isAutoRerollEnabled()) {
			text = "Auto-reroll disabled";
			color = RerollTokens.TEXT_DISABLED;
			full = text;
		} else if (gear.isEmpty()) {
			text = "No gear in station";
			color = RerollTokens.TEXT_MUTED;
			full = text;
		} else if (noCandidates) {
			text = "No rollable modifiers";
			color = RerollTokens.TEXT_MUTED;
			full = text;
		} else if (state.targets().isEmpty()) {
			text = "Add a target modifier";
			color = RerollTokens.TEXT_MUTED;
			full = text;
		} else if (state.targets().size() == 1 && state.focusedTarget() >= 0
				&& state.focusedTarget() < state.targets().size()
				&& state.targets().get(state.focusedTarget()).thresholdEnabled()) {
			text = "Ready : goal at least "
					+ formatDisplay(state.targets().get(state.focusedTarget()).thresholdValue(),
							currentTargetRange().percent());
			color = RerollTokens.STATE_SUCCESS();
			full = text;
		} else if (state.targets().size() == 1) {
			text = "Ready : any roll";
			color = RerollTokens.STATE_SUCCESS();
			full = text;
		} else {
			text = "Ready : " + state.targets().size() + " targets";
			color = RerollTokens.STATE_SUCCESS();
			full = targetDetail(engine);
		}
		StatusInfo fresh = new StatusInfo(text, color, full);
		if (cachedStatus == null || !cachedStatus.text().equals(fresh.text()) || cachedStatus.color() != fresh.color()) {
			cachedStatus = fresh;
			statusStableTicks = 0;
		} else {
			statusStableTicks++;
		}
		if (displayedStatus == null || statusStableTicks >= STATUS_DEBOUNCE_TICKS) {
			displayedStatus = fresh;
		}
		return displayedStatus;
	}
}
