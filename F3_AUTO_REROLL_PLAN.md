# F3 — Artisan Station Auto-Reroll (Requirement & Implementation Plan)

**Status:** Approved plan — implementation in progress on `feature/reroll`
**Target:** Minecraft 1.18.2, Forge 40.3.11, Vault Hunters 3rd Edition - Remastered
(`the_vault-1.18.2-20.0.3-remastered.6872.jar`, the runtime jar the owner tests with)
**Feature type:** Client-side only QoL. Adds a new feature F3 to the existing
`vault_modifier_alerts` mod (F1 expiry alerts, F2 HUD ordering, and the downed-knockout
alert are already shipped).

---

## 1. Requirement (owner-stated)

At the Artisan Station the player can press a re-roll action button (e.g. Reforge All,
Reforge Prefix, Reforge Suffix) repeatedly until the gear piece rolls the modifier the
player wants. Doing this by hand is tedious; the mod should automate it:

1. **Auto-press:** the mod keeps pressing the selected station action until the desired
   modifier appears on the gear piece, then stops and alerts the player.
2. **Applicability guard:** the player may only select a target modifier that can
   *actually* roll on the gear piece currently in the station. Rolling an impossible
   target (e.g. attack damage on a helmet) would otherwise burn bronze/focus/potential
   forever — the mod must never chase an impossible roll.
3. **Crafting potential:** each action consumes crafting potential from the gear (a game
   mechanic). When the gear runs out of potential the game disables the action. By
   default the mod then uses the game's existing **Opportunistic Focus** action
   (`reset_potential`) to refill potential and continues; this behaviour must have a
   toggle so it does not always press the reset button.
4. **GUI placement:** the auto-reroll GUI must not overlap or obstruct the Artisan
   Station GUI.
5. **Do not reimplement game logic.** The game already handles potential costs, the
   actual re-roll, modifier application, and the reset. The mod only *reads* game state
   and *presses existing buttons* (exactly like a player would).

---

## 2. Scope (what the mod does / does not do)

| Does | Does not |
| ---- | -------- |
| Press existing station action buttons (re-roll actions, and optionally the reset action) at a configurable interval. | Implement any game mechanic (re-roll, potential cost, reset, modifier generation). |
| Detect "target rolled" by reading the gear's current modifiers after each roll. | Change gear, items, or server state. |
| Enforce the applicability guard using the gear's own tier-config modifier pools. | Simulate probability / predict rolls. |
| Distinguish *why* a button is disabled (out of potential vs. out of materials) using the game's own `reducePotential` check, to decide whether the reset action can help. | Apply custom maths for costs or potential (the game's own `reducePotential` is invoked read-only on a copy). |
| Alert on success / stop via sound. | Add new HUD elements or modify the station screen's layout. |

The feature has exactly two "new" behaviours not already present in the game:
(a) repeated automatic button presses until the target appears; (b) the success/stop
alert. Everything else reuses game systems.

---

## 3. Verified Vault Hunters internals (jar-verified 2026-08-14)

All signatures below verified with `javap -p/-c` against
`libs\the_vault-1.18.2-20.0.3-remastered.6872.jar` (runtime jar) and cross-checked
against the parchment-mapped dev jar
(`vault-hunters-official-mod-458203-7967092_mapped_parchment_2022.11.06-1.18.2.jar`).

### 3.1 Station screen & container

| Symbol | Signature (runtime) | Notes |
| ------ | ------------------- | ----- |
| `iskallia.vault.client.gui.screen.block.VaultArtisanStationScreen` | `extends AbstractElementContainerScreen<VaultArtisanStationContainer>` | Its GUI framework extends vanilla `AbstractContainerScreen`, so the vanilla public getters `getGuiLeft()/getGuiTop()/getXSize()/getYSize()` and `Screen.width/height` give the full window rect (the framework writes `imageWidth/imageHeight` via `setGuiSize`). |
| `attemptCraft` | `private void attemptCraft(GearModificationAction)` | The exact behaviour of pressing an action button (canApply gate → bronze cache invalidate → `VaultArtisanRequestModificationMessage` to server). Triggered from `lambda$createActionButtons$14`. |
| `render` | SRG `m_6305_(PoseStack, int, int, float)` public, declared in `AbstractElementContainerScreen` | Draw the auto-reroll panel after the whole station GUI (TAIL inject). |
| `mouseClicked` | SRG `m_7933_(int, int, int)` public, declared on `VaultArtisanStationScreen` | Consume clicks that hit the auto-reroll panel (HEAD inject, cancellable). |
| `VaultArtisanStationContainer` | `getModificationActions(): List<GearModificationAction>`, `getGearInputSlot(): Slot`, `getBronzeSlot()/getPlatingSlot(): Slot` | The station menu, reachable via `screen.getMenu()`. |
| `GearModificationAction` | record `(slotIndex, tab, modification, side)`; `canApply(container, player): boolean` | One entry per action button in the station. |
| `GearModification` | `getDisplayStack(): ItemStack`, `getRegistryName(): ResourceLocation`, abstract `doModification(...)` | Registry id = operation id (e.g. `the_vault:reforge_all`). |

