# Auto-Reroll Panel — Fix & Completion Plan (integrated)

Integrates the two prior review passes (own audit + Claude Code review) into one
execution plan. Priority follows Claude Code's ordering: P0 regressions first,
then P1 correctness, then the remaining Phase 3 conversions and polish.

## Status

| Phase | Item | Status |
| ----- | ---- | ------ |
| 0.1   | Anchor `anchorRow` helper + fix 5 child row positions | Done (commit `938bc567` +) |
| 0.2   | Z-order: re-render full element before the tooltip pass | Done |
| 0.3   | Host API verification (done at bytecode level) + compile gate | Done (compile passes) |
| 1     | compactMode removal; debounce ticks; P-key guard; config comments | Done |
| 2     | Non-numeric Min guard; canStart ops; Start tooltip; candidates memo; doc drift | Done (doc drift: value kept, question recorded in DEC-032) |
| 3     | Row conversions (Min stepper → real elements; DropdownRowElement; dropdown internals; tooltip unification) | Partial: 3.1 steppers + 3.2 done; 3.3 remains |
| 4     | Glyph cleanup; per-row QA checklist; DEC-032 docs | Partial (⌄ chevron; DEC-032 recorded; `[x]`/`[ ]` kept as text — DEC-032d) |

## Baseline findings (bytecode-verified against `libs/the_vault-...jar`)

- `AbstractSpatialElement.layoutSelf` runs `world.set(fixed)` →
  `world.translateXYZ(parent)` → **then** the user layout lambda, which
  overwrites the position. Children writing `world.positionXY(0, localY)` land
  at absolute screen `(0, localY)` — the panel children render pinned to the
  top-left of the screen, not inside the panel. Layout lambdas must fold in
  `parent.x()/parent.y()` explicitly.
- `AbstractElementContainerScreen.m_6305_` render order:
  `renderElements → renderSlotItems → renderDebug → renderTooltips`.
- All referenced host APIs exist: `BUTTON_TOGGLE_ON/OFF_HOVER`,
  `BUTTON_TOGGLE_ON/OFF_TEXTURES`, `BUTTON_EMPTY_16_TEXTURES`
  (`ButtonElement$ButtonTextures` = record `(button, buttonHover, buttonHeld,
  buttonDisabled)`), `TooltipDirection.LEFT`.

## 0. P0 — regressions

### 0.1 Child rows render at the wrong screen position

Add one anchoring helper on `RerollPanelElement` and use it for every simple
full-width row:

```java
private static void anchorRow(AbstractSpatialElement<?> child, int localY) {
    child.layout((screenSize, gui, parent, world) -> {
        world.positionXY(parent.x(), parent.y() + localY);
        world.width(parent.width());
    });
}
```

Apply to: `rerollToggle`, `resetToggle`, `statusRow`, `counterRow`.
`startButton` keeps an inset lambda (`+PAD_X`, `width - 2*PAD_X`) but with the
same `parent.x()/parent.y()` folding.

**Mandatory for every future row conversion** — no fresh `.layout(...)` lambdas
outside the anchor helpers, to prevent this bug recurring row-by-row.

### 0.2 Converted rows lost the z-order fix

The TAIL-of-`m_6305_` hook only re-runs `panel.draw(...)` (the legacy hand-drawn
rows); converted element children are painted over by slot items. Fix: inject at
`renderTooltips` HEAD (instead of `m_6305_` TAIL — a TAIL re-render would cover
the tooltips drawn just before it) and re-invoke the full element render:

```java
@Inject(method = "renderTooltips", at = @At("HEAD"), require = 0)
private void vma$renderPanelOnTop(PoseStack poseStack, int mouseX, int mouseY, CallbackInfo ci) {
    try {
        RerollPanelElement element = RerollPanelElement.getInstance();
        if (element != null && element.isVisible()) {
            element.render(elementRenderer, poseStack, mouseX, mouseY, partialTick);
        }
    } catch (Throwable t) {
        VaultModifierAlerts.LOGGER.warn("[VMA] Failed to re-draw auto-reroll panel on top", t);
    }
}
```

Note: the second per-frame render also runs `StatusRowElement.computeStatusInfo()`
twice per frame — the Phase 1 tick-based debounce fix accounts for this.

### 0.3 Host API surface

All symbols verified present in the jar (see baseline). Final gate is a real
compile (`./gradlew compileJava`).

## 1. P1 — config and timing correctness

- **compactMode**: remove `REROLL_PANEL_COMPACT_MODE` config entry and
  `isRerollPanelCompactMode()` — dead config with no observable effect. Re-wire
  only when range/counter rows are converted to elements.
- **Status debounce**: `StatusRowElement` counts render frames, not game ticks
  (and the 0.2 double-render doubles that). Make it tick-based
  (`level.getGameTime()` delta or tick counter), name the constant
  `STATUS_DEBOUNCE_TICKS`.
