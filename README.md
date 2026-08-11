# Vault Modifier Alerts

A small, **client-side only** Forge mod for **Minecraft 1.18.2 / Vault Hunters 3rd Edition**
that adds two Quality-of-Life features around world modifiers inside The Vault:

| ID  | Feature                     | Summary                                                                                                                                                      |
| --- | --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| F1  | **Expiry Audio Alert**      | Plays an audio cue when a watched temporal modifier (e.g. `the_vault:champion_domain` — "Champion's Domain") runs out of time / is exhausted inside a vault. |
| F2  | **HUD Modifier Reordering** | Reorders the vault modifier icons on the HUD: temporal modifiers first (soonest-expiring first), permanent modifiers last.                                   |

---

## Document Map (read in this order)

| File                      | Purpose                                                                                                                                                                                                                      |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `RULES.md`                | **Dev processes & coding standards.** DRY, Java coding guidelines, separation of concerns, code grouping, mixin conventions, git workflow, definition-of-done. Read before doing anything.                                   |
| `MODIFIER_ALERTS_SPEC.md` | **The implementation specification.** Fully prescriptive: verified Vault Hunters internals, exact mixin shapes, algorithms, configuration keys, and BDD story breakdown (S01–S15) with dependencies. Implement exactly this. |
| `DECISIONS.md`            | **Decision log.** Every implementation-time decision/ambiguity resolution must be recorded here (procedure + template + pre-seeded decisions DEC-001…).                                                                      |

---

## Repository layout (this folder)

```
vault_modifier_alerts/
├── README.md                   (this file)
├── RULES.md                    (development procedures & standards)
├── MODIFIER_ALERTS_SPEC.md     (implementation specification + BDD stories)
├── DECISIONS.md                (decision log)
└── src/                        (created during implementation, per SPEC §5.2)
```

## Build & run (after scaffolding per SPEC S01)

```bash
cd C:\Users\Haque\Development\VH\vault_modifier_alerts
./gradlew build          # compile + reobf -> build/libs/vault_modifier_alerts-<version>.jar
./gradlew runClient      # dev client with VH
```

## Expiry sounds (per-modifier, owner-supplied)

Each watched modifier needs its **own** sound entry under the config key `soundOverrides`
(there is no generic default sound). The mod ships one override:

```
"the_vault:champion_domain"  →  "vault_modifier_alerts:champ_domain_expired"
```

whose asset ships in the jar at:

```
src/main/resources/assets/vault_modifier_alerts/sounds/vault/champ_domain_expired.ogg
```

Files must be OGG **Vorbis** (not Opus), ~44.1 kHz, ideally ≤ 2 s. To watch a new modifier,
add it to `watchedModifiers` **and** map it to a registered sound event in `soundOverrides`
inside the generated `config/vault_modifier_alerts-client.toml`; a watch without an entry
warns once and stays silent.

---

**Status:** Spec not yet implemented.
**Author (spec):** AI-assisted design, `C:\Users\Haque\Development\VH`.