### 3.2 Crafting potential gate (read-only reuse)

- `VaultGearCraftingHelper.reducePotential(ItemStack gear, Player, GearModification)` —
  public static, **client-safe** (uses `ClientExpertiseData.getLearnedTalentNodes()` on
  the client). Returns `false` when `currentPotential − cost < 0` — exactly when the
  game disables the action button. **Called only on `gear.copy()`** (never mutates the
  real stack) to classify the disabled reason.
- `VaultGearModificationConfig.getPotentialUsed(mod)` — per-operation potential cost
  (pack values, `config/the_vault/gear/gear_modification.json`: `reforge_all`=4,
  `reforge_affix_prefix/suffix`=8, `reforge_implicits`=16; `reset_potential`=0).
- `ModGearAttributes.CRAFTING_POTENTIAL` / `MAX_CRAFTING_POTENTIAL` —
  `VaultGearAttribute<Integer>`; read via `VaultGearData.getFirstValue(...)` for the
  panel's potential display.
- `ResetPotentialModification` — registry id `the_vault:reset_potential`, display stack
  `ModItems.OPPORTUNISTIC_FOCUS`; `doModification` refreshes potential to max.

### 3.3 Modifier pools & detection (applicability guard)

- `VaultGearTierConfig.getConfig(ItemStack)` → `Optional<VaultGearTierConfig>`; the
  config is keyed per gear piece type.
