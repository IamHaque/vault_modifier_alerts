package io.haque.vault_modifier_alerts.feature.reroll;

import io.haque.vault_modifier_alerts.VaultModifierAlerts;
import io.haque.vault_modifier_alerts.VmaReference;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.expiry.AlertSoundPlayer;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.OperationScope;
import io.haque.vault_modifier_alerts.feature.reroll.ModifierCatalog.RollTarget;
import iskallia.vault.client.gui.screen.block.VaultArtisanStationScreen;
import iskallia.vault.container.VaultArtisanStationContainer;
import iskallia.vault.gear.attribute.VaultGearModifier;
import iskallia.vault.gear.attribute.VaultGearModifier.AffixType;
import iskallia.vault.gear.crafting.VaultGearCraftingHelper;
import iskallia.vault.gear.data.VaultGearData;
import iskallia.vault.gear.modification.GearModificationAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * F3 auto-reroll state machine (see F3_AUTO_REROLL_PLAN.md). Runs on the client
 * thread only. The game handles every mechanic; this engine only presses existing
 * station buttons, classifies *why* a button is disabled (via the game's own
 * reducePotential check on a copy), and detects the rolled target by reading the
 * gear's modifiers. Stop reasons are surfaced in the panel and via sound.
 */
public final class AutoRerollEngine {

	public enum StopReason {
		SUCCESS, NO_GEAR, OUT_OF_MATERIALS, OUT_OF_POTENTIAL, INVALID_TARGET, MAX_ROLLS, TIMEOUT, SCREEN_CLOSED,
		STOPPED
	}

	public enum StopCondition {
		ANY, ALL
	}

	private static final AutoRerollEngine INSTANCE = new AutoRerollEngine();

	private boolean running;
	private boolean inFlight;
	private boolean resetUsedThisSession;
	private int potentialResetsThisSession;
	private ResourceLocation operationId;
	private List<RollTarget> targets = List.of();
	private StopCondition stopCondition = StopCondition.ANY;
	private boolean[] allPassed = new boolean[0];
	private ItemStack lastPressedGear;
	private VaultArtisanStationContainer.Tab lastSelectedTab;
	private long lastPressTick;
	private int rolls;
	private StopReason stopReason;
	private double lastTargetValue;

	private AutoRerollEngine() {
	}

	public static AutoRerollEngine getInstance() {
		return INSTANCE;
	}

	public void start(ResourceLocation operation, List<RollTarget> rollTargets, StopCondition condition) {
		if (operation == null || rollTargets == null || rollTargets.isEmpty()
				|| !VmaClientConfigs.isAutoRerollEnabled()) {
			return;
		}
		operationId = operation;
		targets = List.copyOf(rollTargets);
		stopCondition = condition == null ? StopCondition.ANY : condition;
		allPassed = new boolean[targets.size()];
		running = true;
		inFlight = false;
		resetUsedThisSession = false;
		potentialResetsThisSession = 0;
		lastPressedGear = null;
		lastSelectedTab = null;
		lastPressTick = 0;
		rolls = 0;
		stopReason = null;
		lastTargetValue = 0.0;
		VaultModifierAlerts.LOGGER.debug("[VMA] Auto-reroll started: operation={}, targets={}, condition={}",
				operation, targets, stopCondition);
	}

	public void stop(StopReason reason, boolean playSound) {
		if (!running) {
			return;
		}
		running = false;
		inFlight = false;
		lastPressedGear = null;
		stopReason = reason;
		if (playSound) {
			playStopSound(reason);
		}
		if (VmaClientConfigs.isDebugLogging()) {
			VaultModifierAlerts.LOGGER.debug("[VMA] Auto-reroll stopped: reason={}, rolls={}", reason, rolls);
		}
	}

	/**
	 * Fired by the mixin at HEAD of attemptCraft, for both engine presses and
	 * manual player clicks. Manual clicks while idle-with-cooldown reset the press
	 * timer (double-click protection).
	 */
	public void onCraftTriggered(GearModificationAction action) {
		Minecraft mc = Minecraft.getInstance();
		if (!running || inFlight || mc.player == null || !VmaClientConfigs.isAutoRerollEnabled()) {
			return;
		}
		lastPressTick = mc.player.tickCount;
	}

