# Reroll GUI Audit — Design Guidelines Compliance Plan

## Summary

Audit of the auto-reroll GUI against `docs/DESIGN_GUIDELINES.md` found **14 issues** across layout, rendering, color, typography, and anti-pattern categories. None are regressions; all are gaps relative to the design system. Organized by severity.

---

## P0 — Rendering Correctness (breaks visual layering or depth)

### 1. Missing RenderSystem depth management in draw primitives
**Files:** `RerollPanel.java:537-547`
**Guideline:** §10.2, §14.2
**Issue:** `drawString()`, `drawCentered()`, `drawRight()` call `font.draw()` without `RenderSystem.disableDepthTest()` / `RenderSystem.enableDepthTest()` wrapping. The mixin z-translate (`poseStack.translate(0,0,200)`) masks this, but other mods or future changes could expose it.
**Fix:** Add `RenderSystem.disableDepthTest()` before and `RenderSystem.enableDepthTest()` after each `font.draw()` call in the three static draw helpers.

### 2. Missing RenderSystem blend management in panel frame
**Files:** `RerollPanel.java:380-389`
**Guideline:** §10.3
**Issue:** `drawPanelFrame()` uses `GuiComponent.fill()` without ensuring blend is enabled. Works because the framework enables it, but the design guideline requires explicit `RenderSystem.enableBlend()` + `blendFunc()` + `setShaderColor()` + cleanup.
**Fix:** Add `RenderSystem.enableBlend(); RenderSystem.blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA);` at the start and `RenderSystem.disableBlend();` + `RenderSystem.setShaderColor(1,1,1,1);` at the end of `drawPanelFrame()`.

---

## P1 — Layout Violations (hardcoded positions, not parent-relative)

### 3. Hardcoded magic x-offsets in drawMinRow
**Files:** `RerollPanel.java:400, 110`
**Guideline:** §4 ("Never hardcode absolute screen pixel coordinates")
**Issue:** `x + 22` for the "Min" label, and `x + 16` / `x + width - 16` in `RerollPanelLayout.regionAt()` for stepper hit zones are magic numbers.
**Fix:** Add layout constants to `RerollPanelLayout`:
- `MIN_LABEL_X = 22`
- `STEPPER_ZONE_W = 16`
Update `drawMinRow()` and `regionAt()` to reference these constants.

### 4. Hardcoded magic x-offsets in drawPotentialRow
**Files:** `RerollPanel.java:431, 433`
**Guideline:** §4
**Issue:** Uses `RerollPanelLayout.PAD_X` (correct) but the "rolls" text positioning is implicit (right-aligned via `drawRight()`). Not a violation per se, but the y-offset `+ 3` is repeated everywhere without a constant.
**Fix:** Add `TEXT_V_OFFSET = 3` to `RerollPanelLayout` and use it in all draw calls that offset y by 3px.

### 5. Repeated `y + 3` vertical text offset across all widgets
**Files:** All `ui/*.java` files — `DropdownRowElement.java:97,106,108,115,121`, `DropdownItemRowElement.java:79,85,92,96,100`, `ToggleRowElement.java:60`, `RangeRowElement.java:46`, `StatusRowElement.java:48`, `CounterRowElement.java:37`, `StartStopButtonElement.java:65`, `StepperButtonElement.java:42`, `DropdownListElement.java:89`
**Guideline:** §12 (Design Tokens), §4
**Issue:** Every `font.draw()` call uses `y() + 3` as the text baseline offset. This is a de facto design token that should be a named constant.
**Fix:** Add `TEXT_BASELINE_OFFSET = 3` to `RerollPanelLayout` and replace all `y() + 3` / `y + 3` / `layout.minY + 3` occurrences with the constant.

