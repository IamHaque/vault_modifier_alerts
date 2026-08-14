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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Side panel for F3 auto-reroll, drawn inside the Artisan Station screen by
 * MixinVaultArtisanStationScreen. Anchored outside the station window rect
 * (right side preferred, left fallback, clamped) so it never overlaps the GUI.
 */
public final class RerollPanel {

	private static final int PANEL_WIDTH = 150;
	private static final int PANEL_HEIGHT = 122;
	private static final int MARGIN = 6;
	private static final int BG_COLOR = 0xC0101010;
	private static final int BORDER_COLOR = 0xFF6B6B6B;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int MUTED_COLOR = 0xFFA0A0A0;
	private static final int ACCENT_COLOR = 0xFF55FF55;
	private static final int WARN_COLOR = 0xFFFF5555;

	private static final RerollPanel INSTANCE = new RerollPanel();

	private boolean visible = true;
	private int operationIndex;
	private int targetIndex;

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
	}

	public void toggleVisible() {
		visible = !visible;
	}

	public void render(VaultArtisanStationScreen screen, PoseStack poseStack, int mouseX, int mouseY) {
		if (!visible || !VmaClientConfigs.isAutoRerollEnabled()) {
			return;
		}
		PanelRect rect = panelRect(screen);
		VaultArtisanStationContainer container = (VaultArtisanStationContainer) screen.getMenu();
		List<GearModificationAction> operations = availableOperations(container);
		clampSelections(operations, container.getGearInputSlot().getItem());
		AutoRerollEngine engine = AutoRerollEngine.getInstance();

		GuiComponent.fill(poseStack, rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, BG_COLOR);
		GuiComponent.fill(poseStack, rect.x, rect.y, rect.x + rect.width, rect.y + 1, BORDER_COLOR);
		GuiComponent.fill(poseStack, rect.x, rect.y + rect.height - 1, rect.x + rect.width, rect.y + rect.height,
				BORDER_COLOR);
		GuiComponent.fill(poseStack, rect.x, rect.y, rect.x + 1, rect.y + rect.height, BORDER_COLOR);
		GuiComponent.fill(poseStack, rect.x + rect.width - 1, rect.y, rect.x + rect.width, rect.y + rect.height,
				BORDER_COLOR);

		drawCentered(poseStack, "Auto-Reroll", rect.x + rect.width / 2, rect.y + 3, ACCENT_COLOR);
		int y = rect.y + 15;

		if (operations.isEmpty()) {
			drawString(poseStack, "No re-roll actions", rect.x + 6, y, WARN_COLOR);
			return;
		}

		ItemStack gear = container.getGearInputSlot().getItem();
		GearModificationAction operation = operations.get(operationIndex);
		drawString(poseStack, "Op: " + operation.modification().getDisplayStack().getHoverName().getString(), rect.x + 6,
				y, TEXT_COLOR);
		drawString(poseStack, "<", rect.x + 6, y, MUTED_COLOR);
		drawString(poseStack, ">", rect.x + rect.width - 6 - 8, y, MUTED_COLOR);
		y += 11;

		List<Candidate> candidates = ModifierCatalog.candidates(gear,
				ModifierCatalog.scopeOfOperation(operation.modification().getRegistryName()));
		if (candidates.isEmpty()) {
			drawString(poseStack, "Target: none rollable", rect.x + 6, y, WARN_COLOR);
			y += 11;
		} else {
			drawString(poseStack, "Tgt: " + truncate(candidates.get(targetIndex).displayName(), 24), rect.x + 6, y,
					TEXT_COLOR);
			drawString(poseStack, "<", rect.x + 6, y, MUTED_COLOR);
			drawString(poseStack, ">", rect.x + rect.width - 6 - 8, y, MUTED_COLOR);
			y += 11;
		}

		int potential = ModifierCatalog.craftingPotential(gear);
		int maxPotential = ModifierCatalog.maxCraftingPotential(gear);
		drawString(poseStack, "Potential: " + potential + "/" + maxPotential, rect.x + 6, y, MUTED_COLOR);
		y += 11;

		boolean autoReset = VmaClientConfigs.isAutoResetPotentialEnabled();
		drawString(poseStack, "[" + (autoReset ? "x" : " ") + "] Auto-reset", rect.x + 6, y, TEXT_COLOR);
		y += 13;

		boolean running = engine.isRunning();
		String label = running ? "Stop" : "Start";
		int labelColor = running ? WARN_COLOR : ACCENT_COLOR;
		if (running || !candidates.isEmpty()) {
			GuiComponent.fill(poseStack, rect.x + 6, y, rect.x + rect.width - 6, y + 10, 0xFF303030);
			drawCentered(poseStack, label, rect.x + rect.width / 2, y + 1, labelColor);
		}
		y += 12;

		if (running) {
			drawString(poseStack, "Rolling... (" + engine.rolls() + ")", rect.x + 6, y, ACCENT_COLOR);
		} else if (engine.stopReason() != null) {
			drawString(poseStack, "Stopped: " + stopReasonText(engine.stopReason()), rect.x + 6, y, WARN_COLOR);
		} else {
			drawString(poseStack, "Idle", rect.x + 6, y, MUTED_COLOR);
		}
	}

	/** Selection snapshot used by the Start button and the /vma reroll command. */
	public record RerollSelection(ResourceLocation operationId, ResourceLocation targetId) {
	}

	/** @return the current panel selection, or null if no station screen / no valid choice. */
	public RerollSelection currentSelection() {
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.screen instanceof VaultArtisanStationScreen screen)) {
			return null;
		}
		VaultArtisanStationContainer container = (VaultArtisanStationContainer) screen.getMenu();
		List<GearModificationAction> operations = availableOperations(container);
		if (operations.isEmpty()) {
			return null;
		}
		ItemStack gear = container.getGearInputSlot().getItem();
		clampSelections(operations, gear);
		GearModificationAction operation = operations.get(operationIndex);
		ResourceLocation targetId = currentTargetId(gear, operation);
		if (targetId == null) {
			return null;
		}
		return new RerollSelection(operation.modification().getRegistryName(), targetId);
	}

	/** @return true if the click was consumed by the panel. */
	public boolean handleClick(VaultArtisanStationScreen screen, int mouseX, int mouseY, int button) {
		if (!visible || !VmaClientConfigs.isAutoRerollEnabled() || button != 0) {
			return false;
		}
		PanelRect rect = panelRect(screen);
		if (mouseX < rect.x || mouseX >= rect.x + rect.width || mouseY < rect.y || mouseY >= rect.y + rect.height) {
			return false;
		}
		VaultArtisanStationContainer container = (VaultArtisanStationContainer) screen.getMenu();
		List<GearModificationAction> operations = availableOperations(container);
		if (operations.isEmpty()) {
			return true;
		}
		clampSelections(operations, container.getGearInputSlot().getItem());
		AutoRerollEngine engine = AutoRerollEngine.getInstance();

		int y = rect.y + 15;
		if (mouseY >= y && mouseY < y + 11) {
			cycleOperation(mouseX, rect, operations);
			return true;
		}
		y += 11;
		if (mouseY >= y && mouseY < y + 11) {
			cycleTarget(mouseX, rect, operations.get(operationIndex));
			return true;
		}
		y += 11;
		y += 11;
		if (mouseY >= y && mouseY < y + 11) {
			VmaClientConfigs.setAutoResetPotential(!VmaClientConfigs.isAutoResetPotentialEnabled());
			return true;
		}
		y += 13;
		ItemStack gear = container.getGearInputSlot().getItem();
		boolean hasTargets = !ModifierCatalog
				.candidates(gear, ModifierCatalog.scopeOfOperation(operations.get(operationIndex).modification().getRegistryName()))
				.isEmpty();
		if (mouseY >= y && mouseY < y + 10 && (engine.isRunning() || hasTargets)) {
			if (engine.isRunning()) {
				engine.stop(StopReason.STOPPED, false);
			} else {
				RerollSelection selection = currentSelection();
				if (selection != null) {
					engine.start(selection.operationId(), selection.targetId());
				}
			}
			return true;
		}
		return true;
	}

	private void cycleOperation(int mouseX, PanelRect rect, List<GearModificationAction> operations) {
		boolean next = mouseX >= rect.x + rect.width - 6 - 14;
		boolean prev = mouseX < rect.x + 14;
		if (next) {
			operationIndex = (operationIndex + 1) % operations.size();
			targetIndex = 0;
		} else if (prev) {
			operationIndex = (operationIndex - 1 + operations.size()) % operations.size();
			targetIndex = 0;
		}
	}

	private void cycleTarget(int mouseX, PanelRect rect, GearModificationAction operation) {
		ItemStack gear = stationGear();
		List<Candidate> candidates = ModifierCatalog.candidates(gear,
				ModifierCatalog.scopeOfOperation(operation.modification().getRegistryName()));
		if (candidates.isEmpty()) {
			return;
		}
		boolean next = mouseX >= rect.x + rect.width - 6 - 14;
		boolean prev = mouseX < rect.x + 14;
		if (next) {
			targetIndex = (targetIndex + 1) % candidates.size();
		} else if (prev) {
			targetIndex = (targetIndex - 1 + candidates.size()) % candidates.size();
		}
	}

	private ResourceLocation currentTargetId(ItemStack gear, GearModificationAction operation) {
		List<Candidate> candidates = ModifierCatalog.candidates(gear,
				ModifierCatalog.scopeOfOperation(operation.modification().getRegistryName()));
		if (candidates.isEmpty()) {
			return null;
		}
		if (targetIndex >= candidates.size()) {
			targetIndex = 0;
		}
		return candidates.get(targetIndex).id();
	}

	private void clampSelections(List<GearModificationAction> operations, ItemStack gear) {
		if (operationIndex >= operations.size()) {
			operationIndex = 0;
			targetIndex = 0;
		}
		if (operations.isEmpty()) {
			return;
		}
		GearModificationAction operation = operations.get(operationIndex);
		List<Candidate> candidates = ModifierCatalog.candidates(gear,
				ModifierCatalog.scopeOfOperation(operation.modification().getRegistryName()));
		if (!candidates.isEmpty() && targetIndex >= candidates.size()) {
			targetIndex = 0;
		}
	}

	private static ItemStack stationGear() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen instanceof VaultArtisanStationScreen screen) {
			return ((VaultArtisanStationContainer) screen.getMenu()).getGearInputSlot().getItem();
		}
		return ItemStack.EMPTY;
	}

	private static List<GearModificationAction> availableOperations(VaultArtisanStationContainer container) {
		List<GearModificationAction> result = new ArrayList<>();
		for (GearModificationAction action : container.getModificationActions()) {
			OperationScope scope = ModifierCatalog.scopeOfOperation(action.modification().getRegistryName());
			if (scope != null) {
				result.add(action);
			}
		}
		return result;
	}

	private static PanelRect panelRect(VaultArtisanStationScreen screen) {
		int windowLeft = ((AbstractContainerScreen<?>) screen).getGuiLeft();
		int windowTop = ((AbstractContainerScreen<?>) screen).getGuiTop();
		int windowRight = windowLeft + ((AbstractContainerScreen<?>) screen).getXSize();
		int windowWidth = ((AbstractContainerScreen<?>) screen).getXSize();
		int windowHeight = ((AbstractContainerScreen<?>) screen).getYSize();

		int x = windowRight + MARGIN;
		if (x + PANEL_WIDTH > screen.width) {
			x = windowLeft - MARGIN - PANEL_WIDTH;
		}
		if (x < 0) {
			x = Math.max(0, screen.width - PANEL_WIDTH - MARGIN);
		}
		int y = Math.max(4, windowTop + 16);
		if (y + PANEL_HEIGHT > screen.height) {
			y = Math.max(4, screen.height - PANEL_HEIGHT - 4);
		}
		return new PanelRect(x, y, PANEL_WIDTH, PANEL_HEIGHT, windowWidth, windowHeight);
	}

	private static String stopReasonText(StopReason reason) {
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

	private static String truncate(String text, int maxChars) {
		return text.length() <= maxChars ? text : text.substring(0, maxChars - 1) + "\u2026";
	}

	private static void drawString(PoseStack poseStack, String text, int x, int y, int color) {
		Minecraft.getInstance().font.draw(poseStack, text, x, y, color);
	}

	private static void drawCentered(PoseStack poseStack, String text, int centerX, int y, int color) {
		Minecraft.getInstance().font.draw(poseStack, text, centerX - Minecraft.getInstance().font.width(text) / 2.0F,
				y, color);
	}

	private record PanelRect(int x, int y, int width, int height, int windowWidth, int windowHeight) {
	}
}