- **P-key guard**: `ClientTickEvents.onScreenKeyPressed` toggles the panel on
  P without the `isMinInputFocused()` guard the tick path has; typing P in the
  Min field must not hide the panel.
- **Config comments**: add `.comment(...)` to every `[Auto Reroll]` config
  entry (§8.1 of the design guidelines).

## 2. P2 — correctness gaps

- Min field/`-`/`+` disabled when the target range is non-numeric (infinite
  chase risk); structurally resolved by the 3.1 numeric `TextInputElement`.
- `canStart()` requires non-empty operations (no dead Start button).
- Tooltip on the Start/Stop button.
- Memoize per-frame candidate queries.
- Doc drift: `tickInterval` default 4 vs plan/DEC-025 (15).

## 3. P3 — remaining Phase 3 row conversions (each QA'd before the next)

- **3.1 Min stepper**: two small `ButtonElement`s (`BUTTON_EMPTY_16_TEXTURES`)
  + numeric-filtered `TextInputElement<T>` subclass; host cursor replaces the
  manual blink timer. Preserve right-click clear, Enter/Esc commit, `.` once.
  **Done in part**: the `-`/`+` steppers are now `StepperButtonElement`
  (`ButtonElement` + atlas textures, 12x12, disabled when non-numeric or
  auto-reroll off); legacy button fills removed from `drawMinRow`. The field
  **cannot** use `TextInputElement`: the framework does not route keyboard/char
  events to owned elements (input is screen-event-driven via
  `ClientTickEvents` + `RerollPanelState` by design). Keep the state-machine
  field (blink cursor, right-click clear, Enter/Esc, `.` once stay).
  RegionAt 16px stepper zones retained — with 12px buttons the outer 2px
  still step via `handleClick`, preserving today's effective hit area.
- **3.2 Focus/Modifier/Targets rows**: one parameterized `DropdownRowElement`;
  Targets chip (`any`/`all`) + clear `x` as right-edge child buttons; triangle →
  `⌄` chevron. **Done** (`ui/DropdownRowElement.java`): rows are real container
  children anchored via `anchorRow` at `TITLE_H + n*ROW_H`; chip/clear remain
  zones inside the row's `onMouseClicked`/`onHoverTooltip` (identical geometry
  to `RerollPanelLayout.regionAt`); legacy `drawRow`/`drawModifierRow`/
  `drawTargetsRow` and their `handleClick` cases + dead `HitType`s removed;
  truncated-value and clear-zone tooltips are now declarative. **QA still
  required in-game** (side flips, resize, click-outside, tooltip overlap).
- **3.3 `DropdownListElement` internals**: migrate to
  `VerticalScrollClipContainer` + per-row elements (host scrollbar math).
  **Done**: `DropdownListElement` is now a `VerticalScrollClipContainer`
  (header padding + default INSET_GREY background, host scrollbar at the
  right edge, clipped elastic inner container); each item is a real
  `DropdownItemRowElement` (`AbstractSpatialElement` + `IRenderedElement` +
  `IGuiEventElement`) sized `innerWidth()`×`DROPDOWN_ITEM_H`, rebuilt only
  when the item count changes, with data queried live per frame (name, range,
  `>`/`*`/`[x]`/`[ ]` markers, remove zone + hover `x`). Scroll triangles and
  the hand-rolled `dropdownScroll`/`scrollDropdown`/`clampDropdownScroll`
  math are deleted; scrolling is the host scrollbar (`onMouseScrolled` wheel
  at any cursor position over the open dropdown, Up/Down keys via
  `scrollDropdownBy(int)` stepping `1/range` of the normalized value, scroll
  reset on mode change/reopen). Header click closes the dropdown (legacy
  triangles were the only scroll affordance; wheel+scrollbar replace them).
  `RerollPanelLayout`'s unused `dropdownScroll` ctor param removed.
- **3.4 Tooltip unification**: delete the manual popover
  (`hoverTooltip`/`drawTooltip`/`pendingTooltip`, `TOOLTIP_BG`) once no
  hand-drawn rows remain. **Done**: the only remaining popover user was the
  hand-drawn Range row — now a `RangeRowElement` (same live text + truncation,
  declarative `onHoverTooltip`); the whole popover machinery and
  `TOOLTIP_BG` are deleted. The stale status-text debounce in
  `computeStatusInfo` (`STATUS_DEBOUNCE_TICKS` commit `7f2a5dd3`) is removed
  too — see QA round 2 item 3.

## 4. P4 — glyph cleanup, process, docs

- `[x]`/`[ ]` → small atlas check/cross idiom; triangles → `⌄` chevron;
  `>`/`*` stay as text glyphs (no atlas equivalent).
  **Superseded by DEC-032d** (2026-08-16): `[x]`/`[ ]` stay text glyphs too —
  no verified host atlas check/cross icon to swap to.