- `getModifierGroup(ModifierAffixTagGroup)` → `AttributeGroup` (= `List<ModifierTierGroup>`)
  for `PREFIX`, `SUFFIX`, `IMPLICIT`; each `ModifierTierGroup` has
  `getIdentifier(): ResourceLocation` (the modifier id) and `getModifiersForLevel(int)`
  (the pool for the gear's item level).
- Roll source is the same data: `VaultGearTierConfig.getRandomModifier(AffixType, level, ...)`
  and `VaultGearModifierHelper.generateModifiers(...)` draw from these groups.
  → **A target is reachable iff it is the identifier of a group with a non-empty pool
  for the gear's level in one of the operation's affix groups.**
- Rolled modifier detection: `VaultGearData.read(stack).getModifiers(AffixType.PREFIX/
  SUFFIX/IMPLICIT)` → `VaultGearModifier.getModifierIdentifier()` (set at roll time from
  the group identifier).
- Display names: `VaultGearAttributeRegistry.getAttribute(group.getAttribute())
  .getReader().getModifierName()`.

### 3.4 Re-roll operations and their affix scope (jar-verified)

| Operation id | Class | Affix scope |
| ------------ | ----- | ----------- |
| `the_vault:reforge_all` | `ReforgeAllModification` → `VaultGearModifierHelper.reForgeAllModifiers` = removeAllModifiers(PREFIX,SUFFIX) + generateModifiers | PREFIX + SUFFIX (implicits are NOT re-rolled) |
| `the_vault:reforge_affix_prefix` | `ReforgeAffixGroupModification(AffixType)` (id built as `reforge_affix_<type>`) | PREFIX |
| `the_vault:reforge_affix_suffix` | `ReforgeAffixGroupModification(AffixType)` | SUFFIX |
| `the_vault:reforge_implicits` | `ReforgeImplicitModification` | IMPLICIT |

Only these four are offered in the operation selector (they are the actions whose
outcome can be a random modifier on the gear — i.e. the ones worth auto-pressing toward
a target). Other actions (`reset_potential`, `add_modifier`, `remove_modifier`,
`lock_modifier`, `corrupt_gear`, `improve_*`, `reforge_tier`, `reforge_base_durability`,
`reforge_all_add_tag`) are excluded from the selector; `reset_potential` is used
automatically by the potential-recovery flow. This is logged as a decision (see
`DECISIONS.md` DEC-022).

---

## 4. Design

### 4.1 Components (new files)

```
src/main/java/io/haque/vault_modifier_alerts/
├── feature/reroll/
│   ├── ModifierCatalog.java        applicability guard: candidate targets for (gear, operation)
│   ├── AutoRerollEngine.java       singleton state machine (press → wait → evaluate → repeat)
│   └── RerollPanel.java            panel drawing + hit-testing + input (pure-ish UI logic)
├── mixin/artisan/
│   └── MixinVaultArtisanStationScreen.java   attemptCraft trigger + render + mouseClicked
└── event/
    └── KeyBindings.java            KeyMapping "P" (toggle panel visibility)

modified:
├── config/VmaClientConfigs.java    new [Auto Reroll] group + getters
├── event/ClientTickEvents.java     wire AutoRerollEngine.evaluate()
├── command/VmaClientCommands.java  /vma reroll start|stop
├── VmaReference.java               sound ids/constants
├── VaultModifierAlerts.java        register reroll_success / reroll_stop SoundEvents
├── resources/vault_modifier_alerts.mixins.json   + "artisan.MixinVaultArtisanStationScreen"
├── resources/assets/.../sounds.json + lang/en_us.json
└── docs: README.md, DECISIONS.md (this plan + decisions)
```

### 4.2 Mixin `MixinVaultArtisanStationScreen`

Targets `VaultArtisanStationScreen`, `remap = false` (VH runtime names):

1. **Trigger** — duck interface `ArtisanStationScreenAccessor` with
   `vma$triggerAction(GearModificationAction)` (project's proven duck pattern, like
   `VaultModifierTimeAccessor`); implementation `@Override @Unique` calls
   `this.attemptCraft(action)` via `@Shadow private attemptCraft`. A HEAD `@Inject` on
   `attemptCraft` is also used to notify the engine that a press actually went through.
2. **Render** — `@Inject(method = "m_6305_", at = @At("TAIL"))` draws `RerollPanel`
   (only while the engine/panel is active). Panel anchored **outside** the station
   window: preferred right side (`windowRight + 6`), fallback left side
   (`windowLeft − 6 − panelWidth`) when the right margin is too small, else clamped at
   the screen edge. Never intersects the station rect.
3. **Input** — `@Inject(method = "m_7933_", at = @At("HEAD"), cancellable = true)`: if
   the click hits the panel, the panel handles it and the event is cancelled (the
   station GUI stays fully interactive everywhere else).

### 4.3 Engine (`AutoRerollEngine`, singleton)

State: `IDLE → PRESSED (in-flight) → (gear changed | timeout) → evaluate → …`.

Evaluation order (every `tickInterval` ticks, client thread):

1. `enabled` config off → no-op. No `VaultArtisanStationScreen` open → stop (screen
   closed), clear session.
2. Gear slot empty → stop `NO_GEAR`.
3. `maxRolls` reached (0 = unlimited) → stop `MAX_ROLLS`.
4. **Applicability guard** (each evaluation): target must be in
   `ModifierCatalog.candidates(gear, operation)`. Not applicable → stop `INVALID_TARGET`
   (gear swapped/level changed — never burn resources on an impossible roll).
5. Selected action `canApply(container, player)`:
   - yes → press via `vma$triggerAction`, mark in-flight, snapshot the gear stack.
   - no → classify: `VaultGearCraftingHelper.reducePotential(gear.copy(), player, mod)`
     false ⇒ `OUT_OF_POTENTIAL`, else `OUT_OF_MATERIALS`.
     - `OUT_OF_POTENTIAL` + `autoResetPotential` on + `the_vault:reset_potential`
       action present and `canApply` ⇒ press reset **once per session**, then resume
       rolling.
     - otherwise → stop with alert (`OUT_OF_POTENTIAL` / `OUT_OF_MATERIALS`).
6. In-flight: when the gear slot stack changes (`ItemStack.m_150942_` differs from the
   snapshot) the roll completed: check target in current modifiers (per operation
   scope). Found → stop `SUCCESS` + success sound + toast. Not found → next press after
   the tick interval. No change within `rollTimeoutTicks` → stop `TIMEOUT`.

Stop reasons surfaced in the panel + debug log; success/stop play sounds
(`reroll_success` / `reroll_stop`, defaults to `minecraft:block.note_block.pling`).

Manual player clicks on station buttons reset the engine's cooldown (double-click
protection). Closing the screen stops the run.

### 4.4 Panel (`RerollPanel`)

