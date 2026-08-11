# Vault Modifier Alerts — Implementation Specification

|                           |                                                                          |
| ------------------------- | ------------------------------------------------------------------------ |
| **Spec version**          | 1.0                                                                      |
| **Status**                | Ready for implementation                                                 |
| **Target platform**       | Minecraft 1.18.2, Forge 40.3.11, Vault Hunters 3rd Edition (`the_vault`) |
| **Mod ID / display name** | `vault_modifier_alerts` / "Vault Modifier Alerts"                        |
| **Side**                  | Client-only                                                              |
| **Audience**              | An AI agent implementing this mod without making design decisions        |

> **HOW TO USE THIS DOCUMENT.** This spec is self-contained and prescriptive.
>
> - Implement exactly what is written. **Do not redesign.**
> - Where a section says _"verify in your dev environment"_, open the relevant decompiled
>   class (the VH jar is deobfuscated by ForgeGradle in your dev workspace), confirm the value,
>   and **record the finding in `DECISIONS.md`** (per `RULES.md §13`).
> - Every story S01–S16 has a Definition of Done. Implement stories in dependency order
>   (see §8.1). The dependency order **is** the recommended implementation order.
> - If you believe something in this spec is wrong, do not silently change it: stop, log a
>   decision entry (DEC), and implement the safest behavior described in the story instead.

---

## 1. Purpose & Product Overview

### 1.1 Why this mod exists

Inside The Vault, "world modifiers" modify the vault's rules (loot, mobs, traps, time, …).
Modifiers come in two kinds:

- **Permanent modifiers** — last for the whole vault run (e.g. `the_vault:gilded`, `the_vault:item_quantity`, cake layers).
- **Temporal modifiers** — applied with a time limit and a countdown (ticks remaining); the
  canonical example is the companion "temporal modifier" **Champion's Domain**
  (`the_vault:champion_domain`, display name "Champion's Domain", +50% chance for Champions to
  spawn) whose duration scales with companion level.

This mod adds two features:

- **F1 — Expiry audio alert**: when a _watched_ temporal modifier (configurable list, default
  `[the_vault:champion_domain]`) runs out of time and is exhausted **while the player is still
  inside a vault**, play a distinct audio cue. The alert must not fire on vault exit/entry or
  more than once per expiry.
- **F2 — HUD reordering**: the vault HUD shows all currently-active modifier icons in one list.
  Reorder so temporal modifiers come **first** (sorted by remaining time, **soonest-expiring
  first**), followed by permanent modifiers (stable relative order).

### 1.2 Non-goals

- No server-side changes, no network messages, no server install required.
- No new HUD elements (F1 uses audio only; F2 only changes the order of existing icons).
- No changes to modifier _effects_ — read-only rendering/observation.
- No support for Minecraft versions other than 1.18.2.

---

## 2. Platform, Toolchain & Build

### 2.1 Versions (exact — do not guess)

| Dependency                                  | Value                                                                                                                                                        |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Minecraft                                   | `1.18.2`                                                                                                                                                     |
| Forge (MDK)                                 | `net.minecraftforge:forge:1.18.2-40.3.11`                                                                                                                    |
| Mappings                                    | Parchment `2022.11.06-1.18.2` (channel `parchment`, version `2022.11.06-1.18.2`)                                                                             |
| Java toolchain                              | 17                                                                                                                                                           |
| Mixin                                       | `org.spongepowered:mixin:0.8.5:processor` (annotationProcessor)                                                                                              |
| Vault Hunters (dev dep)                     | curse maven `curse.maven:vault-hunters-official-mod-458203:<vault_hunters_version>` with `fg.deobf`                                                          |
| `vault_hunters_version` (gradle.properties) | `7967092`                                                                                                                                                    |
| Optional gradle plugin (mirrors QOLHunters) | `com.radimous.vh-addon-dev` version `0.3.6`                                                                                                                  |

### 2.2 Gradle requirements

Mirror the structure proven by **QOLHunters** (`build.gradle`), trimmed to this mod's scope:

- Buildscript block: ForgeGradle `5.1.+`, Parchment librarian `1.+`, MixinGradle `0.7-SNAPSHOT`.
- `plugins { id 'maven-publish' }` optional.
- `mixin { add sourceSets.main, "vault_modifier_alerts.refmap.json"; config "vault_modifier_alerts.mixins.json" }`
- Repositories: `maven { url 'https://maven.minecraftforge.net' }`, CurseMaven, `flatDir { dir 'libs' }`.
- Dependencies (minimal set):
  - `minecraft 'net.minecraftforge:forge:1.18.2-40.3.11'`
  - `implementation fg.deobf("curse.maven:vault-hunters-official-mod-458203:${vault_hunters_version}")`
  - `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'`
- No MixinExtras dependency (DEC-016): capture uses stock `@Redirect` only.
- `processResources` expands `${mod_version}` into `META-INF/mods.toml` (property `mod_version`, default `0.1.0`).
- `minecraft { runs { client { workingDirectory project.file('run'); ... mods { vault_modifier_alerts { source sourceSets.main } } } } }`
- `jar.finalizedBy('reobfJar')`.
- `gradle.properties` keys: `org.gradle.jvmargs=-Xmx3G`, `org.gradle.daemon=false`,
  `mod_version=0.1.0`, `vault_hunters_version=7967092`.
- Use the Gradle wrapper (`./gradlew`).

### 2.3 `META-INF/mods.toml`

Mirror QOLHunters' proven layout (`mods.toml`), with these values:

```toml
modLoader="javafml"
loaderVersion="[40,)"
license="MIT"
[[mods]]
modId="vault_modifier_alerts"
version="${mod_version}"
displayName="Vault Modifier Alerts"
authors="(user)"            # or leave as you find it in the scaffold
description='''Audio alert when watched temporal vault modifiers expire,
and HUD sorting of temporal modifiers first.'''
clientSideOnly=true

[[dependencies.vault_modifier_alerts]]
    modId="the_vault"
    mandatory=true
    versionRange="[1.0.0,)"
    ordering="AFTER"
    side="BOTH"

[[dependencies.vault_modifier_alerts]]
    modId="forge"
    mandatory=true
    versionRange="[40,)"
    ordering="NONE"
    side="BOTH"

[[dependencies.vault_modifier_alerts]]
    modId="minecraft"
    mandatory=true
    versionRange="[1.18.2,1.19)"
    ordering="NONE"
    side="BOTH"
```

### 2.4 Mixin config: `src/main/resources/vault_modifier_alerts.mixins.json`

```json
{
  "required": true,
  "package": "io.haque.vault_modifier_alerts.mixin",
  "compatibilityLevel": "JAVA_17",
  "refmap": "vault_modifier_alerts.refmap.json",
  "client": [
    "tracker.MixinVaultModifier",
    "tracker.MixinModifiers",
    "render.MixinModifiersRenderer"
  ],
  "minVersion": "0.8"
}
```

_(Class names must match §5.2 exactly if you keep the default layout.)_

---

## 3. Reference: Verified Vault Hunters Internals

