# Auto-Reroll Panel — GUI Rewrite Plan

### Aligning the RerollPanel with QOLHunters Design Philosophy

This plan rewrites the rendering/interaction layer of the Auto-Reroll panel to use Vault Hunters'
native framework widgets, atlas textures, and QOLHunters design conventions — while preserving the
entire model/state layer (`RerollPanelState`, `ModifierCatalog`, `AutoRerollEngine`, `RerollPanelLayout`)
unchanged.

---

## 1. Current State Assessment

### Architecture (preserved)

| Layer           | Class                            | Role                                                  | Lines | Verdict                                                   |
| --------------- | -------------------------------- | ----------------------------------------------------- | ----- | --------------------------------------------------------- |
| Element wrapper | `RerollPanelElement`             | VH framework element (position, render, click)        | 110   | **Partial rewrite** — must become a container             |
| View/rendering  | `RerollPanel`                    | Draw, hit-test, input routing, tooltips               | 1072  | **Full rewrite** — replace with widget elements           |
| Geometry        | `RerollPanelLayout`              | Single source of truth for row Y-offsets, hit regions | 177   | **Keep** — geometry constants still needed                |
| State/model     | `RerollPanelState`               | Selection, dropdown, min-input text, thresholds       | 439   | **No change**                                             |
| Engine          | `AutoRerollEngine`               | State machine, press/evaluate/stop                    | 480   | **No change**                                             |
| Catalog         | `ModifierCatalog`                | Applicability, roll ranges, human names               | 521   | **No change**                                             |
| Mixin           | `MixinVaultArtisanStationScreen` | Registers element, re-draws on top, craft trigger     | 87    | **Simplify** — remove TAIL redraw once z-order is correct |
| Config          | `VmaClientConfigs`               | Behavior settings (enabled, tick interval, etc.)      | 222   | **Extend** — add panel presentation settings              |
| Events          | `ClientTickEvents`               | Keyboard/mouse-wheel routing to panel                 | —     | **Adapt** — route to new widget tree                      |

### Design Guideline Violations (11 of 15)

| #   | Principle                               | Current                                               | Target                                                    |
| --- | --------------------------------------- | ----------------------------------------------------- | --------------------------------------------------------- |
| 1   | Reuse host textures/atlas               | Zero atlas, 100% `GuiComponent.fill()`                | Atlas panel background, atlas buttons/toggles             |
| 2   | Everything togglable                    | Panel visibility is in-memory only, no config         | Add `PANEL_ENABLED`, `PANEL_SIDE`, `PANEL_COMPACT` config |
| 3   | Additive, small deltas                  | 216x180px dark box reads as separate app              | Match VH panel style (parchment-adjacent, gold rule)      |
| 5   | Config-value-driven color               | 13 `private static final int` literals                | Shared `UIConstants` token set, alpha-based state         |
| 6   | Reuse host tooltips                     | Bespoke `hoverTooltip()`/`drawTooltip()`/`wrapText()` | Declarative `.tooltip(...)` on elements                   |
| 7   | Alpha-encodes state                     | Arbitrary distinct hex colors for hover/open          | `0x40` transient, `0x64` confirmed — same hue             |
| 8   | Buttons are atlas-driven                | `GuiComponent.fill()` rectangles with manual hover    | `ButtonElement` with atlas textures + `.active`           |
| 9   | Search/inputs extend `TextInputElement` | Hand-rolled `minInputText` buffer + manual cursor     | `NineSliceTextInputElement` subclass                      |
| 10  | Lists reuse `ScrollableContainer`       | Hand-drawn scroll lists with manual triangles         | `VerticalScrollClipContainer`-backed list                 |
| 11  | Consistent glyph vocabulary             | Ad hoc ASCII (`[x]`, hand-drawn triangles, `x`)       | Atlas toggles, `⌄` chevron, button icons                  |
| 12  | Right-click = clear/reset               | Not implemented                                       | Right-click Min field clears threshold                    |

---

## 2. Design Target

A panel that a QOLHunters user would recognize as "the same mod family":

