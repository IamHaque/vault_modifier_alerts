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
import iskallia.vault.gear.attribute.ability.AbilityLevelAttribute;
import iskallia.vault.gear.attribute.config.ConfigurableAttributeGenerator;
import iskallia.vault.gear.attribute.config.DoubleAttributeGenerator;
import iskallia.vault.gear.attribute.config.FloatAttributeGenerator;
import iskallia.vault.gear.attribute.config.IntegerAttributeGenerator;
import iskallia.vault.gear.attribute.custom.effect.IEffectAvoidanceChanceAttribute;
import iskallia.vault.gear.attribute.talent.TalentLevelAttribute;
import iskallia.vault.gear.data.AttributeGearData;
import iskallia.vault.gear.data.VaultGearData;
import iskallia.vault.gear.reader.DecimalModifierReader;
import iskallia.vault.init.ModConfigs;
import iskallia.vault.skill.ability.AbilityType;
import iskallia.vault.skill.base.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

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

	public record Candidate(ResourceLocation id, String displayName, AffixType affixType, boolean percent,
			boolean abilityLike) {
	}

	/** One watched re-roll target with its own optional minimum threshold (display units). */
	public record RollTarget(ResourceLocation id, boolean thresholdEnabled, double thresholdValue) {
	}

	/**
	 * Reachable roll range of one target modifier in display units
	 * (percent-ranges converted, e.g. 0.02-0.06 becomes 2.0-6.0).
	 * Not numeric when the modifier carries no numeric value.
	 */
	public record RollRange(double min, double max, double step, boolean numeric, boolean percent) {

		/** Human text of the rollable band, e.g. "2.0 - 6.0" or "2.0 - 6.0%" (empty when not numeric). */
		public String displayText() {
			if (!numeric) {
				return "";
			}
			String suffix = percent ? "%" : "";
			return RerollPanel.formatDisplay(min, false) + " - " + RerollPanel.formatDisplay(max, false) + suffix;
		}
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
				result.add(new Candidate(id, displayName, affixType, percent, isAbilityLike(tierGroup)));
			}
		}
		// Two alphabetical groups: regular modifiers first, ability/talent modifiers last.
		result.sort(Comparator.comparingInt((Candidate c) -> c.abilityLike() ? 1 : 0)
				.thenComparing(Candidate::displayName, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(c -> c.id().toString()));
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
		return readVaultGearData(gear).getFirstValue(iskallia.vault.init.ModGearAttributes.CRAFTING_POTENTIAL)
				.orElse(0);
	}

	public static int maxCraftingPotential(ItemStack gear) {
		return readVaultGearData(gear).getFirstValue(iskallia.vault.init.ModGearAttributes.MAX_CRAFTING_POTENTIAL)
				.orElse(0);
	}

	/**
	 * Reads vault-gear data of the given stack, or empty gear data when the
	 * stack is not a {@link iskallia.vault.gear.item.VaultGearItem}. Only that
	 * item type stores {@link VaultGearData}; calling {@code VaultGearData.read}
	 * on tools, jewels, necklaces or card decks throws a ClassCastException.
	 */
	public static AttributeGearData readVaultGearData(ItemStack gear) {
		if (gear == null || gear.isEmpty() || !(gear.getItem() instanceof iskallia.vault.gear.item.VaultGearItem)) {
			return AttributeGearData.empty();
		}
		return VaultGearData.read(gear);
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
	 * attributes are stored as fractions (0.02-0.06) and displayed as 2-6,
	 * ability/talent level re-rolls compare by their "+N levels" value.
	 */
	public static double toDisplayUnits(VaultGearModifier<?> modifier) {
		if (modifier == null) {
			return 0.0;
		}
		Object value = modifier.getValue();
		if (value instanceof IEffectAvoidanceChanceAttribute avoidance) {
			return avoidance.getChance() * 100.0;
		}
		if (value instanceof AbilityLevelAttribute ability) {
			return ability.getLevelChange();
		}
		if (value instanceof TalentLevelAttribute talent) {
			return talent.getLevelChange();
		}
		if (!(value instanceof Number number)) {
			return 0.0;
		}
		VaultGearAttribute<?> attribute = modifier.getAttribute();
		boolean percent = attribute != null && isPercentType(attribute);
		// Fraction-based percentage attributes (Float/Double generators) store
		// 0.02-0.06 for 2-6%; integer-percent attributes already store display
		// units. Mirrors the scale rule in rollRange so range, display and the
		// threshold comparison all use the same units.
		boolean fractionBased = attribute != null
				&& (attribute.getGenerator() instanceof FloatAttributeGenerator
						|| attribute.getGenerator() instanceof DoubleAttributeGenerator);
		return percent && fractionBased ? number.doubleValue() * 100.0 : number.doubleValue();
	}

	/**
	 * Reachable roll range of one target in display units. Values come from the
	 * typed tier configs through the attribute's own generator API (the same
	 * data source the game's roll uses), e.g. Integer/Float/Double ranges and
	 * ability/talent level-change tiers. JsonObject tier blocks are supported
	 * as a fallback for custom attributes without a generator.
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
				boolean percent = isPercent(tierGroup);
				List<ModifierTier<?>> tiers = tierGroup.getModifiersForLevel(itemLevel);
				if (tiers.isEmpty()) {
					tiers = tierGroup;
				}
				RangeValue range = rangeValue(tierGroup, tiers, percent);
				if (range == null) {
					return new RollRange(0, 0, 0, false, percent);
				}
				return new RollRange(range.min(), range.max(), range.step(), true, range.percent());
			}
		}
		return new RollRange(0, 0, 0, false, false);
	}

	private record RangeValue(double min, double max, double step, boolean percent) {
	}

	/**
	 * Numeric min/max/step over the given (level-applicable) tier configs, in
	 * display units, or null when the modifier carries no numeric value.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static RangeValue rangeValue(ModifierTierGroup tierGroup, List<ModifierTier<?>> tiers, boolean percent) {
		if (tiers.isEmpty()) {
			return null;
		}
		// Ability/talent level tiers hold point values: "+N levels" per tier
		boolean abilityLevels = true;
		boolean talentLevels = true;
		for (ModifierTier<?> tier : tiers) {
			Object config = tier.getModifierConfiguration();
			abilityLevels &= config instanceof AbilityLevelAttribute.Config;
			talentLevels &= config instanceof TalentLevelAttribute.Config;
		}
		if (abilityLevels || talentLevels) {
			int min = Integer.MAX_VALUE;
			int max = Integer.MIN_VALUE;
			for (ModifierTier<?> tier : tiers) {
				Object config = tier.getModifierConfiguration();
				int level = abilityLevels
						? ((AbilityLevelAttribute.Config) config).getLevelChange()
						: ((TalentLevelAttribute.Config) config).getLevelChange();
				min = Math.min(min, level);
				max = Math.max(max, level);
			}
			return min > max ? null : new RangeValue(min, max, 1, percent);
		}

		VaultGearAttribute<?> attribute = attributeOf(tierGroup);
		if (attribute == null) {
			return null;
		}
		ConfigurableAttributeGenerator generator = attribute.getGenerator();
		if (generator == null) {
			return jsonRangeValue(tiers, percent);
		}
		List<Object> configs = new ArrayList<>(tiers.size());
		for (ModifierTier<?> tier : tiers) {
			configs.add(tier.getModifierConfiguration());
		}
		Optional<?> minOpt = generator.getMinimumValue(configs);
		Optional<?> maxOpt = generator.getMaximumValue(configs);
		if (minOpt.isEmpty() || maxOpt.isEmpty()) {
			return null;
		}
		Object minValue = minOpt.get();
		Object maxValue = maxOpt.get();
		// Effect-avoidance attributes (single effect and list type) yield their value
		// type as min/max; the shared chance is the rollable band (fraction = percent).
		if (minValue instanceof IEffectAvoidanceChanceAttribute minAvoidance
				&& maxValue instanceof IEffectAvoidanceChanceAttribute maxAvoidance) {
			return new RangeValue(minAvoidance.getChance() * 100.0, maxAvoidance.getChance() * 100.0, 1.0, true);
		}
		if (!(minValue instanceof Number minNumber) || !(maxValue instanceof Number maxNumber)) {
			return null;
		}
		// Float/Double percentage attributes store fractions (0.1 = 10%);
		// integer ones store display units already.
		double scale = percent && (generator instanceof FloatAttributeGenerator
				|| generator instanceof DoubleAttributeGenerator) ? 100.0 : 1.0;
		double min = minNumber.doubleValue() * scale;
		double max = maxNumber.doubleValue() * scale;
		double step = scale * stepOf(configs, generator);
		if (!(step > 0.0)) {
			step = Math.max(scale, 1.0);
		}
		return new RangeValue(min, max, step, percent);
	}

	/** Smallest usable increment: integer ranges expose it, otherwise derive from the roll values. */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static double stepOf(List<Object> configs, ConfigurableAttributeGenerator generator) {
		double best = 0.0;
		for (Object config : configs) {
			if (config instanceof IntegerAttributeGenerator.Range range && range.step > 0) {
				best = best == 0.0 ? range.step : Math.min(best, range.step);
			}
		}
		if (best > 0.0) {
			return best;
		}
		TreeSet<Double> values = new TreeSet<>();
		for (Object config : configs) {
			Optional<?> minOpt = generator.getMinimumValue(List.of(config));
			Optional<?> maxOpt = generator.getMaximumValue(List.of(config));
			if (minOpt.isPresent() && minOpt.get() instanceof Number number) {
				values.add(number.doubleValue());
			}
			if (maxOpt.isPresent() && maxOpt.get() instanceof Number number) {
				values.add(number.doubleValue());
			}
		}
		double smallest = 0.0;
		double previous = Double.NaN;
		for (double value : values) {
			if (!Double.isNaN(previous) && value > previous) {
				double delta = value - previous;
				smallest = smallest == 0.0 ? delta : Math.min(smallest, delta);
			}
			previous = value;
		}
		return smallest;
	}

	/** Raw JsonObject tier blocks, for custom attributes without a generator. */
	private static RangeValue jsonRangeValue(List<ModifierTier<?>> tiers, boolean percent) {
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		double step = 0.0;
		boolean found = false;
		for (ModifierTier<?> tier : tiers) {
			if (!(tier.getModifierConfiguration() instanceof JsonObject block)) {
				continue;
			}
			JsonObject value = block.has("value") && block.get("value").isJsonObject()
					? block.getAsJsonObject("value") : block;
			if (!value.has("min") || !value.has("max")) {
				continue;
			}
			double minValue = GsonHelper.getAsDouble(value, "min", 0.0);
			double maxValue = GsonHelper.getAsDouble(value, "max", 0.0);
			double stepValue = GsonHelper.getAsDouble(value, "step", 1.0);
			if (percent) {
				minValue *= 100.0;
				maxValue *= 100.0;
				stepValue *= 100.0;
			}
			min = Math.min(min, minValue);
			max = Math.max(max, maxValue);
			if (stepValue > 0.0) {
				step = step == 0.0 ? stepValue : Math.min(step, stepValue);
			}
			found = true;
		}
		if (!found) {
			return null;
		}
		if (step <= 0.0) {
			step = 1.0;
		}
		return new RangeValue(min, max, step, percent);
	}

	private static boolean isPercent(ModifierTierGroup tierGroup) {
		VaultGearAttribute<?> attribute = attributeOf(tierGroup);
		return attribute != null && isPercentType(attribute);
	}

	private static VaultGearAttribute<?> attributeOf(ModifierTierGroup tierGroup) {
		ResourceLocation attributeId = tierGroup.getAttribute();
		if (attributeId == null) {
			return null;
		}
		return VaultGearAttributeRegistry.getAttribute(attributeId);
	}

	private static boolean isPercentType(VaultGearAttribute<?> attribute) {
		return attribute.getReader() instanceof DecimalModifierReader.Percentage;
	}

	/** Ability/talent level tiers sort last in the picker (each group alphabetical). */
	private static boolean isAbilityLike(ModifierTierGroup tierGroup) {
		for (ModifierTier<?> tier : tierGroup) {
			Object config = tier.getModifierConfiguration();
			if (config instanceof AbilityLevelAttribute.Config || config instanceof TalentLevelAttribute.Config) {
				return true;
			}
		}
		return false;
	}

	private static String displayName(ModifierTierGroup tierGroup) {
		VaultGearAttribute<?> attribute = attributeOf(tierGroup);
		String fallback = humanizeId(stripModPrefix(tierGroup.getIdentifier().getPath()));
		for (ModifierTier<?> tier : tierGroup) {
			Object config = tier.getModifierConfiguration();
			if (config instanceof AbilityLevelAttribute.Config abilityConfig) {
				return abilityDisplayName(abilityConfig.getAbilityKey());
			}
			if (config instanceof TalentLevelAttribute.Config talentConfig) {
				return talentDisplayName(talentConfig.getTalent());
			}
			break;
		}
		if (attribute == null || attribute.getReader() == null) {
			return fallback;
		}
		String name = attribute.getReader().getModifierName();
		return name == null || name.isBlank() ? fallback : name;
	}

	/** Just the ability name ("Ice Bolt"), never the "Mod Added Ability Level" prefix wording. */
	private static String abilityDisplayName(String abilityKey) {
		if (abilityKey == null || abilityKey.isBlank()) {
			return "Ability";
		}
		if (AbilityLevelAttribute.ALL_ABILITIES.equalsIgnoreCase(abilityKey)) {
			return "All Abilities";
		}
		if (AbilityType.matches(abilityKey)) {
			return "All " + humanizeId(abilityKey) + " Abilities";
		}
		return ModConfigs.ABILITIES.getAbilityById(abilityKey)
				.map(Skill::getName)
				.filter(name -> name != null && !name.isBlank())
				.orElseGet(() -> humanizeId(trimValueSuffix(abilityKey)));
	}

	/** Just the talent name ("Unbreakable"), never the "Mod Added Talent Level" prefix wording. */
	private static String talentDisplayName(String talentKey) {
		if (talentKey == null || talentKey.isBlank()) {
			return "Talent";
		}
		if (TalentLevelAttribute.ALL_TALENTS.equalsIgnoreCase(talentKey)) {
			return "All Talents";
		}
		return ModConfigs.TALENTS.getTalentById(talentKey)
				.map(Skill::getName)
				.filter(name -> name != null && !name.isBlank())
				.orElseGet(() -> humanizeId(trimValueSuffix(talentKey)));
	}

	/** Ability keys like "Ice_Bolt_Base" end in a base-class tag that reads badly. */
	private static String trimValueSuffix(String key) {
		if (key == null || key.isEmpty()) {
			return key == null ? "?" : key;
		}
		String lower = key.toLowerCase(Locale.ROOT);
		return lower.endsWith("_base") ? key.substring(0, key.length() - 5) : key;
	}

	/** Strips the "mod_" id prefix that reads badly in names ("mod_effect_avoidance" -> "effect_avoidance"). */
	public static String stripModPrefix(String path) {
		return path != null && path.startsWith("mod_") ? path.substring(4) : path;
	}

	/**
	 * Turns a raw modifier id path into readable text ("melee_attack_damage"
	 * becomes "Melee Attack Damage"). Never returns the raw path unchanged.
	 */
	public static String humanizeId(String path) {
		if (path == null || path.isEmpty()) {
			return "?";
		}
		StringBuilder result = new StringBuilder();
		boolean capitalize = true;
		for (int i = 0; i < path.length(); i++) {
			char c = path.charAt(i);
			if (c == '_' || c == '-' || c == ' ') {
				capitalize = true;
				result.append(' ');
				continue;
			}
			result.append(capitalize ? Character.toUpperCase(c) : c);
			capitalize = false;
		}
		return result.toString().trim();
	}
}