- Per-row QA checklist (in force from 0.1): LEFT + RIGHT panel side; GUI-scale
  resize; row renders inside panel bounds in order at correct width; click at
  rendered location works / click at screen top-left does nothing; slot
  tooltip overlap keeps panel on top.
- ~~`DECISIONS.md` DEC-032; supersede notes in `F3_AUTO_REROLL_PLAN.md` §4.4.~~
  **Done (2026-08-16)**: DEC-032 recorded in `DECISIONS.md` (index + entry);
  §4.4 supersede note added to `F3_AUTO_REROLL_PLAN.md` (incl. `RangeRowElement`
  tooltip, state-driven Min field, tickInterval `4`).

## 5. DEC-032 — decisions log

Recorded here first (2026-08-16) because the earlier plans referenced a
`docs/DECISIONS.md` that does not exist; the actual decision log is
`DECISIONS.md` at the repo root. DEC-032 was moved into `DECISIONS.md` the same
day — this section is retained as the working record.

> **Superseded:** recorded in `DECISIONS.md` (DEC-032, index row + full entry).
> This log stays as the in-context reference.

- **DEC-032a `tickInterval` default**: stays `4` (owner-confirmed 2026-08-16).
  The "15" value from DEC-025/F3-plan could not be found in any in-repo doc;
  no change without evidence. **Resolved: keep 4.**
- **DEC-032b Min field stays state-driven**: `TextInputElement` was
  rejected — the framework does not route keyboard/char events to owned
  elements (input path is screen-event-driven by design). Blink cursor,
  right-click clear, Enter/Esc commit, `.` once remain in `RerollPanelState`.
- **DEC-032c stepper hit zones**: 16px `regionAt` zones kept alongside 12px
  button elements; outer 2px on each side still step via `handleClick`,
  preserving today's effective hit area (bigger than the drawn button).
- **DEC-032d `[x]`/`[ ]` markers**: kept as text glyphs in status strings and
  dropdown items; no verified host atlas check/cross icon to swap to. Matches
  the existing `>`/`*` text-glyph pattern.
- **DEC-032e `compactMode`**: removed — dead config with no observable effect
  (both review passes recommended removal).

## 6. QA status — in-game verification still required

### QA round 1 (owner, 2026-08-16) — issues found and fixed

1. **Hover/open row fill covered the 1px side borders** — fills now inset by 1px
   (`x+1 .. x+width-1`) in `DropdownRowElement` (row + Targets chip) and
   `ToggleRowElement`.
2. **Slot items painted over the panel** — root cause: `renderSlotItems`
   delegates to vanilla `AbstractContainerScreen.render`, which draws item
   icons at z=100 (1.18.2 `ItemRenderer`). The panel re-render now runs at
   z=200 in the mixin (`poseStack.translate(0,0,200)`), below tooltips (z=400).
3. **`-`/`+` steppers bled outside their 12px box / next row** — host
   `ButtonElement.render` blits the texture at its native 16px. `StepperButtonElement`
   now stretches the selected texture across its own bounds; glyph color fixed
   (TEXT_DEFAULT enabled / TEXT_DISABLED disabled).
4. **Start/Stop button only 16px** — same native-blit cause; now full-width row
   (drops the PAD_X inset) rendered 9-sliced
   (`NineSlice.TextureRegion.of(region.atlas(), region.resourceLocation(), slice(4,4,4,4))`).
5. Slot **tooltips** intentionally remain above the panel (z=400) — vanilla
   behavior, not a bug.

### QA round 2 (owner, 2026-08-16) — issues found and fixed

1. **Scrollbar handle shown even when the list fits** (2/4 items) — root cause:
   `VerticalScrollbarElement.render` always draws a handle
   (`SCROLLBAR_HANDLE` enabled / `SCROLLBAR_HANDLE_DISABLED` when not); the
   VSCC disables it correctly on non-overflow, but the disabled handle texture
   still renders. The dropdown now hides the scrollbar element entirely when it
   is not enabled (render-time sync of `setVisible` to `isEnabled`).
2. **`-`/`+`, Start/Stop and the toggle icons took more than their row's space**
   — root cause: `ButtonElement`'s ctor ignores the passed spatial and re-sizes
   the element to the texture's native 16x16, and the layout lambdas only set
   position. The stepper and Start/Stop layout lambdas now set explicit sizes
   (12x12 and full-width x `BUTTON_H`); the toggle icons are blitted at 12x12
   (IPosition/ISize overload) instead of native 16px in a 14px row.