> All facts below were verified against the community reference implementation
> [QOLHunters](https://github.com/IridiumIO/QOLHunters) built on the same VH version
> (curse file `7967092`), and against the VH config dumps in
> `C:\Users\Haque\Development\VH\the_vault\`. They are the contract this mod relies on.

### 3.1 Key classes & members

| Class                                                     | Verified facts                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `iskallia.vault.core.vault.Modifiers`                     | Component holding vault world modifiers. Static method `getDisplayGroup()` builds the per-frame display map; inside it iterates `Modifiers.Entry` instances and populates a local `map` of type `it.unimi.dsi.fastutil.objects.Object2IntMap<VaultModifier<?>>` (local name `map` — verified via QOLHunters `MixinModifiers`).                                                                                                                                                                                                                                                                                                                                                                                                       |
| `iskallia.vault.core.vault.Modifiers$Entry`               | Inner class. `getModifier()` → `Optional<VaultModifier<?>>`; `getContext()` → the entry's context object (type name to confirm in your dev env; QOLHunters calls `ctx.getTimeLeft()`). `getTimeLeft()` → `Optional<Integer>` = **ticks remaining**; present only for temporal modifiers.                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `iskallia.vault.core.vault.overlay.ModifiersRenderer`     | Renders the HUD modifier icon list. Static final fields `TEXT_BUFFER` (`MultiBufferSource.BufferSource`) and `MODIFIER_TEXT_RENDER_MODE` (enum `ModifiersRenderer.ModifierTextRenderMode`, member `NONE`). Static method: `renderVaultModifiers(Map<VaultModifier<?>, Integer> group, PoseStack matrixStack, boolean depthTest, float scale, Alignment alignment, boolean useAlignmentAsAnchor)` — note `remap=false` in mixin targets. Per-item locals proven present (after `VertexConsumer.endVertex()` call, ordinal 3, `Shift.AFTER`): `amount` (int), `minecraft` (Minecraft), `matrix` (Matrix4f), `size` (float), `iconX` (int), `iconY` (int), `textOffsetX` (float), `textOffsetY` (float), `modifier` (VaultModifier<?>). |
| `iskallia.vault.core.vault.modifier.spi.VaultModifier<?>` | Base class of all modifiers (registry singletons). `getId()` → `ResourceLocation`; `getIcon()` → `Optional<ResourceLocation>`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `iskallia.vault.core.vault.ClientVaults`                  | Client-side vault view. `ClientVaults.getActive()` → `Optional<...>` of the active vault; `isPresent()` **iff the client is inside a vault**. (Used by QOLHunters `ZeroUsesAlert`.)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `iskallia.vault.util.Alignment`                           | Enum used by the modifiers renderer.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `iskallia.vault.VaultMod.id(String path)`                 | Returns `ResourceLocation` prefixed with `the_vault:` namespace.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `iskallia.vault.init.ModTextureAtlases`                   | `MODIFIERS` field → `ITextureAtlas` of modifier icons (only needed if you touch icons; this mod does not).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |

### 3.2 Proven injection points (copy targets exactly from QOLHunters)

| #   | Mixin (QOLHunters file)                                   | Target & injection                                                                                                                                                                                                                                                                                                                                                                            |
| --- | --------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| P1  | `mixin/temporalmodifiertimer/MixinModifiers.java`         | `@Mixin(value = Modifiers.class, remap = false)`; `@WrapOperation(method = "getDisplayGroup", at = @At(value = "INVOKE", target = "Liskallia/vault/core/vault/Modifiers$Entry;getModifier()Ljava/util/Optional;"))`; captures `@Local(name = "map") Object2IntMap<VaultModifier<?>> map`.                                                                                                     |
| P2  | `mixin/temporalmodifiertimer/MixinModifiersRenderer.java` | `@Mixin(value = ModifiersRenderer.class, remap = false)`; `@Inject(method = "renderVaultModifiers(Ljava/util/Map;Lcom/mojang/blaze3d/vertex/PoseStack;ZFLiskallia/vault/util/Alignment;Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;endVertex()V", ordinal = 3, shift = At.Shift.AFTER, remap = true))`; captures the per-item locals listed in §3.1. |
| P3  | `mixin/temporalmodifiertimer/MixinVaultModifier.java`     | `@Mixin(VaultModifier.class)` (plain — QOLHunters uses default remap here) implementing a duck-typed interface via `@Unique` fields/methods.                                                                                                                                                                                                                                                  |
| P4  | `mixin/vaultmodifiers/MixinModifiersRenderer.java`        | Same class/method as P2 with `@Redirect` on `VaultModifier.getIcon()`. (Not needed by this mod; listed to prove coexistence.)                                                                                                                                                                                                                                                                 |

> P1 describes the **reference's** capture. This mod's capture deviates: vanilla `@Redirect` on
> the same target, no `@Local map`, no MixinExtras — see DEC-016 (the `getModifier()` INVOKE has
> a single call site, so `@Redirect` is equivalent here).

**First implementation step (S01/S04 gate): confirm all descriptors above in your dev
environment; any deviation → DEC entry + adjust the target strings, keeping the injection
semantics identical.** Mixin descriptor strings use **SRG-produced (VH runtime) names** as
compiled by the vault jar — this is why `remap = false` is used for VH classes.

### 3.3 HUD layout contract (`vault_modifier_overlay.json`, verified dump)

The modifier HUD is an **icon grid** configured server-agnostically:

```json
{
  "columns": 5,
  "spacingX": 5,
  "spacingY": 5,
  "size": 16,
  "rightMargin": 8,
  "bottomMargin": 4,
  "textOffsetX": 4,
  "textOffsetY": 2
}
```

- Icons are drawn left-to-right, top-to-bottom in **map iteration order** (the `group` parameter
  of `renderVaultModifiers`). Reordering the map **is** reordering the HUD.
- The map key is the `VaultModifier<?>` instance; the map value (`Integer`, QOLHunters local
  `amount`) is per-modifier display data used by the renderer for the text overlay — **preserve
  each key's value unchanged when reordering.**

### 3.4 Target modifier: Champion's Domain (verified)

| Field        | Value                                                                                                           |
| ------------ | --------------------------------------------------------------------------------------------------------------- |
| Registry id  | `the_vault:champion_domain`                                                                                     |
| Display name | Champion's Domain                                                                                               |
| Effect       | +50% chance for Champions to spawn                                                                              |
| Kind         | **Temporal** modifier (companion "temporal modifier"; duration scales with companion level; activated in-vault) |
| Icon         | `the_vault:gui/modifiers/champion_domain` (atlas `ModTextureAtlases.MODIFIERS`)                                 |

Related ids (informational): `the_vault:champion_chance` ("Champion's Abode", +10%),
`the_vault:champion_paradox` ("More Champions", +2%). Other temporal modifiers exist
(e.g. `the_vault:overpower`, `the_vault:loot_goblin`, `the_vault:door_hunter`,
`the_vault:ultimate_regeneration`, `the_vault:pylon_hunter`, `the_vault:soul_fest`, …).

---

## 4. Architecture

### 4.1 Component map

```
┌────────────────────────────────────────────────────────────────┐
│ vault_modifier_alerts (client)                                  │
│                                                                │
│  VaultModifierAlerts (main @Mod)                                │
│    ├─ VmaClientConfigs        ForgeConfigSpec (CLIENT)          │
│    ├─ ModifierTracker         snapshot cache + generation       │
│    ├─ ExpiryAlertEngine       transition detection → sound      │
│    ├─ AlertSoundPlayer        SoundEvent lookup + playback      │
│    ├─ ModifierOrdering        sort/partition logic (pure fn)    │
│    └─ mixins                                                  │
│         tracker.MixinVaultModifier   duck-typed time field      │
│         tracker.MixinModifiers       capture timeLeft (P1)      │
│         render.MixinModifiersRenderer reorder group (P2-style)  │
└────────────────────────────────────────────────────────────────┘
```

Layering rules (enforced via `RULES.md`):

- **Mixins are thin glue.** They call into feature classes; no business logic inside mixin bodies.
- **Config is read only through `VmaClientConfigs`** — no `ForgeConfigSpec` access elsewhere.
- Feature classes never touch `@Mod` internals except `MOD_ID`/`LOGGER`.

### 4.2 Module layout (package-by-feature)

```
src/main/java/io/haque/vault_modifier_alerts/
├── VaultModifierAlerts.java          @Mod entry, DeferredRegister for SoundEvent
├── config/
│   └── VmaClientConfigs.java         ForgeConfigSpec + path constants
├── tracker/
│   ├── ModifierTracker.java          time snapshot cache, generation counter, in-vault state
│   └── VaultModifierTimeAccessor.java duck interface (get/set ticksLeft)
├── feature/
│   ├── expiry/
│   │   ├── ExpiryAlertEngine.java    transition detection + fire-once logic
│   │   └── AlertSoundPlayer.java     resolve SoundEvent, play via SoundManager
│   └── order/
│       └── ModifierOrdering.java     pure reorder(Map) → LinkedHashMap
├── event/
│   └── ClientTickEvents.java         TickEvent.ClientTickEvent handler (orchestrates engine)
└── mixin/
    ├── tracker/
    │   ├── MixinVaultModifier.java
    │   └── MixinModifiers.java
    └── render/
        └── MixinModifiersRenderer.java

