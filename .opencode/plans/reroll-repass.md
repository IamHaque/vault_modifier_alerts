# Reroll logic re-pass

User decisions: (1) reset only on remove-and-readd (no item-kind swap detection); (2) moderate speed ~2 rolls/s (rollGap 4, tickInterval 15 -> 10).

## 0. Verified jar facts (remastered 20.0.3, from javap)

- Registry names: `the_vault:reforge_all` (ReforgeAllModification), `the_vault:reforge_implicits` (ReforgeImplicitModification), `the_vault:reforge_affix_prefix`, `the_vault:reforge_affix_suffix` (ReforgeAffixGroupModification). All 4 map in `ModifierCatalog.scopeOfOperation` (VmaReference ids match).
- Display stacks (what the panel shows as "Focus"): reforge_all -> "Wild Focus", reforge_implicits -> "Fundamental Focus", reforge_affix_prefix -> "Waxing Focus", reforge_affix_suffix -> "Waning Focus". So "Fundamental Focus" = **reforge_implicits**, "Wild Focus" = **reforge_all**.
- `VaultGearModifier$AffixType` enum order: IMPLICIT=0, PREFIX=1, SUFFIX=2. `VaultGearData.getModifiers(AffixType)` ($SwitchMap verified): IMPLICIT -> baseModifiers, PREFIX -> prefixes, SUFFIX -> suffixes. Engine's scope-based reads are correct.
- `reForgeAllImplicits` (Fundamental Focus) = removeAllModifiersOfType(IMPLICIT) + generateImplicits + generateBaseAttributes — all land in baseModifiers. Armor/Block Chance are implicit-group modifiers in the user's pack (both were listed by `candidates()` under the IMPLICIT scope = `ModifierAffixTagGroup.ofAffixType(IMPLICIT)` = IMPLICIT group only).
- `ModifierTier.makeModifier` sets the rolled `VaultGearModifier.modifierIdentifier` = `tierGroup.identifier` — identical to the tier-group id the panel/engine match against. Identifier matching is sound.
- `ModifierAffixTagGroup.ofAffixType`: IMPLICIT->IMPLICIT, PREFIX->PREFIX, SUFFIX->SUFFIX (1:1; throws otherwise). `candidates()` lists only the selected operation's group.

## 1. ALL-condition bug: engine rolled past a qualifying gear (AutoRerollEngine)

Report: ALL + [Armor>=25, Block Chance>=10%] under "Fundamental Focus" kept re-rolling although a rolled gear visibly met both; panel reset from "Fundamental Focus" to "Wild Focus" (the reset bug, section 2). All mechanical paths verified sound (ids, groups, units, gear-change detection) — the residual failure mode is runtime-data-dependent and cannot be reproduced here (no game, tier configs live in the user's modpack, not the jar).

Fix set (makes the stop decision equal "the gear's visible state" and diagnosable):

a) **Whole-gear evaluation** in `targetRolled()`: iterate ALL THREE affix lists (IMPLICIT/base, PREFIX, SUFFIX) instead of `scopeAffixes(scopeOfOperation(operationId))`. The stop condition judges the gear's state, not what the last press rolled — targets in other groups (e.g. block chance as a prefix while the operation is reforge_implicits) now count. `indexOfTarget` matching unchanged.
b) **Pre-press qualification check**: new field `private ItemStack lastEvaluatedGear;` (null in start()). In `evaluate()` after the in-flight handling, before the interval/rollGap gates:
   ```java
   if (lastEvaluatedGear == null || !ItemStack.matches(lastEvaluatedGear, gear)) {
       lastEvaluatedGear = gear.copy();
       if (targetRolled(gear)) { stop(StopReason.SUCCESS, true); return; }
   }
   ```
   Covers: gear already qualifies at start (0 rolls), and gear changed by manual crafts while the engine is between presses. Remove the `targetRolled` call from the in-flight branch (the check above runs on the same tick after `inFlight=false`; keep the timeout branch).
c) **Exception safety**: wrap the qualification check + in-flight evaluation in try/catch; on Throwable log ERROR and `stop(StopReason.EVALUATION_ERROR, true)`. New StopReason `EVALUATION_ERROR` + text "evaluation error" in RerollPanel's stopReasonText. (Prevents an evaluation exception from freezing the engine in an infinite re-roll loop.)
d) **Per-roll debug log** (see section 3) now also records the affix GROUP each target was found in — if the bug persists, the user's `/vma debug on` log will show exactly why (target absent / value below threshold / not found).

## 2. Reset preferences only on gear remove-and-readd (RerollPanel.java:358-362)

