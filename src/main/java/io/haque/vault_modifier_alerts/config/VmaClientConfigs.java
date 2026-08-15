package io.haque.vault_modifier_alerts.config;

import com.electronwill.nightconfig.core.Config;
import com.mojang.logging.LogUtils;
import io.haque.vault_modifier_alerts.VmaReference;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VmaClientConfigs {

	private static final Logger LOGGER = LogUtils.getLogger();

	public static final ForgeConfigSpec SPEC;

	public static final ForgeConfigSpec.BooleanValue EXPIRY_ALERTS_ENABLED;
	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WATCHED_MODIFIERS;
	public static final ForgeConfigSpec.ConfigValue<Config> SOUND_OVERRIDES;
	public static final ForgeConfigSpec.DoubleValue VOLUME;
	public static final ForgeConfigSpec.DoubleValue PITCH;
	public static final ForgeConfigSpec.BooleanValue ALERT_SOUND_ENABLED;
	public static final ForgeConfigSpec.IntValue GRACE_PERIOD_TICKS;
	public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;

	public static final ForgeConfigSpec.BooleanValue HUD_ORDERING_ENABLED;
	public static final ForgeConfigSpec.BooleanValue HUD_ORDERING_DESCENDING;

	public static final ForgeConfigSpec.BooleanValue DOWNED_ALERTS_ENABLED;
	public static final ForgeConfigSpec.ConfigValue<String> DOWNED_SOUND_EVENT;
	public static final ForgeConfigSpec.BooleanValue DOWNED_LOCAL_SOUND_ENABLED;
	public static final ForgeConfigSpec.BooleanValue DOWNED_TEAMMATE_SOUND_ENABLED;
	public static final ForgeConfigSpec.DoubleValue DOWNED_VOLUME;
	public static final ForgeConfigSpec.DoubleValue DOWNED_PITCH;

	public static final ForgeConfigSpec.BooleanValue AUTO_REROLL_ENABLED;
	public static final ForgeConfigSpec.IntValue AUTO_REROLL_TICK_INTERVAL;
	public static final ForgeConfigSpec.IntValue AUTO_REROLL_ROLL_GAP_TICKS;
	public static final ForgeConfigSpec.IntValue AUTO_REROLL_ROLL_TIMEOUT_TICKS;
	public static final ForgeConfigSpec.IntValue AUTO_REROLL_MAX_ROLLS;
	public static final ForgeConfigSpec.BooleanValue AUTO_REROLL_RESET_POTENTIAL;
	public static final ForgeConfigSpec.ConfigValue<String> REROLL_SUCCESS_SOUND_EVENT;
	public static final ForgeConfigSpec.ConfigValue<String> REROLL_STOP_SOUND_EVENT;
	public static final ForgeConfigSpec.DoubleValue REROLL_VOLUME;
	public static final ForgeConfigSpec.DoubleValue REROLL_PITCH;

	public static final ForgeConfigSpec.BooleanValue REROLL_PANEL_ENABLED;
	public static final ForgeConfigSpec.ConfigValue<String> REROLL_PANEL_SIDE;
	public static final ForgeConfigSpec.BooleanValue REROLL_PANEL_COMPACT_MODE;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

		builder.push("Expiry Alerts");
		EXPIRY_ALERTS_ENABLED = builder.define("enabled", true);
		WATCHED_MODIFIERS = builder.defineList("watchedModifiers", List.of("the_vault:champion_domain"),
				VmaClientConfigs::isValidModifierId);
		Config defaultOverrides = Config.inMemory();
		defaultOverrides.set(VmaReference.CHAMPION_DOMAIN_ID, VmaReference.SOUND_EVENT_NAMESPACED);
		SOUND_OVERRIDES = builder.define("soundOverrides", defaultOverrides,
				VmaClientConfigs::isValidSoundOverrideMap);
		VOLUME = builder.defineInRange("volume", 1.0D, 0.0D, 2.0D);
		PITCH = builder.defineInRange("pitch", 1.0D, 0.5D, 2.0D);
		ALERT_SOUND_ENABLED = builder.define("alertSoundEnabled", true);
		GRACE_PERIOD_TICKS = builder.defineInRange("gracePeriodTicks", 20, 0, 200);
		DEBUG_LOGGING = builder.define("debugLogging", false);
		builder.pop();

		builder.push("HUD Ordering");
		HUD_ORDERING_ENABLED = builder.define("enabled", true);
		HUD_ORDERING_DESCENDING = builder.define("sortTemporalDescending", true);
		builder.pop();

		builder.push("Downed Alerts");
		DOWNED_ALERTS_ENABLED = builder.define("enabled", true);
		DOWNED_SOUND_EVENT = builder.define("soundEvent", VmaReference.DOWNED_SOUND_EVENT_NAMESPACED,
				VmaClientConfigs::isValidSoundEventId);
		DOWNED_LOCAL_SOUND_ENABLED = builder.define("localPlayerDownedSoundEnabled", true);
		DOWNED_TEAMMATE_SOUND_ENABLED = builder.define("teammateDownedSoundEnabled", true);
		DOWNED_VOLUME = builder.defineInRange("volume", 1.0D, 0.0D, 2.0D);
		DOWNED_PITCH = builder.defineInRange("pitch", 1.0D, 0.5D, 2.0D);
		builder.pop();

		builder.push("Auto Reroll");
		AUTO_REROLL_ENABLED = builder.define("enabled", false);
		AUTO_REROLL_TICK_INTERVAL = builder.defineInRange("tickInterval", 4, 2, 200);
		AUTO_REROLL_ROLL_GAP_TICKS = builder.defineInRange("rollGapTicks", 2, 1, 40);
		AUTO_REROLL_ROLL_TIMEOUT_TICKS = builder.defineInRange("rollTimeoutTicks", 60, 10, 400);
		AUTO_REROLL_MAX_ROLLS = builder.defineInRange("maxRolls", 0, 0, Integer.MAX_VALUE);
		AUTO_REROLL_RESET_POTENTIAL = builder.define("autoResetPotential", true);
		REROLL_SUCCESS_SOUND_EVENT = builder.define("successSoundEvent",
				VmaReference.DEFAULT_REROLL_SOUND_EVENT, VmaClientConfigs::isValidSoundEventId);
		REROLL_STOP_SOUND_EVENT = builder.define("stopSoundEvent",
				VmaReference.DEFAULT_REROLL_SOUND_EVENT, VmaClientConfigs::isValidSoundEventId);
		REROLL_VOLUME = builder.defineInRange("volume", 1.0D, 0.0D, 2.0D);
		REROLL_PITCH = builder.defineInRange("pitch", 1.0D, 0.5D, 2.0D);
		builder.pop();

		builder.push("Reroll Panel");
		REROLL_PANEL_ENABLED = builder
				.comment("Show the auto-reroll side panel when the artisan station is open.")
				.define("enabled", true);
		REROLL_PANEL_SIDE = builder
				.comment("Panel side preference: AUTO (left-first, fallback right), LEFT, or RIGHT.")
				.define("side", "AUTO", v -> v instanceof String s && (s.equals("AUTO") || s.equals("LEFT") || s.equals("RIGHT")));
		REROLL_PANEL_COMPACT_MODE = builder
				.comment("Collapse optional rows (range, counter) for a denser HUD.")
				.define("compactMode", false);
		builder.pop();

		SPEC = builder.build();
	}

	private VmaClientConfigs() {
	}

	public static boolean isExpiryAlertsEnabled() {
		return EXPIRY_ALERTS_ENABLED.get();
	}

	public static boolean isDebugLogging() {
		return DEBUG_LOGGING.get();
	}

	public static boolean isAlertSoundEnabled() {
		return ALERT_SOUND_ENABLED.get();
	}

	public static void setDebugLogging(boolean value) {
		DEBUG_LOGGING.set(value);
	}

	public static void setAlertSoundEnabled(boolean value) {
		ALERT_SOUND_ENABLED.set(value);
	}

	public static boolean isHudOrderingEnabled() {
		return HUD_ORDERING_ENABLED.get();
	}

	public static boolean isDownedAlertsEnabled() {
		return DOWNED_ALERTS_ENABLED.get();
	}

	public static boolean isLocalPlayerDownedSoundEnabled() {
		return DOWNED_LOCAL_SOUND_ENABLED.get();
	}

	public static boolean isTeammateDownedSoundEnabled() {
		return DOWNED_TEAMMATE_SOUND_ENABLED.get();
	}

	public static String downedSoundEvent() {
		return DOWNED_SOUND_EVENT.get();
	}

	public static boolean isAutoRerollEnabled() {
		return AUTO_REROLL_ENABLED.get();
	}

	public static void setAutoRerollEnabled(boolean value) {
		AUTO_REROLL_ENABLED.set(value);
	}

	public static int autoRerollTickInterval() {
		return AUTO_REROLL_TICK_INTERVAL.get();
	}

	public static int autoRerollRollGapTicks() {
		return AUTO_REROLL_ROLL_GAP_TICKS.get();
	}

	public static int autoRerollRollTimeoutTicks() {
		return AUTO_REROLL_ROLL_TIMEOUT_TICKS.get();
	}

	public static int autoRerollMaxRolls() {
		return AUTO_REROLL_MAX_ROLLS.get();
	}

	public static boolean isAutoResetPotentialEnabled() {
		return AUTO_REROLL_RESET_POTENTIAL.get();
	}

	public static void setAutoResetPotential(boolean value) {
		AUTO_REROLL_RESET_POTENTIAL.set(value);
	}

	public static boolean isRerollPanelEnabled() {
		return REROLL_PANEL_ENABLED.get();
	}

	public static String rerollPanelSide() {
		return REROLL_PANEL_SIDE.get();
	}

	public static boolean isRerollPanelCompactMode() {
		return REROLL_PANEL_COMPACT_MODE.get();
	}

	public static String rerollSuccessSoundEvent() {
		return REROLL_SUCCESS_SOUND_EVENT.get();
	}

	public static String rerollStopSoundEvent() {
		return REROLL_STOP_SOUND_EVENT.get();
	}

	public static List<String> watchedModifiers() {
		List<String> result = new ArrayList<>();
		for (String raw : WATCHED_MODIFIERS.get()) {
			if (ResourceLocation.tryParse(raw) != null) {
				result.add(raw);
			} else {
				LOGGER.warn("[S02] Ignoring invalid watchedModifiers entry: {}", raw);
			}
		}
		return result;
	}

public static String resolveSoundEventId(ResourceLocation modifierId) {
		return SOUND_OVERRIDES.get().get(modifierId.toString());
	}

	private static boolean isValidModifierId(Object value) {
		return value instanceof String s && ResourceLocation.tryParse(s) != null;
	}

	private static boolean isValidSoundEventId(Object value) {
		return value instanceof String s && ResourceLocation.tryParse(s) != null;
	}

	private static boolean isValidSoundOverrideMap(Object value) {
		if (!(value instanceof Map<?, ?> map)) {
			return false;
		}
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (!(entry.getKey() instanceof String key) || ResourceLocation.tryParse(key) == null) {
				return false;
			}
			if (!(entry.getValue() instanceof String sound) || ResourceLocation.tryParse(sound) == null) {
				return false;
			}
		}
		return true;
	}
}