src/main/resources/
├── META-INF/mods.toml
├── vault_modifier_alerts.mixins.json
├── pack.mcmeta
└── assets/vault_modifier_alerts/
    ├── sounds.json
    ├── lang/en_us.json
    └── sounds/vault/champ_domain_expired.ogg   ← USER-SUPPLIED asset (shipped as of DEC-018)
```

### 4.3 Threading & data flow

- Everything runs on the **Minecraft client thread** (render + tick). No locks needed; no
  cross-thread data.
- **Per render frame:** the VH HUD build calls `Modifiers.getDisplayGroup()`. Our
  `MixinModifiers` wrap (P1) refreshes each modifier's cached `ticksLeft` and bumps the tracker
  **generation counter**.
- **Per client tick:** `ClientTickEvents` polls the tracker. If the generation changed since the
  last processed generation, it snapshots the per-modifier times and runs `ExpiryAlertEngine`
  evaluation (F1). The HUD reorder (F2) runs inside `renderVaultModifiers` itself, synchronously
  in the render frame.

---

## 5. Feature F1 — Expiry Audio Alert (specification)

### 5.1 Functional requirements

| ID   | Requirement                                                                                                                                                                                                                                   |
| ---- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| F1-1 | The mod watches the configured list of modifier ids (`watchedModifiers`). Default: `["the_vault:champion_domain"]`.                                                                                                                           |
| F1-2 | A watched modifier is **active** while its measured time-left value (ticks) is present **and > 0**.                                                                                                                                           |
| F1-3 | An **expiry event** for modifier _m_ occurs when _m_ transitions from active → not-active **while the player remains inside a vault**. "Not-active" = time-left became `null`, `0`, or negative, or _m_ disappeared from the tracked set.     |
| F1-4 | On expiry of a watched modifier, play the configured sound event once via `Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, volume, pitch))`.                                                                  |
| F1-5 | One-shot per expiry: the same modifier id must not re-fire while continuously active, nor double-fire within 1 second; it **may** fire again later in the same vault **if** it becomes active again (e.g. re-applied) and then expires again. |
| F1-6 | Must **never** fire: (a) when leaving a vault (all modifiers vanish together), (b) during the entry grace period (configurable, default 20 ticks after vault entry), (c) outside a vault, (d) when `enabled=false`.                           |
| F1-7 | All F1 state resets on vault exit and on vault entry (fresh session).                                                                                                                                                                         |
| F1-8 | All behavior is surfaced via config (`§5.2`); no hard-coded numbers except documented defaults.                                                                                                                                               |

### 5.2 Configuration (CLIENT spec, file `vault_modifier_alerts-client.toml`)

Group **`[Expiry Alerts]`** (ForgeConfigSpec `push("Expiry Alerts")`):

| Key (path element) | Type           | Default                                    | Range / validator                                                                                  | Notes                                                                                |
| ------------------ | -------------- | ------------------------------------------ | -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| `enabled`          | boolean        | `true`                                     | –                                                                                                  | Master switch for F1.                                                                |
| `watchedModifiers` | List\<String\> | `["the_vault:champion_domain"]`            | `defineList(..., obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null)` | Watched modifier ids. Invalid entries filtered at read-time with a warn log.         |
| `soundOverrides`   | Map\<String,String\> | `{"the_vault:champion_domain": "vault_modifier_alerts:champ_domain_expired"}` | keys/values ResourceLocation-parseable | Per-modifier sound — **required for every watched modifier** (no generic default; missing entry → warn-once + silent). |
| `volume`           | double         | `1.0`                                      | `[0.0, 2.0]`                                                                                       | Playback volume.                                                                     |
| `pitch`            | double         | `1.0`                                      | `[0.5, 2.0]`                                                                                       | Playback pitch.                                                                      |
| `alertSoundEnabled` | boolean        | `true`                                     | –                                                                                                  | Master switch for expiry audio; written by `/vma sound on\|off` (DEC-019).             |
| `gracePeriodTicks` | int            | `20`                                       | `[0, 200]`                                                                                         | Silence window after vault entry (no expiry evaluation).                             |
| `debugLogging`     | boolean        | `false`                                    | –                                                                                                  | When `true`, log every snapshot transition + fire decision at DEBUG level (see §11). |

### 5.3 Sound asset + registry

- Register a custom `SoundEvent` via `DeferredRegister<SoundEvent>` (namespace
  `vault_modifier_alerts`), `SoundEvent` id `champ_domain_expired` (registered name
  `vault_modifier_alerts:champ_domain_expired`).
- `assets/vault_modifier_alerts/sounds.json`:

  ```json
  {
    "champ_domain_expired": {
      "subtitle": "vault_modifier_alerts.subtitle.champ_domain_expired",
      "sounds": [{ "name": "vault_modifier_alerts:vault/champ_domain_expired" }]
    }
  }
  ```

  (Sound file: `assets/vault_modifier_alerts/sounds/vault/champ_domain_expired.ogg` — the
  `"name"`'s path segment after the namespace must match the subdirectory (DEC-013); supplied
  by the owner, shipped as of DEC-018.)

- `assets/vault_modifier_alerts/lang/en_us.json`:
  `{ "vault_modifier_alerts.subtitle.champ_domain_expired": "Champion's Domain expired" }`
- `AlertSoundPlayer.resolve(SoundEvent)` guards:
  - Event registry lookup `ForgeRegistries.SOUND_EVENTS.getValue(id)`; if `null` → log
    **one error** (rate-limited) and no-op. `sound.json` presence does not register the event;
    DeferredRegister does.
  - Playback via `SimpleSoundInstance.forUI(soundEvent, (float) volume, (float) pitch)`.

### 5.4 Time tracking (shared by F1 and F2) — exact mixin specifications

**A. Duck-typed field on `VaultModifier`** (mirrors QOLHunters P3):

```java
// io.haque.vault_modifier_alerts.tracker.VaultModifierTimeAccessor
public interface VaultModifierTimeAccessor {
    Integer vma$getTimeLeft();
    void vma$setTimeLeft(Integer timeLeft);
}