- Atlas-backed panel background (not flat black)
- Real `ButtonElement`/`LabelElement`/`TextInputElement` widgets for each row
- Atlas toggle icons instead of `[x]`/`[ ]` ASCII
- Declarative `.tooltip(...)` instead of hand-drawn popovers
- Alpha-based hover/open state encoding
- Right-click to clear on the Min field
- Config-driven visibility and side preference

---

## 3. Phased Implementation Plan

### Phase 0 — Create shared token constants

**Goal:** Establish the color/spacing/glyph constants referenced by all subsequent phases.

**New file:** `feature/reroll/ui/RerollTokens.java`

```java
package io.haque.vault_modifier_alerts.feature.reroll.ui;

/**
 * Panel-local color and spacing tokens derived from DESIGN_GUIDELINES section 12.
 * These reference the same values QOLHunters uses, ensuring visual identity.
 */
public final class RerollTokens {
    // --- Color roles (ARGB ints) ---
    public static final int PANEL_BG       = 0xEE1A1A1A; // near-black, slightly lighter than pure #111
    public static final int PANEL_BORDER   = 0xFF6B6B6B; // gray border (matches VH)
    public static final int GOLD_ACCENT    = 0xFFE3C38C; // title, selected items (matches VH gold)
    public static final int TEXT_DEFAULT   = 0xFFFFFFFF; // primary text
    public static final int TEXT_MUTED     = 0xFFA0A0A0; // labels, secondary
    public static final int TEXT_SUCCESS   = 0xFF55FF55; // running/success state
    public static final int TEXT_DANGER    = 0xFFFF5555; // stop/error (reserved ONLY for danger)
    public static final int TEXT_DISABLED  = 0xFF707070; // dimmed when feature off
    public static final int FIELD_BG       = 0xFF2E2E2E; // input field background
    public static final int FIELD_FOCUSED  = 0xFF484848; // focused input field

    // --- Alpha-encoded state overlays ---
    public static final int HOVER_ALPHA    = 0x40; // ~25% — transient/hover/preview
    public static final int ACTIVE_ALPHA   = 0x64; // ~39% — confirmed/active/open

    // --- Spacing (px) ---
    public static final int SPACING_TIGHT   = 3;
    public static final int SPACING_DEFAULT = 5;
    public static final int SPACING_LOOSE   = 7;
    public static final int PANEL_PAD_X     = 8;
    public static final int ROW_H           = 14;
    public static final int BUTTON_H        = 14;

    // --- Glyphs ---
    public static final String CHEVRON_DOWN = "\u2304"; // expand affordance, DARK_GRAY
    public static final String DIVIDER      = "\u22EE"; // section divider

    private RerollTokens() {}
}
```

**Deliverables:**

- [ ] Create `RerollTokens.java` with all constants
- [ ] Verify constants match `DESIGN_GUIDELINES.md` section 12 values

---

### Phase 1 — Container migration

**Goal:** Transform `RerollPanelElement` from a single opaque draw blob into a proper
element container that can hold child widget elements.

**Changes to `RerollPanelElement.java`:**

1. Extend `AbstractSpatialElement<RerollPanelElement>` (already does)
2. In the constructor, `addElement(...)` for each child row element (built in Phase 3)
3. The `render()` method no longer calls `RerollPanel.draw()` — it lets the framework
   render its children. It only draws the panel background/frame (Phase 2).
4. The `onMouseClicked()` method delegates to the framework's child hit-testing
   instead of calling `RerollPanel.handleClick()`.
5. Remove the TAIL redraw workaround from `MixinVaultArtisanStationScreen` once
   children are real framework elements with correct z-ordering.

**Key constraint from DEC-026/DEC-028:**

- Keyboard/mouse-wheel events still route through `ClientTickEvents` Forge events
  because the VH framework does not route typed chars to elements owned by a Screen.
- The panel element's `.layout(...)` lambda is unchanged (anchor-relative positioning).

**Changes to `MixinVaultArtisanStationScreen.java`:**

- Remove `vma$renderPanelOnTop` injection (TAIL redraw) — no longer needed once
  rows are real framework elements.

**Deliverables:**

- [ ] Refactor `RerollPanelElement` to be a container (call `addElement()` for children)
- [ ] Remove TAIL redraw from mixin
- [ ] Verify z-ordering is correct (panel renders above slot items naturally)

