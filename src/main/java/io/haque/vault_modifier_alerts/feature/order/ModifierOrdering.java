package io.haque.vault_modifier_alerts.feature.order;

import io.haque.vault_modifier_alerts.VaultModifierAlerts;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.tracker.VaultModifierTimeAccessor;
import iskallia.vault.core.vault.modifier.spi.VaultModifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModifierOrdering {

	private static volatile List<String> lastOrdered;

	private ModifierOrdering() {
	}

	public static Map<VaultModifier<?>, Integer> reorder(Map<VaultModifier<?>, Integer> group) {
		if (!VmaClientConfigs.isHudOrderingEnabled() || group == null || group.size() < 2) {
			lastOrdered = null;
			return group;
		}
		LinkedHashMap<VaultModifier<?>, Integer> result = new LinkedHashMap<>(group.size());
		Comparator<Map.Entry<VaultModifier<?>, Integer>> byTime =
				Comparator.comparingInt(e -> timeOf(e.getKey()));
		group.forEach((modifier, count) -> {
			if (!isTemporal(modifier)) {
				result.put(modifier, count);
			}
		});
		group.entrySet().stream()
				.filter(e -> isTemporal(e.getKey()))
				.sorted(VmaClientConfigs.HUD_ORDERING_DESCENDING.get() ? byTime.reversed() : byTime)
				.forEach(e -> result.put(e.getKey(), e.getValue()));
		if (VmaClientConfigs.isDebugLogging()) {
			VaultModifierAlerts.LOGGER.debug(
					"[VMA] HUD reorder: {} entries -> {} permanent first, {} temporal last (anti-anchor edge)",
					group.size(),
					result.entrySet().stream().filter(e -> !isTemporal(e.getKey())).count(),
					result.entrySet().stream().filter(e -> isTemporal(e.getKey())).count());
		}
		lastOrdered = describe(result);
		return result;
	}

	public static List<String> getLastOrdered() {
		return lastOrdered;
	}

	private static List<String> describe(Map<VaultModifier<?>, Integer> ordered) {
		List<String> entries = new ArrayList<>(ordered.size());
		ordered.forEach((modifier, count) -> entries.add(describe(modifier)));
		return entries;
	}

	private static String describe(VaultModifier<?> modifier) {
		String suffix = "permanent";
		if (modifier instanceof VaultModifierTimeAccessor accessor && accessor.vma$getTimeLeft() != null) {
			int ticks = accessor.vma$getTimeLeft();
			suffix = ticks > 0 ? "t+" + (ticks / 20) + "s" : "permanent(expired)";
		}
		return modifier.getId().toString() + " [" + suffix + "]";
	}

	static boolean isTemporal(VaultModifier<?> modifier) {
		return modifier instanceof VaultModifierTimeAccessor accessor
				&& accessor.vma$getTimeLeft() != null && accessor.vma$getTimeLeft() > 0;
	}

	private static int timeOf(VaultModifier<?> modifier) {
		return ((VaultModifierTimeAccessor) modifier).vma$getTimeLeft();
	}
}