	public void evaluate() {
		Minecraft mc = Minecraft.getInstance();
		if (!VmaClientConfigs.isAutoRerollEnabled()) {
			stop(StopReason.STOPPED, false);
			return;
		}
		Screen screen = mc.screen;
		if (!(screen instanceof VaultArtisanStationScreen station) || mc.player == null) {
			stop(StopReason.SCREEN_CLOSED, false);
			return;
		}
		if (!running) {
			return;
		}
		VaultArtisanStationContainer container = (VaultArtisanStationContainer) station.getMenu();
		ItemStack gear = container.getGearInputSlot().getItem();

		if (inFlight) {
			if (gearChanged(gear)) {
				inFlight = false;
				if (targetRolled(gear)) {
					stop(StopReason.SUCCESS, true);
					return;
				}
			} else if (mc.player.tickCount - lastPressTick > VmaClientConfigs.autoRerollRollTimeoutTicks()) {
				stop(StopReason.TIMEOUT, true);
				return;
			} else {
				return;
			}
		}

		if (mc.player.tickCount - lastPressTick < VmaClientConfigs.autoRerollTickInterval()) {
			return;
		}

		if (gear.isEmpty()) {
			stop(StopReason.NO_GEAR, true);
			return;
		}
		int maxRolls = VmaClientConfigs.autoRerollMaxRolls();
		if (maxRolls > 0 && rolls >= maxRolls) {
			stop(StopReason.MAX_ROLLS, true);
			return;
		}

		OperationScope scope = ModifierCatalog.scopeOfOperation(operationId);
		if (scope == null || targets.stream().noneMatch(t -> ModifierCatalog.isApplicable(gear, t.id(), scope))) {
			stop(StopReason.INVALID_TARGET, true);
			return;
		}

		GearModificationAction action = findAction(container, operationId);
		if (action == null) {
			stop(StopReason.INVALID_TARGET, true);
			return;
		}

		if (action.canApply(container, mc.player)) {
			press(station, action, gear);
		} else if (isOutOfPotential(gear, action)) {
			handleOutOfPotential(station, container, gear);
		} else {
			stop(StopReason.OUT_OF_MATERIALS, true);
		}
	}

	public boolean isRunning() {
		return running;
	}

	public ResourceLocation operationId() {
		return operationId;
	}

	/** The first watched target id (convenience; the run may watch several). */
	public ResourceLocation targetId() {
		return targets.isEmpty() ? null : targets.get(0).id();
	}

	public List<RollTarget> targets() {
		return targets;
	}

	public StopCondition stopCondition() {
		return stopCondition;
	}

	public int rolls() {
		return rolls;
	}

	public StopReason stopReason() {
		return stopReason;
	}

	/** Times crafting potential was auto-reset during the current run. */
	public int potentialResetsThisSession() {
		return potentialResetsThisSession;
	}

	/** The most recent roll value of the target modifier seen during the current run (0 before any roll). */
	public double lastTargetValue() {
		return lastTargetValue;
	}

	private void press(VaultArtisanStationScreen station, GearModificationAction action, ItemStack gear) {
		Minecraft mc = Minecraft.getInstance();
		if (station instanceof ArtisanStationScreenAccessor accessor) {
			VaultArtisanStationContainer.Tab tab = action.tab();
			if (!tab.equals(lastSelectedTab)) {
				station.selectTab(tab);
				lastSelectedTab = tab;
			}
			lastPressedGear = gear.copy();
			lastPressTick = mc.player.tickCount;
			rolls++;
			inFlight = true;
			accessor.vma$triggerAction(action);
		} else {
			stop(StopReason.SCREEN_CLOSED, false);
		}
	}