> **[see DEC-028 + DEC-029 + DEC-030 + DEC-031 + DEC-032] — superseded by the GUI revamp.** Compact
> side panel (150×110) with `‹ ›` cycling selectors and raw row-math hit-testing was
> replaced by a 216px-wide panel with click-to-open dropdown selectors, an Auto-reroll
> on/off toggle, a multi-target watch list with per-target mins and an `any`/`all` stop
> condition, a min-threshold range hint, a colored potential line with a rolls-left
> estimate, a potential-reset counter, and a richer rolling/stop status line. All geometry
> comes from `RerollPanelLayout` (single source of truth for drawing and input); roll
> ranges come from the attributes' typed tier configs via the generator API (DEC-029),
> effect-avoidance targets show their chance band (DEC-030), ability/talent targets are
> named by the ability they add, and over-long row text shows its full value in a hover
> tooltip (declarative `.tooltip(...)`/`onHoverTooltip`; the popover machinery is gone —
> see `RangeRowElement` and `DropdownItemRowElement`). DEC-032 pins the editing UX:
> the Min field stays state-driven (`TextInputElement` rejected — the framework does not
> route keystrokes to owned elements), stepper hit zones keep the 16px `regionAt` area
> around 12px buttons, and `[x]`/`[ ]` remain text glyphs.

Compact side panel (≈150×110 px, scale-safe), drawn with vanilla gui drawing calls
(fill + `drawString`), containing:

- Operation selector (‹ ›) over the available re-roll actions.
- Target selector (‹ ›) over `ModifierCatalog` candidates; shows "no valid targets"
  state and disables Start when the gear has no rollable targets.
- Crafting potential line: `current / max` (`CRAFTING_POTENTIAL` / `MAX_CRAFTING_POTENTIAL`).
- **Auto-reset checkbox** (default on, mirrors config `autoResetPotential`).
- Start / Stop button; status line (RUNNING, stopped reason, roll count).

### 4.5 Config `[Auto Reroll]`

| Key | Type | Default | Range |
| --- | --- | --- | --- |
| `enabled` | boolean | `true` | – |
| `tickInterval` | int | `4` | `[4, 200]` |

> `tickInterval` default is `4` (owner-confirmed, DEC-032a); the "15" once shown in this
> table could not be sourced from any in-repo doc.
| `rollTimeoutTicks` | int | `60` | – |
| `maxRolls` | int | `0` (unlimited) | ≥ 0 |
| `autoResetPotential` | boolean | `true` | – |
| `successSoundEvent` | String | `minecraft:block.note_block.pling` | ResourceLocation-parseable |
| `stopSoundEvent` | String | `minecraft:block.note_block.pling` | ResourceLocation-parseable |
| `volume` | double | `1.0` | `[0.0, 2.0]` |
| `pitch` | double | `1.0` | `[0.5, 2.0]` |

### 4.6 Keybind & commands

- KeyMapping default `P` → toggles panel visibility (also works while the station
  screen is open; registering via `RegisterKeyMappingsEvent`).
- `/vma reroll start` and `/vma reroll stop` (client commands, same pattern as
  `/vma debug`).

### 4.7 Sounds

- Register `reroll_success` and `reroll_stop` SoundEvents (DeferredRegister); entries in
  `sounds.json` + `en_us.json` subtitles. Playback reuses `AlertSoundPlayer.play(...)`
  (single DRY entry point). Defaults to vanilla pling per owner decision (no custom
  assets needed); config keys allow override.

---

## 5. Safety & edge cases

1. **Impossible target** — impossible by construction (selection list) and re-checked
   every evaluation (runtime guard) → stop `INVALID_TARGET`.
2. **Out of potential** — classified via the game's own gate; reset pressed at most once
   per session; if reset is missing/unaffordable the run stops with an alert (never
   loops).
3. **Out of materials** — action disabled for non-potential reasons → stop with alert.
4. **Timeout** — no gear change after a press → stop (network/server hiccup).
5. **Screen closed / gear removed** — run stops immediately.
6. **maxRolls** — hard cap on presses per run (0 = unlimited).
7. **GUI placement** — panel anchored outside the station window with left-side
   fallback + clamping; can never cover the station.
8. **Coexistence** — VH classes patched with `remap=false` + `vma$` unique members
   (project convention; QOLHunters coexistence pattern already proven in S13).

---

## 6. Build & verification

- `./gradlew build` must pass after each commit.
- Runtime testing per DEC-011: owner copies `build/libs/vault_modifier_alerts-<version>.jar`
  into the Prism instance and tests in-game (no dev-client runs).

---

## 7. Implementation order

1. Requirement/plan document (this file) — commit.
2. Config group + sound registration + lang/sounds.json — commit.
3. `ModifierCatalog` (guard logic) — commit.
4. Mixin (trigger/render/click) + mixins.json entry — commit.
5. `AutoRerollEngine` — commit.
6. `RerollPanel` — commit.
7. Keybind + commands — commit.
8. Tick wiring — commit.
9. Docs (README, DECISIONS.md) — commit.
10. Final `./gradlew build` + feature-complete commit.
