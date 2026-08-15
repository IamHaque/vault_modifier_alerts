# QOLHunters UI Design Guidelines

### A reverse-engineered design system for Vault Hunters QoL mods

Source analyzed: `github.com/IridiumIO/QOLHunters` (Forge mod, MC 1.18.2, Vault Hunters 3rd Edition)

---

## Table of Contents

- [0. What This Document Is](#0-what-this-document-is)
- [1. Architectural Foundation](#1-architectural-foundation)
- [2. Core Design Philosophy](#2-core-design-philosophy)
- [3. Component-Level Guidelines](#3-component-level-guidelines)
- [4. Layout & Spatial System](#4-layout--spatial-system)
- [5. Color System](#5-color-system)
- [6. Typography](#6-typography)
- [7. Interaction & State Conventions](#7-interaction--state-conventions)
- [8. Configuration Screen Conventions](#8-configuration-screen-conventions)
- [9. Code & Naming Conventions](#9-code--naming-conventions)
- [10. Rendering Patterns](#10-rendering-patterns)
- [11. Performance Patterns](#11-performance-patterns)
- [12. Design Tokens](#12-design-tokens)
- [13. Code Templates](#13-code-templates)
- [14. Anti-Patterns & Pitfalls](#14-anti-patterns--pitfalls)
- [15. Checklist — Adding a New QoL Feature](#15-checklist--adding-a-new-qol-feature)
- [16. How to Extend This System](#16-how-to-extend-this-system)
- [Appendix: Source References](#appendix--source-references)

---

## 0. What This Document Is

This is **not** a UI toolkit spec for a mod that draws its own screens from scratch. QOLHunters is a
**client-side Mixin mod** that surgically patches the host game's (Vault Hunters / "the_vault") existing
GUI framework. Its "design system" is really a set of **conventions for extending someone else's design
system without breaking it** — visually, structurally, and behaviorally.

The guidelines below capture those conventions so they can be reused consistently in future Vault Hunters
QoL mods, whether or not those mods touch QOLHunters' code directly.

---

## 1. Architectural Foundation

### 1.1 The Host Framework QOLHunters Builds On

Vault Hunters ships its own internal GUI framework (`iskallia.vault.client.gui.framework.*`) that QOLHunters
treats as the source of truth for look & feel. Key primitives observed in use:

| Primitive                                                    | Role                                                                                                                                                          |
| ------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AbstractElementContainerScreen<T>`                          | Base class for composable screens; holds an `elementStore` of GUI elements                                                                                    |
| `ISpatial` / `Spatials.positionXY(x, y).size(w, h)`          | Declarative layout — position + size as a value object, not manual x/y math                                                                                   |
| `.layout((screen, gui, parent, world) -> ...)`               | A lambda that resolves an element's final position **relative to the screen's own bounds** at layout time (`world.translateXY(gui)`, `gui.right() + 5`, etc.) |
| `ButtonElement<T>`                                           | Standard clickable element bound to a `ScreenTextures` atlas region                                                                                           |
| `TextInputElement<T>`                                        | Standard text box element (search boxes extend this)                                                                                                          |
| `ScrollableContainer` / `VerticalScrollClipContainer`        | Standard scrollable list/description containers                                                                                                               |
| `TooltipContainerElement`                                    | A dedicated element that renders the hovered-item tooltip panel                                                                                               |
| `ITextureAtlas` / `TextureAtlasRegion` / `ModTextureAtlases` | Sprite-atlas system; every button/icon/panel piece is a named region on a shared PNG atlas, not a loose texture file                                          |
| `ScreenTextures` (framework)                                 | A static catalogue of pre-defined atlas regions (`BUTTON_TOGGLE_ON`, `BUTTON_RELIC_TEXTURES`, `CYCLE`, etc.) — the "component library" of textures            |

**Note:** QOLHunters also has its own `features/bettercheststats/ScreenTextures.java` for feature-specific icons (e.g. `ENIGMA_CHEST_SMALL_ICON`). This is distinct from the framework's `iskallia.vault.client.gui.framework.ScreenTextures`.

### 1.2 QOLHunters' Role: Mixin-Based Extension, Not Replacement

Every visual change is applied via `@Mixin` classes that:

1. Target a specific vanilla-host screen/element/dialog class (e.g. `AbilityDialog`, `CardDeckScreen`, `GreedTraderScreen`, `PrestigePowerWidget`).
2. `@Inject` at a precise point (`HEAD`, `TAIL`, `RETURN`, or a specific instruction target) rather than overwriting whole methods.
3. Gate the new behavior behind a config flag, so the injected code is a **no-op by default path** the moment the feature is disabled (`if (!Config.FLAG.get()) return;` / `ci.cancel()` only inside the `if`).
4. Reuse host textures, fonts, colors, and layout primitives — new code never invents a new visual language, it borrows the existing one.
5. Prefix any new fields/methods with a namespaced marker (`qOLHunters$fieldName`) via `@Unique`, so Mixin-injected members can never collide with the target class's real members.

**Design principle #1 — "Patch, don't replace."** A feature should look like it always belonged in the base game. If a new button, icon, or label is added, it must be built from existing atlas regions, existing font rendering calls, and existing spacing conventions wherever possible.

### 1.3 Feature Package Structure

Each QOL feature lives in its own package with a clear, focused responsibility:

```
src/main/java/io/iridium/qolhunters/
├── features/                          # Feature implementations
│   ├── bettercheststats/
│   │   └── ScreenTextures.java        # Custom texture references
│   ├── gearcooldowntimer/
│   │   └── GearCooldownTimer.java     # Event-driven rendering
│   ├── searchablevaultstations/
│   │   ├── QOLSearchElement.java      # Custom UI widget
│   │   └── SearchableScreen.java      # Interface contract
│   └── zerousesalert/
│       └── ZeroUsesAlert.java         # HUD overlay
├── mixin/                             # Host class injections
│   ├── abilities/
│   │   └── MixinAbilityDialog.java
│   ├── prestigetoggles/
│   │   └── MixinPrestigePowerWidget.java
│   └── qolhuntersconfig/
│       └── MixinStatisticsElementContainerScreen.java
├── config/
│   └── QOLHuntersClientConfigs.java
├── util/
│   └── SharedFunctions.java           # Cross-feature helpers
└── events/
    └── ClientForgeEvents.java
```

**Naming Convention:**

- Package name = lowercase, descriptive noun phrase
- Main class = PascalCase, noun or noun phrase
- Feature prefix in config = descriptive (e.g., `SHOW_GEAR_COOLDOWN_TIME`)

### 1.4 Mixin Strategy

**When to use Mixin:**

- Modifying existing Vault Hunters screens
- Overriding default rendering behavior
- Intercepting game events for UI updates

**When NOT to use Mixin:**

- Creating new standalone features
- Adding new screen elements (use Forge events instead)
- Simple data display (use HUD overlays)

**Key Mixin Conventions:**

1. Always guard with config check: `if (CONFIG.get()) { ... }`
2. Prefix custom methods with mod ID: `qOLHunters$methodName()`
3. Use `@Unique` for new fields/methods
4. Use `@Shadow` for accessing target class internals
5. Use `@Overwrite` sparingly — prefer `@Inject` with cancellation
6. Split read-only access (`*Accessor`) from behavior injection (`Mixin*`) into separate classes

---

## 2. Core Design Philosophy

1. **Everything is togglable.** Every visual/behavioral change lives behind a `ForgeConfigSpec` boolean or enum, grouped into a named category (`Client-Only Extensions`, `Better Screens`, `HUD Positioning`, etc.). Nothing is hard-baked into the default experience without an opt-out.

2. **Additive, not destructive.** Prefer overlays, small icons, extra text lines, or new elements appended to existing containers over rewriting a whole screen's render method. Full-screen mixins (`qOLHunters$update()` in `MixinAbilityDialog`) are the exception, reserved for screens whose logic is fundamentally being reworked (e.g. "Better Abilities Tab" shows _all_ levels instead of one).

3. **Anchor-relative, resolution-independent layout.** Nothing is placed at hardcoded absolute screen coordinates. Positions are always derived from the parent GUI's bounds at layout time (`gui.right() + 5`, `guiLeft + offset`, `bounds.x`), so elements re-flow correctly if the base screen's size or position changes.

4. **Small, legible deltas.** New UI never competes with the host UI for visual weight. Additions are small glyphs (▾ chevrons, toggle icons), single lines of text, thin dividers made of a repeated character (`⋮`), or numeric badges — never new large panels unless the whole screen is being redone.

5. **Config-value-driven color, not hardcoded palettes.** Anything the player might want to re-theme (rarity colors, overlay colors, grid colors) is stored as an `Integer` RGB(A) value in config, with an `enum` of sane presets as the default UX, rather than a fixed constant buried in render code.

6. **Respect existing tooltips/interactions instead of reinventing them.** New elements use the same `.tooltip((tooltipRenderer, poseStack, mouseX, mouseY, tooltipFlag) -> ...)` lambda pattern and the same `ButtonElement`/`TextInputElement` interaction model as the host, so hover/click/scroll feel identical to vanilla Vault Hunters.

### 2.1 Integration Approach

```
DO: Extend existing Vault Hunters screens via Mixin
DON'T: Create entirely new screen windows for simple data

DO: Add information to existing tooltips
DON'T: Create popup modals for information

DO: Use subtle color indicators on existing elements
DON'T: Add large floating text or banners
```

---

## 3. Component-Level Guidelines

### 3.1 Panels / Backgrounds

- Never redraw background textures from scratch — panels come from the host's existing `ScreenTextures` atlas regions. QOLHunters only ever _adds elements on top_ of a panel that Vault Hunters already drew.
- If a semi-transparent overlay panel is needed (e.g. bingo grid completion tint), express it as an **ARGB int with the alpha nibble explicit** so opacity is tunable:
  - Fill/completed state: alpha `0x64` (~39%) — e.g. `0x64ff0000` (red fill)
  - Selection/hover state: alpha `0x40` (~25%) — e.g. `0x4000d2ff` (blue selection)
  - This "completed = more opaque, selected = less opaque" alpha relationship should be treated as a convention for any future state-overlay: **stronger states get higher alpha, transient/hover states get lower alpha.**

### 3.2 Dialogs

- Dialogs (`AbstractDialog<T>` subclasses like `AbilityDialog`) are **rebuilt as a data/state recompute step (`update()`), not a raw render override.** The mixin recomputes button text, active state, and the scrollable description body every time selection changes, then lets the vanilla `render()` draw it with vanilla styling.

- Dialog description text uses a **section-divider convention**: a run of a single Unicode character (`⋮`) sized to fill the container width, flanked by a bold `ChatFormatting.DARK_GRAY` label (e.g. `All Levels`), used to separate "current" content from "reference" content inside a scrollable text body. Reuse this divider style for any future "all levels / all info" expansion panel.

- Locked/unavailable future content is rendered with a strikethrough-style prefix (`§kO§r`, an obfuscated glyph) plus `DARK_RED` + bold, signalling "exists but not yet reachable" without hiding it entirely.

- Buttons inside dialogs are always constructed with `new Button(0,0,0,0, component, onPress, NO_TOOLTIP)` and then positioned/sized by the surrounding layout code — text and enabled-state are computed first, geometry is applied second.

### 3.3 Buttons

- Buttons are always **atlas-region driven** (`ButtonElement<>(spatial, ScreenTextures.X, onClick)`), sized and positioned via `Spatials` + `.layout(...)`, and given a `.tooltip(...)` lambda.

- Standard small utility button size: **21×21 px** (e.g. the config-menu gear button in the Statistics screen), anchored to a corner of the parent panel with a **+3 to +5 px margin**.

- Toggle-style buttons (on/off state) use a **paired texture convention**: `BUTTON_TOGGLE_ON` / `BUTTON_TOGGLE_OFF`, swapped based on state, drawn as a small badge overlapping the corner of a larger parent icon (not a separate free-floating button) — e.g. the Prestige Power toggle overlaps the skill node icon's corner, offset by roughly half the icon's own size.

- Disabled/inactive buttons are expressed via the vanilla `button.active = false` flag — never a custom greyscale shader or manual re-tinting. Let the host's own disabled-button rendering handle the visual.

- Hit-testing for a small badge/toggle drawn on top of a larger widget is done with an explicit manual rectangle test (`qOLHunters$aboveToggle`) rather than a separate `Button` instance, when the badge is purely decorative-interactive glue on someone else's widget.

### 3.4 Text Inputs / Search Boxes

- All search boxes extend the host's `TextInputElement<T>`, never build a text field from scratch.
- Standard search box size: **10 px tall**, width supplied by the caller.
- Standard placement API: `createLeft(screen, width, offsetX, offsetY)` / `createRight(...)`, computing position from `screen.getGuiLeft()` or `screen.getGuiLeft() + screen.getXSize()` — i.e., **search boxes are always anchored to a screen edge, never floated at an arbitrary point.**
- Interaction convention: **right-click clears the field.** Apply this to any future filter/search input for consistency.
- Search syntax convention (from `ScrollableItemListElement` filtering): space-separated AND terms; `@term` scopes to item namespace/mod id; `$term` scopes to item tag. Any future searchable list should reuse this exact micro-syntax so player muscle memory transfers between screens.

### 3.5 Lists / Scrollable Containers

- Lists reuse `VerticalScrollClipContainer` / `ScrollableContainer` rather than a custom scroll implementation. Where the host's scroll math has bugs (double-scaled scroll delta, missing bounds before first render), QOLHunters patches the _host_ class via mixin rather than build a parallel component — reinforcing principle #1.

- Grid/offer lists that must adapt to item count (e.g. Greed Trader shop offers) compute a **dynamic row height** from available space: `height = clamp(availableHeight / rows, MIN, MAX)`, snapped to an even number of pixels. Any future adaptive grid should follow the same "compute ideal height → clamp to a sane min/max → round to even" pattern.

- "Add an entry to a themed/category list" (e.g. `ThemeListElement`'s "All Themes" button) is done by hooking the list's `initialize*` method and inserting one extra element at the top before the loop, bumping the running `y` cursor by the injected element's height — keep this pattern for "All / Any / Everything" pseudo-categories in future filterable lists.

### 3.6 Tooltips

- Tooltips are attached declaratively via `.tooltip((tooltipRenderer, poseStack, mouseX, mouseY, flag) -> ...)` on the element itself, not drawn manually in a screen-level `render()` override.

- Item tooltips route through a shared `TooltipContainerElement`, positioned just outside the main panel bounds (observed offset: `x = -139` relative to panel, i.e. tooltip panel sits to the left of a 130px-wide panel with a small gap) rather than following the cursor for panel-style item-preview tooltips. Cursor-following tooltips remain vanilla behavior for ordinary hover text.

### 3.7 Overlays / HUD Elements

- HUD text (e.g. "Zero uses" alert) is centered horizontally on screen (`screenWidth/2 - textWidth/2`) and anchored a fixed distance from the **bottom** of the screen (observed: `height - 65`), matching vanilla Minecraft's convention of stacking status text above the hotbar.

- Alert/warning text uses **bold + underline** (`ChatFormatting.BOLD` + `ChatFormatting.UNDERLINE`) in a saturated red (`0xFF0000`), and is debounced: it only starts rendering after the triggering condition has persisted for **20 ticks (1 second)**, to avoid flicker from transient state changes (e.g. entering/exiting a vault). Reuse this debounce pattern for any new "warning" HUD text.

- Small in-world/holographic HUD elements (e.g. the Brazier modifier hologram) support a **dynamic scale toggle**, a **near-distance threshold**, and a **shift-to-reveal** mode — treat "always / only when near / only when sneaking / near-or-sneaking" as a reusable visibility-mode enum for any future proximity-based HUD element.

- Currency/number+icon combos (Greed Trader coin count) follow a fixed micro-layout: right-aligned number in a warm brown/gold text color (`0x744f2c`), followed by a **half-scale (`0.5F`) item icon** rendered a few pixels to its left, vertically nudged to optically center against the text baseline. Reuse this "number + shrunk item icon" idiom for any future currency/resource counter.

### 3.8 Icons

- Small supplemental icons (chest-type icons, "described" modifier icons) live in the host's own texture atlas namespace (`the_vault:gui/screen/icon/...`, `the_vault:gui/modifiers/described/...`) so they inherit mipmapping/atlas batching for free — new icon assets should be added _into the host's atlas folder structure_, not a separate mod-namespaced atlas, when the icon is meant to feel native.

- Modifier icons follow a naming convention of `<modifier_id>` mapped 1:1 to a `described/<modifier_id>` variant that includes an embedded text/number overlay baked into the sprite — with graceful fallback: if the "described" sprite doesn't exist yet (`MissingTextureAtlasSprite`), silently fall back to the plain icon and cache that negative result for 5 seconds to avoid repeated atlas lookups.

- A small dropdown/expand affordance uses a single scaled-up chevron glyph (`⌄`), drawn in `ChatFormatting.DARK_GRAY`, hand-centered against a fixed icon size (16px at 4x internal scale). Reuse this exact glyph + color for any future "click to expand/choose" affordance to keep a single visual vocabulary for disclosure controls.

---

## 4. Layout & Spatial System

- **Never hardcode absolute screen pixel coordinates.** Always derive from a parent's `getGuiLeft()`, `getGuiTop()`, `getXSize()`, or an `ISpatial`'s `.right()`/`.bottom()`.

- Standard small margin unit observed repeatedly: **~3–7 px** between a new element and the panel edge it's anchored to. Treat **5 px** as the default spacing token unless a tighter (`3px`) corner-hug or looser (`7px`) breathing-room case is specifically needed.

- Layout resolution is deferred to a `.layout(...)` lambda evaluated against the _live_ screen/gui/parent bounds — never bake a final X/Y into the element at construction time. This is what makes elements survive screen resizing, GUI scale changes, and (in this codebase specifically) other mods relocating the host panel.

- When placing an element relative to a sibling dialog/description container, offset mouse/interaction math by that container's own internal padding (observed constant: `5.0F` px inset) before delegating to the child's coordinate space.

### 4.1 Config Button Placement Pattern

The Statistics screen config button demonstrates the canonical placement pattern:

```java
this.addElement(new ButtonElement<>(Spatials.positionXY(-3, 3), ScreenTextures.BUTTON_RELIC_TEXTURES, () -> {
    SubMenuConfigScreen screen = SubMenuConfigScreen.find(
        ConfigHelper.ConfigPath.parse("qolhunters:client.Client-Only Extensions"));
    Minecraft.getInstance().setScreen(screen);
}).layout((screen, gui, parent, world) -> {
    world.width(21).height(21)
         .translateX(gui.right() + 5)                                    // 5px right of panel edge
         .translateY(this.getTabContentSpatial().bottom() + 109);       // 109px below tab content top
}).tooltip((tooltipRenderer, poseStack, mouseX, mouseY, tooltipFlag) -> {
    tooltipRenderer.renderTooltip(poseStack,
        List.of(new TextComponent("QOLHunters Config")),
        mouseX, mouseY, ItemStack.EMPTY, TooltipDirection.RIGHT);
    return false;
}));
```

**Key elements:**

- Size: **21×21 px** (small utility button)
- Anchored to **specific screen landmarks** (panel edge + tab content), not absolute coordinates
- Tooltip uses `TooltipDirection.RIGHT` for side placement
- Injected at `@At("RETURN")` of `<init>`, after the screen is fully constructed

---

## 5. Color System

### 5.1 Observed Palette (As Configurable Defaults)

| Token                       | Hex                    | Usage                              |
| --------------------------- | ---------------------- | ---------------------------------- |
| Alert / Danger              | `#FF0000`              | Zero-uses warning text             |
| Currency text               | `#744f2c`              | Greed Trader coin count            |
| Pure white text             | `#FFFFFF` (`16777215`) | Neutral high-contrast numeric text |
| Cake overlay – Pink         | `#FF77BA`              | Cake vault vignette default        |
| Cake overlay – Blue         | `#219EBC`              | Cake vault vignette alt            |
| Cake overlay – Yellow       | `#FFC300`              | Cake vault vignette alt            |
| Cake overlay – Green        | `#77BA77`              | Cake vault vignette alt            |
| Gear rarity – Scrappy+      | `#BC747C`\*            | Gear roll tier color               |
| Gear rarity – Common+       | `#51ABFF`\*            | Gear roll tier color               |
| Gear rarity – Rare+         | `#FFDD40`\*            | Gear roll tier color               |
| Gear rarity – Epic+         | `#FF00FF`\*            | Gear roll tier color               |
| Bingo fill (per color)      | `0x64` alpha + hue     | Grid-cell "completed" state        |
| Bingo selection (per color) | `0x40` alpha + hue     | Grid-cell "selected" state         |

\* Derived from the raw decimal config defaults in source; treat as approximate reference values.

### 5.2 Color Conventions

- **All themeable colors are `Integer` config values with an `enum` of curated presets**, not raw hex strings — this keeps the config UI a simple dropdown while still allowing power users to store any ARGB int.

- **Alpha encodes state intensity**: higher alpha = confirmed/complete/active state; lower alpha = transient/hover/preview state.

- **Warm desaturated tones (browns/golds) for economic/currency text**, distinct from the cooler blues/reds reserved for combat, alerts, and rarity.

- **Reserve pure saturated red for danger/alert only** — it should never be reused for neutral emphasis, to keep its meaning unambiguous across the whole mod suite.

### 5.3 Opacity Patterns

```java
// Semi-transparent for overlays
int color = 0xDD000000 | rgbColor; // 87% opacity

// For bingo grid backgrounds
int opacity = configOpacity * 255 / 100;
return (opacity << 24); // Shift to alpha channel

// For selection highlights
0x40FFFF00 // 25% opacity yellow
0x64FF0000 // 39% opacity red
```

### 5.4 ChatFormatting Usage

```java
// For text components
ChatFormatting.GREEN        // Success/positive
ChatFormatting.RED          // Error/warning
ChatFormatting.GRAY         // Neutral/label
ChatFormatting.DARK_PURPLE  // Special/value
ChatFormatting.DARK_GRAY    // Dividers/labels
ChatFormatting.DARK_RED     // Locked/unavailable content
ChatFormatting.BOLD         // Emphasis
ChatFormatting.UNDERLINE    // Link/clickable

// Usage
new TextComponent("Label: ").withStyle(ChatFormatting.GRAY)
    .append(new TextComponent("Value").withStyle(ChatFormatting.GREEN))
```

---

## 6. Typography

- Always the vanilla Minecraft `Font` (`Minecraft.getInstance().font`) — no custom font ever introduced.

- Emphasis is expressed purely through `ChatFormatting` (`BOLD`, `DARK_GRAY`, `DARK_RED`, `UNDERLINE`), not through font-size scaling, **except** for deliberately-oversized single glyphs (chevrons, dividers), which are scaled via `poseStack.scale(...)` on an isolated pushed pose, then immediately popped.

- Divider/label text is consistently `DARK_GRAY`; destructive/locked content is `DARK_RED`; default body text inherits whatever color the host component already used.

- Numeric formatting for large numbers follows a fixed compaction scale: `<1,000` → raw number; `1,000–9,999` → `X.XXK`; `10,000–99,999` → `X.XK`; `100,000–999,999` → `XK` (no decimal); `≥1,000,000` → `X.XM`. Apply this exact `formatNumber` scale to any new large-quantity display (currency, scores, stacked totals) rather than inventing a new rounding scheme per feature.

### 6.1 Number Formatting

```java
public static String formatNumber(int number) {
    if (number >= 1_000_000) {
        return String.format("%.1fM", number / 1_000_000.0);
    } else if (number >= 1_000) {
        return String.format("%.1fK", number / 1_000.0);
    } else {
        return String.valueOf(number);
    }
}
// Examples: "1.4M", "250K", "999"
```

### 6.2 Time Formatting

```java
long seconds = time / 20L % 60L;
long minutes = time / 20L / 60L % 60L;
long hours = time / 20L / 60L / 60L;

String text = hours > 0
    ? String.format("%2d:%02d:%02d", hours, minutes, seconds)
    : String.format("%d:%02d", minutes, seconds);
// Examples: "2:15", "0:45", "1:05:30"
```

---

## 7. Interaction & State Conventions

- **Config flag ⇒ visual behavior is a hard 1:1 mapping.** Every mixin's injected logic starts with (or is gated at the call site by) a single `Config.FEATURE.get()` check. No feature should have partial "always on" visual side effects once its flag is off.

- **Hover-tooltip and click-to-act are the only two interaction primitives** used across the whole mod; drag, long-press, and multi-step gestures are avoided in favor of Minecraft-native single click / right click / ctrl-click modifiers (e.g. `Screen.hasControlDown()` used as a modifier to bypass a small hitbox for a toggle).

- **Right-click has a consistent secondary meaning** (clear a text field) — reserve right-click across future widgets for a "reset/clear this control" action rather than assigning it ad hoc per-widget meanings.

- **Debounce transient state before surfacing it as UI** (see the 20-tick zero-uses delay, and the 5-second negative-icon-lookup cache) — any new reactive overlay should default to _not flickering_ on single-tick state noise.

### 7.1 Keybind Patterns

```java
public class KeyBindings {
    private static final String CATEGORY = "QOLHunters";
    public static KeyMapping TOGGLE_MAGNET_GUI;
    public static KeyMapping OPEN_CONFIG;

    public static void init() {
        TOGGLE_MAGNET_GUI = new KeyMapping(
            "key.qolhunters.toggle_magnet",
            InputConstants.KEY_UNKNOWN,
            CATEGORY
        );
        OPEN_CONFIG = new KeyMapping(
            "key.qolhunters.open_config",
            InputConstants.getKey("key.keyboard.q"),
            CATEGORY
        );
        ClientRegistry.registerKeyBinding(TOGGLE_MAGNET_GUI);
        ClientRegistry.registerKeyBinding(OPEN_CONFIG);
    }
}

// Event handler
@SubscribeEvent
public static void onKeyInput(InputEvent.KeyInputEvent event) {
    if (KeyBindings.TOGGLE_MAGNET_GUI.consumeClick()) {
        ModNetwork.CHANNEL.sendToServer(ServerboundMagnetToggleMessage.INSTANCE);
    }
    if (KeyBindings.OPEN_CONFIG.consumeClick()) {
        SubMenuConfigScreen screen = SubMenuConfigScreen.find(
            ConfigHelper.ConfigPath.parse("qolhunters:client.Client-Only Extensions"));
        Minecraft.getInstance().setScreen(screen);
    }
}
```

### 7.2 Alert/Notification System

#### Immediate Alert

```java
public static void displayMessageOnScreen(Component message) {
    Minecraft mc = Minecraft.getInstance();
    mc.execute(() -> mc.gui.setOverlayMessage(message, false));
}
```

#### Debounced Alert

```java
private static int firstBrokenTick = 0;
private static final int DELAY_TICKS = 20; // 1 second

if (condition) {
    if (firstBrokenTick == 0 || player.tickCount < firstBrokenTick) {
        firstBrokenTick = player.tickCount;
        return;
    }
    if (player.tickCount - firstBrokenTick < DELAY_TICKS) return;
    drawAlert(matrixStack);
} else {
    firstBrokenTick = 0;
}
```

---

## 8. Configuration Screen Conventions

- Config UI is delivered through the existing Forge config screen tooling already present in the modpack (Create's `SubMenuConfigScreen` / `ConfigHelper`), accessed either via the modpack's global config key (`Alt+Q`) or a small in-context gear button placed on the Statistics screen (21×21, top-right corner, `+5px` from the panel edge, `+109px` down from the tab content top — i.e., docked to a _specific known screen landmark_, not a generic fixed screen coordinate).

- Config values are declared with `CLIENT_BUILDER.comment("...").define(path, default)` — **every option has a human-readable comment**, and options that require a restart are explicitly flagged (`.worldRestart()`) and called out in the comment text itself ("Requires Restart").

- Config paths are organized into **named groups** (`Client-Only Extensions`, `Better Screens`, `HUD Positioning`, `Gear Roll Colors`, etc.) that map directly to the categories a player would recognize from playing the game, not to internal code structure — group by _player-facing feature area_, not by _package name_.

- Naming pattern for path constants: `public static final String X = "Human Readable Label";` — the config path string **is** the display label; there is no separate i18n indirection layer for config screens.

### 8.1 Config Group Hierarchy

```java
CLIENT_BUILDER.push(ConfigPaths.Group.CLIENT_GROUP);       // Top level
    CLIENT_BUILDER.push(ConfigPaths.Group.SCAVENGER_GROUP);  // Sub-group
        SCAVENGER_INV_COUNT = CLIENT_BUILDER
            .comment("Shows the number of scavenger items you have in your inventory")
            .define(ConfigPaths.SCAVENGER_INV_COUNT, true);
        SCAVENGER_HIGHLIGHTER = CLIENT_BUILDER
            .comment("Highlights current objective scavenger items in your inventory")
            .define(ConfigPaths.SCAVENGER_HIGHLIGHTER, true);
    CLIENT_BUILDER.pop();
CLIENT_BUILDER.pop();
```

---

## 9. Code & Naming Conventions

- **Feature-first package layout**: `features/<featurename>/...` holds the new logic/widgets a feature introduces; `mixin/<featurename>/...` holds the injection points into host classes. Keeping these parallel makes it trivial to find "what does this feature draw" vs. "what does this feature patch."

- **`qOLHunters$` prefix** on any `@Unique` member injected into a host class via Mixin, guaranteeing no collision with the target class and making injected code immediately greppable/auditable.

- **`Accessor`/`Mixin` split**: read-only access to a private host field/method is exposed via a dedicated `*Accessor` interface mixin, kept separate from the `Mixin*` class that actually injects behavior — don't mix "expose" and "modify" responsibilities in the same mixin class.

- Shared, cross-feature rendering helpers (number formatting, slot-highlight rendering) live in a single `util/SharedFunctions.java` rather than being duplicated per feature — any new formatting/drawing helper that a second feature will plausibly need should be promoted here immediately rather than copy-pasted.

---

## 10. Rendering Patterns

### 10.1 Text Rendering

#### Text with Shadow (Most Common)

```java
Font font = Minecraft.getInstance().font;
MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(bufferBuilder);

font.drawInBatch(
    text,                    // Component or String
    x, y,                   // Position
    0xFFFFFFFF,              // Color (white with full alpha)
    true,                    // Shadow enabled
    poseStack.last().pose(), // Transformation matrix
    bufferSource,            // Buffer source
    false,                   // See-through (false = normal)
    0,                       // Background color (0 = none)
    15728880                 // Light (full brightness)
);

bufferSource.endBatch();
```

#### Colored Text with Formatting

```java
MutableComponent text = new TextComponent("Dehammerizer ")
    .withStyle(ChatFormatting.WHITE)
    .append(new TextComponent(String.valueOf(index))
        .withStyle(ChatFormatting.GREEN))
    .append(new TextComponent(": " + coordinates)
        .withStyle(ChatFormatting.GRAY));

font.draw(matrixStack, text, x, y, 0xFFFFFF);
```

### 10.2 PoseStack Transformations

#### Basic Transform Pattern

```java
poseStack.pushPose();
poseStack.translate(x, y, z);
poseStack.scale(scaleX, scaleY, scaleZ);
// ... render ...
poseStack.popPose(); // Always restore state
```

#### Scaling for Small Text

```java
poseStack.pushPose();
poseStack.translate(x, y, 400);
poseStack.scale(0.6F, 0.6F, 1.0F);

int adjustedY = (int) (16 / 0.6) - font.lineHeight;
font.drawInBatch(text, 0, adjustedY, ...);

poseStack.popPose();
```

### 10.3 Render State Management

```java
RenderSystem.enableBlend();
RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
RenderSystem.disableDepthTest(); // Render above other elements

// ... render ...

RenderSystem.enableDepthTest();
RenderSystem.disableBlend();
```

### 10.4 Z-Index Layering

| Layer           | Z Value | Purpose               |
| --------------- | ------- | --------------------- |
| Base items      | 0       | Normal item rendering |
| Slot highlights | 100     | Gradient overlays     |
| Cooldown text   | 400     | Above items           |
| Tooltips        | 500+    | On top of everything  |

---

## 11. Performance Patterns

### 11.1 TTL-Based Caching

```java
private static final ConcurrentHashMap<String, VaultModifierOverlayCacheEntry> cache = new ConcurrentHashMap<>();

public static Optional<ResourceLocation> getModifierIcon(VaultModifier<?> instance) {
    String cacheKey = instance.getId().toString();
    long currentTime = System.currentTimeMillis();

    VaultModifierOverlayCacheEntry entry = cache.get(cacheKey);
    if (entry != null && (currentTime - entry.timestamp) < 5000) { // 5 second TTL
        return entry.resourceLocation;
    }

    // Compute and cache
    Optional<ResourceLocation> result = computeIcon(instance);
    cache.put(cacheKey, new VaultModifierOverlayCacheEntry(result, currentTime));
    return result;
}
```

### 11.2 Inventory Snapshot Cache

```java
public static Map<NamedItem, Integer> getPlayerInventoryItems(LocalPlayer player, int cacheTimeout) {
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastCheckedTime < cacheTimeout) {
        return new HashMap<>(cachedInventoryItems); // Return copy
    }

    Map<NamedItem, Integer> items = new HashMap<>();
    for (InventoryUtil.ItemAccess access : InventoryUtil.findAllItems(player)) {
        // Count items
    }

    cachedInventoryItems = items;
    lastCheckedTime = currentTime;
    return new HashMap>(items);
}
```

### 11.3 Thread Safety

```java
private static synchronized void saveDehammerizer(int index, Coordinates coords) {
    // Modify shared config
}

private static final ThreadLocal<Double> DISTANCE = ThreadLocal.withInitial(() -> 0.0);
```

---

## 12. Design Tokens

Suggested `UIConstants` class for codifying observed conventions:

```java
public final class UIConstants {
    // Spacing
    public static final int SPACING_TIGHT   = 3;   // corner-hugging elements
    public static final int SPACING_DEFAULT = 5;   // standard element-to-panel-edge margin
    public static final int SPACING_LOOSE   = 7;   // breathing room next to grouped controls

    // Sizes
    public static final int BUTTON_UTILITY_SIZE = 21;  // small square utility buttons (e.g. config gear icon)
    public static final int SEARCH_BOX_HEIGHT   = 10;  // standard search/text input height
    public static final int GRID_ROW_MIN_HEIGHT = 18;  // adaptive list row height floor
    public static final int GRID_ROW_MAX_HEIGHT = 38;  // adaptive list row height ceiling

    // Alpha convention (state intensity)
    public static final int ALPHA_CONFIRMED = 0x64; // ~39%, "completed/active" overlay fill
    public static final int ALPHA_TRANSIENT = 0x40; // ~25%, "selected/hover/preview" overlay fill

    // Color roles
    public static final int COLOR_ALERT    = 0xFF0000; // danger/zero-uses/critical only
    public static final int COLOR_CURRENCY = 0x744f2c; // coins/economy text
    public static final int COLOR_NEUTRAL  = 0xFFFFFF; // default high-contrast numeric text
    public static final int COLOR_DIVIDER  = 0x555555; // ChatFormatting.DARK_GRAY equivalent
    public static final int COLOR_LOCKED   = 0x8B0000; // ChatFormatting.DARK_RED equivalent

    // Debounce
    public static final int  ALERT_DEBOUNCE_TICKS      = 20;   // 1 second
    public static final long NEGATIVE_LOOKUP_CACHE_MS   = 5000; // cache "sprite not found" results

    // Glyphs
    public static final String GLYPH_EXPAND = "\u2304"; // disclosure/expand affordance, DARK_GRAY
    public static final char   DIVIDER_CHAR = '\u22EE'; // repeated for section-break rule
}
```

---

## 13. Code Templates

### 13.1 New HUD Overlay Template

```java
package io.iridium.qolhunters.features.[FEATURE_NAME];

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.iridium.qolhunters.QOLHunters;
import io.iridium.qolhunters.config.QOLHuntersClientConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = QOLHunters.MOD_ID, value = Dist.CLIENT)
public class [FeatureName]Alert {

    private static int alertColor = 0xFF0000;
    private static boolean shouldDraw = false;

    @SubscribeEvent
    public static void onRenderGameOverlay(RenderGameOverlayEvent event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!QOLHuntersClientConfigs.[CONFIG_KEY].get()) return;
        if (Minecraft.getInstance().player == null) return;

        Component text = buildAlertText();
        if (shouldDraw) {
            drawText(event.getMatrixStack(), text);
        }
    }

    private static void drawText(PoseStack matrixStack, Component text) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int x = width / 2 - font.width(text) / 2;
        int y = height - 65;

        RenderSystem.enableBlend();
        font.draw(matrixStack, text, x, y, alertColor);
        RenderSystem.disableBlend();
    }
}
```

### 13.2 New Slot Decoration Template

```java
@Mod.EventBusSubscriber(modid = QOLHunters.MOD_ID, value = Dist.CLIENT)
public class [FeatureName]SlotDecoration {

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.DrawScreenEvent.Post event) {
        if (!QOLHuntersClientConfigs.[CONFIG_KEY].get()) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (shouldDecorate(stack)) {
                int x = slot.x + screen.getGuiLeft();
                int y = slot.y + screen.getGuiTop();
                renderDecoration(event.getPoseStack(), stack, x, y);
            }
        }
    }

    private static void renderDecoration(PoseStack poseStack, ItemStack stack, int x, int y) {
        RenderSystem.disableDepthTest();
        poseStack.pushPose();
        poseStack.translate(x, y, 400);
        // Render your decoration
        poseStack.popPose();
        RenderSystem.enableDepthTest();
    }
}
```

### 13.3 New Config Entry Template

```java
// 1. Add field
public static final ForgeConfigSpec.ConfigValue<Boolean> [FEATURE_NAME];

// 2. Add path constant
public static final String [FEATURE_NAME] = "Feature Display Name";

// 3. Add to builder in appropriate group
CLIENT_BUILDER.push(ConfigPaths.Group.[GROUP_NAME]);
    [FEATURE_NAME] = CLIENT_BUILDER
        .comment("Description of what this feature does")
        .define(ConfigPaths.[FEATURE_NAME], true);
CLIENT_BUILDER.pop();
```

### 13.4 New Mixin Screen Template

```java
@Mixin(value = [TargetScreen].class, remap = false)
public abstract class Mixin[ScreenName] {

    @Inject(method = "[METHOD_TO_MODIFY]", at = @At("HEAD"), cancellable = true)
    public void onBefore[Method](CallbackInfo ci) {
        if (QOLHuntersClientConfigs.[CONFIG_KEY].get()) {
            // Your custom logic
            // ci.cancel(); // Optionally cancel original
        }
    }
}
```

---

## 14. Anti-Patterns & Pitfalls

### 14.1 What to Avoid

| Anti-Pattern                          | Problem                              | Solution                         |
| ------------------------------------- | ------------------------------------ | -------------------------------- |
| Creating new screens for simple info  | Disrupts gameplay flow               | Use overlays or tooltips         |
| Hardcoded positions                   | Can't adapt to different resolutions | Use `Spatials` + `.layout(...)`  |
| No config guard                       | Feature can't be disabled            | Always check `CONFIG.get()`      |
| Creating textures for simple shapes   | Asset bloat                          | Use colored rectangles/gradients |
| Unbounded caches                      | Memory leaks                         | Use TTL-based expiration         |
| Rendering every frame unconditionally | Performance hit                      | Use event-specific rendering     |

### 14.2 Common Mistakes

```java
// WRONG: Rendering without depth management
font.draw(matrixStack, text, x, y, color);

// RIGHT: Proper depth management
RenderSystem.disableDepthTest();
font.draw(matrixStack, text, x, y, color);
RenderSystem.enableDepthTest();

// WRONG: Not restoring PoseStack state
poseStack.translate(x, y, 0);

// RIGHT: Always use push/pop
poseStack.pushPose();
poseStack.translate(x, y, 0);
// ... render ...
poseStack.popPose();

// WRONG: No null checks
Player player = Minecraft.getInstance().player;

// RIGHT: Defensive coding
Player player = Minecraft.getInstance().player;
if (player == null) return;
```

---

## 15. Checklist — Adding a New QoL Feature

Use this before merging any new visual feature:

- [ ] Is the new visual gated behind its own `ForgeConfigSpec` entry, with a plain-English comment?
- [ ] Is the config entry grouped under an existing or clearly-named new player-facing category?
- [ ] Does it reuse an existing `ScreenTextures` atlas region / host font / host color, rather than introducing a new asset, unless the feature is explicitly a full screen redesign?
- [ ] Is every position computed relative to the parent GUI's live bounds (`Spatials` + `.layout(...)`), not a hardcoded pixel pair?
- [ ] If it adds a toggle affordance, does it reuse the `BUTTON_TOGGLE_ON` / `BUTTON_TOGGLE_OFF` pair rather than inventing new toggle art?
- [ ] If it shows a themeable color, is that color stored as a config `Integer`/`enum`, not a hardcoded literal in render code?
- [ ] Does state-intensity map to alpha (higher = confirmed, lower = transient) if it's an overlay?
- [ ] Does any new alert/HUD text debounce against single-tick flicker?
- [ ] Are new injected members/methods prefixed `qOLHunters$` and marked `@Unique`?
- [ ] Does right-click (on inputs) follow the existing "clear/alt-action" convention?
- [ ] Is the tooltip attached via the declarative `.tooltip(...)` element API rather than a manual screen-level draw call?

---

## 16. How to Extend This System for a New Mod/Feature

1. **Identify the host screen/element class** you need to touch and confirm whether a mixin target already exists in QOLHunters for it — reuse rather than duplicate.

2. **Decide additive vs. rework.** Default to additive (new small element/overlay). Only choose a full `update()`/`render()` rework if the underlying interaction model itself needs to change (see `MixinAbilityDialog` as the template for a full, principled rework).

3. **Draft the config entry first.** Name, group, comment, and default value — this forces the feature's scope and player-facing description to be clear before any drawing code is written.

4. **Reuse §12 tokens** for spacing, alpha, and color unless the feature has a hard reason to deviate; if it must deviate, add the new token to §12 rather than inlining a one-off literal.

5. **Build via `Spatials` + `.layout(...)`**, never raw coordinates, so the element self-corrects if the host screen changes.

6. **Attach interaction via the declarative element API** (`.tooltip(...)`, `ButtonElement` `onPress`, `TextInputElement` overrides) rather than manual screen-level input handling.

7. **Run the §15 checklist** before considering the feature visually "done."

---

## Appendix: Source References

| Pattern                         | Source File                                                                       |
| ------------------------------- | --------------------------------------------------------------------------------- |
| Config & color definitions      | `config/QOLHuntersClientConfigs.java`                                             |
| Element/search framework usage  | `features/searchablevaultstations/QOLSearchElement.java`, `SearchableScreen.java` |
| Dialog rework template          | `mixin/abilities/MixinAbilityDialog.java`                                         |
| Scroll/list system patches      | `mixin/fixscrollbar/*`                                                            |
| Toggle badge pattern            | `mixin/prestigetoggles/MixinPrestigePowerWidget.java`                             |
| Config-button-in-screen pattern | `mixin/qolhuntersconfig/MixinStatisticsElementContainerScreen.java`               |
| Currency/number+icon idiom      | `features/greedtrader/GreedTraderScreenTweaks.java`                               |
| HUD alert/debounce pattern      | `features/zerousesalert/ZeroUsesAlert.java`                                       |
| Slot decoration pattern         | `features/gearcooldowntimer/GearCooldownTimer.java`                               |
| Icon overlay pattern            | `mixin/magnetstate/MixinItemRenderer.java`                                        |
| Shared number formatting        | `util/SharedFunctions.java`                                                       |
| Icon fallback/caching pattern   | `features/vaultmodifiertextoverlays/VaultModifierOverlays.java`                   |

---

_This document is extracted from QOLHunters and can be extended for future Vault Hunters mods while maintaining consistent look, feel, and functionality._
