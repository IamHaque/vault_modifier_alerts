package io.haque.vault_modifier_alerts.feature.reroll;

import io.haque.vault_modifier_alerts.VmaReference;
import iskallia.vault.config.gear.VaultGearTierConfig;
import iskallia.vault.config.gear.VaultGearTierConfig.AttributeGroup;
import iskallia.vault.config.gear.VaultGearTierConfig.ModifierAffixTagGroup;
import iskallia.vault.config.gear.VaultGearTierConfig.ModifierTierGroup;
import iskallia.vault.gear.attribute.VaultGearAttribute;
import iskallia.vault.gear.attribute.VaultGearAttributeRegistry;
import iskallia.vault.gear.attribute.VaultGearModifier.AffixType;
import iskallia.vault.gear.data.VaultGearData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Applicability guard for auto-reroll targets (F3, see F3_AUTO_REROLL_PLAN.md).
 * A target is reachable on a gear piece iff it is the identifier of a tier-group
 * with a non-empty pool for the gear's item level, in one of the affix groups the
 * selected operation actually re-rolls. Same data source the game's own roll uses
 * (VaultGearTierConfig.getRandomModifier / VaultGearModifierHelper.generateModifiers).
 */
public final class ModifierCatalog {

	public enum OperationScope {
		PREFIX, SUFFIX, IMPLICIT, PREFIX_SUFFIX
	}

	public record Candidate(ResourceLocation id, String displayName, AffixType affixType) {
	}

	private ModifierCatalog() {
	}

	public static OperationScope scopeOfOperation(ResourceLocation operationId) {
		if (operationId == null) {
			return null;
		}
		String id = operationId.toString();
		if (VmaReference.OPERATION_REFORGE_ALL.equals(id)) {
			return OperationScope.PREFIX_SUFFIX;
		}
		if (VmaReference.OPERATION_REFORGE_PREFIX.equals(id)) {
			return OperationScope.PREFIX;
		}
		if (VmaReference.OPERATION_REFORGE_SUFFIX.equals(id)) {
			return OperationScope.SUFFIX;
		}
		if (VmaReference.OPERATION_REFORGE_IMPLICITS.equals(id)) {
			return OperationScope.IMPLICIT;
		}
		return null;
	}

	public static boolean isRerollOperation(ResourceLocation operationId) {
		return scopeOfOperation(operationId) != null;
	}

	public static List<Candidate> candidates(ItemStack gear, OperationScope scope) {
		if (gear == null || gear.isEmpty() || scope == null) {
			return List.of();
		}
		Optional<VaultGearTierConfig> configOpt = VaultGearTierConfig.getConfig(gear);
		if (configOpt.isEmpty()) {
			return List.of();
		}
		VaultGearTierConfig config = configOpt.get();
		int itemLevel = VaultGearData.read(gear).getItemLevel();
		List<Candidate> result = new ArrayList<>();
		Set<ResourceLocation> seen = new HashSet<>();
		for (AffixType affixType : scopeAffixes(scope)) {
			ModifierAffixTagGroup tagGroup = ModifierAffixTagGroup.ofAffixType(affixType);
			AttributeGroup attributeGroup = config.getModifierGroup(tagGroup);
			if (attributeGroup == null) {
				continue;
			}
			for (ModifierTierGroup tierGroup : attributeGroup) {
				ResourceLocation id = tierGroup.getIdentifier();
				if (id == null || !seen.add(id) || tierGroup.getModifiersForLevel(itemLevel).isEmpty()) {
					continue;
				}
				String displayName = displayName(tierGroup);
				result.add(new Candidate(id, displayName, affixType));
			}
		}
		return result;
	}

	public static boolean isApplicable(ItemStack gear, ResourceLocation targetId, OperationScope scope) {
		if (targetId == null) {
			return false;
		}
		for (Candidate candidate : candidates(gear, scope)) {
			if (candidate.id().equals(targetId)) {
				return true;
			}
		}
		return false;
	}

	public static int craftingPotential(ItemStack gear) {
		return VaultGearData.read(gear).getFirstValue(iskallia.vault.init.ModGearAttributes.CRAFTING_POTENTIAL)
				.orElse(0);
	}

	public static int maxCraftingPotential(ItemStack gear) {
		return VaultGearData.read(gear).getFirstValue(iskallia.vault.init.ModGearAttributes.MAX_CRAFTING_POTENTIAL)
				.orElse(0);
	}

	private static AffixType[] scopeAffixes(OperationScope scope) {
		return switch (scope) {
			case PREFIX -> new AffixType[] { AffixType.PREFIX };
			case SUFFIX -> new AffixType[] { AffixType.SUFFIX };
			case IMPLICIT -> new AffixType[] { AffixType.IMPLICIT };
			case PREFIX_SUFFIX -> new AffixType[] { AffixType.PREFIX, AffixType.SUFFIX };
		};
	}

	private static String displayName(ModifierTierGroup tierGroup) {
		ResourceLocation attributeId = tierGroup.getAttribute();
		if (attributeId == null) {
			return tierGroup.getIdentifier().getPath();
		}
		VaultGearAttribute<?> attribute = VaultGearAttributeRegistry.getAttribute(attributeId);
		if (attribute == null || attribute.getReader() == null) {
			return tierGroup.getIdentifier().getPath();
		}
		String name = attribute.getReader().getModifierName();
		return name == null || name.isBlank() ? tierGroup.getIdentifier().getPath() : name;
	}
}