# cloud-itonami-isic-8710

Open Business Blueprint for **ISIC Rev.5 8710**: Residential nursing
care facilities.

This repository publishes a residential-nursing-care actor -- resident
intake, residential-nursing-care regulatory assessment, nursing-staff-
credential screening, medication administration and incident-response
finalization -- as an OSS business that any qualified, licensed
operator can fork, deploy, run, improve and sell, so a community or
independent provider never surrenders patient/resident data and
ledgers to a closed SaaS.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720),
[`8521`](https://github.com/cloud-itonami/cloud-itonami-isic-8521),
[`6619`](https://github.com/cloud-itonami/cloud-itonami-isic-6619),
[`3600`](https://github.com/cloud-itonami/cloud-itonami-isic-3600),
[`6190`](https://github.com/cloud-itonami/cloud-itonami-isic-6190),
[`3030`](https://github.com/cloud-itonami/cloud-itonami-isic-3030),
[`3830`](https://github.com/cloud-itonami/cloud-itonami-isic-3830),
[`7020`](https://github.com/cloud-itonami/cloud-itonami-isic-7020),
[`9420`](https://github.com/cloud-itonami/cloud-itonami-isic-9420),
[`9491`](https://github.com/cloud-itonami/cloud-itonami-isic-9491),
[`2610`](https://github.com/cloud-itonami/cloud-itonami-isic-2610),
[`3512`](https://github.com/cloud-itonami/cloud-itonami-isic-3512),
[`8810`](https://github.com/cloud-itonami/cloud-itonami-isic-8810),
[`8691`](https://github.com/cloud-itonami/cloud-itonami-isic-8691),
[`8569`](https://github.com/cloud-itonami/cloud-itonami-isic-8569),
[`6419`](https://github.com/cloud-itonami/cloud-itonami-isic-6419),
[`7310`](https://github.com/cloud-itonami/cloud-itonami-isic-7310),
[`7320`](https://github.com/cloud-itonami/cloud-itonami-isic-7320),
[`7210`](https://github.com/cloud-itonami/cloud-itonami-isic-7210),
[`7410`](https://github.com/cloud-itonami/cloud-itonami-isic-7410)) --
here it is **NursingOps-LLM ⊣ Nursing Care Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a
> resident-intake summary, normalizing records, and checking whether a
> proposed medication actually appears on a resident's own recorded
> contraindication list -- but it has **no notion of which
> jurisdiction's residential-nursing-care law is official, no license
> to administer a real medication dose or finalize a real incident
> response, and no way to know on its own whether a nursing-staff
> credential has actually stayed current**. Letting it administer
> medication or finalize an incident response directly invites
> fabricated regulatory citations, a contraindicated medication or an
> above-maximum dosage being administered, and a not-current
> credential being quietly overlooked -- and liability, and resident-
> safety risk, for whoever runs it. This project seals the
> NursingOps-LLM into a single node and wraps it with an independent
> **Nursing Care Governor**, a human **approval workflow**, and an
> immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers resident intake through residential-nursing-care
regulatory assessment, nursing-staff-credential screening, medication
administration and incident-response finalization. It does **not**,
by itself, hold any license required to operate as a nursing-care
facility in a given jurisdiction, and it does not claim to. It also
does not model a real electronic medication-administration-record
system, the actual physical act of administering medication, or
clinical/nursing judgment -- `nursing.registry/medication-dosage-
exceeds-maximum?` is a pure ceiling recompute against the resident's
own recorded fields, not a clinical assessment. Whoever deploys and
operates a live instance (a licensed nursing-care facility) supplies
any jurisdiction-specific license, the real nursing workforce and the
real electronic-health-record integrations, and bears that
jurisdiction's liability -- the software supplies the governed,
spec-cited, audited execution scaffold so that facility does not have
to build the compliance layer from scratch.

### Actuation

**Administering a real medication dose or finalizing a real incident
response is never autonomous, at any phase, by construction.** Two
independent layers enforce this (`nursing.governor`'s `:actuation/
administer-medication`/`:actuation/finalize-incident-response`
high-stakes gate and `nursing.phase`'s phase table, which never puts
`:actuation/administer-medication`/`:actuation/finalize-incident-
response` in any phase's `:auto` set) -- see `nursing.phase`'s
docstring and `test/nursing/phase_test.clj`'s `administer-medication-
never-auto-at-any-phase`/`finalize-incident-response-never-auto-at-
any-phase`. The actor may draft, check and recommend; a human licensed
nurse is always the one who actually administers medication or
finalizes an incident response. Like `6512`/`6622`/`6520`/`6530`/
`6820`/`6920`/`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/
`8610`/`8510`/`9412`/`8720`/`8521`/`6619`/`3600`/`6190`/`3030`/`3830`/
`9420`/`9491`/`2610`/`3512`/`8810`/`8691`/`8569`/`6419`, this actor has
TWO actuation events, both POSITIVE (administering/finalizing a real
record), matching the majority pattern in this fleet (`3600`/`6190`
are the fleet's two NEGATIVE-actuation exceptions).

## The core contract

```
resident intake + jurisdiction facts (nursing.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ NursingOps-  │ ─────────────▶ │ Nursing Care Governor:        │  (independent system)
   │ LLM (sealed) │  + citations    │ spec-basis · evidence-        │
   └──────────────┘                 │ incomplete · medication-       │
          │                 commit ◀┼ contraindicated · medication-  │
          │                         │ dosage-exceeds-maximum ·        │
    record + ledger        escalate ┼ credential-not-current           │
          │              (ALWAYS for│ (unconditional) ·                 │
          │               :actuation│ already-administered/-finalized   │
          │               /administer-└───────────────────────┘
          ▼               medication /
      human approval      :actuation/finalize-
                           incident-response)
```

**The NursingOps-LLM never administers medication or finalizes an
incident response the Nursing Care Governor would reject, and never
does so without a human sign-off.** Hard violations (fabricated
regulatory requirements; unsupported evidence; a contraindicated
medication; a dosage above the resident's own recorded maximum; a
not-current nursing-staff credential; a double administration or
finalization) force **hold** and *cannot* be approved past; a clean
proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a resident-monitoring and
mobility-assist robot supports physical care tasks under the actor,
gated by the independent **Nursing Care Governor**. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions require
human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Nursing Care Governor, medication-administration + incident-response-finalization draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`8710`). This vertical's care/case records are practice-specific rather
than a shared cross-operator data contract, so `nursing.*` runs on the
generic robotics/identity/forms/dmn/bpmn/audit-ledger stack only -- no
bespoke domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/nursing/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate medication-administration/incident-response-finalization history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded resident, and the double-actuation guards check dedicated `:medication-administered?`/`:incident-response-finalized?` booleans rather than a `:status` value |
| `src/nursing/registry.cljc` | Medication-administration + incident-response-finalization draft records, plus `medication-contraindicated?` (the THIRD literal reuse of `clinic.registry/treatment-contraindicated?`'s set-membership/conflict concept, after `veterinary`) and `medication-dosage-exceeds-maximum?` -- the EIGHTH instance of this fleet's MAXIMUM-ceiling check family (`facility`/`school`/`card`/`recovery`/`care`/`navigator`/`advertising` established the first seven) |
| `src/nursing/facts.cljc` | Per-jurisdiction residential-nursing-care catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/nursing/nursingadvisor.cljc` | **NursingOps-LLM** -- `mock-advisor` ‖ `llm-advisor`; intake/careplan-verification/credential-screening/medication-administration/incident-response-finalization proposals |
| `src/nursing/governor.cljc` | **Nursing Care Governor** -- 5 HARD checks (spec-basis · evidence-incomplete · medication-contraindicated, set-membership/conflict recompute · medication-dosage-exceeds-maximum, pure ground-truth ceiling recompute · credential-not-current, unconditional evaluation, the FORTY-SECOND grounding of this discipline, a literal reuse already established by `clinic`/`hospital`/`eldercare`/`veterinary` and several other licensed-professional siblings) + already-administered/already-finalized guards + 1 soft (confidence/actuation gate) |
| `src/nursing/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (both medication administration and incident-response finalization always human; resident intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/nursing/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/nursing/sim.cljc` | demo driver |
| `test/nursing/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers resident intake through residential-nursing-care
regulatory assessment, nursing-staff-credential screening, medication
administration and incident-response finalization -- the core governed
lifecycle this blueprint's own `docs/business-model.md` names as its
Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Resident intake + per-jurisdiction residential-nursing-care checklisting, HARD-gated on an official spec-basis citation (`:resident/intake`/`:careplan/verify`) | Real electronic-medication-administration-record integration, real physical nursing care itself (see `nursing.facts`'s docstring) |
| Nursing-staff-credential screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:credential/screen`) | Any clinical/nursing judgment itself -- deliberately outside this actor's competence |
| Medication administration, HARD-gated on full evidence, the resident's own contraindication list, and their own maximum-authorized dosage, plus a double-administration guard (`:actuation/administer-medication`) | |
| Incident-response finalization, HARD-gated on full evidence, plus a double-finalization guard (`:actuation/finalize-incident-response`) | |
| Immutable audit ledger for every intake/verification/screening/administration/finalization decision | |

Extending coverage is additive: add the next gate (e.g. a fall-risk-
reassessment cadence) as its own governed op with its own HARD checks
and tests, following the SAME "an independent governor re-verifies
against the actor's own records before any real-world act" pattern
this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`nursing.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `nursing.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `nursing.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `NursingOps-LLM` + `Nursing Care Governor` run as
real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the fifty-
seven prior actors' architecture. See `docs/adr/0001-architecture.md`
for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
