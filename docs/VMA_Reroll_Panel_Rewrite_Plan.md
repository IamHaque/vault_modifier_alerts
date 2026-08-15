# Auto-Reroll Panel — Rewrite Plan

### Bringing `vault_modifier_alerts` (IamHaque) in line with the QOLHunters Design Guidelines

Source analyzed: `github.com/IamHaque/vault_modifier_alerts`, focus: `feature/reroll/*`,
`mixin/artisan/MixinVaultArtisanStationScreen.java` — the Auto-Reroll panel attached to the
Vault Artisan Station (reroll) screen.

---

## 1. What the current panel is

`RerollPanel` is a **fully hand-drawn, self-contained widget** bolted onto the side of the Artisan
Station screen. Structurally it's well engineered — a single `RerollPanelLayout` class is the shared
source of truth for both drawing and hit-testing, state lives in one singleton, and the model layer
(`ModifierCatalog`, `AutoRerollEngine`) is cleanly separated from the view. That engineering quality
should be **preserved as-is**; nothing here proposes touching the reroll logic itself.

The problem is entirely visual/interactive: the panel is built as a **generic dark-mode debug overlay**,
not a Vault Hunters-native surface. It renders every pixel itself with `GuiComponent.fill()` rectangles
and raw `font.draw()` calls, using a bespoke flat color palette and ASCII-art affordances
(`[x]` checkboxes, hand-drawn triangles, `x` close glyphs, `-`/`+` text buttons). It never touches the
host's `ScreenTextures` atlas, `ButtonElement`, `TextInputElement`, or the declarative `.tooltip(...)` API
that QOLHunters — and the host game itself — are built on.

## 2. Audit: current implementation vs. QOLHunters principles

| #   | QOLHunters principle                                                      | Current `RerollPanel` state                                                                                                                                                                                  | Verdict               |
| --- | ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------- |
| 1   | "Patch, don't replace" — reuse host textures/fonts/atlas regions          | Zero atlas usage; 100% flat-fill rectangles and font-only glyphs                                                                                                                                             | ❌ Violates           |
| 2   | Everything togglable via grouped `ForgeConfigSpec` entries                | `AUTO_REROLL_ENABLED` exists for _behavior_, but panel visibility/position/appearance have no config entries at all (`visible` is in-memory only, resets each session)                                       | ❌ Partially violates |
| 3   | Additive, small deltas — don't compete for visual weight                  | Panel is a full 216×~180px dark box that visually reads as a separate application window, not part of the VH screen                                                                                          | ❌ Violates           |
| 4   | Anchor-relative, resolution-independent layout                            | ✅ Actually does this well — `RerollPanelElement.layout(...)` derives x/y from `gui.left()/right()`, clamps to screen bounds                                                                                 | ✅ Matches            |
| 5   | Config-value-driven, re-themeable color                                   | 13 colors are `private static final int` literals baked into `RerollPanel` — no config, no token reuse                                                                                                       | ❌ Violates           |
| 6   | Reuse host tooltip/interaction idioms                                     | Bespoke `hoverTooltip()`/`drawTooltip()`/`wrapText()` reimplementation instead of `.tooltip(...)` element API or `TooltipContainerElement`                                                                   | ❌ Violates           |
| 7   | Alpha-encodes state intensity                                             | Not used — hover/open/selected states are encoded as arbitrary distinct hex colors (`HOVER_COLOR`, `HIGHLIGHT_COLOR`) with no relationship to each other                                                     | ❌ Violates           |
| 8   | Buttons are atlas-region driven with `.tooltip()` + `active` flag         | Buttons are `GuiComponent.fill()` rectangles with manual hover-rect math (`hoveredDec`, `hoveredInc`, etc.) — no `ButtonElement`, no `.active` state, disabled state simulated with a fourth hardcoded color | ❌ Violates           |
| 9   | Search/inputs extend `TextInputElement`                                   | The "Min" numeric field is a hand-rolled text buffer (`minInputText`, `acceptChar`, `onKeyPressed`) with a blinking `_` cursor drawn manually                                                                | ❌ Violates           |
| 10  | Lists/scroll reuse `ScrollableContainer` / `VerticalScrollClipContainer`  | The 3 dropdowns (Operation/Modifier/Targets) are hand-drawn scroll lists with manual up/down triangle buttons and manual `dropdownScroll` clamping                                                           | ❌ Violates           |
| 11  | Small, legible deltas / consistent glyph vocabulary (⌄ expand, ⋮ divider) | Uses ad hoc ASCII (`▾`-style hand triangle, `[x]`/`[ ]`, plain `x`, `*`, `>`) instead of the established glyph set                                                                                           | ❌ Violates           |
| 12  | Right-click reserved for "clear/reset"                                    | Not implemented anywhere in the panel; clearing targets requires hitting a small `x` hitbox instead                                                                                                          | ❌ Violates           |
| 13  | Debounce transient/flicker-prone state                                    | Not applicable to most rows, but the blinking text cursor is a manual `System.currentTimeMillis()` timer rather than using the host's own text-field cursor rendering                                        | ⚠️ Minor              |
| 14  | Config screen conventions (grouped, commented, human labels)              | `Auto-Reroll` config group already follows this well for _behavior_ settings                                                                                                                                 | ✅ Matches            |
| 15  | `qOLHunters$`-style unique-member prefixing / Mixin hygiene               | Uses `vma$` prefix consistently, `Accessor`/`Mixin` split present — mirrors QOLHunters conventions exactly                                                                                                   | ✅ Matches            |