3. **Status text stayed on "Rolling #0 - 0/2 met" during a run** — **root
   cause found and fixed**: `computeStatusInfo` still ran the leftover tick
   debounce from `7f2a5dd3` (`STATUS_DEBOUNCE_TICKS`): the displayed status
   only advanced after the text stayed identical for 4 consecutive game
   ticks, and during a run the text changes every roll, so the counter kept
   resetting and the display froze on the first committed line until the run
   paused (e.g. at a potential reset). The debounce is removed —
   `computeStatusInfo` returns the live status. `runningStatus` additionally
   appends `(reset x N)` when the engine auto-reset potential this session
   (the earlier "reset-loop" hypothesis is no longer required to explain the
   freeze).

### Open in-game QA (after the fixes above)

Run the per-row checklist (§4): LEFT + RIGHT panel side, GUI-scale resize, row
renders inside panel bounds in order at correct width, click at rendered
location works / click at screen top-left does nothing, slot tooltip overlap
keeps panel on top, dropdown open/close on all three rows, stepper + Min field
interactions (numeric and non-numeric targets), and the start/stop button
tooltip. New for 3.3: dropdown items are clipped/scrollable via the host
scrollbar (drag handle, wheel anywhere over the open dropdown, Up/Down keys),
scroll resets on mode change and on reopen, click-outside closes, and the
remove-zone hover `x` still works at the row's right edge.

All fixes through `09bc9ce5` are build-validated but **not yet verified
in-game**; the QA round 2 fixes above are build-validated and await the next
QA session.

### QA round 3 (owner, 2026-08-17) — issues found and fixed

1. **Panel inputs active during a reroll run** — clicking Focus/Modifier/Targets
   rows, dropdown items, Min steppers/field, stop-condition chip, clear-targets, or
   auto-reset toggle while a run is in progress could change the selection mid-run.
   Fix: `RerollPanelState` mutators guarded by `selectionLocked()` (stops the engine
   on any attempted mutation); `DropdownRowElement`, `DropdownItemRowElement`,
   `RerollPanel.handleClick`, and stepper disabled lambdas block input while
   `AutoRerollEngine.isRunning()`. Auto-reset toggle blocked; auto-reroll toggle
   stays live (OFF = stop switch). Visual: rows grey out while running.

2. **Effect cloud modifiers show "Effect Cloud"** — `EffectCloudAttribute$Reader`
   hardcodes "Effect Cloud" / "Effect Cloud when Hit"; `CloudConfig.tooltipDisplayName`
   is private with no getter. Fix: `ModifierCatalog.displayName` detects
   `EffectCloudAttribute.CloudConfig` tier configs and returns humanized identifier
   (e.g. `mod_healing_cloud` → "Healing Cloud", `crafted_fear_cloud` → "Crafted Fear
   Cloud", `regencloud` → "Healing Cloud" manual mapping). Full inventory of 10
   identifiers verified from all 6 gear_modifiers JSONs. See DEC-033.

3. **Start/Stop button touches panel edges** — full-width layout had no horizontal
   breathing space. Fix: button spatial inset by `PAD_X` (8px) on each side in the
   layout lambda; 9-slice renders across the element's bounds so the inset is visual.

### Open in-game QA (after the fixes above)

Run the per-row checklist (§4): LEFT + RIGHT panel side, GUI-scale resize, row
renders inside panel bounds in order at correct width, click at rendered location
works / click at screen top-left does nothing, slot tooltip overlap keeps panel on
top, dropdown open/close on all three rows, stepper + Min field interactions
(numeric and non-numeric targets), and the start/stop button tooltip. New for 3.3:
dropdown items are clipped/scrollable via the host scrollbar (drag handle, wheel
anywhere over the open dropdown, Up/Down keys), scroll resets on mode change and on
reopen, click-outside closes, and the remove-zone hover `x` still works at the row's
right edge. New for QA round 3: verify that panel inputs are locked while a run is
in progress (steppers greyed, dropdown items non-clickable, auto-reset toggle
non-clickable, Min field/steppers non-functional), effect cloud names show per-type
labels (Healing Cloud, Fear Cloud, Poison Cloud, Chilling Cloud, Crafted variants,
and Regen Cloud on drink items), and the Start/Stop button has visible side padding.

All fixes through `be3d89d8` are build-validated but **not yet verified
in-game**; the QA round 3 fixes above are build-validated and await the next
QA session.

## Commit discipline

Commit after each concrete phase once `./gradlew compileJava` (and `build`
where noted) passes. Conventional commit style (`fix(reroll): ...`,
`chore(reroll): ...`) matching the repo history.

Commits (newest first):
- `be3d89d8` fix(reroll): inset Start/Stop button with PAD_X side breathing space
- `26105b9d` fix(reroll): show effect cloud names (Healing Cloud, Fear Cloud, etc.)
- `618f957a` fix(reroll): lock panel inputs while reroll run is in progress
- `c17e3699` fix(reroll): vma reroll disable now hides the panel too
- `56ec767a` docs(reroll): record DEC-032 decisions, supersede F3 plan notes
