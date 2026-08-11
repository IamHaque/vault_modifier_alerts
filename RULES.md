# RULES.md — Development Procedures & Coding Standards

**Applies to:** all implementation work on the `vault_modifier_alerts` Forge mod.
**Authority:** `MODIFIER_ALERTS_SPEC.md` (what to build), `DECISIONS.md` (why / deviations).
**Read order:** this file first, then the spec, then the decision log.

---

## 0. Golden rules

1. **Spec-compliance over cleverness.** Implement exactly what the spec says. If you deviate,
   you must first add a decision entry (`DECISIONS.md`) and mark the affected spec section.
2. **DRY (Don't Repeat Yourself).** Every fact, constant, key, and mechanism has exactly one
   source of truth. Never duplicate logic by copy-paste.
3. **Verification before guesswork.** When interacting with decompiled VH internals, verify
   signatures against the real jar (see §4) before writing mixins around them.
4. **A working build is the merchant of record.** `./gradlew build` must pass before work is
   considered done. Then play-test (S13) for the coexistence guarantee.

---

## 1. DRY principle

- Single source of truth for:
  - Config keys/group names → `VmaClientConfigs` constants.
  - Mod id, sound event id, subtitle key → module-level constants (one `Ids`/`VmaReference`
    class or `ModConstants`; nothing hard-coded twice in different files).
- Shared helper logic (e.g. "modifier is temporal", "ticks to expiry string") lives in one
  utility class (`ModifierTimeUtils` or similar per spec §4.2); mixins and features import it.
- If the same code would appear twice, extract it or delegate — do not copy.
- Data duplicated from the spec (lists of modifier ids, config defaults) is kept only in the
  config default section; the tracker reads config, never a second hard-coded list.

---

## 2. Java coding standards (Java 17, Forge/Fabric-less plain JDK where possible)

- **Formatting:** tabs for indentation; 100-column wrap; braces on same line (K&R);
  single blank line between methods; no trailing whitespace; final newline at EOF.
- **Naming:**
  - Classes: PascalCase nouns/mixes (`ModifierTracker`, `ExpiryAlertEngine`).
  - Mixins/new interfaces: `VaultModifier`-touching ones get the `Vma`/`vma$` prefix for
    owned members to avoid collisions when other mods (e.g. QOLHunters) also patch VH.
  - Methods: camelCase verbs (imperative). Boolean getters read like questions/assertions
    where sensible (`isTemporal()`, `shouldFire()`).
  - Constants: `UPPER_SNAKE_CASE` (`DEFAULT_WATCHED_MODIFIERS`).
- **Modifiers/visibility:** context-aware privacy — classes `final` by default; fields
  `private` + getters; only expose what mixins/features need; avoid static mutable state
  (prefer injected/owned instances; a single `ServiceLocator` or constructor-bound instances
  per spec §4.2).
- **Null-safety:** never return raw `null` from public APIs where `Optional` fits; guard
  external data (config list, sound asset) against empty/missing with graceful defaults.
- **Immutability:** prefer immutable collections for constants (`List.of(...)`), never
  `Arrays.asList` when a static list is intended; defensive copies on config reads.
- **Exceptions:** don't swallow; log with context (mod id + story id); if an exception means
  feature responsibility fails, isolate it (see §5 SoC) and log `debug`/`warn` — no crash of
  unrelated features.
- **No dead code:** no unused imports/fields/methods; remove scaffolding immediately.
- **No comments to explain trivia**; doc comments only for non-obvious design decisions —
  then reference the decision id (`// see DEC-005`) instead of re-explaining.

---

## 3. Code grouping & file organization

Follow the package-by-feature layout from spec §4.2, strictly:

```
io.haque.vault_modifier_alerts
├── VaultModifierAlerts.java          (mod main class; registries + event subscription)
├── config/VmaClientConfigs.java      (the ONLY file reading config values)
├── tracker/
│   ├── ModifierTracker.java          (master time snapshot + expiry detection)
│   └── VaultModifierTimeAccessor.java(duck-typed interface, vma$ prefixed)
├── feature/
│   ├── expiry/ExpiryAlertEngine.java
│   ├── expiry/AlertSoundPlayer.java
│   └── order/ModifierOrdering.java
├── event/ClientTickEvents.java       (client tick → tracker update, vault lifecycle)
└── mixin/
    ├── tracker/MixinVaultModifier.java
    ├── tracker/MixinModifiers.java
    └── render/MixinModifiersRenderer.java
```

- Classes for feature F1 stay in `feature/expiry`; F2 classes in `feature/order`. No
  cross-feature imports between them except via the tracker (shared core).
- Mixins are thin: intercept once, delegate to tracker/feature classes. Never implement
  business logic inside a `@Mixin` class unless spec says otherwise.
- Group related constants with their owning class; `@VisibleForTesting`/package-private where
  tests would need access.

---

## 4. Separation of concerns (layering)

- **Config layer** (`config/`): reads `ForgeConfigSpec`, exposes typed getters
  (`boolean isExpiryAlertsEnabled()`, `List<String> watchedModifiers()`). Nothing else
  touches the spec object directly.
- **Tracking layer** (`tracker/`): maintains vault lifecycle + time snapshot; no UI, no
  sound, no config decisions — receives filtered/decided inputs from engine.
- **Feature layer** (`feature/`): expiry alerting (engine) and ordering (HUD transform) are
  independent consumers of the tracker; neither knows the other.
- **Presentation/event layer** (`event/`, `mixin/`): wires Minecraft/VH events and mixin
  hooks to the layers above; contains no business logic.
- **Interaction rule:** layers point downward only. `mixin` → `tracker`/`feature`;
  `feature` → `tracker`; `config` is read by `feature`/`tracker` but config never depends on
  features. Violations must be flagged in review.
- **Fail-safe:** each feature degrades gracefully if its prerequisite (hook unavailable,
  sound asset missing) fails — it must never take down another feature or crash the client.

---

## 5. Mixin conventions (critical — project-specific)

- Never mixin into classes you don't own without first confirming the exact target via
  decompilation (§4) — VH obfuscation names must be resolved against the actual jar version
  (per `gradle.properties` → `vault_hunters_version=7967092`).
- All cross-mod compat: this mod targets the VH jar (`the_vault`). QOLHunters may also patch
  the same classes. Rules:
  1. Keep injected locals minimal and reference-safe (guard against missing entries).
  2. Prefer `@Local` named capture (e.g. `@Local(name="map")`) over positional where both are
     available — more resilient to unrelated code changes.
  3. Never remove/redirect-ignore an existing call that other code relies on; either
     `@WrapOperation` on the exact call or inject after `endVertex`.
  4. Mixin method names use the `vma$` prefix for `@Unique` members and `on...`/`modify...`
     verbs for injection targets.
  5. No cross-mixin state: if two mixins must share data, they communicate via the owned
     tracker class, never via static fields in mixins.
- Config-driven injection guard: if a feature is disabled by config, its mixin should still
  attach but early-return / no-op (cheap check via `VmaClientConfigs`), so toggling config
  does not require a restart to be safe.

---

## 6. Git & commit workflow

- Commit in small, story-sized units: one commit per completed story (S0x), with the story id
  in the message subject, e.g. `S04: capture time-left snapshots`.
- Message format: `S<NN>: <imperative summary>`; body paragraphs explain _why_, referencing
  DEC/SRP numbers when relevant (`refs DEC-005`).
- Never commit build artifacts (`build/`, `.gradle/`, `run/`) or `.class` outputs — see
  `.gitignore` in project root.
- History stays linear on the working branch; no `--no-verify` bypasses; review `git status`
  / `git diff --stat` before each commit; don't commit secrets or absolute dev paths.

---

## 7. Definition of Done (per story)

A story is **done** only when:

1. Implements exactly the spec acceptance criteria for that story (S0x list).
2. `./gradlew build` passes (no warnings introduced; mixin refmaps generated without errors).
3. The classes introduced follow §2 and §3 layout; no dead code; no DRY violations.
4. Config keys/documented defaults match §7.4 exactly.
5. If the story required a decompiled-VH signature check, the verified symbol + source
   reference is noted in the commit message or DEC entry.
6. Any deviation is recorded in `DECISIONS.md` (status updated).
7. Client smoke test ran (see §8) for stories that touch runtime behaviour.

---

## 8. Verification workflow

- **Fast checks, always:** `./gradlew build` after each story; fix warnings before moving on.
- **Runtime smoke test** (stories S05–S15): `./gradlew runClient`; join a vault with
  `the_vault:champion_domain`; verify:
  - HUD shows only active modifiers, temporal-first ordering (S11/S12).
  - F1 alert fires exactly once per expiry, not repeatedly (S06/S09).
  - Toggling `[Expiry Alerts].enabled` / `[HUD Ordering].enabled` changes behaviour without
    restart (S12).
- **Coexistence play-test** (S13): run with QOLHunters loaded (as in the user's modpack);
  if anything breaks, stop, diagnose, record in DECISIONS.md before proceeding.
- The final gate before declaring the whole mod done is the complete §9 verification plan of
  the spec, plus a manual pass of S11 contract tests in a real vault.

---

## 9. Decision logging obligation (repeat)

- Every spec deviation or ambiguity resolution **must** get an entry in `DECISIONS.md` (§1,
  template §2) at the moment of the decision, not at the end.
- Escalate to the project owner (via question) when: the spec is ambiguous AND impact is high,
  or a requirement conflicts with an existing decision.
- Do not open issues that duplicate an existing entry; update the entry with a `SUPERSEDED
BY DEC-0YY` / status change instead.

---

## 10. Scope guard

- Implement F1 and F2 per spec §5/§6 and stories S01–S15, nothing else. No "quick wins" that
  are not in the spec (no new mixins, no extra config keys, no mini-features) without an
  owner-approved DEC entry.
- The user-supplied `.ogg` file is _not_ required for completion of S07/S08; the guarded
  silent playback path is the defined behaviour until the asset exists.
