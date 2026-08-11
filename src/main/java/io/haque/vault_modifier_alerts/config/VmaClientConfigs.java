package io.haque.vault_modifier_alerts.config;

import com.mojang.logging.LogUtils;
import io.haque.vault_modifier_alerts.VmaReference;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class VmaClientConfigs {

	private static final Logger LOGGER = LogUtils.getLogger();

	public static final ForgeConfigSpec SPEC;

	public static final ForgeConfigSpec.BooleanValue EXPIRY_ALERTS_ENABLED;
	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WATCHED_MODIFIERS;
	public static final ForgeConfigSpec.ConfigValue<String> SOUND_EVENT;
	public static final ForgeConfigSpec.DoubleValue VOLUME;
	public static final ForgeConfigSpec.DoubleValue PITCH;
	public static final ForgeConfigSpec.IntValue GRACE_PERIOD_TICKS;
	public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;

	public static final ForgeConfigSpec.BooleanValue HUD_ORDERING_ENABLED;
	public static final ForgeConfigSpec.BooleanValue HUD_ORDERING_ASCENDING;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

		builder.push("Expiry Alerts");
		EXPIRY_ALERTS_ENABLED = builder.define("enabled", true);
		WATCHED_MODIFIERS = builder.defineList("watchedModifiers", List.of("the_vault:champion_domain"),
				VmaClientConfigs::isValidModifierId);
		SOUND_EVENT = builder.define("soundEvent", VmaReference.MOD_ID + ":" + VmaReference.SOUND_EVENT_ID,
				VmaClientConfigs::isValidModifierId);
		VOLUME = builder.defineInRange("volume", 1.0D, 0.0D, 2.0D);
		PITCH = builder.defineInRange("pitch", 1.0D, 0.5D, 2.0D);
		GRACE_PERIOD_TICKS = builder.defineInRange("gracePeriodTicks", 20, 0, 200);
		DEBUG_LOGGING = builder.define("debugLogging", false);
		builder.pop();

		builder.push("HUD Ordering");
		HUD_ORDERING_ENABLED = builder.define("enabled", true);
		HUD_ORDERING_ASCENDING = builder.define("sortTemporalAscending", true);
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

	public static boolean isHudOrderingEnabled() {
		return HUD_ORDERING_ENABLED.get();
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

	private static boolean isValidModifierId(Object value) {
		return value instanceof String s && ResourceLocation.tryParse(s) != null;
	}
}