### 6. DropdownListElement header drawn at raw y without clip awareness
**Files:** `DropdownListElement.java:89-90`
**Guideline:** §4
**Issue:** The header text and gold top rule are drawn at `y()` (the container's top), but the scroll clip starts below the header padding (`DROPDOWN_HEADER_H`). If the scroll container clips the header, it could be cut off.
**Fix:** Verify the `Padding.of(0, 0, DROPDOWN_HEADER_H, 0)` at line 39 correctly excludes the header from clipping. If not, move the header render into a non-clipped zone or draw it before `super.render()` with a separate clip guard.

---

## P2 — Color System Violations (hardcoded colors)

### 7. Non-configurable hardcoded colors in RerollTokens
**Files:** `RerollTokens.java:23-24, 29-34, 41-42`
**Guideline:** §5.2 ("All themeable colors are Integer config values with an enum of curated presets")
**Issue:** `TEXT_MUTED`, `TEXT_DISABLED`, `ROW_HOVER`, `ROW_OPEN`, `INPUT_BG`, `INPUT_FOCUS`, `DROPDOWN_BG`, `DROPDOWN_REMOVE_HOVER` are `static final` hardcoded hex values. While some are arguably non-themeable, the design system requires config-driven colors for any color the player might want to re-theme.
**Fix:** Add config entries for the most player-relevant subset:
- `TEXT_MUTED` — configurable (players may want higher contrast)
- `TEXT_DISABLED` — configurable
- `ROW_HOVER` — configurable
- `ROW_OPEN` — configurable
- `INPUT_BG` / `INPUT_FOCUS` — leave hardcoded (input chrome, not themeable)
- `DROPDOWN_BG` — leave hardcoded
- `DROPDOWN_REMOVE_HOVER` — leave hardcoded

Add corresponding fields to `VmaClientConfigs` under the `[Reroll Panel]` group with comments.

---

## P3 — Typography & Text Styling

### 8. Labels drawn with color instead of ChatFormatting
**Files:** `DropdownRowElement.java:97`, `RerollPanel.java:400`, `RangeRowElement.java:46`, `StatusRowElement.java:48`, `CounterRowElement.java:37`
**Guideline:** §5.4 ("For text components... ChatFormatting.GRAY // Neutral/label")
**Issue:** Label text ("Min", "Range:", row labels) uses `RerollTokens.TEXT_MUTED` (a color int) instead of `ChatFormatting.GRAY`. The design guideline convention is to use `ChatFormatting` for semantic emphasis.
**Fix:** For label text that's purely informational, build a `MutableComponent` with `.withStyle(ChatFormatting.GRAY)` and draw it with `font.draw(poseStack, component, x, y, 0xFFFFFF)`. Or, if keeping the int-color approach for simplicity, add a comment noting the deviation and ensure the fallback color matches `ChatFormatting.GRAY` equivalent.

### 9. Panel title "Auto-Reroll" not using ChatFormatting.BOLD
**Files:** `RerollPanel.java:322`
**Guideline:** §5.4, §6
**Issue:** Title is drawn as plain text with `ACCENT_GOLD` color. Design guideline §3.2 says dialog titles use bold formatting. The title should use `ChatFormatting.BOLD` for emphasis.
**Fix:** Build a `MutableComponent` with `.withStyle(ChatFormatting.BOLD)` for the title, or draw it with `font.draw()` and the gold color but ensure the visual weight matches bold.

### 10. Dropdown chevron not using the design system glyph convention
**Files:** `DropdownRowElement.java:108`
**Guideline:** §3.8 ("A small dropdown/expand affordance uses a single scaled-up chevron glyph (⌄), drawn in ChatFormatting.DARK_GRAY, hand-centered against a fixed icon size (16px at 4x internal scale)")
**Issue:** The chevron `\u2304` is drawn at normal font scale in the default text color. The design guideline calls for a scaled-up chevron in `DARK_GRAY`.
**Fix:** Draw the chevron with `poseStack.pushPose()` + `poseStack.scale()` to render it at ~1.5x, using `ChatFormatting.DARK_GRAY` color (`0xFF555555`), then `popPose()`. This matches the disclosure affordance convention.

---

## P4 — Interaction & Anti-Pattern Gaps

### 11. Config paths are not named constants
**Files:** `VmaClientConfigs.java:92-152`
**Guideline:** §8 ("Naming pattern for path constants: `public static final String X = 'Human Readable Label';`")
**Issue:** Config path strings like `"enabled"`, `"side"`, `"panelBgColor"` are inline string literals, not named constants.
**Fix:** Add constants to a `ConfigPaths` inner class or similar:
```java
public static final String AUTO_REROLL_ENABLED = "enabled";
public static final String AUTO_REROLL_TICK_INTERVAL = "tickInterval";
// etc.
```
Then reference them in the builder calls.

### 12. Missing debounce on status row rendering
**Files:** `StatusRowElement.java:35-48`
**Guideline:** §7 ("Debounce transient state before surfacing it as UI")
**Issue:** The status row re-computes and re-renders every frame via `computeStatusInfo()`. Rapid state changes (gear insertion, target toggling) could cause flickering. The design guideline calls for debouncing transient state.
**Fix:** Add a frame-count debounce: only update `lastInfo` every N frames (e.g., 5 frames = ~250ms) when the status text is changing rapidly. Or, at minimum, cache the previous status text and only update when the text actually changes (already partially done — `lastInfo` is set each frame, but there's no smoothing).

### 13. `font.draw()` calls lack explicit shadow parameter
**Files:** All `font.draw()` calls in `RerollPanel.java`, all `ui/*.java` files
**Guideline:** §10.1 ("Text with Shadow (Most Common)... font.drawInBatch(..., true, ...)")
**Issue:** The code uses `font.draw()` (which defaults to shadow=true) everywhere. This is functionally correct but doesn't match the explicit pattern in §10.1 which uses `font.drawInBatch()` for full control. Not a bug, but a style gap.
**Fix:** No change needed — `font.draw()` is the standard vanilla shorthand and is equivalent. Document this as an intentional simplification.

---

## P5 — Checklist Compliance (§15)

| Checklist Item | Status | Notes |
|---|---|---|
| Gated behind ForgeConfigSpec | ✅ | `VmaClientConfigs.isAutoRerollEnabled()` / `isRerollPanelEnabled()` |
| Config grouped under player-facing category | ✅ | "Auto Reroll", "Reroll Panel" |
| Reuses existing atlas regions / host font / host color | ⚠️ | Uses `ScreenTextures.BUTTON_TOGGLE_*` and `BUTTON_EMPTY_16` but custom fill-based panel |
| Positions relative to parent GUI bounds | ⚠️ | Layout constants are relative, but magic offsets in draw code (Issues #3-5) |
| Toggle uses BUTTON_TOGGLE_ON/OFF pair | ✅ | `ToggleRowElement` uses atlas toggles |
| Themeable colors stored as config Integer | ⚠️ | Main colors yes, secondary colors hardcoded (Issue #7) |
| State-intensity maps to alpha for overlays | ❌ | No alpha-based state overlays; uses solid color fills |
| Alert/HUD text debounced | N/A | No HUD alert in reroll panel |
| Injected members prefixed `vma$` + `@Unique` | ✅ | Mixin uses `vma$` prefix |
| Right-click follows clear convention | ✅ | Right-click on min field clears threshold |
| Tooltip via declarative `.tooltip()` API | ✅ | `DropdownRowElement`, `RangeRowElement`, `StatusRowElement`, `StartStopButtonElement` all use declarative tooltips |

---

## Implementation Order (All P0-P4)

| Phase | Issues | Files Changed | Estimated Effort |
|---|---|---|---|
| P0: Rendering correctness | #1, #2 | `RerollPanel.java` | Small |
| P1: Layout constants | #3, #4, #5, #6 | `RerollPanelLayout.java`, `RerollPanel.java`, all `ui/*.java` | Medium |
| P2: Config-driven colors | #7 | `RerollTokens.java`, `VmaClientConfigs.java` | Small |
| P3: Typography | #8, #9, #10 | `RerollPanel.java`, `DropdownRowElement.java` | Small |
| P4: Config constants + debounce | #11, #12 | `VmaClientConfigs.java`, `StatusRowElement.java` | Small |
| P5: Build + lint verification | — | Run build | — |

## Status: COMPLETED ✓

All 14 issues (P0–P4) fixed. Build passes clean.

---

## Files To Modify

| File | Issues |
|---|---|
| `feature/reroll/RerollPanel.java` | #1, #2, #3, #4, #5, #8, #9 |
| `feature/reroll/RerollPanelLayout.java` | #3, #4, #5 |
| `feature/reroll/ui/RerollTokens.java` | #7 |
| `feature/reroll/ui/DropdownRowElement.java` | #5, #8, #10 |
| `feature/reroll/ui/DropdownItemRowElement.java` | #5, #8 |
| `feature/reroll/ui/ToggleRowElement.java` | #5 |
| `feature/reroll/ui/RangeRowElement.java` | #5, #8 |
| `feature/reroll/ui/StatusRowElement.java` | #5, #8, #12 |
| `feature/reroll/ui/CounterRowElement.java` | #5, #8 |
| `feature/reroll/ui/StartStopButtonElement.java` | #5 |
| `feature/reroll/ui/StepperButtonElement.java` | #5 |
| `feature/reroll/ui/DropdownListElement.java` | #5, #6 |
| `config/VmaClientConfigs.java` | #7, #11 |