**Summary: the plumbing (mixin hygiene, layout math, single-source-of-truth geometry, config grouping for
behavior) is already excellent and should not be touched. The rendering layer is the entire problem** — it
was built as an independent immediate-mode UI library instead of as a citizen of the host's element
framework.

---

## 3. Design Target

Rebuild the panel so a player who has used QOLHunters' Statistics config button, search boxes, and
toggle badges would recognize the Auto-Reroll panel as "the same mod family," while preserving:

- The exact same information density and functionality (operation picker, modifier picker, targets list
  with per-target thresholds, any/all condition, potential/cost readout, start/stop, status line).
- The exact same model/state layer (`RerollPanel` fields, `ModifierCatalog`, `AutoRerollEngine`) — this is
  a **view-layer rewrite**, not a feature rewrite.
- The single-source-of-truth layout discipline `RerollPanelLayout` already provides — that pattern is
  worth keeping, just re-pointed at framework elements instead of raw fills.

---

## 4. Rewrite Plan

### Phase 0 — Freeze the model boundary

1. Treat `RerollPanel`'s **non-drawing** public API (`currentSelection()`, `toggleTarget()`,
   `focusTarget()`, `removeTarget()`, `commitMinInput()`, `handleClick()`'s _hit-type switch bodies_,
   `clampSelections()`, etc.) as the contract the new view must call into. Do not change method
   signatures used by `AutoRerollEngine` or `VmaClientCommands`.
2. Split `RerollPanel` into two classes to make the boundary explicit:
   - `RerollPanelState` — pure state/model (targets, operationIndex, stopCondition, threshold logic,
     dropdown mode/scroll, min-input text buffer semantics minus the blinking-cursor rendering).
   - `RerollPanelView` (replaces the drawing half of the old `RerollPanel`) — composed of framework
     elements, built in Phase 2 onward.
3. Keep `RerollPanelLayout` — its _geometry constants and row-offset math_ remain useful even when rows
   become real elements, because child elements still need to be told where to sit. Its `regionAt()`
   hit-testing can be deleted once rows are real `IGuiEventElement`s with their own hit-testing.

### Phase 1 — Container migration

