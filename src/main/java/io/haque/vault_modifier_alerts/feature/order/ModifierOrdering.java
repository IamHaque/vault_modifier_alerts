package io.haque.vault_modifier_alerts.feature.order;

import io.haque.vault_modifier_alerts.VaultModifierAlerts;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.tracker.VaultModifierTimeAccessor;
import iskallia.vault.core.vault.modifier.spi.VaultModifier;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModifierOrdering {

	private ModifierOrdering() {
	}

	public static Map<VaultModifier<?>, Integer> reorder(Map<VaultModifier<?>, Integer> group) {
		if (!VmaClientConfigs.isHudOrderingEnabled() || group == null || group.size() < 2) {
			return group;
		}
		LinkedHashMap<VaultModifier<?>, Integer> result = new LinkedHashMap<>(group.size());
		Comparator<Map.Entry<VaultModifier<?>, Integer>> byTime =
				Comparator.comparingInt(e -> timeOf(e.getKey()));
		group.entrySet().stream()
				.filter(e -> isTemporal(e.getKey()))
				.sorted(VmaClientConfigs.HUD_ORDERING_ASCENDING.get() ? byTime : byTime.reversed())
				.forEach(e -> result.put(e.getKey(), e.getValue()));
		group.forEach(result::putIfAbsent);
		if (VmaClientConfigs.isDebugLogging()) {
			VaultModifierAlerts.LOGGER.debug("[VMA] HUD reorder: {} entries -> {} temporal first, {} permanent",
					group.size(),
					result.entrySet().stream().filter(e -> isTemporal(e.getKey())).count(),
					result.entrySet().stream().filter(e -> !isTemporal(e.getKey())).count());
		}
		return result;
	}

	static boolean isTemporal(VaultModifier<?> modifier) {
		return modifier instanceof VaultModifierTimeAccessor accessor
				&& accessor.vma$getTimeLeft() != null && accessor.vma$getTimeLeft() > 0;
	}

	private static int timeOf(VaultModifier<?> modifier) {
		return ((VaultModifierTimeAccessor) modifier).vma$getTimeLeft();
	}
}