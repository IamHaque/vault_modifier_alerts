# Decision Log — Vault Modifier Alerts

**Purpose:** Every decision that shapes, changes, or clarifies the design of this mod is
recorded here, in chronological order. The log is the single source of truth for _why_ the
implementation is the way it is, and for any deviation from the spec.

> **Requirement:** An implementing AI **must not** silently deviate from `MODIFIER_ALERTS_SPEC.md`.
> Any deviation, ambiguity resolution, or new constraint discovered during implementation
> first requires the spec/standards to be re-checked, then this decision log to be updated
> (new entry, appended at the end). Spec deviations additionally require the affected section
> of the spec to be marked with the DEC reference (e.g. `[see DEC-008]`).

---

## 1. When to add a decision

Add an entry when any of the following happens:

- A design question from the spec (§10, §11 open items) is resolved.
- Implementation reality contradicts the spec (renamed class, method signature differs,
  injection point unavailable, config key changed, etc.).
- A new dependency, toolchain change, or re-scope is introduced (e.g. dropping a feature).
- A behavioral choice with more than one reasonable option is made (sound fallback behaviour,
  HUD sort tie-breaking, alert debounce, etc.).
- A clarifying question is answered by the project owner (or a question must be escalated to
  the owner because the spec is ambiguous and impact is high — in that case record the
  escalation as a decision with status `PENDING OWNER`).

Non-decisions (bugs fixed, formatting, naming only) do not get entries.

---

## 2. Decision entry template

```markdown
### DEC-0XX — <short title>

- **Date:** YYYY-MM-DD
- **Status:** OPEN | RESOLVED | SUPERSEDED BY DEC-0YY | PENDING OWNER
- **Category:** Design | Spec Amendment | Toolchain | Scope | Bug Workaround

**Context**
What prompted the decision (factual, including evidence such as decompiled source, stack
trace, test result, or owner answer).

**Decision**
The course of action taken, stated precisely and implementable.

**Rationale**
Why this option over the alternatives.

**Alternatives considered**

1. ...
2. ...

**Impact**

- Spec sections affected: (§x, §y)
- Stories affected: S01–S15)
- Risks introduced / mitigated:
```

---

## 3. Decision index

| ID      | Title                                                      | Status   | Date       |
| ------- | ---------------------------------------------------------- | -------- | ---------- |
| DEC-001 | HUD sort order: soonest-expiring first                     | RESOLVED | 2026-08-11 |
| DEC-002 | Alert scope: configurable watched list                     | RESOLVED | 2026-08-11 |
| DEC-003 | Alert sound: custom bundled .ogg                           | RESOLVED | 2026-08-11 |
| DEC-004 | Mod id, display name, package                              | RESOLVED | 2026-08-11 |
| DEC-005 | Context class exposes time left (verify at implementation) | RESOLVED | 2026-08-11 |
| DEC-006 | Time-capture fallback hook                                 | OPEN     | 2026-08-11 |
| DEC-007 | QOLHunters coexistence play-test                           | OPEN     | 2026-08-11 |
| DEC-008 | Mixins.json entries appear only with their story's class   | RESOLVED | 2026-08-11 |
| DEC-009 | Dev client loads pack mods from Prism instance             | RESOLVED | 2026-08-11 |
| DEC-010 | runClient JDK 17.0.18 netty add-opens args                 | RESOLVED | 2026-08-11 |
| DEC-011 | Runtime testing = user-tests built jar; no runClient       | RESOLVED | 2026-08-11 |
| DEC-012 | Tracker frame data recorded inside the capture mixin       | RESOLVED | 2026-08-11 |
| DEC-013 | sounds.json "name" resolved against README drop path        | RESOLVED | 2026-08-11 |
| DEC-014 | S10: Strategy A applied; class/package alignment corrections | RESOLVED | 2026-08-11 |
| DEC-015 | Temporary default alert sound: vanilla note block pling     | RESOLVED | 2026-08-11 |
| DEC-016 | Drop MixinExtras: vanilla @Redirect capture (Option B)      | RESOLVED | 2026-08-12 |
| DEC-017 | @Redirect handler must be non-static (instance target)      | RESOLVED | 2026-08-12 |
| DEC-018 | Bundled Champ's Domain sound + per-modifier overrides       | RESOLVED | 2026-08-12 |
| DEC-019 | Client commands for debug + sound toggles                   | RESOLVED | 2026-08-12 |
| DEC-020 | F2 order = anti-anchor edge (permanents first, temporals last) | RESOLVED | 2026-08-12 |
| DEC-021 | Temporal bucket direction: longest-lasting first (descending) | RESOLVED | 2026-08-12 |
| DEC-022 | F3 operation selector: re-roll ops only; reset_potential automatic | RESOLVED | 2026-08-15 |
| DEC-023 | F3 panel drawn inside the station screen via mixin (no separate Screen) | RESOLVED | 2026-08-15 |
| DEC-024 | F3 applicability guard source: VaultGearTierConfig groups per scope | RESOLVED | 2026-08-15 |
| DEC-025 | F3 engine press semantics, stop reasons & 1.18.2 API facts | RESOLVED | 2026-08-15 |
| DEC-026 | F3 panel drawing/input routing via framework element + title/row math | RESOLVED | 2026-08-15 |
| DEC-027 | Runtime testing = user-tests built jar in Prism instance | RESOLVED | 2026-08-15 |
| DEC-028 | F3 GUI revamp: dropdowns, human names, toggles, layout engine | RESOLVED | 2026-08-15 |
| DEC-029 | Typed tier-config parsing, ability names, threshold retention, spacing | RESOLVED | 2026-08-15 |
| DEC-030 | Effect-avoidance chance ranges, name fixes, status wording, reset counter | RESOLVED | 2026-08-15 |
| DEC-031 | Multi-target watch list: per-target mins, stop condition, picker UX | RESOLVED | 2026-08-15 |

---

## 4. Seeded decisions

### DEC-001 — HUD sort order: soonest-expiring first

- **Date:** 2026-08-11
- **Status:** RESOLVED
- **Category:** Design

**Context**
F2 requires temporal modifiers to be displayed first on the HUD. There are two natural orders:
urgent-first (soonest-expiring at the front) or expiring-last (countdown at the back).