// io.haque.vault_modifier_alerts.mixin.tracker.MixinVaultModifier
@Mixin(VaultModifier.class)                       // see P3 — same as QOLHunters
public abstract class MixinVaultModifier implements VaultModifierTimeAccessor {
    @Unique private Integer vma$ticksLeft;

    @Override public Integer vma$getTimeLeft() { return vma$ticksLeft; }
    @Override public void vma$setTimeLeft(Integer timeLeft) { vma$ticksLeft = timeLeft; }
}
```

> Mixin `@Unique` members are renamed at runtime to avoid collisions (QOLHunters' identically
> named members coexist — both mods installed together is supported, see S13).

**B. Capture during `Modifiers.getDisplayGroup()`** (vanilla `@Redirect` — deviates from the
QOLHunters P1 mirror per **DEC-016**):

```java
// io.haque.vault_modifier_alerts.mixin.tracker.MixinModifiers
@Mixin(value = Modifiers.class, remap = false)
public abstract class MixinModifiers {

    @Redirect(method = "getDisplayGroup",
        at = @At(value = "INVOKE",
                 target = "Liskallia/vault/core/vault/Modifiers$Entry;getModifier()Ljava/util/Optional;"))
    private Optional<VaultModifier<?>> vma$captureTimeLeft(Modifiers.Entry instance) {

        Optional<VaultModifier<?>> result = instance.getModifier();
        VaultModifier<?> modifier = result.orElse(null);
        if (modifier instanceof VaultModifierTimeAccessor accessor) {
            ModifierContext context = instance.getContext();      // verified: spi.ModifierContext
            Integer timeLeft = context != null ? context.getTimeLeft().orElse(null) : null;
            accessor.vma$setTimeLeft(timeLeft);                   // unconditional (DEC-016, F2-7)
            ModifierTracker.getInstance().recordFrameEntry(modifier.getId(), timeLeft);
        }
        return result;
    }
}
```

- The `getModifier()` INVOKE has exactly one call site in `getDisplayGroup` (DEC-005-R1), so a
  `@Redirect` is equivalent to a `@WrapOperation` here; the returned `Optional` is passed
  through unchanged. MixinExtras is **not** a dependency (DEC-016).
- The handler must be a **non-static (instance) method**: `getDisplayGroup()` is an instance
  method and vanilla Mixin enforces matching staticness (DEC-017; unlike `@WrapOperation`,
  which tolerated a static handler).
- The duck time mirrors the raw context value every frame; no `containsKey` reset and no
  bounded-decrease guard (subsumed by unconditional capture — DEC-016).
- This redirect runs once per entry per `getDisplayGroup()` call. If VH builds the display
  group once per render frame (as QOLHunters' timers imply), time values stay fresh each
  frame.

**C. Tracker state** (`ModifierTracker`):

```java
class ModifierTracker {
    long generation;                 // bumped on every frameProcessed()
    long lastProcessedGeneration;    // last generation consumed by the alert engine
    // Per-vault session state (see 5.5):
    Map<ResourceLocation, Integer> lastSnapshot;   // id → ticksLeft (null-value = absent marker? use Map + containsKey)
    Set<ResourceLocation> fired;
    boolean inVault;
    long suppressUntilTick;                          // vault entry grace
}
```

- `lastSnapshot` only stores ids that were observed with a time-left (temporal modifiers) **or**
  watched ids (see detection semantics §5.5) — the engine works on (id → ticksLeft) where
  `ticksLeft = null` means "absent/none this frame". Represent absence explicitly, e.g.
  `Map<ResourceLocation,Integer>` + `containsKey` check.

### 5.5 Expiry detection algorithm (exact)

Run on every client tick (event handler class `ClientTickEvents`, Forge
`TickEvent.ClientTickEvent`, subscribed `@Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)`):

```
PRE (every tick):
  if (Config.enabled disabled) → skip everything
  inVaultNow = ClientVaults.getActive().isPresent()
  if (inVaultNow != tracker.inVault):
      tracker.resetSession()          // clear lastSnapshot, fired; generation = lastProcessedGeneration
      tracker.inVault = inVaultNow
      if inVaultNow: tracker.suppressUntilTick = mc.player.tickCount + Config.gracePeriodTicks
      return
  if (!inVaultNow) return
  if (mc.player.tickCount < tracker.suppressUntilTick) return
  if (tracker.generation == tracker.lastProcessedGeneration) return    // nothing new since last eval

EVALUATE:
  newSnapshot = buildSnapshot()       // for all tracked temporal modifiers seen this frame:
                                      //   id = modifier.getId(), time = ((VaultModifierTimeAccessor) m).vma$getTimeLeft()
  for each watchedId in Config.watchedModifiers:
      prevActive = wasActive(tracker.lastSnapshot, watchedId)
      currActive = isActive(newSnapshot, watchedId)
      if (prevActive && !currActive) → ExpiryAlertEngine.fire(watchedId)
      else if (isActive(newSnapshot, watchedId)) → tracker.fired.remove(watchedId)   // re-arm for next expiry
  tracker.lastSnapshot = newSnapshot
  tracker.lastProcessedGeneration = tracker.generation

wasActive(map, id): map.containsKey(id) && map.get(id) != null && map.get(id) > 0
isActive  (map, id): same predicate (id not present ⇒ not active)

fire(id):
  if (tracker.fired.contains(id)) return                      // one-shot
  tracker.fired.add(id)
  sound = Config.soundOverrides[id]                           // override-only (DEC-018)
  if (sound == null) { warn-once; return }                    // misconfigured watch -> silent, no repeat warn
  AlertSoundPlayer.play(sound, Config.volume, Config.pitch, id)
  if (Config.debugLogging) LOGGER.debug("[VMA] Temporal modifier {} expired; alert fired", id)
