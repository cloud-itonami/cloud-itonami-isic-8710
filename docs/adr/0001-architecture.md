# ADR-0001: NursingOps-LLM ⊣ Nursing Care Governor architecture

## Status

Accepted. `cloud-itonami-isic-8710` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-8710` publishes an OSS business blueprint for
residential nursing care: combined nursing, supervisory and personal
care for residents requiring ongoing medical attention, run by a
qualified, licensed operator so a community or independent provider
never surrenders patient/resident data and ledgers to a closed SaaS.
Like every prior actor in this fleet, the blueprint alone is not an
implementation: this ADR records the governed-actor architecture that
promotes it to real, tested code, following the same langgraph-clj
StateGraph + independent Governor + Phase 0→3 rollout pattern
established by `cloud-itonami-isic-6511` (life insurance) and applied
across fifty-seven prior siblings, most recently `cloud-itonami-isic-
7410` (specialized design activities).

## Decision

### Decision 1: entity and op shape

The primary entity is a `resident` (a residential-nursing-care
resident, matching the blueprint's own Offer language -- "resident
intake", "care-plan proposal", "medication/incident-response
proposal"). Five ops: `:resident/intake` (directory upsert, no capital
risk), `:careplan/verify` (per-jurisdiction residential-nursing-care
evidence checklist, never auto), `:credential/screen` (nursing-staff-
credential-currency screening, unconditional-evaluation discipline,
never auto), `:actuation/administer-medication` (POSITIVE, high-
stakes -- administering a real medication dose), and `:actuation/
finalize-incident-response` (POSITIVE, high-stakes -- finalizing a
real incident response).

### Decision 2: dual-actuation shape on one entity

This blueprint's own operator-guide names TWO real-world acts ("no
medication is administered and no incident response is finalized
without human sign-off"), both acting on the SAME entity (the
resident). Matching `6512`/`6622`/`6520`/`6530`/`6820`/`6920`/`6611`/
`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/`8510`/`9412`/
`8720`/`8521`/`6619`/`3600`/`6190`/`3030`/`3830`/`9420`/`9491`/`2610`/
`3512`/`8810`/`8691`/`8569`/`6419`'s dual-actuation-on-one-entity
shape, `high-stakes` is the two-member set `#{:actuation/administer-
medication :actuation/finalize-incident-response}`, each with its own
history collection, sequence counter, and dedicated double-actuation-
guard boolean (`:medication-administered?`/`:incident-response-
finalized?`, never a single `:status` value).

### Decision 3: `medication-contraindicated?` -- the 3rd literal reuse of the set-membership/conflict family

`clinic.registry/treatment-contraindicated?` established the FIRST
instance of this fleet's set-membership/conflict check family;
`veterinary.registry/treatment-contraindicated?` reused it literally
for the veterinary domain as the SECOND; `entertainment.governor/
release-channel-restricted-violations` reused the shape under a
different name as the third instance of the broader family.
`nursing.registry/medication-contraindicated?` is the THIRD literal
reuse of the "contraindicated" concept specifically (clinic,
veterinary, now nursing) -- the same real-world failure mode (a
proposed medication/treatment appearing on the patient/resident's own
recorded contraindication list) genuinely recurs across every
licensed-medical-care vertical in this fleet, so reusing the literal
name is honest, not a stretch. Gates only `:actuation/administer-
medication`.

### Decision 4: `medication-dosage-exceeds-maximum?` -- the 8th MAXIMUM-ceiling check

Following `facility.registry/occupancy-exceeds-capacity?` (1st),
`school.registry/class-size-exceeds-maximum?` (2nd), `card.registry/
settlement-amount-exceeds-authorized?` (3rd), `recovery.registry/
contamination-percentage-exceeds-maximum?` (4th), `care.registry/
caregiver-workload-exceeds-maximum?` (5th), `navigator.registry/
eligibility-window-elapsed-exceeds-validity?` (6th) and `advertising.
registry/media-spend-exceeds-authorized?` (7th), `nursing.registry/
medication-dosage-exceeds-maximum?` applies the same lo-bound-absent/
hi-bound-only comparison to a resident's own recorded proposed
medication dosage against their own recorded maximum-authorized
dosage -- a direct, natural mapping onto real medication-safety
practice. Gates only `:actuation/administer-medication`.

### Decision 5: `credential-not-current-violations` -- the 42nd unconditional-evaluation grounding, a literal reuse (not new)