**Decision**
Sort temporal modifiers descending by time left (longest-lasting first — DEC-021 supersedes the
original ascending default per the owner's in-game feedback); permanents follow,
in stable order. Ties keep insertion (map) order. Config flag `sortTemporalDescending=true`
(default) toggles between descending and ascending (see DEC-021).

**Rationale**
The alert use case is about _urgency_; the most-about-to-run-out modifier is the one the
player must react to now. Ascending matches the visual priority of the expiry alert feature.

**Alternatives considered**

1. Descending (countdown at the end) — rejected: emphasises the least urgent modifier.
2. No reordering (only grouping) — rejected: does not satisfy the owner-stated requirement.

**Impact**

- Spec sections: §6 (F2 algorithm), §7.4 config
- Stories: S10, S11, S12

---

### DEC-002 — Alert scope: configurable watched list

- **Date:** 2026-08-11
- **Status:** RESOLVED
- **Category:** Design

**Context**
The owner asked whether the alert should cover all temporal modifiers or only specific ones.

**Decision**
Configurable list `watchedModifiers`, default `["the_vault:champion_domain"]`. A modifier is
watched when the default list is used with only `the_vault:champion_domain` present; the list
is compared against the raw modifier id string (e.g. `the_vault:champion_domain`), not the
display name.

**Rationale**
Champion's Domain is the only VH3 temporal _companion_ modifier and was the named requirement;
a config list keeps the feature generic without hard-coding. Matching on raw id survives
translation/localisation changes.

**Alternatives considered**

1. Alert on all temporal modifiers — rejected: noisy, out of scope, owner asked for a list.
2. Hard-code `champion_domain` — rejected: not future-proof.

**Impact**

- Spec sections: §7.4 config group `[Expiry Alerts]`
- Stories: S09

---

### DEC-003 — Alert sound: custom bundled .ogg

- **Date:** 2026-08-11
- **Status:** RESOLVED
- **Category:** Design

**Context**
Owner was asked how the sound event should be provided (existing vanilla sound vs. custom).

**Decision**
Ship a custom sound event `vault_modifier_alerts:temporal_expired` (DeferredRegister), with the
audio expected at `assets/vault_modifier_alerts/sounds/vault/temporal_expired.ogg`. The owner
supplies the file; until then the mod builds and runs but the sound plays silently
(`Assets.get()... == null` guard). Volume/pitch configurable via config.

**Rationale**
A bundled custom sound is reliably present in single-player and on servers (client mod), gives
full control, and does not depend on third-party mods (e.g. Not Enough Crashes/мг/other packs),
unlike reusing a vanilla sound which could be missed/confused with other game audio.

**Alternatives considered**

1. Reuse an existing vanilla event — rejected: indistinguishable, no ownership.
2. External MP3 playback (JLayer) — rejected: extra dependency + licensing, out of scope.

**Impact**

- Spec sections: §6.3, README "Custom expiry sound"
- Stories: S07, S08

---

### DEC-004 — Mod id, display name, package

- **Date:** 2026-08-11
- **Status:** RESOLVED
- **Category:** Design

**Context**
Owner specified a project name for folder naming; the mod needs a Forge mod id and Java package.

**Decision**

- Mod id: `vault_modifier_alerts`
- Display name: `Vault Modifier Alerts`
- Base package: `io.haque.vault_modifier_alerts`
- Folder: `<repo>/vault_modifier_alerts/`

**Rationale**
Matches the owner-stated project name; package mirrors the Mod id for clarity and to avoid
collisions. Forge requires lowercase alphanumeric ids.

**Alternatives considered**

1. Shorter id `vma` — rejected: too generic, likely to collide.
2. `haque.vaultmodificeralerts` — rejected: id/package mismatch adds confusion.

**Impact**

- Spec sections: §4 (architecture), S01 (scaffold)
- Stories: S01

---

### DEC-005 — Context class exposes time left (verify at implementation)

- **Date:** 2026-08-11
- **Status:** RESOLVED
- **Category:** Design (verification required)

**Context**
F1/F2 need time-left in ticks per active temporal modifier. Spec §3 (verified internals) is
based on QOLHunters' use of `Entry.getContext().getTimeLeft()` returning `Optional<Integer>`.
The exact signature of the entry context class must be re-verified against the actual VH jar
(`C:\Users\Haque\Development\VH\the_vault` decompile, or `gradlew`-resolved dependency) at
implementation time.

**Decision**
At implementation start: decompile/inspect the VH jar and confirm the context class exposed by
`Modifiers.Entry.getContext()` actually has a `getTimeLeft(): Optional<Integer>` (or a
`getRemainingTime`/`getDurationLeft` equivalent). If the true signature differs, amend this
entry (status → RESOLVED via sub-entry) and adjust spec §5/§6 accordingly; choose names
matching the found symbols.

**Rationale**
The spec is prescriptive but was derived from the QOLHunters open-source mod; the underlying VH
API could differ between versions. Two minutes of verification prevents a cascade of bogus
compile errors.

**Alternatives considered**

1. Trust the spec blindly and adjust on compile errors — rejected: more expensive.
2. Mixin into `Entry` directly — rejected: relies on the same unknown API.

**Impact**

- Spec sections: §3, §5, §6
- Stories: S03, S04, S05, S06, S10, S11

---

### DEC-005-R1 — Context class confirmed via javap on the real VH jar (S01 gate)

- **Date:** 2026-08-11
- **Status:** RESOLVED (supersedes sub-entry status of DEC-005)
- **Category:** Verification record

**Context**
S01 gate requires every §3.1/§3.2 descriptor to be verified against the actual VH jar before
any mixin code is written. Verified with `javap -p` (`-c`/`-v` where noted) against the raw
CurseMaven artifact `vault-hunters-official-mod-458203-7967092.jar`
(`C:\Users\Haque\.gradle\caches\modules-2\files-2.1\curse.maven\vault-hunters-official-mod-458203\7967092\...jar`).

**Findings (all match spec §3.1/§3.2)**

1. `iskallia.vault.core.vault.Modifiers` — present; `getDisplayGroup()` is an **instance**
   method returning `it.unimi.dsi.fastutil.objects.Object2IntMap<VaultModifier<?>>`; the local
   `map` (slot 1) exists in the LocalVariableTable with exact name `map` and signature
   `Object2IntMap<VaultModifier<?>>` → `@Local(name="map")` resolves.
2. `iskallia.vault.core.vault.Modifiers$Entry` — nested class name is `Modifiers$Entry`;
   `getModifier()` → `Optional<VaultModifier<?>>`; `getContext()` → **declared return type**
   `iskallia.vault.core.vault.modifier.spi.ModifierContext`.
3. `iskallia.vault.core.vault.modifier.spi.ModifierContext` — has
   `getTimeLeft()` → `Optional<Integer>` (ticks). NO casts needed in the mixin: declare
   `ModifierContext context = instance.getContext();`.
4. The wrap call target `Liskallia/vault/core/vault/Modifiers$Entry;getModifier()Ljava/util/Optional;`
   exists inside `getDisplayGroup()` (offset 54).
5. `iskallia.vault.core.vault.overlay.ModifiersRenderer` — static fields
   `TEXT_BUFFER` (`MultiBufferSource$BufferSource`) and `MODIFIER_TEXT_RENDER_MODE`
   (`ModifiersRenderer$ModifierTextRenderMode`) present; the 6-arg static overload
   `renderVaultModifiers(Map<VaultModifier<?>,Integer>, PoseStack, boolean, float, Alignment, boolean)`
   exists (descriptor matches the spec's mixin string exactly: `ZFLiskallia/vault/util/Alignment;Z`).
6. `iskallia.vault.core.vault.modifier.spi.VaultModifier<P>` — abstract class; `getId()` →
   `ResourceLocation`; `getIcon()` → `Optional<ResourceLocation>`.
7. `iskallia.vault.core.vault.ClientVaults` — static `getActive()` → `Optional<Vault>` (present
   iff client is inside a vault).
8. `the_vault`'s `META-INF/mods.toml` declares only **forge** + **minecraft** as mandatory
   dependencies → dev client runs with Forge + VH only; no extra runtime deps needed for
   `runClient`.

**Decision**
Use the verified signatures verbatim for all mixin targets (§5.4A/B, §6.3 Strategy A) and the
confirmed time-capture expression `context.getTimeLeft()` (returning `Optional<Integer>`),
used as `context.getTimeLeft().orElse(null)` in `MixinModifiers` per spec §5.4B.

**Rationale**
Verification against the actual jar (not memory/QOLHunters) satisfies RULES.md §0.3 and the
S01 gate; all spec descriptors held, so no target-string adjustments were needed.

**Alternatives considered** — none; this is a verification record.

**Impact**

- Spec sections: §3, §5.4, §6.3
- Stories: S01 (gate), S03, S04, S10

---

### DEC-006 — Time-capture fallback hook

- **Date:** 2026-08-11
- **Status:** OPEN
- **Category:** Design (contingency)

**Context**
Primary capture point for time-left is `@WrapOperation` on `VaultModifier.Entry.getModifier()`
inside `Modifiers.getDisplayGroup` (spec §5.2 P2). If P2 cannot attach (name changed, loader
problem), a fallback is needed instead of failing the build.

**Decision**
Fallback: `@Redirect` (or plain `@Inject`) on `Map.entrySet()` in `ModifiersRenderer`'s
render loop (spec §6/C path) to capture `(modifier, timeLeft)` pairs at render time. For the
alert subscription the mixin must record the pair in the tracker before the renderer consumes
it. If P2 attaches fine, renderer-side capture is not used for tracking (kept only as a
diagnostic `debugLogging` dump).

**Rationale**
Keeping the fallback documented (rather than discovered mid-implementation) removes a class of
"stuck" states: the capture hook is the only hard dependency of both features.

**Alternatives considered**

1. Fail hard if P2 unavailable — rejected: graceful degradation preferred.
2. Capture via `TickingTimeout`/other VH systems — rejected: not verified, scope creep.

**Impact**

- Spec sections: §5.2 (P2), §6 (path C), §11 open item
- Stories: S04, S10

---

### DEC-008 — Mixins.json entries appear only with their story's mixin class

- **Date:** 2026-08-11
- **Status:** RESOLVED
- **Category:** Spec deviation (build/launch gate)

**Context**
Spec §2.4 (S01 DoD) defines `vault_modifier_alerts.mixins.json` with all three mixin entries
(`tracker.MixinVaultModifier`, `tracker.MixinModifiers`, `render.MixinModifiersRenderer`)
from day one. But S01 scenario 2 requires `runClient` to launch with **no mixin application
errors** — and Mixin 0.8.5 with `"required": true` hard-fails at startup when a listed mixin
class does not exist:

```
InvalidMixinException: The specified mixin '...tracker.MixinVaultModifier' was not found
, runClient FAILED
```

Those classes only arrive at S03 (duck interface), S04 (capture mixin), S10 (renderer mixin).

**Decision**
`src/main/resources/vault_modifier_alerts.mixins.json` keeps its spec §2.4 structure, but the
`client` array starts empty and each story (S03, S04, S10) adds its own entry **in the same
commit** that creates the mixin class. `"minVersion": "0.8"`, `"required": true`,
`"refmap"` wiring, and the mixin Gradle block stay untouched — the file's final state is
exactly spec §2.4.

**Rationale**
Every commit must remain launchable (RULES.md §6 story-sized commits, S01 gate scenario 2).
Stub mixin classes were rejected (RULES.md §2: no dead code). Deferring the entries is
zero-risk: mixin application of a listed-but-absent class is the only failure mode, and each
entry now lands atomically with its class.

**Alternatives considered**

1. Ship all three mixin classes as stubs at S01 — rejected: dead-code violations; every
   later story would rewrite them.
2. Set `"required": false` — rejected: would mask genuine target-attach failures in later
   stories (turns required-errors into warnings).
3. Keep literal §2.4 and accept runClient crash until S03 — rejected: breaks the S01 gate
   scenario 2 that this file is meant to serve.

**Impact**

- Spec sections: §2.4 (deviation — final content unchanged)
- Stories: S01, S03, S04, S10

---

### DEC-009 — Dev client loads pack mods from the Prism instance's mods folder

- **Date:** 2026-08-11
- **Status:** RESOLVED
- **Category:** Environment (S01 gate scenario 2)

**Context**
`runClient` with only Forge + `the_vault` on the classpath crashes at model baking:
`NoClassDefFoundError: top.theillusivec4.curios.api.type.capability.ICurioItem` thrown from
VH's own `MixinModelBakery` (`assets/the_vault/the_vault.mixins.json`) while loading block
models. VH 6574 requires its pack ecosystem (Curios etc.) even client-side; the S01 gate
scenario 2 (client launches with `the_vault`, no mixin errors) therefore needs those mods
present in the dev run.

**Findings**

- The user's matching environment is the Prism instance `The Vaulters S05 DEV`
  (`C:\Users\Haque\AppData\Roaming\PrismLauncher\instances\The Vaulters S05 DEV`):
  Forge **40.3.11** (matches `build.gradle`), `the_vault-1.18.2-3.21.5-remastered.6574.jar`
  (matches `gradle.properties` `vault_hunters_version=7967092`), plus QOL Hunters 0.42.12
  and ~207 other pack mods.
- `run/` is already in `.gitignore`, so dev-only copies never enter the repo.

**Decision**
Copy all jars from the instance's `minecraft/mods` into the project's `run/mods`, **excluding
`the_vault*.jar`** (it is already a Gradle `implementation fg.deobf(...)` dependency of the
dev run; a second copy in `run/mods` would cause a duplicate-mod-id error). This gives the
dev client the exact pack environment (incl. QOLHunters, which later matters for S13).

**Rationale**
Copy (not symlink) keeps the dev run independent of the instance's layout and gives a
reproducible S01/S13 environment; only one further copy step is needed (`DEC-009` note
below) if `run/mods` is ever wiped.

**Alternatives considered**

1. Copy everything including `the_vault` — rejected: duplicate mod id `the_vault` error at
   startup (userdev also discovers the classpath dependency).
2. Pin Curios alone via Gradle — rejected: VH's model path also touches GeckoLib, Mantle,
   etc.; chasing missing classes one-by-one repeats this crash.
3. `implementation` of a local `the_vault` on the classpath with a `run/mods` junction —
   still duplicates `the_vault`; rejected.

**Impact**

- Spec sections: none (dev environment only)
- Stories: S01 (scenario 2), S13, all later runtime smoke tests

---

### DEC-010 — runClient JVM args for netty reflective access on JDK 17.0.18

- **Date:** 2026-08-11
- **Status:** RESOLVED
- **Category:** Environment (build infra)

**Context**
`runClient` crashed in Forge's own `NetworkConstants.<clinit>` (netty 4.1.68 bundled with
Forge 1.18.2):
`UnsupportedOperationException: Reflective setAccessible(true) disabled`. Root cause:
Temurin/Adoptium **JDK 17.0.18** (2026-01 update; 17.0.12+) denies reflective
`setAccessible(true)` on `jdk.internal.misc.Unsafe` by default, and netty's
`PlatformDependent0` (loaded as module `io.netty.all` by modlauncher) needs it
(`IllegalAccessException: java.base does not export jdk.internal.misc to module io.netty.all`).

**Decision**
Add to the `runs.client` block of `build.gradle` (dev-run only; no effect on the shipped jar
or on the user's game):

```
jvmArgs '--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED',
        '--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED',
        '--add-opens=java.base/jdk.internal.misc=io.netty.all',
        '--add-exports=java.base/jdk.internal.misc=io.netty.all',
        '-Dio.netty.tryReflectionSetAccessible=true'
```

**Rationale**
The mod's runtime (player's pack) launches netty on the pack's own JVM config and is
unaffected; this only makes the Gradle dev client launchable on the machine's current JDK.

**Alternatives considered**

1. Install an older JDK (17.0.x < 17.0.12) — rejected: changing the machine's JDK for one
   dev-only task; add-opens is the standard netty fix.
2. `-Dio.netty.noUnsafe=true` alone — rejected: still allowed the module export failure for
   `io.netty.all`; the exports/opens are the direct fix.

**Impact**

- Spec sections: none (dev environment only)
- Stories: S01 (scenario 2) and every `runClient` smoke test after it

---

### DEC-011 — Runtime testing: user tests the built jar; no dev-client runs

- **Date:** 2026-08-11
- **Status:** RESOLVED
- **Category:** Process (owner instruction; replaces RULES.md §8 runtime smoke tests)

**Context**
The full pack (209 mods) cannot boot under ForgeGradle's parchment dev mapping: multiple pack
mods (canary, smoothboot, kubejs, botania, …) ship `@Shadow`/`@Overwrite` with hardcoded SRG
names (`m_36222_` etc.) that do not exist in the dev-mapped runtime, crashing the client
before the main menu — unrelated to this mod. Iterating removals is a whack-a-mole.

**Decision (owner instruction)**
`runClient` is **not used for testing at all**. The mod is validated by the owner manually:
they copy `build/libs/vault_modifier_alerts-<version>.jar` into their pack instance (Prism
`The Vaulters S05 DEV`) and test in-game. The owner tests "whenever meaningful changes are
there" and wants advance notice when a test round is needed.

**Consequences**

- The S01 gate's scenario 2 (dev client launch) is satisfied by this decision instead of a
  runClient run; the rest of S01 DoD stands (`./gradlew build` passes — verified 2026-08-11).
- RULES.md §8 runtime-smoke-test steps are superseded: every story that touches runtime
  behaviour ends with "jar built, user notified for manual test" rather than a dev-client run.
- The copies in `run/` are dev leftovers and are removed (folder stays gitignored); the
  DEC-010 `jvmArgs` stay in `build.gradle` in case a future dev-client run is ever wanted.
- The spec's §7 scenario text and RULES.md §8 are not amended file-by-file; this entry is the
  single source of truth for the deviation (per RULES §9: entry + status update).

**Impact**

- Spec sections: §7 scenario 2 (S01), §7 §8 verification workflows, §9 verification plan
- Stories: S01 gate, S05–S15 (every runtime-behaviour story)
- RULES.md: §8 (superseded for runtime tests)

---

### DEC-012 — Frame data recorded inside the capture mixin (buildSnapshot source)

- **Date:** 2026-08-11
- **Status:** RESOLVED
- **Category:** Spec clarification (design gap in §5.5)

**Context**
Spec §5.5's `EVALUATE` step needs `newSnapshot = buildSnapshot()` — the "tracked temporal
modifiers seen this frame (id → time)" — but spec §5.4C's tracker state exposes only
`lastSnapshot` (previous frame) and gives the tick-side no pointer to the frame's
`VaultModifier` objects or their duck values. Without a source for the current frame's
times, the engine has nothing to compare against `lastSnapshot`.

**Decision**
The capture mixin (`MixinModifiers.vma$captureTimeLeft`) records each seen entry into the
tracker the moment it runs: `ModifierTracker.recordFrameEntry(modifier.getId(), timeLeft)`,
writing into a new `currentFrame` map (id → ticks, `null` for "not temporal this frame" —
explicit absence per §5.4C note). Generation still bumps **once per frame**, done inside
`recordFrameEntry` when `currentFrame` is empty (first entry of a frame) — this satisfies
S04's "generation counter increments exactly once per frame with candidates" and is
equivalent to spec §5.4B's per-entry `markFrameProcessed()` while avoiding N bumps/frame.

**Rationale**
This is the minimal concrete reading of §5.5's `buildSnapshot()`: the only writer of time
values is the capture mixin, so the tracker is its natural sink. The tick handler (S05) copies
`currentFrame` via `consumeFrame()` (copy + clear + mark processed) at evaluation time; the
map is cleared per frame so an expired modifier that stops appearing becomes absent (fires,
F1-3/edge 3) rather than lingering.

**Alternatives considered**

1. Store the display-group `Object2IntMap` reference — rejected: it's the HUD's per-frame
   live object; reading it later is racy/opaque (values are display counts, not ticks).
2. Re-query `Entry.getContext()` from the tick side — rejected: entries aren't retained.

**Impact**

- Spec sections: §5.4B (tracker call), §5.4C (new `currentFrame` map), §5.5 `buildSnapshot()`
- Stories: S04, S05

---

### DEC-005-R2 — P2 wrap target bytecode-verified (DEC-006 contingency not needed so far)

- **Date:** 2026-08-11
- **Status:** RESOLVED (record; DEC-006 stays OPEN as in-game attach contingency)
- **Category:** Verification record

**Context**
DEC-006 defines a renderer-side capture fallback for the case where P2 (wrap
`Modifiers.Entry.getModifier()` inside `getDisplayGroup`) cannot attach.

**Findings**
`javap -c` on `iskallia.vault.core.vault.Modifiers` shows the call
`Liskallia/vault/core/vault/Modifiers$Entry;getModifier()Ljava/util/Optional;` present at
offset 54 inside `getDisplayGroup()` (see DEC-005-R1 finding #4). The `@WrapOperation` target
descriptor and the parenthesis method name both match the spec §5.4B mixin string, so the
mixin can attach at the bytecode level.

**Decision**
Proceed with P2 as primary capture. Renderer-side fallback (DEC-006) is not implemented now;
it remains the documented contingency if in-game test shows P2 not firing (attach happens at
runtime, after all). No code change required.

**Impact**

- Spec sections: §5.2
- Stories: S04

---

### DEC-007 — QOLHunters coexistence play-test

- **Date:** 2026-08-11
- **Status:** OPEN
- **Category:** Bug Workaround / Behavior (Schrödinger)

**Context**
If the user runs QOLHunters with its `temporalmodifiertimer` feature enabled alongside this
mod, both mods could mixin into the same `getDisplayGroup`/renderer methods and interfere
(duplicate capture, ordering toggles).

**Decision**
During Story S13, run a play-test with the real modpack (VH edition + QOLHunters) in a vault
with a temporal modifier. Validate that: (1) the HUD shows exactly the icons of the active
modifiers without duplication, (2) expiry alert fires exactly once, (3) no log spam/warnings
from mixin conflicts. If a conflict appears, record root cause here with DEC-NEW and re-scope
(possible fallback: gate ordering mixin behind config flag and disable under QOLHunters via a
detection point, e.g. mod container presence check).

**Rationale**
Mixin conflicts are the top technical risk of the project (spec §10); an explicit play-test
story with owner environment is the cheapest mitigation.

**Alternatives considered**

1. Assume coexistence, no test — rejected: spec §10 lists it as risk; too important.
2. Auto-detect QOLHunters and always disable ordering — rejected: premature, owner runs it but
   not always enabled.

**Impact**

- Spec sections: §9 (verification plan), §10 (risks), story S13
- Stories: S13

---

### DEC-013 — sounds.json "name" resolved against README drop path

- **Date:** 2026-08-11
- **Status:** RESOLVED
- **Category:** Verification record (spec clarification)

**Context**
Spec §5.3 shows `"name": "vault_modifier_alerts:sounds/temporal_expired"` while stating the
sound file expectation as `assets/vault_modifier_alerts/sounds/vault/temporal_expired.ogg`
(with a note claiming the directories match — they do not under any resolve rule). The README
(§"Custom expiry sound") and DEC-003 fix the authoritative drop path:
`assets/vault_modifier_alerts/sounds/vault/temporal_expired.ogg`.

**Findings**
`javap -c` on `Sound.getPath()` (forge-1.18.2-40.3.11_mapped_parchment joined jar) shows it
builds `new ResourceLocation(getNamespace(), "sounds/" + getPath())` — i.e. the client
**prepends** `sounds/` to the name's path. Therefore:
- spec example name `...:sounds/temporal_expired` → `assets/.../sounds/sounds/temporal_expired.ogg` (wrong, double dir);
- name `vault_modifier_alerts:vault/temporal_expired` → `assets/vault_modifier_alerts/sounds/vault/temporal_expired.ogg` (matches README drop path).

**Decision**
`sounds.json` ships `"name": "vault_modifier_alerts:vault/temporal_expired"`. The registered
`SoundEvent` id stays `vault_modifier_alerts:temporal_expired` (registry id is independent of
the file path); sounds.json key stays `temporal_expired`.

**Impact**

- Spec sections: §5.3 (example corrected via decision)
- Stories: S07

---

### DEC-014 — S10: Strategy A applied; class/package alignment corrections

- **Date:** 2026-08-11
- **Status:** RESOLVED
- **Category:** Verification record

**Context**
S10 DoD requires the chosen reorder strategy (§6.3 A/B/C) recorded, and the spec §6.3 mixin
snippet leaves the `ModifiersRenderer` import implicit.

**Findings**
`@ModifyVariable` (Strategy A) compiles and the `renderVaultModifiers(...)` descriptor matches
DEC-005-R1 #5 exactly. `ModifiersRenderer` actually lives in
`iskallia.vault.core.vault.overlay` (not `iskallia.vault.util`); `VaultModifier` in
`iskallia.vault.core.vault.modifier.spi` (both confirmed against the 7967092 jar).
Computed refmap is empty — expected, all mixins are `remap = false` (runtime class names).

**Decision**
Strategy A in `mixin/render/MixinModifiersRenderer` with the corrected packages. Mixin callback
wraps `ModifierOrdering.reorder` in a narrow `try/catch (RuntimeException)`, logging once and
falling back to the vanilla map (NFR-7). Strategy B (`@Redirect` on `Map.entrySet()`) and
Strategy C (`@WrapOperation` on the iterated collection) remain documented fallbacks if the
runtime dev-client shows ordinal mismatch.

**Impact**

- Spec sections: §6.3, NFR-7
- Stories: S10, S11, S12

---

### DEC-015 — Temporary default alert sound: vanilla note block pling

- **Date:** 2026-08-11
- **Status:** RESOLVED — fully superseded by DEC-018 (2026-08-12): the generic `soundEvent`
  config key was removed; sounds are per-modifier `soundOverrides` only.
- **Category:** Scope / Test enablement

**Context**
The owner has no custom `.ogg` asset available yet (spec §11-1). To make the first manual
test round (DEC-011) audible without the user editing the generated toml by hand, the
`soundEvent` config default is set to `minecraft:block.note_block.pling`.

**Decision**
`VmaClientConfigs.SOUND_EVENT` default = `"minecraft:block.note_block.pling"` while the owner
has no asset. The registry entry `vault_modifier_alerts:temporal_expired`, `sounds.json` and
`en_us.json` remain as shipped (S07); playback falls back to the configured event only at
runtime. When the `.ogg` lands at
`assets/vault_modifier_alerts/sounds/vault/temporal_expired.ogg`, revert this default to
`vault_modifier_alerts:temporal_expired` (spec §6.2 default).

**Rationale**
Audible cue now, zero functional changes to the wiring; the owner can also still override via
the toml at any time.

**Alternatives considered**

1. Keep spec default and instruct owner to edit the toml — rejected: one more manual step in
   the test loop.
2. Bundle a placeholder `.ogg` — rejected: no asset available and licensing uncertainty.

**Impact**

- Spec sections: §6.2 (temporary divergence, reverted later), §11-1
- Stories: S09, S14

---

### DEC-016 — Drop MixinExtras: vanilla `@Redirect` capture (Option B)

- **Date:** 2026-08-12
- **Status:** RESOLVED
- **Category:** Design / Build

**Context**
The S04 capture mixin originally used MixinExtras (`@WrapOperation` + `@Local(name = "map")`),
mirroring QOLHunters P1 (DEC-005-R2). That required an extra dependency (`mixinextras-forge`
0.4.1) bundled at runtime via jarJar, inflating the jar (~180 KB nested module) and adding a
second mixin framework to the classpath. The `getModifier()` INVOKE in
`Modifiers.getDisplayGroup()` has exactly **one call site** (offset 54, DEC-005-R1), so the
capture is implementable with stock Mixin as a vanilla `@Redirect`.

**Findings**
- `@Redirect` handler receives the target instance as its first parameter and must call the
  redirected method itself — same JVM-stack shape as `@WrapOperation` for this single-call-site
  case; the returned `Optional` is passed through unchanged, so the renderer sees identical
  values.
- The only MixinExtras feature in use, `@Local(name = "map")`, exists to reset the duck time
  when a modifier is not yet in the display map. That reset is equivalent to setting the duck
  time to the raw context value every call, because an entry's `getModifier()` runs in the same
  loop iteration in which the modifier is added to the map.
- QOLHunters 0.42.12 bundles `META-INF/jarjar/mixinextras-forge-0.4.1.jar` itself; it is a
  consumer, not a provider of MixinExtras to other mods. Removing our dependency therefore
  cannot break QOLHunters' own mixins (coexistence parity kept — S13). Note the reference's
  duck-update guards only protect its text-renderer, not alert detection.

**Decision**
Replace `MixinModifiers`' `@WrapOperation` with a vanilla `@Redirect` on the same target
(`Liskallia/vault/core/vault/Modifiers$Entry;getModifier()Ljava/util/Optional;`). Capture rule
(owner-approved F2-7 alignment):

- `timeLeft = context != null ? context.getTimeLeft().orElse(null) : null`
- duck time set **unconditionally**: `accessor.vma$setTimeLeft(timeLeft)`
- frame entry recorded with the same raw value:
  `ModifierTracker.recordFrameEntry(modifier.getId(), timeLeft)`

Remove the mixinextras dependencies from `build.gradle` (annotationProcessor `-common` and
jarJar `-forge` lines). `MixinModifiersRenderer` already uses stock
`@ModifyVariable` (DEC-014 Strategy A) and stays untouched. Result: ~22 KB jar, no `-all.jar`,
no nested `META-INF/jarjar/`, and the requirement to install the fat jar disappears.

**Behavior deltas vs the previous implementation**

1. F2-7 fix: a modifier still shown on the HUD whose context stops reporting a countdown
   (e.g. became permanent) is now treated as **permanent for that frame**; the old code
   retained a stale positive countdown indefinitely — a latent spec deviation now closed.
2. Fresh-frame reset (`!map.containsKey` → null) is subsumed: the first `getModifier()` call
   for a modifier sets its duck to the raw value anyway.
3. Bounded-decrease guard (`timeLeft < current`) is dropped; the vault's own display map and
   QOLHunters read the same `getTimeLeft()` each frame, so a still-active countdown is
   re-reported every frame — the guard only masked same-frame staleness.

**F1 expiry impact (traced, no change in stable paths)**
Snapshot values recorded are identical in every stable path: the snapshot/`isActive`/one-shot
logic compares transitions (`> 0` → `0`-or-absent). Natural expiry either reports
`Optional.of(0)` (recorded as 0 → inactive) or the entry disappears (`getModifier()` not
called → absent this frame → inactive); both fire exactly as before.

**Known edge (documented, mitigated)**
If `getTimeLeft()` ever transiently returned empty *while a countdown was still alive*, the
new code records `null` for that frame (old code retained the last value): one spurious alert,
recovered by `reArm` on the next active frame (DEC-012 semantics). The same read would make
QOLHunters' countdown text flicker, which does not occur in practice — this mod consumes the
same source. **Mitigation:** if a spurious fire is ever observed while a countdown still
renders, restore the bounded-decrease guard on the duck update (F1 stays authoritative on raw
records) and record a follow-up DEC.

**Alternatives considered**

1. Keep `@WrapOperation` + jarJar and only swap jar classifiers (single-jar naming) —
   rejected: keeps a second mixin framework + nested module for one call-site wrap; jar grew
   ~180 KB and the install story stayed complicated.
2. `@Redirect` with the old guard preserved and no map — rejected: leaves the F2-7
   deviation in place; owner approved closing it.
3. `@Inject` at HEAD of `getDisplayGroup` + re-reading entries — rejected: duplicates the
   loop work and loses per-entry ordering context.

**Impact**

- Spec sections: §2.1/§2.2 (MixinExtras dependency row + snippet removed), §3.2 (P1 note
  added: deviation from reference), §5.4 B (capture snippet replaced), §6.3 (Strategy C
  requires re-adding the dependency)
- Stories: S04 (capture mechanism), S14 (build surface); all others unaffected
- Build: `build.gradle` loses two dependency lines; installable jar becomes the single thin
  `vault_modifier_alerts-0.1.0.jar`; README install step unchanged (it already names the
  plain jar)

---

### DEC-017 — `@Redirect` handler must be non-static (instance target)

- **Date:** 2026-08-12
- **Status:** RESOLVED
- **Category:** Verification record (bug fix from first in-game launch)

**Context**
First in-game launch of the `feature/drop-mixinextras` branch crashed during mod loading:
`InvalidInjectionException: 'static' modifier of handler method does not match target` while
applying `tracker.MixinModifiers` to `iskallia/vault/core/vault/Modifiers::getDisplayGroup`.

**Findings**
- `getDisplayGroup()` is an **instance** (non-static) method — verified with `javap` against
  both the dev jar (`vault-hunters-official-mod-458203-7967092.jar`) and the runtime jar the
  owner tests with (`the_vault-1.18.2-20.0.3-remastered.6872.jar` in the "Vault Hunters Third
  Edition - Remastered" Prism instance).
- Vanilla `@Redirect` enforces matching staticness between handler and target method.
  `@WrapOperation` (MixinExtras, previous implementation) tolerated a static handler, so the
  pattern carried over verbatim — which the stock injector rejects.
- `ModifiersRenderer.renderVaultModifiers(...)` 6-arg overload is **static** in both jars,
  and `MixinModifiersRenderer`'s `@ModifyVariable` handler is static — unaffected.
- `MixinVaultModifier` performs no method injection — unaffected.

**Decision**
`vma$captureTimeLeft` becomes a non-static (instance) handler; first parameter stays the
target instance (`Modifiers.Entry`). Spec §5.4 B snippet corrected to match. All other
injection/plumbing unchanged (DEC-016 capture semantics preserved: unconditional raw capture,
F2-7 alignment, frame recording).

**Impact**

- Spec sections: §5.4 B (snippet corrected + staticness note added)
- Stories: S04
- Test note: this launch also confirms the owner's runtime instance is "Vault Hunters Third
  Edition - Remastered" (`the_vault` 20.0.3-remastered.6872); the verified §3 target shapes
  are identical in both jars, so DEC-005-R1 descriptors apply to both environments.

---

### DEC-018 — Bundled Champion's Domain sound + per-modifier sound overrides

- **Date:** 2026-08-12
- **Status:** RESOLVED
- **Category:** Scope / Design

**Context**
The owner supplied an audio asset for the alert (a meme clip extracted from an MP4, trimmed and
normalized to ~1.31 s) and scoped it explicitly: the sound plays **only** for **Champion's
Domain** temporal expiration. Renamed `champ_domain_expired.ogg` accordingly.

**Findings (asset validation)**
`champ_domain_expired.ogg` (15,516 bytes): Ogg container (`OggS` magic), **Vorbis** codec
(not Opus — MC 1.18.2's OpenAL plays Vorbis only), 44,100 Hz, stereo, last-page granule
57,600 samples → ~1.31 s. Meets spec §5.3 requirements; mono conversion unnecessary.

**Decision**
1. Sound event id renamed `temporal_expired` → **`champ_domain_expired`** (registry id, sounds.json
   key, subtitle key follow; sounds.json name `vault_modifier_alerts:vault/champ_domain_expired`
   resolves to the asset per the DEC-013 rule). The generic `temporal_expired` event is removed.
2. Per-modifier sound overrides implemented at v1 (previously spec §5.6/§11-3 "v2 only"): new
   client config `soundOverrides: Map<modifierId, soundEvent>`, default
   `{ "the_vault:champion_domain": "vault_modifier_alerts:champ_domain_expired" }`. Resolution:
   override for the firing modifier id wins, else generic `soundEvent`.
3. **DEC-015 fully superseded** — the generic `soundEvent` config key is **removed entirely**
   (pled default pling and the temporary-test rationale are gone). Sound resolution is
   **override-only**: every watched modifier must carry its own `soundOverrides` entry (i.e.
   its own .ogg-backed event) or the expiration stays silent with a warn-once log
   (misconfiguration surfaces, never a wrong sound).
4. `ExpiryAlertEngine.fire` resolves per id via
   `VmaClientConfigs.resolveSoundEventId(modifierId)` (nullable; null → warn-once + suppress);
   `AlertSoundPlayer` API unchanged (single DRY entry point).

**Alternatives considered**

1. Rename asset to `temporal_expired.ogg` and keep one global event — rejected: would play the
   Champ's Domain cue for every watched id, contradicting the owner's scoping.
2. Hard-code "champion_domain → champ sound" in code — rejected: violates F1-8
   (config-driven), and the map generalizes cleanly to future mods' sounds.

**Impact**

- Spec sections: §4.1, §5.2 (default + new `soundOverrides` row), §5.3, §5.6 (promoted),
  §7.8 (S07 ids), §11-1/§11-3 (updated), §12 tail corruption fixed (duplicated §11/§12 block)
- Stories: S07 (id), S09/S14 (config surface: generic `soundEvent` removed), README updated
- Config note: existing `vault_modifier_alerts-client.toml` in the pack keeps old values; the
  test round must delete it (or it keeps the pling for champion domain too)

---

### DEC-019 — Client commands for debug + sound toggles

- **Date:** 2026-08-12
- **Status:** RESOLVED
- **Category:** Scope / Design

**Context**
The owner asked for in-game commands to toggle debug logging and expiry sounds (reporting the F2
ordering issue and wanting a sound kill-switch while testing). `debugLogging` existed in the toml
but required a restart to edit; `alertSoundEnabled` did not exist.

**Findings (API availability)**
Forge 1.18.2-40.3.11 provides `net.minecraftforge.client.event.RegisterClientCommandsEvent`
(FORGE bus, client dist) with `getDispatcher()` returning
`CommandDispatcher<CommandSourceStack>`, plus `net.minecraftforge.client.ClientCommandSourceStack
extends CommandSourceStack`. Verified via `javap` on `forge-1.18.2-40.3.11-universal.jar`.
Client commands execute in both single-player and multiplayer via Forge's client command handler.

**Decision**
1. New `command/VmaClientCommands` (`@Mod.EventBusSubscriber(Dist.CLIENT, Bus.FORGE)`), registering
   under literal `vma`:
   - `/vma debug on|off` → writes existing `debugLogging`.
   - `/vma sound on|off` → writes new config `alertSoundEnabled` (default `true`,
     `[Expiry Alerts]`), read by `ExpiryAlertEngine.fire` before playback (fired-marking still
     happens; missing-override warn is independent of the master switch).
   - `/vma status` → config state, last HUD order (via `ModifierOrdering.getLastOrdered()`),
     vault/frame state, per-watched-id ticks + sound override.
2. Toggles are config-backed (ForgeConfigSpec `.set()`); Forge persists them on game exit —
   the owner confirmed persistence is desired.
3. `ModifierOrdering` now records the last ordered id list (volatile, debug-support only).

**Impact**
- Spec: §5.2 (`alertSoundEnabled` row), new §7.17 S16 story, §14 tests 11–13; README commands
  table
- Stories: S16 (new), S15 (diagnostics vehicle)
- Config note: `alertSoundEnabled` missing in an existing toml is auto-added by Forge on save

---

### DEC-020 — F2 order = anti-anchor edge (permanents first, temporals last)

- **Date:** 2026-08-12
- **Status:** RESOLVED
- **Category:** Design (supersedes DEC-001's ordering direction)
- **Relates to:** DEC-001 (original sort order), DEC-014

**Context**
The owner reported on the in-game HUD (Vault Hunters 3rd Edition - Remastered) that temporal
modifiers were "not shown first / at the top" — a mismatch between expected and actual layout.
Bytecode-verified facts about the remastered HUD module (`VaultModifiersModule`,
`iskallia.vault.client.render.hud.module.vault`):
- The HUD map is built per frame from `Modifiers.getDisplayGroup()` (+ favours + influences) —
  our `MixinModifiers` capture redirect fires on this exact path (verified: `Modifiers$Entry`
  has `getContext()`, the `Entry.getModifier()` redirect site exists at the expected offset).
- The module renders via `ModifiersRenderer.renderVaultModifiers(Map, PoseStack, boolean, float,
  Alignment, boolean)` — exactly the 6-arg method our `MixinModifiersRenderer` reorders.
- The `Alignment` comes from `ModOptions.VAULT_MODIFIERS_ALIGNMENT` (default
  `new Alignment(RIGHT, BOTTOM)`; the overlay config file is NOT used by this module).
- The renderer fills rows row-major from the anchor: `BOTTOM` → index 0 draws on the **bottom**
  row, `TOP` → index 0 on the top row. First map entries sit adjacent to the anchor.

**Conclusion**
The ordering code was already applying (sound fired on the old jar → capture pipeline alive),
so "temporals first" landed on the **bottom** row of the bottom-anchored HUD — which reads
top-down as "permanents before temporals". This is a layout/expectation mismatch, not a broken
reorder.

**Decision**
Per the owner's explicit placement request ("temporal modifiers … shown at BOTTOM when TOP
alignment is selected and TOP priority when BOTTOM is selected"), temporals move to the
**anti-anchor edge**: map order = **permanents first (vanilla relative order), temporal bucket
last** (sorted by time within the bucket per `sortTemporalDescending`, DEC-021). Because the renderer
fills from the anchor, "last in map order" lands on the block's outer row under BOTH vertical
alignments — the owner's rule holds without touching the vanilla renderer or horizontal
alignment. `ModifierOrdering.reorder` is updated accordingly (pass 1 permanents, pass 2 sorted
temporals; key→value pairing preserved; `size()<2`/disabled guards unchanged).

**Impact**
- Spec: §6.1 F2-1/F2-2/F2-3 (order direction), §6.3 snippet, §7.12 S11 scenario, §14 test 6;
  README F2 row; DEC-001's *order direction* is superseded for the HUD output (the time-sort
  comparator semantics remain configurable)
- Config: `sortTemporalAscending` replaced by `sortTemporalDescending` (default `true`, see
  DEC-021); `sortTemporalDescending` applies inside the temporal bucket
- Diagnostics: `/vma status` (DEC-019) exposes the exact applied order for verification

---

### DEC-021 — Temporal bucket direction: longest-lasting first (descending)

- **Date:** 2026-08-12
- **Status:** RESOLVED
- **Category:** Design (overrides DEC-001's sort direction default)
- **Relates to:** DEC-001, DEC-020

**Context**
After shipping DEC-020 (temporals at the anti-anchor edge, sorted ascending by default), the
owner tested in-game and confirmed the placement works but asked: "Within the temporal group, it
should be reversed" — i.e. longest-lasting temporal first, soonest-expiring last.

**Decision**
1. Reverse the default bucket direction: descending (highest remaining ticks first).
2. Config key renamed for clarity: `sortTemporalAscending` → **`sortTemporalDescending`**
   (default `true` = longest-lasting first; `false` = soonest first). The old key is
   ignored if left in an existing toml (harmless); the test round deletes the toml anyway.
3. `ModifierOrdering` sorts via `HUD_ORDERING_DESCENDING.get() ? byTime.reversed() : byTime`.

**Impact**
- Spec: §6.1 F2-2, §6.2 table row, §6.3 snippet, §7.12 S11 scenario, §7.13 S12 scenario;
  DEC-001 decision text amended with a supersede note
- Stories: S11, S12
- Config: new key `sortTemporalDescending`; jar version stays 0.1.1

---

### DEC-022 — F3 operation selector: re-roll operations only; reset_potential automatic

- **Date:** 2026-08-15
- **Status:** RESOLVED
- **Category:** Design
- **Relates to:** F3_AUTO_REROLL_PLAN.md §3/§4

**Context**
The Artisan Station exposes many operations through
`VaultArtisanStationContainer.getModificationActions()` (reforge_all, reforge_affix_prefix/
suffix, reforge_implicits, reset_potential, flat modifiers, etc.). The F3 selector must list
only operations that actually roll modifiers toward a target — and the owner approved
auto-using `reset_potential` (Opportunistic Focus) instead of listing it.

**Decision**
The panel lists only the 4 re-roll operations (`the_vault:reforge_all`,
`reforge_affix_prefix`, `reforge_affix_suffix`, `reforge_implicits`), identified via
`ModifierCatalog.isRerollOperation` (OperationScope != null). `reset_potential` is **not**
selectable: the engine presses it automatically (at most once per run) when the selected
operation is disabled for lack of potential and `autoResetPotential` (default `true`) is on;
if it cannot be pressed, the run stops with OUT_OF_POTENTIAL. Note reforge_all only re-rolls
PREFIX+SUFFIX (implicits untouched — jar-verified `reForgeAllModifiers`).

**Rationale**
Every listed operation is a chaseable roll (a run on `reset_potential` could never succeed —
it yields no modifier); reusing the container's own action list keeps the UI honest and
version-robust.

**Alternatives considered**

1. List `reset_potential` as a fifth operation — rejected: it never rolls a modifier, so a
   run could never reach SUCCESS on it.
2. Hard-code the auto-reset without a config — rejected: the owner asked for a toggle.

**Impact**

- Plan: §3 (catalog), §4.2 (selector)
- Config: `autoResetPotential`
- Stories: F3 (engine, panel, commands)

---

### DEC-023 — F3 panel drawn inside the station screen via mixin (no separate Screen)

- **Date:** 2026-08-15
- **Status:** RESOLVED
- **Category:** Design

**Context**
The owner required the F3 GUI to never overlap/obstruct the Artisan Station GUI. A separate
`Screen` cannot satisfy this: opening one replaces the station screen (breaking container
rendering) — and the station's window + buttons occupy most of the screen. The station
screen renders via `render` (SRG `m_6305_`, inherited from `AbstractElementContainerScreen`)
and receives clicks via `mouseClicked` (SRG `m_7933_`); the private `attemptCraft`
(`GearModificationAction`) is the exact code path of a button press.

**Decision**
`RerollPanel` is a plain drawable rendered at TAIL of `m_6305_` by
`mixin/artisan/MixinVaultArtisanStationScreen`, anchored **outside** the station window rect
(right side preferred, left fallback, clamped to screen bounds). Clicks inside the panel
rect are consumed at HEAD of `m_7933_` (cancellable) before the station sees them. The
engine triggers presses through the duck interface `ArtisanStationScreenAccessor`
(`vma$triggerAction` → `@Shadow attemptCraft`); a HEAD inject on `attemptCraft` feeds
`AutoRerollEngine.onCraftTriggered` (cooldown/double-click protection). No Screen subclass
is created; the panel follows the station lifecycle (engine stops on screen close). The P
key (KeyBindings, `ClientRegistry.registerKeyBinding` in FMLClientSetupEvent) toggles panel
visibility.

**Rationale**
Single-screen overlay is the only design that both keeps the station interactive and
satisfies the no-overlap requirement; the duck interface is the established accessor
pattern (`tracker.VaultModifierTimeAccessor`).

**Alternatives considered**

1. Standalone `RerollScreen` opened on top — rejected: replaces the station, breaks its
   rendering, cannot avoid overlap.
2. Reflection on private `attemptCraft` — rejected: fragile, bypasses the accessor pattern.
3. Render a vanilla `Screen`/widget layer inside the station — rejected: over-engineering.

**Impact**

- Plan: §3 (mixins), §4.1 (panel)
- Files: `mixin/artisan/MixinVaultArtisanStationScreen`, `ArtisanStationScreenAccessor`,
  `feature/reroll/RerollPanel`, `event/KeyBindings`; mixins.json entry added atomically
- Stories: F3 (panel, mixin)

---

### DEC-024 — F3 applicability guard source: VaultGearTierConfig groups per scope

- **Date:** 2026-08-15
- **Status:** RESOLVED
- **Category:** Design

**Context**
The owner required that impossible targets (e.g. an attack-damage modifier on a helmet) can
never be selected or chased. The candidate pool must come from the same data the station's
re-roll itself uses, or the guard and the roll would disagree.

**Decision**
`ModifierCatalog` derives candidates from
`VaultGearTierConfig.getConfig(gear).getModifierGroup(ModifierAffixTagGroup)` for the
operation's scope (PREFIX / SUFFIX / IMPLICIT / PREFIX_SUFFIX — affix groups via
`ModifierAffixTagGroup.ofAffixType`), taking each `ModifierTierGroup` whose
`getModifiersForLevel(gear item level)` is non-empty; display names come from the
`VaultGearAttributeRegistry` attribute reader. `ModifierCatalog.isApplicable` re-validates
the target at **every** engine evaluation, so a gear swap mid-run that removes the target
stops the run with INVALID_TARGET instead of pressing a disabled action.

**Rationale**
A single source of truth identical to the roll itself; runtime re-validation covers the
mid-run gear-change case that a static selector could not.

**Alternatives considered**

1. Static allow-list of modifier ids — rejected: version-sensitive, diverges from the roll
   pool.
2. Always allow, rely on the game to reject — rejected: violates the owner requirement.

**Impact**

- Plan: §3 (catalog), §4.3 (INVALID_TARGET)
- Stories: F3 (catalog, engine, panel)

---

### DEC-025 — F3 engine press semantics, stop reasons & 1.18.2 API facts

- **Date:** 2026-08-15
- **Status:** RESOLVED
- **Category:** Design

**Context**
The F3 engine must press the exact same button path as a player click (canApply gate +
bronze cache invalidation + `VaultArtisanRequestModificationMessage`) without reimplementing
any game mechanic (owner constraint), classify why an action is disabled, and alert the user
with a stop reason. During implementation, several 1.18.2 API facts had to be pinned down.

**Decision**
`AutoRerollEngine` presses only through the accessor (`attemptCraft`); presses are
rate-limited by `tickInterval` (default 15 ticks); a roll is in-flight until the gear slot
stack changes (`ItemStack.matches` on the copy taken at press time; `rollTimeoutTicks`
default 60 → TIMEOUT); `maxRolls` (0 = unlimited) caps presses → MAX_ROLLS; a disabled
action is classified with `VaultGearCraftingHelper.reducePotential(gear.copy(), player,
modification)` — `false` ⇒ OUT_OF_POTENTIAL (one auto `reset_potential` per run if enabled
and available, else stop), otherwise OUT_OF_MATERIALS. Stop reasons: SUCCESS (target
modifier id present in the gear's modifiers for the operation scope), NO_GEAR,
OUT_OF_MATERIALS, OUT_OF_POTENTIAL, INVALID_TARGET, MAX_ROLLS, TIMEOUT, SCREEN_CLOSED,
STOPPED. SUCCESS plays `successSoundEvent`, every other stop plays `stopSoundEvent`
(defaults `minecraft:block.note_block.pling`).

**Verified 1.18.2 facts**

- `ItemStack.matches(ItemStack, ItemStack)` is the static tag-compare; the two-arg static
  `is(...)` form is 1.19+.
- `getGuiLeft()`/`getGuiTop()`/`getXSize()`/`getYSize()` do **not** exist in vanilla 1.18.2
  `AbstractContainerScreen` (confirmed against the official Mojang client mappings) — they
  are **Forge 1.18.2 patches**; VH's own `AbstractElementContainerScreen.getGuiSpatial()`
  bytecode calls them, proving their runtime presence. Our panel uses them directly.
- Java 17 (project target) forbids `case null` in switch statements.
- `m_6305_` (render) is declared on `AbstractElementContainerScreen`; `m_7933_
  (mouseClicked)` on `VaultArtisanStationScreen`; both public — verified on the SRG jar.

**Rationale**
Zero game-mechanic reimplementation (owner constraint); classification reuses the game's own
potential reducer on a copy (client-safe, read-only), so stop reasons match what the player
would see.

**Alternatives considered**

1. Send `VaultArtisanRequestModificationMessage` directly — rejected: bypasses the canApply
   gate and bronze cache, duplicating game logic.
2. Click blind on disabled buttons and guess the reason — rejected: wrong classification,
   no reliable stop reasons.

**Impact**

- Plan: §4.3 (engine), §3 (panel anchor)
- Stories: F3 (engine, tick wiring, commands, sounds)

---

---

### DEC-026 - Auto-reroll panel as a VH framework element + min-value threshold

- **Date:** 2026-08-15
- **Status:** RESOLVED
- **Category:** Design

**Context**
The F3 panel was previously drawn/clicked directly from the artisan screen mixin using raw
rect math. The Remastered VH 3.15 screen framework draws everything through elements, so
the panel was refactored into a real framework element added from the screen constructor
(`addElement(RerollPanelElement.create(screen))` in `<init>` RETURN, exactly like reference
mods), rendering and click-routing through the framework pipeline.

**Decision**
Two classes, split model/rendering:

1. `RerollPanelElement` (thin, `io.haque.vault_modifier_alerts.feature.reroll`) -
   `AbstractSpatialElement` + `IRenderedElement` + `IGuiEventElement`; positioning in
   `layout((screenSize, gui, parent, world) -> world.positionXY(x, y))` anchored left of
   the station window with right-side fallback (`MARGIN = 22`); `render`/`onMouseClicked`
   delegate to the model. Panel size constants live in `RerollPanel` (single source of
   truth, 150x122).
2. `RerollPanel` (model + raw draw/hit code) - selection state (operationIndex,
   targetIndex), min-value threshold state (minInputText draft, thresholdEnabled,
   thresholdValue), `draw(...)`, `hitTest(...)`, `handleClick(...)`, and the keyboard
   feed `acceptChar(char)` / `inputKey(int)` / `isMinInputFocused()`.

Min-value threshold rules:

- Draft accepts digits and at most one `.` (max 6 chars); empty draft = "any" (no
  threshold). Commit on Enter, KP-Enter, Escape, or focus loss (`commitMinInput()`).
- Commit strips a trailing `.`, parses, and clamps to the target's reachable
  `RollRange` (min/max in display units; percent ranges are stored as fractions and
  converted x100 in `ModifierCatalog.rollRange`); the draft is normalised with
  `formatDisplay(value, false)` (no `%` in the draft - the suffix is only added when
  drawing the committed value).
- The `<`/`>` arrows on the Min row step by `RollRange.step()` (clamped); the field is
  only rendered/clickable when the target range is numeric.
- Because the framework does not route typed chars to elements owned by a `Screen`
  (only click/render/update), `ClientTickEvents` feeds the field through
  `ScreenEvent.KeyboardKeyPressedEvent.Pre` (`inputKey`) and
  `ScreenEvent.KeyboardCharTypedEvent.Pre` (`acceptChar`), both cancelling the event
  when consumed. `isMinInputFocused()` gates the P-key toggle so typing never toggles
  the panel. Escape while focused is consumed (does not close the screen).

**Verified framework API facts (reference for future widget work)**

- `AbstractSpatialElement` exposes `public final int x()/y()/width()/height()`.
- Element `layout` takes a 4-arg lambda `(ISpatial screenSize, ISpatial gui, ISpatial
  parent, ISpatial world)`; `gui.left()/right()/top()` describe the window rect.
- Widget surface verified by javap (not yet used - raw draw kept for now):
  `DropdownElement(ISpatial, List<String>, Consumer<String>)`;
  `NineSliceTextInputElement(ISpatial, NineSlice$TextureRegion, Font)` +
  `setPadding/setMaxLength/setUsePlaceholder/setPlaceholderText/onTextChanged/
  onEnterPressed/setText/getText/setFocused/isFocused` + `protected isValidChar(char)`;
  `NineSliceButtonElement(ISpatial, NineSliceButtonTextures, Runnable)` +
  `label(Supplier<Component>, LabelTextStyle$Builder)` + `setDisabled/setVisible(
  Supplier<Boolean>)`; `LabelElement(pos, component, style)` / `(pos, size, component,
  style)` + `set(Component)/setSupplier(...)`; `ScreenTextures` has
  `INSET_BLACK_BACKGROUND`, `BUTTON_EMPTY_TEXTURES`,
  `BUTTON_EMPTY_DARK_GRAY_TEXTURES`, `BUTTON_EMPTY_GREEN_TEXTURES`.

**Rationale**
The framework pipeline now owns render ordering, click routing, and z-order; the model
class keeps all state and validation testable without the UI. Threshold guards reuse the
same `ModifierCatalog.rollRange` data the game's own roll uses (see DEC-022), so "never
exceed the target's max roll" holds by construction.

**Alternatives considered**

1. Full widget tree (DropdownElement + NineSliceTextInputElement) - rejected for now:
   the raw-draw port was the minimal safe refactor; the widget surface above is verified
   and documented for a future pass.
2. Keep mixin-driven drawing - rejected: fights the framework element pipeline.

**Impact**

- Plan: 3 (panel anchor), 4.4 (threshold)
- Stories: F3 (panel, threshold)
- Caveat: in-game smoke test of the new element panel (rendering, min-field focus, step
  arrows, P-key behaviour) has NOT been run yet - install
  `build/libs/vault_modifier_alerts-<mod_version>.jar` (see DEC-027) into the Prism
  instance first.

---

---

### DEC-027 - Build & install workflow for the VH instance (session reference)

- **Date:** 2026-08-15
- **Status:** RESOLVED
- **Category:** Toolchain

**Context**
The mod is developed against the Remastered VH pack installed via Prism Launcher, and
every session needs the same toolchain facts.

**Decision**

- VH jar to inspect: `the_vault-1.18.2-20.0.3-remastered.6872.jar` inside the instance's
  `minecraft/mods/` folder (no decompile needed). VH classes are NOT obfuscated, so
  mixins use `remap = false` and real method names.
- Signature verification without decompiling:
  `javap -p -classpath <vault-jar-path> 'iskallia.vault.client.gui.framework.element.spi.<Class>'`
  (also works for `element.<Class>`, `screen.block.<Class>`, etc.).
- Build: `gradlew build` (Java 17); artifact lands at
  `build/libs/vault_modifier_alerts-<mod_version>.jar` (`mod_version` in
  `gradle.properties`, currently 0.2.4). Compile-only fast check: `gradlew compileJava`.
- Install: copy the built jar over the same-named jar in the instance's `minecraft/mods/`
  folder (or remove the old version first), then launch via Prism. Do not reference
  absolute instance paths in committed files.
- The repo keeps four tracking docs current: `RULES.md` (process),
  `MODIFIER_ALERTS_SPEC.md` (what to build), `F3_AUTO_REROLL_PLAN.md` (auto-reroll
  stories), `DECISIONS.md` (why - this file).

**Rationale**
Fast, reliable iteration loop without decompiler tooling; documented once so future
sessions do not rediscover the jar location or command shape.

**Impact**

- All future sessions: read this entry before touching VH internals.

---

---

### DEC-029 - Typed tier-config parsing, ability names, threshold retention, spacing (owner request)

- **Date:** 2026-08-15
- **Status:** RESOLVED
- **Category:** Design

**Context**
Owner review of the F3 panel after DEC-028: (a) every ability/talent re-roll
candidate showed the same "Mod Added Ability Level" (resp. "Mod Added Talent Level")
prefix wording instead of the ability/it adds; (b) "No Numeric Threshold" appeared for
the selected attribute and typed threshold values were silently cleared instead of
accepted; (c) rows were cramped; (d) over-long text should reveal the full text on
hover. Reference implementation studied: `radimous/VHatCanIRoll` (same data path).

**Root causes (verified against the Remastered instance's pack configs and jar)**
1. `ModifierTier.getModifierConfiguration()` returns **typed config objects**, not
   `JsonObject` (e.g. `IntegerAttributeGenerator.Range` with public `min/max/step`,
   `Float/DoubleAttributeGenerator.Range` with private fields, `AbilityLevelAttribute.Config`
   with `abilityKey`/`levelChange`), so the old `instanceof JsonObject` check was always
   false and every range resolved to non-numeric.
2. Candidate display names came from the reader/group-id strings ("Ability Level",
   "Mod Added Ability Level Icebolt"); the real per-tier configs carry the ability id
   (`"value": {"abilityKey": "Ice_Bolt_Base", "levelChange": N}`).
3. The old self-heal cleared a committed threshold whenever it considered the target
   non-numeric.

**Decision**
1. **Numeric extraction:** `rollRange` uses the attribute's own generator API first -
   `generator.getMinimumValue/getMaximumValue` over the level-applicable tiers
   (`tierGroup.getModifiersForLevel(gearLevel)`, all-tiers fallback) - plus typed
   casing: `IntegerAttributeGenerator.Range` public fields (step), ability/talent
   `Config` as point values ("+N levels", step 1). Float/Double percentage ranges
   store fractions and are scaled into display units (×100) exactly like
   `toDisplayUnits` does, so thresholds stay comparable. Raw `JsonObject` tier blocks
   remain supported as a fallback for custom attributes without a generator.
2. **Ability/talent names:** display = the ability/talent name only, from
   `AbilityLevelAttribute.Config.getAbilityKey()` / `TalentLevelAttribute.Config.getTalent()`
   -> `ModConfigs.ABILITIES.getAbilityById(...)` / `ModConfigs.TALENTS.getTalentById(...)`
   -> `Skill.getName()`; special keys "all_abilities"/"all_talents" -> "All Abilities" /
   "All Talents"; `AbilityType.matches` -> "All <Type> Abilities"; fallback humanizes
   the key with a trailing "_Base" tag trimmed. `toDisplayUnits` handles
   `AbilityLevelAttribute`/`TalentLevelAttribute` values by their `levelChange`, so the
   engine can compare "+N levels" thresholds.
3. **Threshold retention:** `commitMinInput` keeps any valid number - clamped to the
   known roll range when numeric, kept as-is (compare "at least X" unclamped) when the
   range is unknown; only unparseable text clears. `stepMin` uses ±step when known and
   ±1 unclamped when not. The draw-time self-heal reset is removed; the range row shows
   "Range: ?" when the range could not be read.
4. **Spacing:** panel width 200 -> 216, row height 11 -> 14, title band 12 -> 18, side
   padding 6 -> 8, buttons 12 -> 14, dropdown rows 11 -> 14 with 8 visible by default
   (clamped 3..8), all label/hit zones recomputed in `RerollPanelLayout`.
5. **Hover popover:** any text truncated to its row (Focus/Modifier values, range row,
   status line, dropdown items) shows its full text in a small dark popover racing the
   cursor (manually wrapped, screen-clamped), drawn after the panel frame.

**Rationale**
Reading values through VH's own generator API (as `VHatCanIRoll` does) makes the range
and threshold logic correct for every generator type the game ships, instead of
guessing JSON shapes; names come from the same config values the game rolls with.

**Impact**
- Files: `ModifierCatalog`, `RerollPanel`, `RerollPanelLayout`, `README.md`,
  `F3_AUTO_REROLL_PLAN.md`.
- Engine state machine, stop reasons, sounds, `/vma reroll` contract: unchanged.
- DEC-028's "Impact" numbers are superseded: base panel height is now ~150 px,
  width 216.

- **Date:** 2026-08-15
- **Status:** RESOLVED
- **Category:** Design

**Context**
The owner reviewed the shipped F3 panel and asked for a GUI revamp, referencing
`massuus/vault-party-ui` as a visual/UX reference (dark panels, gold `0xFFE3C38C` accents,
popup/button-list selectors). Complaints: (a) `‹ ›` arrow cycling for operation/target is
too basic - click should open a full picker; (b) modifier names were code-like in some
fallback paths (raw id path) instead of human text; (c) no on/off auto-reroll toggle in
the GUI; (d) status/potential info too thin; (e) hand-maintained draw-vs-click row math
in `RerollPanel` is brittle. The previous panel also truncated names to 16 chars and the
whole GUI was deleted-and-rewritten as instructed, keeping the engine's behaviour.

**Decision**

1. **Selector UX:** Focus and Modifier rows open an **in-panel dropdown** (the framework
   element's rect grows via `setHeight` + `requestLayout` when a dropdown is open) listing
   every option; Escape / arrows / wheel scroll it, clicks outside close it. A separate
   popup `Screen` (vault-party-ui `DifficultySelectionScreen` style) was rejected: the
   engine stops a run with `SCREEN_CLOSED` whenever `mc.screen` is not the station screen,
   so any popup screen would kill a running roll on open.
2. **Input routing:** mouse clicks stay on the framework element (proven path);
   keyboard (Escape/Up/Down for dropdown, editing keys for the min field), mouse-wheel
   scroll and click-outside-to-close are handled by screen-level Forge events in
   `ClientTickEvents` (the same proven path as the existing key/char routing). Wheel and
   mouse events are consumed/cancelled exactly once; the element does NOT override
   `onMouseScrolled` to avoid double-handling.
3. **Human names:** `ModifierCatalog` gains `humanizeId(path)` (snake/kebab -> title
   case) as the guaranteed fallback; primary display stays the game's own
   `VaultGearModifierReader.getModifierName()`. New `RollRange.displayText()` renders the
   rollable band ("2.0 - 6.0%") in the dropdown and the Min range row.
4. **GUI toggle:** new "Auto-reroll" checkbox flips config `enabled`
   (`VmaClientConfigs.setAutoRerollEnabled`). The panel no longer hides itself when the
   config is off (`RerollPanelElement.isVisible()` now only checks `panel.visible`), so
   the toggle stays reachable from the station; controls are dimmed and the engine keeps
   its existing off-switch stop.
5. **Status/potential:** potential row is colored (red at 0) and shows "~N rolls" from
   `ModConfigs.VAULT_GEAR_MODIFICATION_CONFIG.getPotentialUsed(...)`; status line shows
   rolls, the last rolled target value (`AutoRerollEngine.lastTargetValue`, recorded in
   `targetRolled` for every roll), and the existing stop-reason text.
6. **Layout engine:** new `RerollPanelLayout` computes every row/button/dropdown rect
   once per frame; `draw` and `handleClick`/`handleScroll` both use `regionAt(...)`,
   removing the parallel row-math entirely.
7. **Min threshold:** kept (click-to-type, `-`/`+` step, clamps to the target's roll
   range) and now self-heals: a threshold that no longer fits a newly selected
   non-numeric modifier is cleared, and a range hint row shows the rollable band.

**Rationale**
Matches the reference UX while keeping the proven single-screen element architecture and
the engine's stop-reason semantics untouched; gives the owner the requested "click for
full list" behaviour and human-readable modifiers.

**Alternatives considered**

1. Popup `Screen` per vault-party-ui (`DifficultySelectionScreen`) - rejected: stops
   running rolls (engine screen guard), adds screen-swap complexity.
2. Rebuild with vanilla `Button`s in a bigger panel - rejected: framework elements are
   the codebase's proven pattern for this screen.
3. Keep `‹ ›` cycling with a longer value field - rejected: no full-list visibility,
   the exact pain point reported.

**Impact**

- Files: `RerollPanel`, `RerollPanelElement`, `RerollPanelLayout` (new),
  `ModifierCatalog`, `AutoRerollEngine`, `VmaClientConfigs`, `ClientTickEvents`,
  `README.md`, `F3_AUTO_REROLL_PLAN.md` (§4.4 marked superseded).
- Engine state machine, stop reasons, sounds, `/vma reroll` contract: unchanged.
- Panel minimum height grows from 122 to 116+dropdown (base 116), width 150 -> 200;
  anchored left of the window with right-side fallback, clamped so the full height
  (dropdown included) fits on screen.

---

### DEC-030 - Effect-avoidance ranges, name fixes, status wording, reset counter (owner request)

- **Date:** 2026-08-15
- **Status:** RESOLVED
- **Category:** Design

**Context**
Owner review of the panel after DEC-029: (a) the status line "Ready · min 3" was
confusing ("what is it? why is it needed?") and its `·` glyph renders as garbage in
Minecraft's font; (b) "Mod Effect Avoidance" should read "Effect Avoidance" and shows
no min-max roll band; (c) the `-`/`+` step buttons had no background and uncentered
glyphs; (d) when auto-reset-potential is on, the panel should count and show the
resets during a run.

**Root causes (verified against pack configs + jar)**

1. The armor avoidance mods (`the_vault:mod_effect_avoidance` on chest/legs/helm/boots,
   `crafted_effect_avoidance`, `u_effect_avoidance`) are **list-type**
   `the_vault:effect_list_avoidance` (effect list + one shared chance); drink/unique and
   the idol suffixes use single-effect `the_vault:effect_avoidance`. Both Config classes
   (`EffectAvoidanceGearAttribute$Config` / `EffectAvoidanceListGearAttribute$Config`)
   have **no public getters**; the numeric band is only reachable as the *value type*:
   both generators' `getMinimumValue/getMaximumValue` return the attribute itself, and
   both implement `IEffectAvoidanceChanceAttribute.getChance()` (fraction).
2. Neither avoidance reader overrides `getModifierName()`, so names fell back to the
   humanized raw id path "Mod Effect Avoidance".
3. Tiers store `minChance/maxChance/step` as fractions (0.1 = 10%).

**Decision**

1. **Chance band:** `ModifierCatalog.rangeValue` handles
   `IEffectAvoidanceChanceAttribute` min/max results -> `RangeValue(chance*100, chance*100,
   step 1, percent=true)`, so the dropdown/range row shows "10% - 80%" on chestplate
   (tiers 0.1/0.31/0.61/0.8). `toDisplayUnits` maps any
   `IEffectAvoidanceChanceAttribute` value to `getChance()*100`, so "at least X" compares
   the rolled chance in percent units like every other percentage attribute.
2. **Name:** the `displayName` fallback strips a leading `mod_` id prefix before
   humanizing (helper public as `stripModPrefix`) -> "Effect Avoidance" (idol suffixes:
   "... Poison" etc; `crafted_`/`u_` stay as-is).
3. **Status line:** ASCII wording - `Ready : goal at least 3` (or `...3%` for percent
   targets), `Ready : any roll`, `Ready : N targets`, `Add a target modifier`; the
   `Stopped: ...` roll-count separator changes `·` -> `-`.
4. **Step buttons:** the `-`/`+` hit zones now draw a real button rect (12x10, inset 2)
   with the game's button background (`0xFF303030`, `HOVER_COLOR` on hover, dimmed when
   the config is off) and the glyph centered via `drawCentered`.
5. **Reset counter:** `AutoRerollEngine` gains `potentialResetsThisSession` (incremented
   in `handleOutOfPotential`, reset per run) + accessor; a new last panel row
   (`counterY`) prints `Potential reset x N` while rolling with
   `isAutoResetPotentialEnabled()`.

**Rationale**
The chance band is semantically the rollable value for both avoidance types and matches
the per-roll behaviour (the game rolls one shared chance per modifier); reading it
through the generator API keeps DEC-029's "typed configs only" rule while the Config
classes hide their fields.

**Impact**

- Files: `ModifierCatalog`, `RerollPanel`, `RerollPanelLayout`, `AutoRerollEngine`.
- Panel gains one optional bottom row (only during a run with reset enabled).
- `VaultGearModifierReader.getModifierName()` stays the primary name source; the
  `mod_` prefix strip only affects the fallback path.

---

### DEC-031 - Multi-target watch list, per-target mins, stop condition, picker UX (owner request)

- **Date:** 2026-08-15
- **Status:** RESOLVED
- **Category:** Design

**Context**
Owner request: the panel only watched one modifier; it must support adding several and
stop auto-rerolling once *at least one* of them rolls. Owner confirmed the stop mode
should itself be user-selectable ("whether all targets should pass or any target should
pass and by default it should be set as any"), thresholds per target, and an explicit
targets row + add-picker editor instead of a single toggle.

**Decision**

1. **Model:** `ModifierCatalog.RollTarget(id, thresholdEnabled, thresholdValue)`; the
   panel holds `List<RollTarget>` + `focusedTarget` index; the Min row/Mode/range hint
   edit the focused target (per-target thresholds, each clamped to its own band).
2. **Stop condition:** `AutoRerollEngine.StopCondition { ANY, ALL }`, default ANY,
   selected via a clickable chip on the Targets row (`any`/`all`, cycles, 24px hit
   zone). ANY = first passing target stops; ALL = every watched target must have rolled
   and passed at least once in the run (per-run `allPassed[]`, OR-marked per gear
   change - a later lower roll never un-marks).
3. **Engine contract:** `start(operation, List<RollTarget>, StopCondition)` replaces the
   single-target overloads; `INVALID_TARGET` fires only when *no* watched target is
   applicable to the operation scope; `targetRolled` scans scope affixes and matches per
   modifier id (any out-of-scope target simply can never roll). `targetId()` now returns
   the first target (compat). `/vma reroll start` logs the target count.
4. **Editor UX:** a new "Targets" row (between Modifier and Min) shows the focused
   target's name + `n` count and the condition chip; its dropdown lists the watch list
   with per-entry `min X` / `any`, a `>` focus marker, and a 16px `x` remove zone.
   The Modifier row becomes an add-picker placeholder ("add a modifier..." /
   "+ add modifier"); dropdown items carry a `*` marker when already watched and click
   toggles add/remove (add appends untargeted with no min and focuses it). Removing the
   focused target shifts focus to a neighbour; empty list disables Start ("Add a target
   modifier" status). Targets persist across operation switches (out-of-scope ones never
   roll, shown with "Range: ?").
5. **Layout:** rows grow by one (Targets) and one optional bottom row (reset counter,
   DEC-030); all downstream Ys recomputed in `RerollPanelLayout` (`targetsY`,
   `counterY`); new hit types `TARGETS_ROW`/`TARGETS_CHIP` and a
   `DropdownMode.TARGETS` overlay.

**Rationale**
Keeps the proven single-editor Min row (per-target focused), reuses the existing
dropdown/popover machinery for the watch list, and pins the "at least one" semantics
the owner asked for with an explicit default.

**Impact**

- Files: `ModifierCatalog`, `AutoRerollEngine`, `RerollPanel`, `RerollPanelLayout`,
  `VmaClientCommands`, `README.md`, `F3_AUTO_REROLL_PLAN.md`.
- `/vma reroll start` uses the updated `RerollSelection(operationId, targets,
  stopCondition)`; engine public API changed (list-based start).
- Base panel height grows by one row (~178 px), width stays 216.