Current: `if (!ItemStack.matches(lastSeenGear, gear)) { lastSeenGear = gear.copy(); resetSelection(); }`
Every roll replaces the stack (new NBT) -> matches() fails -> targets/thresholds cleared after every roll (and operationIndex -> 0, i.e. "Fundamental Focus" -> "Wild Focus"). Bug.

New:
```java
ItemStack gear = stationGear();
if (lastSeenGear.isEmpty() && !gear.isEmpty()) {
    resetSelection();
}
lastSeenGear = gear.copy();
```
- Only the empty -> gear transition (removed & re-added) clears selections. Rolls never empty the slot, so selections survive.
- `lastSeenGear` updated unconditionally so the empty state is tracked while the gear is out.
- First ever open: lastSeenGear = EMPTY, gear present -> reset, harmless (nothing selected yet).
- Direct same-kind swap without emptying keeps selections (per user decision).

## 3. Debug log all rolls (AutoRerollEngine)

Gate everything by `VmaClientConfigs.isDebugLogging()` and log at INFO level (plain debug never appears in production latest.log). Helper: `private void logRoll(String fmt, Object... args)` -> `if (isDebugLogging()) LOGGER.info("[VMA] " + fmt, args)`.

- start(): log operation, targets (id + threshold), condition. (was unconditional debug)
- stop(): log reason, rolls, final values of each watched target `id=value`.
- press(): log `roll #N press: <operation id> (potential <n>)` before triggering.
- handleOutOfPotential(): log `roll #N potential reset #<k>`.
- targetRolled(): per matched modifier `roll #N target=<id> group=<BASE|PREFIX|SUFFIX> raw=<v> value=<display> threshold=<t|null> passed=<b>`; after loop, result line `roll #N result: <met/not>` + metCount.
- Timeout path in evaluate(): log `roll #N timeout after <ticks>`.

## 4. Speed (~2 rolls/s)

AutoRerollEngine:
- New field `private int lastRollCompletedTick;` reset to 0 in start().
- In evaluate() in-flight branch, on gearChanged: set `lastRollCompletedTick = mc.player.tickCount;`.
- Two gates before pressing (replacing the single interval gate), after the qualification check:
  ```java
  if (mc.player.tickCount - lastPressTick < VmaClientConfigs.autoRerollTickInterval()) return;
  if (lastRollCompletedTick > 0
          && mc.player.tickCount - lastRollCompletedTick < VmaClientConfigs.autoRerollRollGapTicks()) return;
  ```
  tickInterval = anti-double-click floor (10); rollGapTicks = real pacing from roll completion (4). Press-to-press = server round trip + 4 ticks.
- Applicability cache: fields `private ItemStack lastApplicabilityGear; private boolean applicabilityOk;` reset in start(). In evaluate():
  ```java
  if (lastApplicabilityGear == null || !ItemStack.matches(lastApplicabilityGear, gear)) {
      lastApplicabilityGear = gear.copy();
      OperationScope scope = ModifierCatalog.scopeOfOperation(operationId);
      applicabilityOk = scope != null && targets.stream()
              .anyMatch(t -> ModifierCatalog.isApplicable(gear, t.id(), scope));
  }
  if (!applicabilityOk) { stop(StopReason.INVALID_TARGET, true); return; }
  ```
  (was: candidates() walk every client tick while running)

VmaClientConfigs:
- New `AUTO_REROLL_ROLL_GAP_TICKS = builder.defineInRange("rollGapTicks", 4, 2, 40);` + getter `autoRerollRollGapTicks()`.
- `tickInterval` default 15 -> 10 (min stays 4).
- Timeout stays 60.

## 5. Dead code

- AutoRerollEngine: remove `targetId()`, `lastTargetValue()`, unused `import java.util.Optional;`.
- ModifierCatalog: remove `isRerollOperation()` (no callers).
- RerollPanel: remove `dropdownMode()` getter (line 111), `operationIndex()` getter (line 115) (no external callers); make `thresholdValue()` private (internal only).

## 6. Verify

- `.\gradlew.bat build --console=plain` (expect BUILD SUCCESSFUL).
- Commit: "fix(reroll): stop on whole-gear state, keep selections on rolls, debug-log every roll, faster roll pacing, dead code cleanup".
- User tests in production modpack with `/vma debug on`: ALL with cross-group targets must stop as soon as the gear qualifies; selections must survive rolls; watch roll cadence and the per-roll eval lines in latest.log. If the reported bug persists, the log lines show which target is not met (absent/value/group).