1. Replace `RerollPanelElement extends AbstractSpatialElement` (a single opaque draw+click blob) with a
   proper **element container**: either an `AbstractElementContainerScreen`-style sub-container, or (since
   VH's framework supports nested elements) a parent `RerollPanelElement` that itself `addElement(...)`s
   child elements for each row — mirroring how `MixinCardDeckScreen` adds a `TooltipContainerElement` as a
   real child rather than drawing a tooltip panel by hand.
2. Register the panel exactly as today, via `@Inject(method = "<init>", at = @At("RETURN"))` on
   `MixinVaultArtisanStationScreen`, calling `addElement(RerollPanelElement.create(screen))` — this part of
   the integration is already correct and QOLHunters-consistent; keep it unchanged.
3. Drop the manual "redraw at TAIL of `m_6305_`" workaround once rows are real framework elements with
   correct z-ordering through the normal element-render pass (the workaround exists specifically because
   today's panel is drawn "off to the side" of the framework's own render pass — a symptom of not being a
   true framework element).

### Phase 2 — Panel chrome (background/frame/title)

1. Replace `drawPanelFrame()`'s four `GuiComponent.fill()` calls with a **9-slice or 3-slice atlas panel
   texture** added to the mod's own texture atlas namespace (`vault_modifier_alerts:gui/panel/reroll_bg`),
   styled to match Vault Hunters' existing side-panel/tooltip panel look (dark parchment-adjacent panel
   with a thin gold top rule) rather than the current flat near-black `#EE111111` box with a pure gray
   border.
   - If a suitable existing host panel texture already fits (e.g. the tooltip/side-panel background used
     elsewhere in VH), prefer reusing that region directly — per Guideline §1.2 ("borrow the existing
     visual language before inventing a new one").
2. Title ("Auto-Reroll") keeps its centered gold text, but the gold used should be sampled from
   QOLHunters' `GOLD_COLOR`/divider conventions (§4 of the Design Guidelines) rather than the panel's own
   one-off `0xFFE3C38C`, so both mods render an identical "gold" when viewed side by side.
3. Panel width/anchor logic (`RerollPanel.computeWidth`, `RerollPanelElement`'s `.layout(...)`) is already
   anchor-relative and needs **no change** — it's a model example of Guideline §6.

### Phase 3 — Rows → elements

Convert each hand-drawn row into a small reusable element type, replacing bespoke per-row draw+hit code:

| Current row                                 | New element type                                                                                                                                                                                                                            | Notes                                                                                                                                   |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| Focus (operation picker)                    | `DropdownRowElement` (new, generic) wrapping a `ButtonElement`-style trigger + label                                                                                                                                                        | Reused for Operation, Modifier, and Targets rows — currently three near-duplicate `drawXRow` methods become one parameterized component |
| Modifier ("add a target")                   | Same `DropdownRowElement`, `placeholder` variant                                                                                                                                                                                            |                                                                                                                                         |
| Targets (chip + clear + focused target)     | `DropdownRowElement` + two small `ButtonElement`s (`stopConditionChip`, `clearTargetsButton`) anchored to its right edge                                                                                                                    | Clear button becomes a real `ButtonElement` with `.tooltip("Clear all targets")` instead of manual `clearHovered` bool + inline fill    |
| Min (stepper + numeric field)               | `StepperFieldElement` (new) = two `ButtonElement`s (`-`/`+`, using the same small-utility-button convention as QOLHunters' 21×21 config gear button, scaled down) + one `TextInputElement<RerollPanel$MinInput>` subclass, numeric-filtered | Directly modeled on `QOLSearchElement extends TextInputElement<T>`                                                                      |
| Range readout                               | Plain label element (no interaction) — unchanged in spirit, just moved off raw `drawString` into a small `LabelElement` for consistency                                                                                                     |                                                                                                                                         |
| Potential/cost readout                      | Plain label element, values driven by existing `ModifierCatalog` calls                                                                                                                                                                      |                                                                                                                                         |
| Auto-reroll / Auto-reset toggles            | `ToggleRowElement` using the **`BUTTON_TOGGLE_ON`/`BUTTON_TOGGLE_OFF`** atlas pair from `ScreenTextures` (see Design Guidelines §3.3) instead of the `[x]`/`[ ]` ASCII checkbox                                                             | Direct reuse of the exact convention QOLHunters already established in `MixinPrestigePowerWidget`                                       |
| Start/Stop button                           | Real `ButtonElement` bound to a `BUTTON_*` atlas texture (or the vanilla generic button atlas region already used elsewhere in VH) with `.active = canStart()`                                                                              | Replaces manual `hovered`/`canStart` color branching with the host's own disabled-button rendering, per Guideline §3.3                  |
| Status line                                 | Plain label element; truncation/tooltip-on-hover logic replaced by `.tooltip(...)` on the label element instead of the custom `hoverTooltip`/`pendingTooltip` system                                                                        |                                                                                                                                         |
| Counter row ("Potential reset x N")         | Plain label element                                                                                                                                                                                                                         |                                                                                                                                         |
| Dropdown (Operation/Modifier/Targets lists) | `VerticalScrollClipContainer`-backed list, one row-element per entry, with the host's own scrollbar rendering instead of hand-drawn up/down triangles                                                                                       | Directly mirrors `ThemeListElement`/`ScrollableItemListElement` usage pattern documented in Design Guidelines §3.5                      |
| Tooltip-on-truncated-text                   | `.tooltip((tooltipRenderer, poseStack, mouseX, mouseY, flag) -> ...)` on each label/row element                                                                                                                                             | Deletes `hoverTooltip()`, `drawTooltip()`, `wrapText()` entirely — the host's tooltip renderer already wraps text                       |

Each new element still asks `RerollPanelLayout` (or its Phase-0 successor) for its row's Y-offset, so the
"one source of truth for geometry" property is preserved — only the _drawing/hit-testing implementation_
per row changes, not the layout math.

### Phase 4 — Color & glyph normalization

1. Delete the panel's private color constants and replace with the shared token set proposed in the
   QOLHunters Design Guidelines §11 (`UIConstants`), extended with reroll-specific _semantic_ names that
   map onto the same underlying tokens:

   ```java
   // io.iridium.qolhunters-style shared tokens, referenced (not reinvented) by VMA
   int PANEL_BG        = UIConstants.PANEL_BG;         // was BG_COLOR 0xEE111111
   int PANEL_BORDER     = UIConstants.COLOR_DIVIDER;     // was BORDER_COLOR 0xFF6B6B6B
   int TEXT_DEFAULT     = UIConstants.COLOR_NEUTRAL;     // was TEXT_COLOR 0xFFFFFFFF
   int TEXT_MUTED       = UIConstants.COLOR_DIVIDER;     // was MUTED_COLOR 0xFFA0A0A0
   int ACCENT_GOLD      = UIConstants.COLOR_GOLD;        // was GOLD_COLOR 0xFFE3C38C
   int STATE_SUCCESS    = UIConstants.COLOR_SUCCESS;     // was ACCENT_COLOR 0xFF55FF55
   int STATE_DANGER     = UIConstants.COLOR_ALERT;       // was WARN_COLOR 0xFFFF5555 — reserved ONLY for stop/error/danger per Guideline §4.2
   int ROW_HOVER        = UIConstants.ALPHA_TRANSIENT hue // was HOVER_COLOR — reuse the 0x40-alpha "transient state" convention instead of a flat opaque gray
   int ROW_OPEN         = UIConstants.ALPHA_CONFIRMED hue // was HIGHLIGHT_COLOR — reuse the 0x64-alpha "confirmed/active state" convention
   ```

   This directly resolves audit items #5 and #7: colors become shared, config-overridable tokens, and
   hover-vs-open states become alpha variants of the _same_ hue instead of two unrelated hardcoded colors.

2. Replace ad hoc glyphs with the QOLHunters glyph vocabulary:
   - Dropdown expand marker (currently a hand-drawn `drawTriangle`) → the same **`⌄` chevron** convention
     used in `AutoChosenTextWidget`, `DARK_GRAY`, matching size/weight.
   - Section/list dividers, if needed for grouping dropdown headers → the **`⋮`-rule + bold label**
     convention from `MixinAbilityDialog`, instead of a plain filled header bar.
   - Checkbox/toggle rows → **atlas toggle icons** (Phase 3), not `[x]`/`[ ]` text.
   - "Remove" affordance on chips/dropdown rows → reuse the same small `x`-badge idiom but rendered as a
     tiny icon button (`ButtonElement`) with a tooltip, not an inline colored rectangle + drawn glyph.

### Phase 5 — Interaction conventions

1. **Right-click = clear/reset**, applied consistently:
   - Right-click the Min field → clears the threshold (same semantic as `QOLSearchElement`'s right-click
     clear), removing the need for the dedicated small `x`/"TARGETS*CLEAR" hit-zone as the \_only* way to
     clear.
   - Keep the explicit "Clear all targets" button for discoverability, but right-click becomes the fast
     path power users expect from having used other QOLHunters-family panels.
2. Replace the hand-rolled blinking cursor (`System.currentTimeMillis() / 500 % 2`) with the vanilla
   `EditBox` cursor rendering that `TextInputElement` already provides for free once the Min field is a
   real `TextInputElement` subclass (Phase 3) — deletes a whole manual-timer code path.
3. Keep the existing debounce-free design for rows that don't need it, but audit the **status line** for
   flicker: today it recomputes and can flash between "Rolling #N" sub-states on fast ticks. Apply the
   same 20-tick-style debounce convention documented for `ZeroUsesAlert` if flicker is observed in testing.

### Phase 6 — Config integration for panel presentation

Today only reroll _behavior_ is configurable (`AUTO_REROLL_ENABLED`, tick interval, etc.) — the panel's
own presence/position is entirely runtime, in-memory, non-persistent. Bring this in line with QOLHunters
Guideline §2.1 ("everything is togglable") and §8 (config screen conventions):

1. Add a new config group, `Reroll Panel` (sits alongside the existing `Auto-Reroll` behavior group),
   containing:
   - `PANEL_ENABLED` (boolean, default `true`) — show/hide the panel entirely, independent of the
     behavior toggle, mirroring QOLHunters' `SHOW_CONFIG_BUTTON`-style presentational flags.
   - `PANEL_SIDE_PREFERENCE` (enum: `AUTO`, `LEFT`, `RIGHT`) — today's fallback logic
     (`RerollPanelElement`'s left-then-right-then-shrink cascade) becomes overridable rather than fixed.
   - `PANEL_COMPACT_MODE` (boolean) — optional stretch goal: collapses optional rows (range, counter) for
     players who want a denser HUD, matching the "small, legible deltas" ethos when screen space is tight.
2. Every entry gets a `.comment(...)` in plain English, per Guideline §8 — no undocumented config keys.

### Phase 7 — QA pass against the QOLHunters checklist

Run the existing Design Guidelines §10 checklist against the rebuilt panel before merging:

- [ ] Config-gated (visibility) with plain-English comments — Phase 6
- [ ] Reuses atlas regions/host font/host color instead of new one-off assets — Phases 2–4
- [ ] All positions derived from live parent bounds via `Spatials`/`.layout(...)` — already true, verify
      it still holds once rows are child elements
- [ ] Toggle affordances reuse `BUTTON_TOGGLE_ON`/`BUTTON_TOGGLE_OFF` — Phase 3
- [ ] Themeable colors are config `Integer`/`enum`, not literals — Phase 4
- [ ] State-intensity maps to alpha — Phase 4
- [ ] New alert text debounces flicker — Phase 5.3
- [ ] Injected members prefixed and `@Unique` — already true (`vma$`), no change needed
- [ ] Right-click reserved for clear/reset — Phase 5.1
- [ ] Tooltips via declarative `.tooltip(...)` — Phase 3

---

## 5. Proposed New File/Package Layout

```
feature/reroll/
  RerollPanelState.java          // (was: state half of RerollPanel) model + selection logic, unchanged behavior
  RerollPanelElement.java        // container element; owns child rows, anchor/width logic unchanged
  ui/
    DropdownRowElement.java      // generic "label + value + ⌄" trigger row, used 3x
    StepperFieldElement.java     // "- [field] +" numeric stepper, used for Min row
    ToggleRowElement.java        // atlas-icon on/off row, used 2x
    DropdownListElement.java     // VerticalScrollClipContainer-backed list for Operation/Modifier/Targets
    RerollTokens.java            // panel-local aliases into the shared UIConstants token set
  RerollPanelLayout.java         // kept: row-offset constants feeding the new elements' .layout(...)
  ModifierCatalog.java           // unchanged
  AutoRerollEngine.java          // unchanged
```

This mirrors QOLHunters' own `features/<name>/...` convention (Design Guidelines §9) and specifically its
practice of pulling reusable widgets (like `AutoChosenTextWidget`) out into their own small class rather
than inlining them into a mega-class — the current 700-line `RerollPanel` is exactly the anti-pattern that
convention exists to avoid.

---

## 6. What Explicitly Does Not Change

To keep this a scoped, low-risk rewrite:

- `ModifierCatalog`, `AutoRerollEngine`, `AlertSoundPlayer`, `ModifierTracker`, and all non-reroll-panel
  features are untouched.
- The reroll **behavior** config group (tick interval, roll gap, max rolls, sound events) is untouched.
- `ArtisanStationScreenAccessor` / the `attemptCraft` duck-interface hook is untouched — it's already a
  clean, minimal, QOLHunters-consistent integration point.
- The panel's anchor/fallback-width algorithm (`RerollPanel.computeWidth`, the left-then-right-then-shrink
  cascade in `RerollPanelElement.create`) is untouched — it already satisfies Guideline §6.
- No gameplay/balance behavior changes — this is strictly a rendering/interaction-layer rewrite.

---

## 7. Suggested Sequencing

1. **Phase 0** (state/view split) — mechanical, low risk, unlocks everything else, ~0 visual change.
2. **Phase 2** (chrome only: panel background + title color) — smallest visible win, validates the atlas
   texture pipeline works end-to-end before touching interactive rows.
3. **Phase 3** (rows → elements), **row by row**, not all at once — start with the two `ToggleRowElement`s
   (lowest risk, isolated on/off state) then the `StepperFieldElement`, then the three
   `DropdownRowElement`/`DropdownListElement` pairs (highest complexity, most shared code to extract).
4. **Phase 4** (color/glyph tokens) — can land incrementally alongside Phase 3 per-row, since each row PR
   naturally touches its own colors.
5. **Phase 5** (interaction polish: right-click clear, real text cursor) — after the corresponding element
   exists.
6. **Phase 6** (config integration) — independent, can land any time after Phase 0.
7. **Phase 7** (QA checklist) — gate before final merge.

Each phase should be shippable/testable on its own — the panel remains fully functional throughout, since
Phase 0 explicitly preserves the existing public contract that `AutoRerollEngine` and commands depend on.
