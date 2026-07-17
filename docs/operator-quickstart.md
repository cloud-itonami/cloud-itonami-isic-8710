# Operator Quickstart

Get the Nursing Care Governor running locally in minutes.

## Prerequisites

- **Clojure CLI**: [Install Clojure](https://clojure.org/guides/install_clojure) (includes `clojure` and `clj` commands)
- **Java 11+**: Clojure runs on the JVM

This repository is standalone and can be forked outside the monorepo. If you're in the workspace and want offline development:

```bash
# Inside the monorepo checkout, the deps.edn aliases use :local/root paths
# A standalone fork should update deps.edn to use :git/url coordinates instead
```

## Run the demo

Walk through one complete lifecycle plus five HARD-hold cases:

```bash
clojure -M:dev:run
```

This executes `src/nursing/sim.cljc`, driving the `OperationActor` through resident intake, care-plan verification, medication administration, and incident-response finalization, showing where the Nursing Care Governor holds or approves each step.

## Run tests

Verify the governor contract, phase invariants, store behavior, and jurisdiction facts:

```bash
clojure -M:dev:test
```

Or just the basics (no workspace dev overrides):

```bash
clojure -M:test
```

Key test suites:

- `test/nursing/governor_test.clj` — Nursing Care Governor holds/escalates decisions correctly
- `test/nursing/phase_test.clj` — Phase table invariants; medication administration and incident-response finalization are never autonomous
- `test/nursing/store_test.clj` — Store parity (MemStore vs DatomicStore)
- `test/nursing/registry_test.clj` — Medication/incident-response draft records conform to schema
- `test/nursing/facts_test.clj` — Jurisdiction catalog coverage and citation accuracy

## Run static analysis

```bash
clojure -M:lint
```

Validates code with [clj-kondo](https://github.com/clj-kondo/clj-kondo); failures exit with status 1 (same as CI).

## Key files

| File | Role |
|---|---|
| `src/nursing/governor.cljc` | **Nursing Care Governor** — the independent decision layer that holds or escalates medication administration and incident-response finalization |
| `src/nursing/nursingadvisor.cljc` | **NursingOps-LLM** — drafts proposals (intake, care plans, medication, incident responses); sealed from direct actuation |
| `src/nursing/phase.cljc` | **Phase table** — guarantees medication/incident-response are never in any phase's `:auto` set; both always require human sign-off |
| `src/nursing/store.cljc` | **Store protocol** — MemStore (dev) and DatomicStore (prod) with append-only audit ledger |
| `src/nursing/facts.cljc` | **Jurisdiction catalog** — official spec-basis citations for residential-nursing-care requirements (currently JPN, USA, GBR, DEU) |

## The core contract

```
Resident intake + jurisdiction facts
         |
         v
   ┌─────────────────┐   proposal      ┌──────────────────────────┐
   │  NursingOps-LLM │ ───────────────▶ │ Nursing Care Governor    │
   │   (sealed)      │  + citations     │ (independent system)     │
   └─────────────────┘                  │                          │
          │                             │ ✓ Hold · Escalate        │
          │                        commit ◀─ (ALWAYS for actuation)│
    record + ledger            escalate → ─ Hold/approval flow    │
          │              (ALWAYS for     │                          │
          │               :actuation/    └──────────────────────────┘
          v                 administer-
       human                medication /
       approval              finalize-
                             incident-response)
```

**Actuation is never autonomous.** Two independent layers enforce this:

1. The governor's `:actuation/administer-medication` and `:actuation/finalize-incident-response` gates
2. The phase table, which never puts those operations in any phase's `:auto` set

Medication administration and incident-response finalization always require a licensed nursing-staff member's sign-off.

## What's next

1. **Integrate with your jurisdiction's registry**: Add a new entry to `src/nursing/facts.cljc` with an official regulatory citation
2. **Customize the governor's hold policy**: Edit `src/nursing/governor.cljc` to match your facility's SLAs and workflows
3. **Connect to your EHR**: Extend `src/nursing/store.cljc` with real patient/resident record integration
4. **Deploy and certify**: Follow `docs/operator-guide.md` for production deployment and regulatory certification

See `docs/business-model.md` for revenue and service models, and `docs/operator-guide.md` for deployment and certification requirements.