---

### Phase 2 — Panel chrome (background/frame)

**Goal:** Replace the flat `GuiComponent.fill()` background with an atlas-backed panel texture.

**Approach:**

1. Check if VH's existing `ScreenTextures` atlas regions include a suitable panel
   background (e.g. `INSET_BLACK_BACKGROUND` or tooltip panel). Prefer reusing that
   region directly per DESIGN_GUIDELINES section 1.2 ("borrow existing visual language").
2. If no suitable host region exists, add a 9-slice atlas PNG to
   `assets/vault_modifier_alerts/gui/panel/` (a dark parchment-style panel with a thin
   gold top rule, matching VH's existing side-panel look).
3. The panel frame rendering uses `GuiComponent.blit()` with the atlas region instead
   of four `GuiComponent.fill()` calls.
4. Title "Auto-Reroll" keeps centered gold text, color from `RerollTokens.GOLD_ACCENT`.

**File changes:**

- `RerollPanelElement.render()` — draw atlas-backed background instead of `fill()` calls
- OR: create a small `PanelBackgroundElement` child that renders the atlas texture

**Deliverables:**

- [ ] Identify or create atlas panel texture
- [ ] Replace `drawPanelFrame()` with atlas-backed rendering
- [ ] Verify visual match with VH panels

---

### Phase 3 — Rows to framework elements

**Goal:** Replace every hand-drawn row with a real VH framework element.

#### 3a. Toggle rows (lowest risk)

**New file:** `feature/reroll/ui/ToggleRowElement.java`

Convert the two toggle rows (Auto-reroll, Auto-reset potential) to use:

- `BUTTON_TOGGLE_ON` / `BUTTON_TOGGLE_OFF` atlas pair from `ScreenTextures`
- A `LabelElement` for the label text
- `.tooltip(...)` for hover explanation
- `.active = featureEnabled` for disabled state

Current code (`drawToggleRow` in `RerollPanel.java:650-660`):

```java
drawString(poseStack, "[" + (enabled ? "x" : " ") + "]", ...);
drawString(poseStack, label, ...);
```

New approach:

```java
// In RerollPanelElement constructor
ToggleButton toggle = new ToggleButton(
    RerollPanelLayout.rerollToggleY,
    "Auto-reroll",
    VmaClientConfigs::isAutoRerollEnabled,
    VmaClientConfigs::setAutoRerollEnabled
);
addElement(toggle);
```

**Deliverables:**

- [ ] Create `ToggleRowElement.java`
- [ ] Replace `drawToggleRow()` calls with element instances
- [ ] Wire click to config toggle + engine stop (matching existing `handleClick` logic)

#### 3b. Start/Stop button

**New file:** `feature/reroll/ui/StartStopButtonElement.java`

Convert the Start/Stop button to use:

- `ButtonElement` with `BUTTON_EMPTY_TEXTURES` (or `BUTTON_EMPTY_GREEN_TEXTURES` for start)
- `.active = canStart()` for disabled state
- `.tooltip(...)` for "Start auto-reroll" / "Stop auto-reroll"
- Label text computed from engine state ("Start" / "Stop")
- Color via `RerollTokens`: green for start, red for stop, disabled gray

Current code (`drawButton` in `RerollPanel.java:662-681`):

```java
GuiComponent.fill(poseStack, ...); // background rect
drawCentered(poseStack, label, ...);
```

**Deliverables:**

- [ ] Create `StartStopButtonElement.java`
- [ ] Replace `drawButton()` with element instance
- [ ] Wire click to engine start/stop (matching existing `handleClick` logic)

#### 3c. Min stepper field

**New file:** `feature/reroll/ui/StepperFieldElement.java`

Convert the Min row to use:

- Two small `ButtonElement`s for `-` and `+` (using `BUTTON_EMPTY_DARK_GRAY_TEXTURES`)
- One `NineSliceTextInputElement` subclass for the numeric field
- `.tooltip(...)` on the field for "Enter minimum threshold"
- Right-click on field clears threshold (DESIGN_GUIDELINES section 3.4)

Current code (`drawMinRow` in `RerollPanel.java:579-619`):

- Hand-drawn `-`/`+` rectangles with `GuiComponent.fill()`
- Hand-rolled text buffer with manual `System.currentTimeMillis() / 500 % 2` cursor
- Manual field background fill

New approach:

```java
NineSliceTextInputElement minField = new NineSliceTextInputElement(
    spatial, ScreenTextures.INSET_BLACK_BACKGROUND, font
) {
    @Override protected boolean isValidChar(char c) {
        return Character.isDigit(c) || (c == '.' && !getText().contains("."));
    }
};
minField.onTextChanged(text -> { /* update threshold draft */ });
minField.onEnterPressed(() -> { /* commit threshold */ });
```

**Keyboard routing note:**
The `NineSliceTextInputElement` handles its own keyboard input when focused. However,
since the panel is owned by a `Screen` (not a standalone element tree), the framework
does NOT route `KeyboardCharTypedEvent` to the element. We must keep the existing
`ClientTickEvents` keyboard routing but point it at the `TextInputElement`'s API
(`setText()`, `getText()`, `setFocused()`) instead of the hand-rolled buffer.

**Deliverables:**

- [ ] Create `StepperFieldElement.java`
- [ ] Subclass `NineSliceTextInputElement` for numeric filtering
- [ ] Replace `drawMinRow()` with element instance
- [ ] Adapt `ClientTickEvents` keyboard routing to use `TextInputElement` API
- [ ] Implement right-click = clear threshold

#### 3d. Dropdown rows (highest complexity)

**New file:** `feature/reroll/ui/DropdownRowElement.java`

Convert Focus, Modifier, and Targets rows to use:

- A trigger row: `LabelElement` (label) + `LabelElement` (value) + chevron glyph
- `.tooltip(...)` for truncated values
- Click opens a `DropdownListElement`

**New file:** `feature/reroll/ui/DropdownListElement.java`

Convert the dropdown overlay to use:

- `VerticalScrollClipContainer`-backed scroll list
- One `LabelElement` per visible item
- Host scrollbar rendering instead of hand-drawn up/down triangles
- `.tooltip(...)` on items for full text

Current code (`drawDropdown` in `RerollPanel.java:818-909`):

- Hand-drawn header with gold background
- Manual scroll arrows (drawTriangle)
- Manual item rects with hover highlighting
- Manual remove zone (16px right edge for Targets)

The dropdown list is rendered as a floating overlay below the panel. Since the panel
element grows its height when a dropdown is open (via `setHeight` + `requestLayout`),
the list can be a child element that becomes visible/hidden.

**Targets row extras:**

- "any"/"all" chip: small `ButtonElement` with `BUTTON_TOGGLE_ON`/`BUTTON_TOGGLE_OFF`
- "x" clear button: small `ButtonElement` with tooltip "Clear all targets"

**Deliverables:**

- [ ] Create `DropdownRowElement.java` (generic trigger row)
- [ ] Create `DropdownListElement.java` (scrollable list overlay)
- [ ] Replace three `drawXRow()` + `drawDropdown()` with element instances
- [ ] Wire dropdown selection to `RerollPanelState` mutations
- [ ] Wire targets chip toggle, clear button

#### 3e. Read-only rows

**New file:** `feature/reroll/ui/LabelRowElement.java`

Convert Range, Potential, Status, Counter rows to `LabelElement` instances:

- Range: read-only label, tooltip on truncation
- Potential: read-only label, color-coded (red at 0)
- Status: read-only label, tooltip on truncation, debounced if flicker observed
- Counter: read-only label, only visible during active run with reset on

These are the simplest conversions — just `LabelElement` with appropriate styling.

**Deliverables:**

- [ ] Create `LabelRowElement.java` (or use `LabelElement` directly)
- [ ] Replace four `drawXRow()` calls with label elements
- [ ] Wire tooltip on truncation

---

### Phase 4 — Color & glyph normalization

**Goal:** Replace all hardcoded color literals with `RerollTokens` constants and
alpha-encoded state.

**Changes to `RerollPanel.java` (now mostly gutted):**

- Delete all 13 `private static final int` color constants
- Replace with `RerollTokens.*` references in any remaining drawing code
- Hover state: `RerollTokens.HOVER_ALPHA << 24 | baseHue` instead of flat `HOVER_COLOR`
- Active/open state: `RerollTokens.ACTIVE_ALPHA << 24 | baseHue` instead of `HIGHLIGHT_COLOR`

**Glyph replacements:**

- Hand-drawn triangles (`drawTriangle`) to `⌄` chevron (`RerollTokens.CHEVRON_DOWN`)
- `[x]`/`[ ]` ASCII checkboxes to `BUTTON_TOGGLE_ON`/`BUTTON_TOGGLE_OFF` atlas
- `x` close glyph to small `ButtonElement` icon
- `*`/`>` selection markers to atlas check icon or `>` in `GOLD_ACCENT`

**Deliverables:**

- [ ] Delete hardcoded color constants from `RerollPanel`
- [ ] Update all remaining draw code to use `RerollTokens`
- [ ] Replace ASCII glyphs with atlas/text equivalents
- [ ] Verify alpha state encoding matches DESIGN_GUIDELINES section 5.2

---

### Phase 5 — Interaction conventions

**Goal:** Implement right-click clear, real text cursor, and debounced status.

**5a. Right-click = clear/reset:**

- Right-click on Min field: clear threshold (set `thresholdEnabled = false`)
- Keep explicit "Clear all targets" button for discoverability
- Right-click on any dropdown row: remove that target (Targets mode)

**5b. Real text cursor:**

- `NineSliceTextInputElement` provides vanilla `EditBox` cursor rendering for free
- Delete the manual `System.currentTimeMillis() / 500 % 2` blinking cursor code
- Delete the `pendingTooltip`/`drawTooltip`/`wrapText` custom tooltip system

**5c. Debounced status:**

- Audit the status line for flicker during fast ticks
- If flicker observed, apply 20-tick debounce per DESIGN_GUIDELINES section 7
- Store `firstStatusTick` and only update displayed status after 20-tick hold

**Deliverables:**

- [ ] Implement right-click clear on Min field
- [ ] Implement right-click remove on Targets dropdown items
- [ ] Remove manual cursor blinking code
- [ ] Remove custom tooltip system (`hoverTooltip`, `drawTooltip`, `wrapText`)
- [ ] Audit and debounce status line if needed

---

### Phase 6 — Config integration for panel presentation

**Goal:** Make panel visibility and position configurable, per DESIGN_GUIDELINES section 2.1.

**Changes to `VmaClientConfigs.java`:**

```java
// New config group: "Reroll Panel" (alongside existing "Auto Reroll")
builder.push("Reroll Panel");
    PANEL_ENABLED = builder
        .comment("Show the auto-reroll panel when the Artisan Station is open")
        .define("showPanel", true);
    PANEL_SIDE = builder
        .comment("Panel position: Auto (prefer left, fallback right), Left, or Right")
        .define("sidePreference", "Auto");
    PANEL_COMPACT = builder
        .comment("Compact mode hides optional rows (Range, Counter) for a denser panel")
        .define("compactMode", false);
builder.pop();
```

**New getters:**

```java
public static boolean isPanelEnabled() { return PANEL_ENABLED.get(); }
public static String panelSide() { return PANEL_SIDE.get(); }
public static boolean isPanelCompact() { return PANEL_COMPACT.get(); }
```

**Integration in `RerollPanelElement`:**

- `isVisible()` now checks `VmaClientConfigs.isPanelEnabled()` AND the in-memory
  `visible` flag (P-key toggle)
- Side preference overrides the left-then-right fallback logic in `.layout(...)`
- Compact mode hides Range and Counter rows via `RerollPanelLayout`

**Deliverables:**

- [ ] Add `Reroll Panel` config group with 3 entries
- [ ] Add getters to `VmaClientConfigs`
- [ ] Wire panel visibility to config + P-key toggle
- [ ] Wire side preference to layout logic
- [ ] Wire compact mode to hide optional rows
- [ ] Add plain-English comments to all config entries

---

### Phase 7 — QA pass against DESIGN_GUIDELINES section 15 checklist

Run the full checklist before considering the rewrite complete:

- [ ] Config-gated (visibility) with plain-English comments — Phase 6
- [ ] Reuses atlas regions/host font/host color instead of new one-off assets — Phases 2-4
- [ ] All positions derived from live parent bounds via `Spatials`/`.layout(...)` — verify
- [ ] Toggle affordances reuse `BUTTON_TOGGLE_ON`/`BUTTON_TOGGLE_OFF` — Phase 3a
- [ ] Themeable colors are config `Integer`/`enum`, not literals — Phase 4
- [ ] State-intensity maps to alpha — Phase 4
- [ ] New alert text debounces flicker — Phase 5c
- [ ] Injected members prefixed and `@Unique` — already true (`vma$`)
- [ ] Right-click reserved for clear/reset — Phase 5a
- [ ] Tooltips via declarative `.tooltip(...)` — Phase 3 (all elements)

---

## 4. New File Layout

```
feature/reroll/
  RerollPanelState.java          // UNCHANGED — pure state/model
  RerollPanelElement.java        // REWRITTEN — container element with child widgets
  RerollPanelLayout.java         // KEPT — row-offset constants, simplified hit-testing
  ui/
    RerollTokens.java            // NEW — shared color/spacing/glyph constants
    ToggleRowElement.java        // NEW — atlas toggle + label row (used 2x)
    StartStopButtonElement.java  // NEW — atlas button for start/stop
    StepperFieldElement.java     // NEW — "- [field] +" numeric stepper
    DropdownRowElement.java      // NEW — "label + value + chevron" trigger row (used 3x)
    DropdownListElement.java     // NEW — scrollable dropdown overlay
    LabelRowElement.java         // NEW — read-only label row (used 4x)
  ModifierCatalog.java           // UNCHANGED
  AutoRerollEngine.java          // UNCHANGED
  ArtisanStationScreenAccessor.java  // UNCHANGED
```

**Deleted files:**

- `RerollPanel.java` — entirely replaced by the new element classes + `RerollPanelState`
  (its public API methods like `currentSelection()`, `operations()`, `candidates()`,
  `stationGear()`, `currentTargetRange()`, `targetName()`, `formatDisplay()`,
  `stopReasonText()`, `truncate()` are migrated to `RerollPanelState` or utility methods
  on the new elements)

---

## 5. Migration of RerollPanel Public API

The following methods in `RerollPanel` are called by external code and must be preserved:

| Method                                             | Called by                              | Migration target                                       |
| -------------------------------------------------- | -------------------------------------- | ------------------------------------------------------ |
| `currentSelection()`                               | `AutoRerollEngine`, `/vma reroll`      | `RerollPanelState` (already has access to screen)      |
| `operations(screen)`                               | `RerollPanelState`, `AutoRerollEngine` | Move to `RerollPanelState` (static util)               |
| `candidates(gear, operation)`                      | `RerollPanelState`, `AutoRerollEngine` | Move to `RerollPanelState` (static util)               |
| `stationGear()`                                    | `RerollPanelState`, `AutoRerollEngine` | Move to `RerollPanelState` (static util)               |
| `currentTargetRange()`                             | `RerollPanelState`                     | Move to `RerollPanelState`                             |
| `targetName(id)`                                   | Dropdown rendering                     | `RerollPanelState`                                     |
| `formatDisplay(value, percent)`                    | Multiple                               | Static utility in `RerollTokens` or `RerollPanelState` |
| `stopReasonText(reason)`                           | Status display                         | Static utility in `RerollPanelState`                   |
| `truncate(text, max)`                              | Tooltip rendering                      | Static utility in `RerollTokens`                       |
| `isVisible()` / `setVisible()` / `toggleVisible()` | P-key toggle, element                  | `RerollPanelState` (already has these)                 |
| `isMinInputFocused()`                              | `ClientTickEvents`                     | `RerollPanelState` (already has this)                  |
| `isDropdownOpen()`                                 | `ClientTickEvents`                     | `RerollPanelState` (already has this)                  |
| `handleScroll(delta)`                              | `ClientTickEvents`                     | `RerollPanelState.scrollDropdown()`                    |
| `closeDropdown()`                                  | `ClientTickEvents`                     | `RerollPanelState` (already has this)                  |
| `acceptChar(c)` / `onKeyPressed(keyCode)`          | `ClientTickEvents`                     | `RerollPanelState` (already has these)                 |
| `computeWidth(...)`                                | `RerollPanelElement` layout            | Keep as static util in `RerollPanelLayout`             |

**Key insight:** `RerollPanelState` already has `setPanel(RerollPanel)` back-reference
for screen-dependent queries. After the rewrite, this back-reference is removed —
`RerollPanelState` gains direct static utility methods for screen queries (operations,
candidates, stationGear), eliminating the circular dependency.

---

## 6. What Explicitly Does Not Change

- **`ModifierCatalog`** — applicability guard, roll ranges, human names: untouched
- **`AutoRerollEngine`** — state machine, press/evaluate/stop: untouched
- **`ArtisanStationScreenAccessor`** — duck interface for attemptCraft: untouched
- **Reroll behavior config** — tick interval, roll gap, max rolls, sounds: untouched
- **Panel anchor/fallback algorithm** — left-then-right-then-shrink: untouched
- **Mixin registration** — `addElement(RerollPanelElement.create(screen))`: untouched
- **No gameplay/balance changes** — strictly rendering/interaction-layer rewrite

---

## 7. Suggested Sequencing

| Phase             | Depends on | Risk   | Testable independently               |
| ----------------- | ---------- | ------ | ------------------------------------ |
| 0 (tokens)        | None       | Low    | Yes — compile check                  |
| 1 (container)     | 0          | Medium | Yes — panel still draws via fallback |
| 2 (chrome)        | 1          | Low    | Yes — visual only                    |
| 3a (toggles)      | 1          | Low    | Yes — isolated on/off                |
| 3b (button)       | 1          | Low    | Yes — isolated start/stop            |
| 3c (stepper)      | 1          | Medium | Yes — keyboard routing must work     |
| 3d (dropdowns)    | 1          | High   | Yes — but most shared code           |
| 3e (labels)       | 1          | Low    | Yes — read-only                      |
| 4 (colors/glyphs) | 3          | Low    | Yes — visual only                    |
| 5 (interactions)  | 3          | Medium | Yes — right-click, cursor, debounce  |
| 6 (config)        | 0          | Low    | Yes — independent                    |
| 7 (QA)            | All        | —      | Final gate                           |

Each phase is shippable/testable on its own since the panel remains fully functional
throughout — the existing `RerollPanel` draw code runs as fallback until its replacement
is wired in, at which point the old code path is deleted.

---

## 8. Risk Assessment

| Risk                                                                                 | Mitigation                                                                                   |
| ------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------- |
| VH framework elements don't render above slot items                                  | Test z-order early (Phase 1); TAIL redraw kept as fallback until verified                    |
| `NineSliceTextInputElement` keyboard routing doesn't work from Screen-owned elements | Keep `ClientTickEvents` keyboard routing pointing at `TextInputElement` API                  |
| Atlas textures not available at runtime                                              | Verify with `javap` on the VH jar before committing to atlas usage                           |
| Dropdown overlay clips against panel bounds                                          | Use `VerticalScrollClipContainer` which handles this natively                                |
| Panel height changes cause layout thrashing                                          | Cache `totalHeight` and only call `requestLayout()` on change (already done in `syncSize()`) |

---

## 9. Verification Commands

After each phase:

```bash
.\gradlew.bat compileJava          # Compile check
.\gradlew.bat build --console=plain  # Full build
```

Final verification:

1. Install built jar into Prism instance
2. Open Artisan Station with gear
3. Verify: panel appears left of station (right fallback works)
4. Verify: each row renders with atlas textures, not flat fills
5. Verify: dropdowns open/close, scroll, select items
6. Verify: Min field accepts typed input, cursor blinks, right-click clears
7. Verify: toggles flip config, Start/Stop button works
8. Verify: status line updates during rolls, no flicker
9. Verify: panel visibility toggle via P-key and config
10. Verify: compact mode hides Range/Counter rows
