# Vault Modifier Alerts

A small, **client-side only** Forge mod for **Minecraft 1.18.2 / Vault Hunters 3rd Edition**
that adds Quality-of-Life features around world modifiers inside The Vault:

| ID  | Feature                     | Summary                                                                                                                                                      |
| --- | --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| F1  | **Expiry Audio Alert**      | Plays an audio cue when a watched temporal modifier (e.g. `the_vault:champion_domain` — "Champion's Domain") runs out of time / is exhausted inside a vault. |
| F2  | **HUD Modifier Reordering** | Reorders the vault modifier icons on the HUD: permanents first, temporal modifiers last (at the anti-anchor/outer edge of the block — DEC-020). |
| F3  | **Auto-Reroll (Artisan Station)** | Chases a chosen modifier at the Artisan Station: a side panel picks the re-roll operation + target, auto-presses the station's own re-roll buttons until the target rolls (or materials/potential run out), then alerts and stops. |

---

## Document Map (read in this order)

| File                      | Purpose                                                                                                                                                                                                                      |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `RULES.md`                | **Dev processes & coding standards.** DRY, Java coding guidelines, separation of concerns, code grouping, mixin conventions, git workflow, definition-of-done. Read before doing anything.                                   |
| `MODIFIER_ALERTS_SPEC.md` | **The implementation specification.** Fully prescriptive: verified Vault Hunters internals, exact mixin shapes, algorithms, configuration keys, and BDD story breakdown (S01–S16) with dependencies. Implement exactly this. |
| `F3_AUTO_REROLL_PLAN.md`  | **F3 requirement & implementation plan.** The owner-approved plan the F3 (Auto-Reroll) feature was implemented against. |
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

## In-game commands

| Command             | Effect                                                                                                     |
| ------------------- | ---------------------------------------------------------------------------------------------------------- |
| `/vma debug on`     | Enables `[VMA]` debug logging (writes `debugLogging=true`; persists across restarts).                      |
| `/vma debug off`    | Disables debug logging.                                                                                    |
| `/vma sound on`     | Enables expiry alert sounds (writes `alertSoundEnabled=true`; persists across restarts).                   |
| `/vma sound off`    | Silences expiry alerts (firing state still tracked; no audio).                                             |
| `/vma reroll enable` | Enables auto-rerolling and shows the side panel (writes `enabled=true` for both `[Auto Reroll]` and `[Reroll Panel]`; persists across restarts). |
| `/vma reroll disable` | Master off-switch: stops any running auto-reroll, hides the side panel, and disables both toggles (persists across restarts). |
| `/vma reroll start` | Starts auto-rerolling with the panel's current operation + target selection.                               |
| `/vma reroll stop`  | Stops the running auto-reroll.                                                                             |
| `/vma status`       | Prints config state, the last observed HUD modifier order, vault/frame state, and each watched id's status. |

Commands are client-side (Forge `RegisterClientCommandsEvent`) and work in single-player and
multiplayer. Toggles persist in `config/vault_modifier_alerts-client.toml`.

## Auto-Reroll (F3)

At an Artisan Station, press **P** to toggle the Auto-Reroll side panel (drawn **outside**
the station window — right side preferred, left fallback — so it never obstructs the GUI):

- **Focus** — the re-roll operation: reforge all, reforge prefix / suffix, or reforge
  implicits. Click the row to open a dropdown with the full list.
- **Modifier** — the *add-a-target* picker: click to open a scrollable dropdown with every
  modifier that can actually roll under the selected operation for the gear in the station
  (impossible targets, e.g. an attack-damage modifier on a helmet, are not offered). Items
  show human-readable names and their rollable value ranges; already-watched ones carry a
  `*` and clicking toggles them in/out. The list is alphabetical, with ability/talent level
  modifiers grouped at the bottom (each group alphabetical); ability targets are named by
  the ability they add (e.g. "Ice Bolt") and effect-avoidance targets show their chance band
  (e.g. "10% - 80%"). Any name or range too long for its row shows its full text on hover.
- **Targets** — your watch list (you can watch several at once). The row shows the focused
  target and its count; click it to open the list — click a name to focus it for editing,
  click the `x` on the right to remove it. The chip on the right selects the stop
  condition: `any` (default — stop when *any* watched target passes) or `all` (stop only
  after *every* watched target has passed at least once).
- **Min** — an optional minimum threshold for the *focused* target (each target keeps its
  own): keep rolling until it rolls at least this value (type it in, or step with the
  `-`/`+` buttons; the rollable range is shown below the field). The value is always kept,
  even when the panel cannot read the target's roll range ("Range: ?") — the engine then
  compares "at least X" without clamping. No threshold = that target passes on any roll.
- **Auto-reroll** toggle — master switch for the feature, right in the GUI (persists to the
  `enabled` config; turning it off stops any running roll and dims the controls).
- **Auto-reset potential** checkbox — when the selected operation is disabled for lack of
  crafting potential, press `reset_potential` (Opportunistic Focus) automatically (config
  `autoResetPotential`, default `true`). While rolling, a counter at the bottom of the panel
  shows how many times the potential was reset this run (`Potential reset x N`).
- **Potential** line — current/max crafting potential plus an estimate of the rolls left.
- **Status** line — roll counter, the last rolled value of the target, the armed goal, and
  the stop reason (e.g. `Rolling... #7 (4.2%)`, `Ready : goal at least 4`, `Stopped:
  target rolled - 12 rolls`).

Start / Stop with the panel button or `/vma reroll start|stop` (the command uses the panel's
current selection — all watched targets and the stop condition). The engine presses the
station's own buttons through the same code path as a player click; it stops and plays the
configured sound (`successSoundEvent` on success, `stopSoundEvent` otherwise) on: any/all
target(s) passed, gear removed, out of materials, out of potential, no target rollable any
more, max rolls reached, no roll detected in time, station closed, or manual stop.

Config `[Auto Reroll]` in `config/vault_modifier_alerts-client.toml`:

| Key                 | Default                          | Meaning                                             |
| ------------------- | -------------------------------- | --------------------------------------------------- |
| `enabled`           | `true`                           | Master switch for the feature.                      |
| `tickInterval`      | `15` (4–200)                     | Ticks between presses.                              |
| `rollTimeoutTicks`  | `60` (10–400)                    | Wait for the gear to change before giving up.       |
| `maxRolls`          | `0`                              | Press cap; `0` = unlimited.                         |
| `autoResetPotential`| `true`                           | Auto-press Opportunistic Focus when out of potential (once per run). |
| `successSoundEvent` | `minecraft:block.note_block.pling` | Sound when the target rolls.                      |
| `stopSoundEvent`    | `minecraft:block.note_block.pling` | Sound for any other stop.                         |
| `volume` / `pitch`  | `1.0` / `1.0`                    | Playback settings for both events.                  |

The keybind (default **P**) is rebindable under *Options → Controls → Key Binds → Vault
Modifier Alerts*.

---

**Status:** implemented (see `DECISIONS.md`).
**Author (spec):** AI-assisted design, `C:\Users\Haque\Development\VH`.
