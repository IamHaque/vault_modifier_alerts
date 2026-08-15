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

import java.util.Arrays;
import java.util.List;

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
		EVALUATION_ERROR, STOPPED
	}

	public enum StopCondition {
		ANY, ALL
	}

	private static final AutoRerollEngine INSTANCE = new AutoRerollEngine();

	/**
	 * Comparison tolerance in display units. Roll values are stored as raw
	 * doubles (percent attributes as fractions of 1), so an exact "5.0" roll can
	 * arrive as 4.99999999... after the display-unit conversion; the panel also
	 * rounds to two decimals. 0.005 = half of that rounding step, so a roll the
	 * panel displays as the threshold counts as met.
	 */
	private static final double THRESHOLD_EPSILON = 0.005;

	/** Every affix list on a gear; the stop condition judges the whole gear state, not the last roll's scope. */
	private static final AffixType[] ALL_AFFIX_TYPES = { AffixType.IMPLICIT, AffixType.PREFIX, AffixType.SUFFIX };

	private boolean running;
	private boolean inFlight;
	private int potentialResetsThisSession;
	private ResourceLocation operationId;
	private List<RollTarget> targets = List.of();
	private StopCondition stopCondition = StopCondition.ANY;
	private boolean[] allPassed = new boolean[0];
	private double[] lastRolledValues = new double[0];
	private ItemStack lastPressedGear;
	private ItemStack lastEvaluatedGear;
	private ItemStack lastApplicabilityGear;
	private boolean applicabilityOk;
	private VaultArtisanStationContainer.Tab lastSelectedTab;
	private long lastPressTick;
	private int lastRollCompletedTick;
	private int rolls;
	private StopReason stopReason;

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
		lastRolledValues = new double[targets.size()];
		running = true;
		inFlight = false;
		potentialResetsThisSession = 0;
		lastPressedGear = null;
		lastEvaluatedGear = null;
		lastApplicabilityGear = null;
		applicabilityOk = false;
		lastSelectedTab = null;
		lastPressTick = 0;
		lastRollCompletedTick = 0;
		rolls = 0;
		stopReason = null;
		logRoll("Auto-reroll started: operation={}, targets={}, condition={}", operation, targets, stopCondition);
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
			StringBuilder values = new StringBuilder();
			for (int i = 0; i < targets.size(); i++) {
				if (i > 0) {
					values.append(", ");
				}
				values.append(targets.get(i).id()).append('=').append(formatRollValue(lastRolledValues[i]));
			}
			VaultModifierAlerts.LOGGER.info("[VMA] Auto-reroll stopped: reason={}, rolls={}, potentialResets={}, values=[{}]",
					reason, rolls, potentialResetsThisSession, values);
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
				lastRollCompletedTick = mc.player.tickCount;
			} else if (mc.player.tickCount - lastPressTick > VmaClientConfigs.autoRerollRollTimeoutTicks()) {
				logRoll("roll #{} timeout after {} ticks", rolls, VmaClientConfigs.autoRerollRollTimeoutTicks());
				stop(StopReason.TIMEOUT, true);
				return;
			} else {
				return;
			}
		}

		if (gearChangedSince(lastEvaluatedGear, gear)) {
			lastEvaluatedGear = gear.copy();
			try {
				boolean qualified = targetRolled(gear);
				logRoll("roll #{} result: {} met {}/{}", rolls, stopCondition, metCount(), targets.size());
				if (qualified) {
					stop(StopReason.SUCCESS, true);
					return;
				}
			} catch (Throwable t) {
				VaultModifierAlerts.LOGGER.error("[VMA] Roll evaluation failed", t);
				stop(StopReason.EVALUATION_ERROR, true);
				return;
			}
		}

		if (mc.player.tickCount - lastPressTick < VmaClientConfigs.autoRerollTickInterval()) {
			return;
		}
		if (lastRollCompletedTick > 0
				&& mc.player.tickCount - lastRollCompletedTick < VmaClientConfigs.autoRerollRollGapTicks()) {
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

		if (lastApplicabilityGear == null || !ItemStack.matches(lastApplicabilityGear, gear)) {
			lastApplicabilityGear = gear.copy();
			OperationScope scope = ModifierCatalog.scopeOfOperation(operationId);
			applicabilityOk = scope != null
					&& targets.stream().anyMatch(t -> ModifierCatalog.isApplicable(gear, t.id(), scope));
		}
		if (!applicabilityOk) {
			stop(StopReason.INVALID_TARGET, true);
			return;
		}

		GearModificationAction action = findAction(container, operationId);
		if (action == null) {
			stop(StopReason.INVALID_TARGET, true);
			return;
		}

		if (action.canApply(container, mc.player)) {
			press(station, action, gear, true);
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

	/** The most recent roll value of the target at the given index (0 before any roll). */
	public double currentValue(int index) {
		return index >= 0 && index < lastRolledValues.length ? lastRolledValues[index] : 0.0;
	}

	/** Whether the target at the given index met its threshold on the last roll evaluation. */
	public boolean isMet(int index) {
		return index >= 0 && index < allPassed.length && allPassed[index];
	}

	/** How many targets met their threshold on the last roll evaluation. */
	public int metCount() {
		int count = 0;
		for (boolean passed : allPassed) {
			if (passed) {
				count++;
			}
		}
		return count;
	}

	private void press(VaultArtisanStationScreen station, GearModificationAction action, ItemStack gear,
			boolean countsAsRoll) {
		Minecraft mc = Minecraft.getInstance();
		if (station instanceof ArtisanStationScreenAccessor accessor) {
			VaultArtisanStationContainer.Tab tab = action.tab();
			if (!tab.equals(lastSelectedTab)) {
				station.selectTab(tab);
				lastSelectedTab = tab;
			}
			lastPressedGear = gear.copy();
			lastPressTick = mc.player.tickCount;
			if (countsAsRoll) {
				rolls++;
				logRoll("roll #{} press: {} (potential {})", rolls, action.modification().getRegistryName(),
						ModifierCatalog.craftingPotential(gear));
			}
			inFlight = true;
			accessor.vma$triggerAction(action);
		} else {
			stop(StopReason.SCREEN_CLOSED, false);
		}
	}

	private void handleOutOfPotential(VaultArtisanStationScreen station, VaultArtisanStationContainer container,
			ItemStack gear) {
		Minecraft mc = Minecraft.getInstance();
		if (!VmaClientConfigs.isAutoResetPotentialEnabled()) {
			stop(StopReason.OUT_OF_POTENTIAL, true);
			return;
		}
		ResourceLocation resetId = ResourceLocation.tryParse(VmaReference.OPERATION_RESET_POTENTIAL);
		GearModificationAction resetAction = findAction(container, resetId);
		if (resetAction == null || !resetAction.canApply(container, mc.player)) {
			stop(StopReason.OUT_OF_POTENTIAL, true);
			return;
		}
		potentialResetsThisSession++;
		logRoll("roll #{} potential reset #{}", rolls + 1, potentialResetsThisSession);
		press(station, resetAction, gear, false);
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

	private static boolean gearChangedSince(ItemStack last, ItemStack current) {
		return last == null || !ItemStack.matches(last, current);
	}

	/**
	 * Whether the current gear state satisfies the stop condition. Judges the
	 * whole gear (base/implicits, prefixes, suffixes) - never only what the last
	 * press re-rolled - so a gear that visibly meets the watched targets stops
	 * the run no matter which operation produced it.
	 */
	private boolean targetRolled(ItemStack gear) {
		if (targets.isEmpty() || gear.isEmpty() || !(gear.getItem() instanceof iskallia.vault.gear.item.VaultGearItem)) {
			return false;
		}
		// Every station press re-rolls its operation scope, so the ALL
		// condition must be judged against the current gear state only - never
		// carry pass flags from earlier rolls.
		Arrays.fill(allPassed, false);
		Arrays.fill(lastRolledValues, 0.0);
		VaultGearData data = VaultGearData.read(gear);
		boolean all = stopCondition == StopCondition.ALL;
		for (AffixType affixType : ALL_AFFIX_TYPES) {
			for (VaultGearModifier<?> modifier : data.getModifiers(affixType)) {
				int index = indexOfTarget(modifier.getModifierIdentifier());
				if (index < 0) {
					continue;
				}
				double displayValue = ModifierCatalog.toDisplayUnits(modifier);
				RollTarget target = targets.get(index);
				boolean passed = !target.thresholdEnabled()
						|| displayValue + THRESHOLD_EPSILON >= target.thresholdValue();
				lastRolledValues[index] = displayValue;
				logRoll("roll #{} target={} group={} value={} threshold={} passed={}", rolls, target.id(),
						groupName(affixType), formatRollValue(displayValue),
						target.thresholdEnabled() ? formatRollValue(target.thresholdValue()) : null, passed);
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

	private static String groupName(AffixType affixType) {
		return switch (affixType) {
			case IMPLICIT -> "BASE";
			case PREFIX -> "PREFIX";
			case SUFFIX -> "SUFFIX";
		};
	}

	/** Display units without trailing zeros ("25", "10.5"). */
	private static String formatRollValue(double value) {
		if (value == Math.rint(value)) {
			return String.valueOf((long) value);
		}
		String text = String.format("%.2f", value);
		return text.replaceAll("0$", "").replaceAll("\\.$", "");
	}

	/** INFO-level roll log, only visible with debug logging enabled (production latest.log). */
	private void logRoll(String format, Object... args) {
		if (VmaClientConfigs.isDebugLogging()) {
			VaultModifierAlerts.LOGGER.info("[VMA] " + format, args);
		}
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