This exact concept (a licensed professional's credential/license
currency, evaluated unconditionally so the screening op itself can
HARD-hold on its own finding) was already established by `clinic.
governor/credential-not-current-violations` and has since been reused
literally by `hospital`/`eldercare`/`veterinary`/`conservation`/
`museum`/`salon`/`entertainment`/`funeral`/`repairshop`/`registrar`/
`wagering`/`facility`/`casework`/`parksafety` and others (verified via
grep across every sibling's governor namespace before this claim was
finalized -- ~17 siblings already reference this exact concept).
`nursing.governor/credential-not-current-violations` is NOT claimed as
new; it is the 42nd distinct application of the unconditional-
evaluation discipline overall (continuing the count established
across this fleet's builds, most recently `design.governor/ip-
licensing-conflict-unresolved-violations` at 41st), and the most
natural, honest choice for this vertical since `hospital`/`eldercare`
-- the two nearest-neighbor verticals to residential nursing care --
already established it. Directly grounded in this blueprint's own
operator-guide text: "licensed-professional sign-off required before
any determination." Gates `:credential/screen` and both actuation
ops.

### Decision 6: Store protocol, MemStore + DatomicStore parity

`nursing.store/Store` is implemented by both `MemStore` (atom-backed,
default for dev/tests/demo) and `DatomicStore` (`langchain.db`-
backed), proven to satisfy the same contract in `test/nursing/
store_contract_test.clj` -- the same seam every sibling actor uses so
swapping the SSoT backend is a configuration change, not a rewrite.
The protocol's per-entity accessor is named `resident` directly -- not
a Clojure special form, so no `-of` suffix workaround was needed
(matching `cloud-itonami-isic-7410`'s own `project` accessor
precedent).

### Decision 7: Phase 0→3 rollout

Phase 3's `:auto` set has exactly one member, `:resident/intake` (no
capital risk). `:careplan/verify` and `:credential/screen` are never
auto-eligible at any phase (matching every sibling's screening-op
posture), and both `:actuation/administer-medication`/`:actuation/
finalize-incident-response` are permanently excluded from every
phase's `:auto` set -- a structural fact, not a rollout milestone,
enforced by BOTH `nursing.phase` and `nursing.governor`'s `high-
stakes` set independently.

### Decision 8: no bespoke domain capability lib

This blueprint's own `:itonami.blueprint/required-technologies` names
no domain-specific capability beyond the generic robotics/identity/
forms/dmn/bpmn/audit-ledger stack -- resident/care records here are
practice-specific rather than a shared cross-operator data contract,
so there was no capability-lib decision to make at all.

### Decision 9: mock + LLM advisor pair

`nursing.nursingadvisor` provides `mock-advisor` (deterministic,
default everywhere -- the actor graph and governor contract run
offline) and `llm-advisor` (backed by `langchain.model/ChatModel`,
with a defensive EDN-proposal parser so a malformed LLM response
degrades to a safe low-confidence noop rather than ever auto-
administering medication or auto-finalizing an incident response).

### Decision 10: no `blueprint.edn` field-sync fixes needed

Matching `advertising`/7310's, `polling`/7320's, `research`/7210's and
`design`/7410's own experience, this repo's `blueprint.edn` already
had the correct `isic-` prefixed `:id` and correctly populated
`:required-technologies`/`:optional-technologies` matching the
`kotoba-lang/industry` registry's own entry for `"8710"` exactly --
only the `:maturity` field itself needed adding.

## Alternatives considered

- **A single-actuation shape** (only medication administration, or
  only incident-response finalization). Rejected: the blueprint's own
  operator-guide text names BOTH acts explicitly ("no medication is
  administered and no incident response is finalized without human
  sign-off") -- omitting either would understate the blueprint's own
  scope.
- **Inventing a new name for the credential-currency concept** (e.g.
  "nursing-license-lapsed") instead of reusing `credential-not-
  current`. Rejected: this is genuinely the SAME real-world concept
  already established by `clinic`/`hospital`/`eldercare`/`veterinary`
  and over a dozen other licensed-professional siblings -- inventing a
  new name would obscure that this is the same concept, not a novel
  one, violating the precedent-verification discipline this fleet's
  ADRs apply in both directions (claim reuse honestly when it IS
  reuse, same as claiming novelty honestly when it IS novel).
- **Merging `medication-contraindicated?` and `medication-dosage-
  exceeds-maximum?` into one check.** Rejected: they are independent
  ground-truth recomputes over different field pairs (set membership
  vs. numeric ceiling) -- merging would obscure which specific failure
  mode triggered a hold, and would break the pattern every sibling's
  test suite exercises each check independently.

## Consequences

- Fifty-eighth actor in this fleet (57 implemented before this build).
- Confirms the set-membership/conflict check family generalizes to a
  4th instance overall, and the literal "contraindicated" concept to a
  3rd instance.
- Confirms the MAXIMUM-ceiling check family generalizes to an 8th
  instance.
- Honestly reuses (not "invents") the credential-not-current concept
  for the 42nd unconditional-evaluation grounding overall, grep-
  verified as an established concept across ~17 prior siblings before
  this claim was finalized.
- `MemStore` ‖ `DatomicStore` parity is proven by `test/nursing/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- `blueprint.edn` required no field-sync fixes this time (already
  correct) -- only the `:maturity` flip itself.

## References

- `orgs/cloud-itonami/cloud-itonami-isic-8710/README.md`
- `orgs/cloud-itonami/cloud-itonami-isic-8710/docs/business-model.md`
- `orgs/kotoba-lang/industry/resources/kotoba/industry/registry.edn` (entry `"8710"`)