```

Edge-case semantics (must hold):

1. **Vault exit** — `inVaultNow` flips false → session reset, no evaluation, nothing fires.
2. **Vault entry** — session reset + grace; frames during grace may populate `lastSnapshot` but
   evaluation is suppressed; no firing for pre-existing temporal modifiers that were already
   mid-countdown when the player entered.
3. **Expiry during HUD absence** — a modifier that stops appearing (and whose time resets to
   `null` per B above) transitions to not-active → fires (this is "exhausted" per F1-3).
4. **Re-application in same vault** — id re-enters active → `fired` cleared → may fire again on
   next expiry.
5. **Duplicate frames** — generation gating guarantees one evaluation per unique snapshot.
6. **Modifier unpaused** — if the game is paused, ticks do not advance; nothing fires while
   paused because tick events don't run. (Accepted behavior; no special handling.)

### 5.6 Per-modifier sound overrides

**The only sound source** (DEC-018, owner request): every watched modifier must have its own
sound, so there is **no generic `soundEvent` default**. Config key `soundOverrides`
(Map, §5.2), default `{ "the_vault:champion_domain":
"vault_modifier_alerts:champ_domain_expired" }`. Resolution per expiry event equals
`SOUND_OVERRIDES[modifierId]`; a missing entry logs a **warn once** per modifier id and stays
silent (F1-8 config-driven). Implemented in `VmaClientConfigs.resolveSoundEventId(...)` +
`ExpiryAlertEngine.fire(...)`; `AlertSoundPlayer` stays a single DRY entry point (unchanged
signature).

---

## 6. Feature F2 — HUD Modifier Reordering (specification)

### 6.1 Functional requirements

| ID   | Requirement                                                                                                                                                                                                                |
| ---- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| F2-1 | Modifier icons rendered by `ModifiersRenderer.renderVaultModifiers` appear in the order defined by the `group` map's iteration. Ordered result: **permanent first, temporal last** — see DEC-020 (anti-anchor edge placement). |
| F2-2 | Temporal modifiers (time-left measured as in §5.4) are sorted by remaining ticks within the temporal bucket — **descending (longest-lasting first, soonest-expiring last) when `sortTemporalDescending=true`** (default). Tie-break: original (VH) insertion order — the sort must be **stable**. |
| F2-3 | Permanent modifiers (no time-left) lead the list, preserving their original relative order. |
| F2-4 | Every map entry keeps its **key→value** pairing (value = the `Integer` amount used by the renderer text overlay).                                                                                                          |
| F2-5 | Ordering applies **only when** `hudOrdering.enabled = true`; otherwise the map passes through unchanged.                                                                                                                   |
| F2-6 | Ordering must not affect: icon texture, countdown overlay rendering (QOLHunters timer text if installed), or any server/network state.                                                                                     |
| F2-7 | Unknown modifiers (no tracked time because e.g. the frame's capture hasn't run yet) are treated as **permanent** for that frame (degraded order, never a crash).                                                           |

### 6.2 Configuration (CLIENT spec, `vault_modifier_alerts-client.toml`)

Group **`[HUD Ordering]`**:

| Key                     | Type    | Default | Range | Notes                                                                                                                                         |
| ----------------------- | ------- | ------- | ----- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `enabled`               | boolean | `true`  | –     | Master switch for F2.                                                                                                                         |
| `sortTemporalDescending` | boolean | `true`  | –     | Direction inside the temporal bucket: `true` = longest-lasting first, soonest-expiring last (default per product decision DEC-021); `false` = soonest-expiring first (original DEC-002 direction). |

_(F2 has no per-modifier config in v1.)_

### 6.3 Implementation strategy — decision tree

Do these steps **in order**; each strategy must be validated in the dev client before moving to
the next. Record which strategy was used + observed decompiled method shape in `DECISIONS.md`.

**Strategy A (primary) — parameter replacement at HEAD (shape-independent).**

```java
// io.haque.vault_modifier_alerts.mixin.render.MixinModifiersRenderer
@Mixin(value = ModifiersRenderer.class, remap = false)
public abstract class MixinModifiersRenderer {

    @ModifyVariable(method = "renderVaultModifiers(Ljava/util/Map;Lcom/mojang/blaze3d/vertex/PoseStack;ZFLiskallia/vault/util/Alignment;Z)V",
            argsOnly = true, ordinal = 0, at = @At("HEAD"))
    private static Map<VaultModifier<?>, Integer> vma$reorderGroup(
            Map<VaultModifier<?>, Integer> group) {
        return ModifierOrdering.reorder(group);
    }
}
```

`ModifierOrdering.reorder` (pure function, no mod state writes):

```java
public static Map<VaultModifier<?>, Integer> reorder(Map<VaultModifier<?>, Integer> group) {
    if (!VmaClientConfigs.HUD_ORDERING_ENABLED.get() || group == null || group.size() < 2) {
        return group;
    }
    LinkedHashMap<VaultModifier<?>, Integer> result = new LinkedHashMap<>(group.size());
    // 1. permanent bucket first (stable: stream of original LinkedHashMap preserves encounter order)
    group.forEach((m, c) -> { if (!isTemporal(m)) result.put(m, c); });
    // 2. temporal bucket last (anti-anchor edge, DEC-020)
    group.entrySet().stream()
        .filter(e -> isTemporal(e.getKey()))
        .sorted(VmaClientConfigs.HUD_ORDERING_DESCENDING.get()
                ? Comparator.comparingInt(e -> timeOf(e.getKey())).reversed()
                : Comparator.comparingInt(e -> timeOf(e.getKey())))
        .forEach(e -> result.put(e.getKey(), e.getValue()));
    return result;
}