	private void handleOutOfPotential(VaultArtisanStationScreen station, VaultArtisanStationContainer container,
			ItemStack gear) {
		Minecraft mc = Minecraft.getInstance();
		if (!VmaClientConfigs.isAutoResetPotentialEnabled() || resetUsedThisSession) {
			stop(StopReason.OUT_OF_POTENTIAL, true);
			return;
		}
		ResourceLocation resetId = ResourceLocation.tryParse(VmaReference.OPERATION_RESET_POTENTIAL);
		GearModificationAction resetAction = findAction(container, resetId);
		if (resetAction == null || !resetAction.canApply(container, mc.player)) {
			stop(StopReason.OUT_OF_POTENTIAL, true);
			return;
		}
		resetUsedThisSession = true;
		potentialResetsThisSession++;
		press(station, resetAction, gear);
	}

	private static boolean isOutOfPotential(ItemStack gear, GearModificationAction action) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		return !VaultGearCraftingHelper.reducePotential(gear.copy(), mc.player, action.modification());
	}

	private static GearModificationAction findAction(VaultArtisanStationContainer container,
			ResourceLocation operationId) {
		if (operationId == null) {
			return null;
		}
		for (GearModificationAction action : container.getModificationActions()) {
			if (operationId.equals(action.modification().getRegistryName())) {
				return action;
			}
		}
		return null;
	}

	private boolean gearChanged(ItemStack gear) {
		return lastPressedGear == null || !ItemStack.matches(lastPressedGear, gear);
	}

	private boolean targetRolled(ItemStack gear) {
		if (targets.isEmpty() || gear.isEmpty() || !(gear.getItem() instanceof iskallia.vault.gear.item.VaultGearItem)) {
			return false;
		}
		VaultGearData data = VaultGearData.read(gear);
		boolean all = stopCondition == StopCondition.ALL;
		for (AffixType affixType : scopeAffixes(ModifierCatalog.scopeOfOperation(operationId))) {
			for (VaultGearModifier<?> modifier : data.getModifiers(affixType)) {
				int index = indexOfTarget(modifier.getModifierIdentifier());
				if (index < 0) {
					continue;
				}
				double displayValue = ModifierCatalog.toDisplayUnits(modifier);
				RollTarget target = targets.get(index);
				boolean passed = !target.thresholdEnabled() || displayValue >= target.thresholdValue();
				lastTargetValue = displayValue;
				VaultModifierAlerts.LOGGER.debug("[VMA] Rolled target={} value={}, threshold={}, passed={}",
						target.id(), displayValue, target.thresholdEnabled() ? target.thresholdValue() : null, passed);
				if (all) {
					if (passed) {
						allPassed[index] = true;
					}
				} else if (passed) {
					return true;
				}
			}
		}
		if (!all) {
			return false;
		}
		for (boolean passed : allPassed) {
			if (!passed) {
				return false;
			}
		}
		return true;
	}

	private int indexOfTarget(ResourceLocation id) {
		for (int i = 0; i < targets.size(); i++) {
			if (targets.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	private static AffixType[] scopeAffixes(OperationScope scope) {
		if (scope == null) {
			return new AffixType[0];
		}
		return switch (scope) {
			case PREFIX -> new AffixType[] { AffixType.PREFIX };
			case SUFFIX -> new AffixType[] { AffixType.SUFFIX };
			case IMPLICIT -> new AffixType[] { AffixType.IMPLICIT };
			case PREFIX_SUFFIX -> new AffixType[] { AffixType.PREFIX, AffixType.SUFFIX };
		};
	}

	private static void playStopSound(StopReason reason) {
		if (reason == StopReason.SUCCESS) {
			AlertSoundPlayer.play(VmaClientConfigs.rerollSuccessSoundEvent(), VmaClientConfigs.REROLL_VOLUME.get(),
					VmaClientConfigs.REROLL_PITCH.get(), null);
			return;
		}
		AlertSoundPlayer.play(VmaClientConfigs.rerollStopSoundEvent(), VmaClientConfigs.REROLL_VOLUME.get(),
				VmaClientConfigs.REROLL_PITCH.get(), null);
	}
}