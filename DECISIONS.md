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
| DEC-005 | Context class exposes time left (verify at implementation) | OPEN     | 2026-08-11 |
| DEC-006 | Time-capture fallback hook                                 | OPEN     | 2026-08-11 |
| DEC-007 | QOLHunters coexistence play-test                           | OPEN     | 2026-08-11 |

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
Sort temporal modifiers ascending by time left (soonest-expiring first); permanents follow,
in stable order. Ties keep insertion (map) order. Config flag `sortTemporalAscending=true`
(default) toggles between ascending and descending.

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
- **Status:** OPEN
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