static boolean isTemporal(VaultModifier<?> m) {
    return m instanceof VaultModifierTimeAccessor a
            && a.vma$getTimeLeft() != null && a.vma$getTimeLeft() > 0;   // null ⇒ permanent this frame (F2-7)
}
static int timeOf(VaultModifier<?> m) { return ((VaultModifierTimeAccessor) m).vma$getTimeLeft(); }
```

> `Stream.sorted` on a `LinkedHashMap.entrySet()` stream is stable for ties — matches F2-2/F2-3.
> Pass 1 inserts permanents in original order; pass 2 appends the sorted temporal bucket
> (key→value pairing preserved throughout — F2-4).

**Strategy B (fallback) — redirect the iteration source.** If Strategy A produces a runtime mixin
error in the dev client (e.g. variable ordinal mismatch), target the documented loop shape
verified from the decompiled method instead:

```java
@Redirect(method = "renderVaultModifiers(Ljava/util/Map;Lcom/mojang/blaze3d/vertex/PoseStack;ZFLiskallia/vault/util/Alignment;Z)V",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;"), remap = false)
private static Set<Map.Entry<VaultModifier<?>, Integer>> vma$sortedEntries(
        Map<VaultModifier<?>, Integer> group) {
    return ModifierOrdering.reorder(group).entrySet();
}
```

**Strategy C (last resort).** If the renderer does **not** receive the display map directly (e.g.
it receives a `List` or iterates a converted collection — inspect the decompiled method), wrap the
**collection expression** the method iterates with a `@WrapOperation` (**requires re-adding the
MixinExtras dependency removed in DEC-016 — record that follow-up DEC**) that returns
`ModifierOrdering.reorder(...)`'s equivalent collection, and update `ModifierOrdering` to reorder
that collection type. Document the adjusted shape in `DECISIONS.md`. The **ordering contract
(F2-2/F2-3/F2-4) never changes** — only the plumbing.

### 6.4 Interaction between F1 and F2 (data reuse)

- Both features read time-left through the same `VaultModifierTimeAccessor` populated by
  `MixinModifiers`. No duplicate tracking.
- F2 runs in the render frame; F1 runs on ticks consuming snapshots. They are independent and
  safe to run in any interleaving. `ModifierTracker` holds no lock; all access is client-thread.

---

## 7. BDD Stories

### 7.0 Story metadata legend

Each story has: **Priority** (Must / Should / Could), **Feature type**
(ARCH, CONFIG, TRACKER, ALERT, SOUND, ORDER, COMPAT, ROBUST), **Depends on** (story ids it
requires first), **Do-not-break** (behaviors that must keep working). Scenarios are
Given/When/Then. **Definition of Done (DoD)** lists verifiable criteria.

### 7.1 Story dependency graph

```
S01 Project Scaffolding & Build
 │
 ├─> S02 Client Config System
 │     └─> S05 Vault Lifecycle & Snapshot Management
 │           └─> S06 Expiry Detection Engine
 │                 └─> S08 Sound Playback Wiring
 │                       └─> S09 Alert–Config Integration
 ├─> S03 Tracker Duck Interface (VaultModifier)
 │     └─> S04 Time Capture in Modifiers.getDisplayGroup
 │           ├─> S05 (above)
 │           └─> S10 HUD Order Capture & Reorder Mixin
 │                 ├─> S11 Ordering Contract (anti-anchor, DEC-020)
 │                 │     └─> S12 HUD Ordering Config
 │                 └─> S13 Coexistence with QOLHunters
 ├─> S07 Sound Registry & Asset Packaging
 │     └─> S08 (above)
 ├─> S15 Debug Logging & Diagnostics
 │     └─> S16 Client Commands (debug / sound / status)
 └─> S14 Resilience & Failure Modes
```

Critical path: **S01 → S03 → S04 → S05 → S06 → S08 → S09** (F1) and
**S01 → S03 → S04 → S10 → S11 → S12** (F2).

### 7.2 S01 — Project Scaffolding & Build

- **Priority:** Must — **Feature:** ARCH — **Depends on:** – — **Do-not-break:** –
- **Scenarios**
  1. Given the empty project folder; When the developer runs `./gradlew build`;
     Then the mod compiles, reobfuscates, and produces `build/libs/vault_modifier_alerts-0.1.0.jar`.
  2. Given the built jar; When the developer runs `./gradlew runClient`;
     Then a Forge 1.18.2 client launches with `the_vault` present and no mixin application errors in the log.
  3. Given `git status`; When checking modified files; Then only the intended project files are present.
- **DoD:** Build files per §2; `mods.toml` per §2.3; mixins.json per §2.4; mixin targets in §3.2
  verified against the dev environment and findings recorded in `DECISIONS.md`; empty mod class
  `VaultModifierAlerts` exists and registers nothing yet.

### 7.3 S02 — Client Config System

- **Priority:** Must — **Feature:** CONFIG — **Depends on:** S01
- **Scenarios**
  1. Given the game has started once; When the player opens `config/vault_modifier_alerts-client.toml`;
     Then it contains `[Expiry Alerts]` (7 keys, defaults per §5.2) and `[HUD Ordering]` (2 keys,
     defaults per §6.2) with correct types/ranges.
  2. Given an invalid `watchedModifiers` entry (`"not..valid"`); When the engine reads the list;
     Then the invalid entry is filtered with a warn log and the rest still work.
  3. Given `enabled=false` for Expiry Alerts; When a temporal modifier expires in-vault;
     Then no sound plays and no snapshot state is consumed.
- **DoD:** `VmaClientConfigs` exposes typed `ConfigValue`s; config registered via
  `ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ..., "vault_modifier_alerts-client.toml")`;
  tests 2 & 3 verified manually.

### 7.4 S03 — Tracker Duck Interface

- **Priority:** Must — **Feature:** TRACKER — **Depends on:** S01
- **Scenarios**
  1. Given a `VaultModifier` instance; When passed through the mixin-applied class;
     Then it is an instance of `VaultModifierTimeAccessor` and get/setter round-trip correctly.
  2. Given both this mod and QOLHunters loaded; When the client starts;
     Then no `@Unique` member collision is reported for `VaultModifier` (both injectors coexist).
- **DoD:** `VaultModifierTimeAccessor` + `MixinVaultModifier` per §5.4A; runtime-verified via a
  temporary debug log line (removed before S15 completes, or kept behind `debugLogging`).

### 7.5 S04 — Time Capture in `Modifiers.getDisplayGroup`

- **Priority:** Must — **Feature:** TRACKER — **Depends on:** S03
- **Scenarios**
  1. Given a vault with an active temporal modifier (verified by the HUD countdown);
     When `getDisplayGroup()` runs (render frame);
     Then the modifier's `vma$getTimeLeft()` equals the ticking countdown (ticks) and decrements over frames.
  2. Given a vault with only permanent modifiers; When `getDisplayGroup()` runs;
     Then no modifier has a non-null time-left (permanent bucket stays empty of temporals).
  3. Given a temporal modifier that ends; When the next frames run;
     Then its time-left transitions to `null` and `ModifierTracker` reports a generation change.
- **DoD:** `MixinModifiers` per §5.4B with confirmed context accessor; generation counter
  increments exactly once per frame with candidates; test 1–3 recorded in `DECISIONS.md`.

### 7.6 S05 — Vault Lifecycle & Snapshot Management

- **Priority:** Must — **Feature:** TRACKER — **Depends on:** S02, S04
- **Scenarios**
  1. Given the player enters a vault; When `ClientVaults.getActive()` becomes present;
     Then the session resets (empty snapshot, clear fired set, grace timer starts).
  2. Given the player leaves a vault; When the active-vault optional empties;
     Then the session resets and **no expiry evaluation occurs** on that tick.
  3. Given re-entry into another vault within the same game session; When the grace period elapses;
     Then evaluation resumes with a fresh snapshot.
- **DoD:** `ModifierTracker` state machine matches §5.5 `PRE` steps; tick handler placed in
  `event/ClientTickEvents`.

### 7.7 S06 — Expiry Detection Engine

- **Priority:** Must — **Feature:** ALERT — **Depends on:** S05
- **Scenarios**
  1. Given a watched modifier active with 1200 ticks
     (simulate: set `watchedModifiers` to a live temporal id); When its time crosses to 0/absent
     in-vault; Then `fire(id)` is invoked exactly once.
  2. Given the modifier stays active; When ticks decrease across frames;
     Then no fire occurs.
  3. Given the modifier expired and was re-applied later in the same vault; When it expires again;
     Then `fire(id)` is invoked again (re-arm per §5.5).
  4. Given vault exit exactly when a modifier's last frame shows absent; When the tick handler runs;
     Then no fire occurs (exit wins per F1-6).
- **DoD:** `ExpiryAlertEngine` implemented per §5.5; all four scenarios run in the dev client with
  `debugLogging=true` and results logged to `DECISIONS.md`.

### 7.8 S07 — Sound Registry & Asset Packaging

- **Priority:** Must — **Feature:** SOUND — **Depends on:** S01
- **Scenarios**
  1. Given the mod loads; When the sound registries finish;
     Then `ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("vault_modifier_alerts","champ_domain_expired"))` is non-null.
  2. Given the user's `.ogg` absent; When the mod builds and runs;
     Then no crash occurs and playback is silent (guarded).
  3. Given the `.ogg` present at the documented path; When the client runs;
     Then `/playsound vault_modifier_alerts:champ_domain_expired master @s` (or equivalent in-game
     command) produces audio.
- **DoD:** DeferredRegister wired into `VaultModifierAlerts` constructor; `sounds.json` +
  `en_us.json` included; guards per §5.3.

### 7.9 S08 — Sound Playback Wiring

- **Priority:** Must — **Feature:** SOUND — **Depends on:** S06, S07
- **Scenarios**
  1. Given an expiry event; When `AlertSoundPlayer.play(...)` runs;
     Then `SoundManager.play(SimpleSoundInstance.forUI(soundEvent, volume, pitch))` is called on the client thread.
  2. Given an override mapped to an unregistered id; When `play(...)` runs;
     Then exactly one error-level log appears and the call no-ops.
  3. Given `volume=0`; When `play(...)` runs; Then no audible output and no exception.
- **DoD:** `<PlayerTickEvent/TickEvent>` → engine → sound path proven end-to-end
  (`debugLogging` log + audible cue in dev client).

### 7.10 S09 — Alert ↔ Config Integration

- **Priority:** Must — **Feature:** CONFIG/ALERT — **Depends on:** S08, S02
- **Scenarios**
  1. Given `watchedModifiers = ["the_vault:champion_domain"]` and its `soundOverrides` entry
     (bundled Champ's Domain cue); When Champion's Domain expires in-vault;
     Then the bundled sound plays (pitch/volume from config).
  2. Given `watchedModifiers = []`; When any temporal modifier expires;
     Then nothing plays.
  3. Given `gracePeriodTicks = 0` and a modifier mid-countdown at entry;
     Then evaluation starts immediately after entry (state already building) and expiry still fires.
- **DoD:** all config keys flow into engine + player; config file edits apply on next read (no
  restart requirement — read `get()` at use time).

### 7.11 S10 — HUD Order Capture & Reorder Mixin

- **Priority:** Must — **Feature:** ORDER — **Depends on:** S04, S03
- **Scenarios**
  1. Given the HUD renders a modifier list of size ≥ 2; When Strategy A (`@ModifyVariable` at HEAD)
     is active; Then the renderer receives the reordered map on every frame.
  2. Given the same scenario with QOLHunters installed; When both mods' mixins apply to
     `renderVaultModifiers`;
     Then neither crashes and the countdown text still renders.
  3. Given `enabled=false`; When the HUD renders; Then the original map identity is returned
     unchanged (no copy).
- **DoD:** one of strategies A/B/C (§6.3) applied and chosen strategy recorded; ordering code
  lives in `ModifierOrdering` (not in the mixin body).

### 7.12 S11 — Ordering Contract

- **Priority:** Must — **Feature:** ORDER — **Depends on:** S10
- **Scenarios**
  1. Given temporal modifiers with times {300, 60, 900} and permanents [A, B, C] in original order;
     When the HUD renders; Then displayed order is [A, B, C, T900, T300, T60] (permanents first,
     temporal bucket last, longest-lasting first — DEC-020/021 anti-anchor placement).
  2. Given two temporals with equal time; When the HUD renders;
     Then they appear in their pre-sort relative order (stable).
  3. Given a modifier with no tracked time (unknown this frame);
     Then it renders with the permanents (F2-7), never crashes.
- **DoD:** `ModifierOrdering.reorder` unit-verifiable via a small console test/debug hook or
  in-client observation with `debugLogging`; contract F2-2/3/4 checked against the HUD.

### 7.13 S12 — HUD Ordering Config

- **Priority:** Should — **Feature:** CONFIG/ORDER — **Depends on:** S11, S02
- **Scenarios**
  1. Given `sortTemporalDescending=false`; When the HUD renders temporals {300,60,900};
   Then order is [T60, T300, T900] (soonest-expiring first).
2. Given `enabled=false`; Then the vanilla order is restored.
- **DoD:** both config keys wired into `ModifierOrdering`; scenario 1 verified in-client.

### 7.14 S13 — Coexistence with QOLHunters

- **Priority:** Must — **Feature:** COMPAT — **Depends on:** S04, S10
- **Scenarios**
  1. Given both jars in the mods folder; When the client starts and enters a vault;
     Then no mixin target/conflict errors appear in the log.
  2. Given QOLHunters' Temporal Modifier Timer enabled; When the HUD renders reordered icons;
     Then countdown text still appears on the correct icons (their `@Inject` after `endVertex`
     ordinal 3 runs after our HEAD reorder — order is preserved per key).
- **DoD:** a manual play-test with QOLHunters 0.42.12-style build (per DEC-007) recorded in
  `DECISIONS.md`; any discovered inter-mod edge case resolved via a decision entry.

### 7.15 S14 — Resilience & Failure Modes

- **Priority:** Must — **Feature:** ROBUST — **Depends on:** S09, S12
- **Scenarios**
  1. Given the `.ogg` asset missing; When expiry fires; Then no crash, silent no-op + one warn log.
  2. Given a watched id with no `soundOverrides` entry (or a malformed override map in the toml);
     When the engine runs; Then config load does not crash (validator rejects), and that watch
     stays silent with one warn log.
  3. Given `the_vault` updates its config dump (e.g. new modifier ids); When a watched id is
     unknown; Then nothing special happens — unknown ids simply never match.
  4. Given `ClientVaults.getActive()` unavailable in an edge state (null-free defensive check);
     When the tick handler runs; Then no NPE propagates (method is dually guarded).
- **DoD:** every failure path above logged distinctly (`[VMA]` prefix) and recovered from.

### 7.16 S15 — Debug Logging & Diagnostics

- **Priority:** Should — **Feature:** ROBUST — **Depends on:** S14
- **Scenarios**
  1. Given `debugLogging=true` and snapshots changing; When frames/ticks progress;
     Then DEBUG logs show: frame captured (generation + count), each transition, each fire and
     its id, and each reorder application (original vs sorted counts).
  2. Given `debugLogging=false` (default); When the same flow runs;
     Then no debug lines appear (only warn/error paths remain).
- **DoD:** all logs go through the mod's `LOGGER` with format `[VMA] …`.

### 7.17 S16 — Client Commands

- **Priority:** Should — **Feature:** CONFIG/ROBUST — **Depends on:** S15
- **Scenarios**
  1. Given the game running (single-player or multiplayer); When the player runs `/vma debug on`;
     Then `debugLogging` flips to `true` in the toml (persists after restart) and a feedback
     message confirms.
  2. Given the player runs `/vma sound off`; Then `alertSoundEnabled` flips to `false`
     (persists); the next expiry marks fired but plays no sound (debug line when `debugLogging`).
  3. Given the player runs `/vma status`; Then chat shows: debug/sound/HUD-ordering state,
     the last observed HUD order (per-modifier `[t+Ns]`/`[permanent]` markers), vault
     presence + frame generation, and each watched id's remaining ticks + sound override.
  4. Given an invalid subcommand; Then the dispatcher reports usage (no crash).
- **DoD:** commands registered on the Forge bus via `RegisterClientCommandsEvent`
  (`ClientCommandSourceStack`, verified in Forge 1.18.2-40.3.11); toggles persist across
  restarts; `/vma status` verified in-client.

---

## 8. Non-Functional Requirements

| ID    | Requirement                                                                                                                                                                                                                                                        |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| NFR-1 | **Performance:** reorder does ≤ 1 `LinkedHashMap` build + 1 stream sort per frame, only when `enabled`; guard `size()<2`. No per-frame allocations in the tracker beyond snapshot maps (reuse where trivial). Time budget well under 0.1 ms/frame at 30 modifiers. |
| NFR-2 | **Threading:** all code runs on the client thread; no locks, no `Thread`s, no async.                                                                                                                                                                               |
| NFR-3 | **Server:** zero server interaction, zero packets, zero server component in `mods.toml` (`clientSideOnly=true`).                                                                                                                                                   |
| NFR-4 | **Compatibility:** no mixin injection points that QOLHunters (or Wold's Vaults) also use at the same site with conflicting handlers; no dependency declarations on QOLHunters.                                                                                     |
| NFR-5 | **Logging discipline:** use the mod `LOGGER`; prefix messages `[VMA]`; debug behind `debugLogging`; error paths rate-limited to once per session where applicable.                                                                                                 |
| NFR-6 | **Code quality:** `RULES.md` applies in full (DRY, SoC, grouping, conventions). Compile with zero warnings for our code; no unchecked casts except at the documented duck-interface boundary.                                                                      |
| NFR-7 | **Stability:** if anything throws during a mixin callback, catch narrowly, log, and continue rendering vanilla behavior (no crash loops).                                                                                                                          |

---

## 9. Verification Plan (manual, in-game)

Perform after S09 and S12 respectively; results + screenshots/notes go to `DECISIONS.md`.

1. **Baseline (no mod actions):** start client, enter vault, confirm HUD icons render and no log
   errors (`[VMA]`).
2. **F1 happy path:** configure `watchedModifiers` to an active temporal modifier you can control
   (canonical: activate a companion temporal modifier in-vault). Watch countdown; when it hits 0,
   expect one sound + one `[VMA]` debug line (if enabled). Verify no second sound if it stays off.
3. **F1 re-fire:** re-activate the same temporal modifier; let it expire again; expect a second
   cue.
4. **F1 exit guard:** with a temporal modifier still ticking, leave the vault; expect **no** cue.
5. **F1 grace:** enter a vault while a long companion modifier is already active; expect no cue
   during the grace window even if it lapses (test by setting `gracePeriodTicks=200`).
6. **F2 order:** with ≥2 temporals at different times + ≥2 permanents, screenshot the HUD; verify
   permanents first, temporal bucket last at the anti-anchor (outer) edge of the block (DEC-020),
   values/amounts intact. With BOTTOM vertical the temporals sit on the block's top row; with TOP
   vertical on its bottom row.
7. **F2 + QOLHunters:** repeat 6 with QOLHunters installed and its Temporal Modifier Timer on;
   countdowns must remain positioned per-icon.
8. **F2 off:** set `enabled=false`; verify vanilla order returns.
9. **Config surface:** edit toml mid-game; confirm no restart needed and no validator crashes
   (invalid list entries filtered).
10. **Build hygiene:** `./gradlew build` clean; jar contains `assets/`, `refmap`,
    `vault_modifier_alerts.mixins.json`, `mods.toml`.
11. **Commands (debug):** `/vma debug on`; enter a vault with an active temporal; then
    `[VMA] HUD reorder` and `[VMA] Frame captured` lines appear; `/vma debug off` removes them.
12. **Commands (sound):** `/vma sound off`; expire a watched temporal; no cue plays (debug line
    "sound suppressed"); `/vma sound on` restores the cue; restart the game and confirm the toggle
    persisted in the toml.
13. **Commands (status):** in-vault with mixed modifiers, run `/vma status`; verify the HUD-order
    line matches the screenshot of test 6 (temporal bucket last).

---

## 10. Risks & Mitigations

| Risk                                                | Likelihood   | Mitigation                                                                                                                                                                                                                                                                             |
| --------------------------------------------------- | ------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| VH internals differ on the installed version        | Low–Med      | S01 gate: verify every §3 descriptor in the dev environment; DEC entry before coding mixins.                                                                                                                                                                                           |
| `getDisplayGroup()` not called each frame           | Low          | Tracker is generation-gated; F1 evaluation catches any snapshot change; worst case alert latency = one frame. Also capture time-left in the F2 mixin as a secondary hook if measurements show otherwise (record in DECISIONS.md; keep both features functional with one capture path). |
| Strategy A mixin (param modify) rejected at runtime | Low          | Fallbacks B/C in §6.3; contract unchanged.                                                                                                                                                                                                                                             |
| QOLHunters co-installed behaves differently         | Med          | S13 play-test; both mods proven on the same jar lineage; if a conflict appears, wrap/re-route via decision + optional `minecraft.client.properties`-free runtime check `ModList.get().isLoaded("qolhunters")`.                                                                         |
| User's `.ogg` missing/inaudible per override        | Certain (v1) | Watched id without a `soundOverrides` entry logs wonce + is silent; guarded `AlertSoundPlayer` (rate-limited error); README documents drop paths. |
| New VH modifier ids unknown to config               | Low          | Unknown ids never match; ordering handles by time-absence (F2-7).                                                                                                                                                                                                                      |

---

## 11. Assumptions & Open Items

1. **`.ogg` supply** — shipped as of DEC-018:
   `src/main/resources/assets/vault_modifier_alerts/sounds/vault/champ_domain_expired.ogg`
   (Vorbis, 44.1 kHz, stereo, ~1.31 s), wired to the Champion's Domain override only.
2. **Context class name** — the exact type returned by `Modifiers.Entry.getContext()` (with
   `getTimeLeft()`) is confirmed during S01/S04 and recorded as DEC-005.
3. **Per-modifier sounds** — in scope for v1 via `soundOverrides` (DEC-018); only
   Champion's Domain ships a bundled override.
4. **Vault "world vs crystal" modifiers** — both permanent and temporal lists come from the
   same `Modifiers` mechanism; no special-casing of source. If testing shows companion
   temporals aren't present in `getDisplayGroup` on your setup, switch the capture hook to the
   F2 mixin path (see §10 risk row 2) and log DEC-006.
5. **Mod author/display metadata** — fill `authors` in `mods.toml` with the product owner's
   name/handle.

---

## 12. References

| Ref                                            | Link                                                                                                                   |
| ---------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| QOLHunters (reference implementation)          | https://github.com/IridiumIO/QOLHunters                                                                                |
| — `temporalmodifiertimer` files                | `src/main/java/io/iridium/qolhunters/{features,mixin}/temporalmodifiertimer/*`                                         |
| — `vaultmodifiers` mixins                      | `src/main/java/io/iridium/qolhunters/mixin/vaultmodifiers/MixinModifiersRenderer.java`                                 |
| — build.gradle / mods.toml / gradle.properties | repo root                                                                                                              |
| Vault Hunters Official Wiki                    | https://wiki.vaulthunters.gg/ (Vault Champion, Vault Companions, Temporal Relic pages)                                 |
| VH config dump (id verification)               | `C:\Users\Haque\Development\VH\the_vault\vault_modifiers.json`, `companion_relics.json`, `vault_modifier_overlay.json` |
| HUD overlay layout values                      | `vault_modifier_overlay.json` (columns 5 / size 16 / spacing 5 / margins 8·4)                                          |
