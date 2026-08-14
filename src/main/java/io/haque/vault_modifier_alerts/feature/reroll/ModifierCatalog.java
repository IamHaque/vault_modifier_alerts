package io.haque.vault_modifier_alerts.feature.reroll;

import io.haque.vault_modifier_alerts.VmaReference;
import com.google.gson.JsonObject;
import iskallia.vault.config.gear.VaultGearTierConfig;
import iskallia.vault.config.gear.VaultGearTierConfig.AttributeGroup;
import iskallia.vault.config.gear.VaultGearTierConfig.ModifierAffixTagGroup;
import iskallia.vault.config.gear.VaultGearTierConfig.ModifierTier;
import iskallia.vault.config.gear.VaultGearTierConfig.ModifierTierGroup;
import iskallia.vault.gear.attribute.VaultGearAttribute;
import iskallia.vault.gear.attribute.VaultGearAttributeRegistry;
import iskallia.vault.gear.attribute.VaultGearModifier;
import iskallia.vault.gear.attribute.VaultGearModifier.AffixType;
import iskallia.vault.gear.data.VaultGearData;
import iskallia.vault.gear.reader.DecimalModifierReader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
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

	public record Candidate(ResourceLocation id, String displayName, AffixType affixType, boolean percent) {
	}

	/**
	 * Reachable roll range of one target modifier in display units
	 * (percent-ranges converted, e.g. 0.02-0.06 becomes 2.0-6.0).
	 * Not numeric when the modifier carries no numeric value.
	 */
	public record RollRange(double min, double max, double step, boolean numeric, boolean percent) {
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
				boolean percent = isPercent(tierGroup);
				result.add(new Candidate(id, displayName, affixType, percent));
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

	/**
	 * The stored gear-modifier value converted to display units: percentage
	 * attributes are stored as fractions (0.02-0.06) and displayed as 2-6.
	 */
	public static double toDisplayUnits(VaultGearModifier<?> modifier) {
		if (modifier == null || !(modifier.getValue() instanceof Number number)) {
			return 0.0;
		}
		VaultGearAttribute<?> attribute = modifier.getAttribute();
		boolean percent = attribute != null && isPercentType(attribute);
		return percent ? number.doubleValue() * 100.0 : number.doubleValue();
	}

	/**
	 * Reachable roll range of one target in display units, taken from the tier
	 * configuration the game's own roll uses ("value": {min, max, step}).
	 */
	public static RollRange rollRange(ItemStack gear, ResourceLocation targetId, OperationScope scope) {
		if (gear == null || gear.isEmpty() || targetId == null || scope == null) {
			return new RollRange(0, 0, 0, false, false);
		}
		Optional<VaultGearTierConfig> configOpt = VaultGearTierConfig.getConfig(gear);
		if (configOpt.isEmpty()) {
			return new RollRange(0, 0, 0, false, false);
		}
		VaultGearTierConfig config = configOpt.get();
		int itemLevel = VaultGearData.read(gear).getItemLevel();
		for (AffixType affixType : scopeAffixes(scope)) {
			ModifierAffixTagGroup tagGroup = ModifierAffixTagGroup.ofAffixType(affixType);
			AttributeGroup attributeGroup = config.getModifierGroup(tagGroup);
			if (attributeGroup == null) {
				continue;
			}
			for (ModifierTierGroup tierGroup : attributeGroup) {
				if (!targetId.equals(tierGroup.getIdentifier())) {
					continue;
				}
				for (ModifierTier<?> tier : tierGroup) {
					if (!tierAppliesAt(tier, itemLevel)) {
						continue;
					}
					if (tier.getModifierConfiguration() instanceof JsonObject block
							&& block.get("value") instanceof JsonObject value) {
						double step = GsonHelper.getAsDouble(value, "step", 1.0);
						double min = GsonHelper.getAsDouble(value, "min", 0.0);
						double max = GsonHelper.getAsDouble(value, "max", 0.0);
						boolean percent = isPercent(tierGroup);
						if (percent) {
							min *= 100.0;
							max *= 100.0;
							step *= 100.0;
						}
						return new RollRange(min, max, step, true, percent);
					}
					return new RollRange(0, 0, 0, false, isPercent(tierGroup));
				}
			}
		}
		return new RollRange(0, 0, 0, false, false);
	}

	private static boolean tierAppliesAt(ModifierTier<?> tier, int itemLevel) {
		if (itemLevel < tier.getMinLevel()) {
			return false;
		}
		return tier.getMaxLevel() == -1 || itemLevel <= tier.getMaxLevel();
	}

	private static boolean isPercent(ModifierTierGroup tierGroup) {
		ResourceLocation attributeId = tierGroup.getAttribute();
		if (attributeId == null) {
			return false;
		}
		VaultGearAttribute<?> attribute = VaultGearAttributeRegistry.getAttribute(attributeId);
		return attribute != null && isPercentType(attribute);
	}

	private static boolean isPercentType(VaultGearAttribute<?> attribute) {
		return attribute.getReader() instanceof DecimalModifierReader.Percentage;
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