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
	public static final ForgeConfigSpec.ConfigValue<Integer> PANEL_BG_COLOR;
	public static final ForgeConfigSpec.ConfigValue<Integer> PANEL_BORDER_COLOR;
	public static final ForgeConfigSpec.ConfigValue<Integer> PANEL_TEXT_COLOR;
	public static final ForgeConfigSpec.ConfigValue<Integer> PANEL_ACCENT_GOLD_COLOR;
	public static final ForgeConfigSpec.ConfigValue<Integer> PANEL_SUCCESS_COLOR;
	public static final ForgeConfigSpec.ConfigValue<Integer> PANEL_DANGER_COLOR;
	public static final ForgeConfigSpec.ConfigValue<Integer> PANEL_MUTED_COLOR;
	public static final ForgeConfigSpec.ConfigValue<Integer> PANEL_DISABLED_COLOR;
	public static final ForgeConfigSpec.ConfigValue<Integer> PANEL_ROW_HOVER_COLOR;
	public static final ForgeConfigSpec.ConfigValue<Integer> PANEL_ROW_OPEN_COLOR;

	// Config path constants (§8 Design Guidelines — paths are display labels)
	public static final class Paths {
		public static final String AUTO_REROLL_ENABLED = "enabled";
		public static final String AUTO_REROLL_TICK_INTERVAL = "tickInterval";
		public static final String AUTO_REROLL_ROLL_GAP_TICKS = "rollGapTicks";
		public static final String AUTO_REROLL_ROLL_TIMEOUT_TICKS = "rollTimeoutTicks";
		public static final String AUTO_REROLL_MAX_ROLLS = "maxRolls";
		public static final String AUTO_REROLL_RESET_POTENTIAL = "autoResetPotential";
		public static final String REROLL_SUCCESS_SOUND_EVENT = "successSoundEvent";
		public static final String REROLL_STOP_SOUND_EVENT = "stopSoundEvent";
		public static final String REROLL_VOLUME = "volume";
		public static final String REROLL_PITCH = "pitch";
		public static final String REROLL_PANEL_ENABLED = "enabled";
		public static final String REROLL_PANEL_SIDE = "side";
		public static final String PANEL_BG_COLOR = "panelBgColor";
		public static final String PANEL_BORDER_COLOR = "panelBorderColor";
		public static final String PANEL_TEXT_COLOR = "panelTextColor";
		public static final String PANEL_ACCENT_GOLD_COLOR = "panelAccentGoldColor";
		public static final String PANEL_SUCCESS_COLOR = "panelSuccessColor";
		public static final String PANEL_DANGER_COLOR = "panelDangerColor";
		public static final String PANEL_MUTED_COLOR = "panelMutedColor";
		public static final String PANEL_DISABLED_COLOR = "panelDisabledColor";
		public static final String PANEL_ROW_HOVER_COLOR = "panelRowHoverColor";
		public static final String PANEL_ROW_OPEN_COLOR = "panelRowOpenColor";
	}

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
		AUTO_REROLL_ENABLED = builder
				.comment("Enable auto-reroll while the artisan station is open.")
				.define(Paths.AUTO_REROLL_ENABLED, false);
		AUTO_REROLL_TICK_INTERVAL = builder
				.comment("Engine evaluate interval in game ticks between rolls.")
				.defineInRange(Paths.AUTO_REROLL_TICK_INTERVAL, 4, 2, 200);
		AUTO_REROLL_ROLL_GAP_TICKS = builder
				.comment("Minimum ticks between two reroll button presses.")
				.defineInRange(Paths.AUTO_REROLL_ROLL_GAP_TICKS, 2, 1, 40);
		AUTO_REROLL_ROLL_TIMEOUT_TICKS = builder
				.comment("Ticks to wait for a reroll result before giving up on the current press.")
				.defineInRange(Paths.AUTO_REROLL_ROLL_TIMEOUT_TICKS, 60, 10, 400);
		AUTO_REROLL_MAX_ROLLS = builder
				.comment("Maximum rolls per run; 0 means unlimited.")
				.defineInRange(Paths.AUTO_REROLL_MAX_ROLLS, 0, 0, Integer.MAX_VALUE);
		AUTO_REROLL_RESET_POTENTIAL = builder
				.comment("Auto-apply a potential reset when the station offers one.")
				.define(Paths.AUTO_REROLL_RESET_POTENTIAL, true);
		REROLL_SUCCESS_SOUND_EVENT = builder
				.comment("Sound event played when a run stops with all targets met.")
				.define(Paths.REROLL_SUCCESS_SOUND_EVENT,
						VmaReference.DEFAULT_REROLL_SOUND_EVENT, VmaClientConfigs::isValidSoundEventId);
		REROLL_STOP_SOUND_EVENT = builder
				.comment("Sound event played when a run stops without reaching the goal.")
				.define(Paths.REROLL_STOP_SOUND_EVENT,
						VmaReference.DEFAULT_REROLL_SOUND_EVENT, VmaClientConfigs::isValidSoundEventId);
		REROLL_VOLUME = builder
				.comment("Volume for reroll status sounds.")
				.defineInRange(Paths.REROLL_VOLUME, 1.0D, 0.0D, 2.0D);
		REROLL_PITCH = builder
				.comment("Pitch for reroll status sounds.")
				.defineInRange(Paths.REROLL_PITCH, 1.0D, 0.5D, 2.0D);
		builder.pop();

		builder.push("Reroll Panel");
		REROLL_PANEL_ENABLED = builder
				.comment("Show the auto-reroll side panel when the artisan station is open.")
				.define(Paths.REROLL_PANEL_ENABLED, true);
		REROLL_PANEL_SIDE = builder
				.comment("Panel side preference: AUTO (left-first, fallback right), LEFT, or RIGHT.")
				.define(Paths.REROLL_PANEL_SIDE, "AUTO", v -> v instanceof String s && (s.equals("AUTO") || s.equals("LEFT") || s.equals("RIGHT")));
		PANEL_BG_COLOR = builder
				.comment("Panel background color (ARGB hex).")
				.define(Paths.PANEL_BG_COLOR, 0xEE111111);
		PANEL_BORDER_COLOR = builder
				.comment("Panel border color (ARGB hex).")
				.define(Paths.PANEL_BORDER_COLOR, 0xFF6B6B6B);
		PANEL_TEXT_COLOR = builder
				.comment("Default text color (ARGB hex).")
				.define(Paths.PANEL_TEXT_COLOR, 0xFFFFFFFF);
		PANEL_ACCENT_GOLD_COLOR = builder
				.comment("Gold accent color for headers and highlights (ARGB hex).")
				.define(Paths.PANEL_ACCENT_GOLD_COLOR, 0xFFE3C38C);
		PANEL_SUCCESS_COLOR = builder
				.comment("Success/ready state color (ARGB hex).")
				.define(Paths.PANEL_SUCCESS_COLOR, 0xFF55FF55);
		PANEL_DANGER_COLOR = builder
				.comment("Danger/error state color (ARGB hex).")
				.define(Paths.PANEL_DANGER_COLOR, 0xFFFF5555);
		PANEL_MUTED_COLOR = builder
				.comment("Muted/secondary text color (ARGB hex).")
				.define(Paths.PANEL_MUTED_COLOR, 0xFFA0A0A0);
		PANEL_DISABLED_COLOR = builder
				.comment("Disabled/greyed-out text color (ARGB hex).")
				.define(Paths.PANEL_DISABLED_COLOR, 0xFF707070);
		PANEL_ROW_HOVER_COLOR = builder
				.comment("Row hover highlight color (ARGB hex).")
				.define(Paths.PANEL_ROW_HOVER_COLOR, 0xFF3A3A3A);
		PANEL_ROW_OPEN_COLOR = builder
				.comment("Row open/active dropdown background color (ARGB hex).")
				.define(Paths.PANEL_ROW_OPEN_COLOR, 0xFF543C1F);
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

	public static void setRerollPanelEnabled(boolean value) {
		REROLL_PANEL_ENABLED.set(value);
	}

	public static String rerollPanelSide() {
		return REROLL_PANEL_SIDE.get();
	}

	public static int panelBgColor() {
		return PANEL_BG_COLOR.get();
	}

	public static int panelBorderColor() {
		return PANEL_BORDER_COLOR.get();
	}

	public static int panelTextColor() {
		return PANEL_TEXT_COLOR.get();
	}

	public static int panelAccentGoldColor() {
		return PANEL_ACCENT_GOLD_COLOR.get();
	}

	public static int panelSuccessColor() {
		return PANEL_SUCCESS_COLOR.get();
	}

	public static int panelDangerColor() {
		return PANEL_DANGER_COLOR.get();
	}

	public static int panelMutedColor() {
		return PANEL_MUTED_COLOR.get();
	}

	public static int panelDisabledColor() {
		return PANEL_DISABLED_COLOR.get();
	}

	public static int panelRowHoverColor() {
		return PANEL_ROW_HOVER_COLOR.get();
	}

	public static int panelRowOpenColor() {
		return PANEL_ROW_OPEN_COLOR.get();
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