# Business Model: Residential nursing care facilities

## Classification

- Repository: `cloud-itonami-isic-8710`
- ISIC Rev.5: `8710`
- Activity: residential nursing care -- combined nursing, supervisory and personal care for residents requiring ongoing medical attention
- Social impact: care quality, data sovereignty, transparent audit

## Customer

- independent nursing homes
- cooperative eldercare facilities
- community long-term-care operators

## Offer

- resident intake
- care-plan proposal
- medication/incident-response proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per facility
- support: monthly retainer with SLA
- migration: import from an incumbent long-term-care system
- per-resident-month fee

## Trust Controls

- no medication is administered and no incident response is finalized without human sign-off (licensed nursing staff)
- a fabricated care-plan rationale forces a hold, not an override
- every care path is auditable
- resident health data stays outside Git
- emergency manual override paths remain outside LLM control
- a medication that appears on a resident's own recorded contraindication
  list, or a proposed dosage above their own recorded maximum-authorized
  dosage, forces a hold, not an override
- medication administration and incident-response finalization are each
  logged and escalated, and cannot be finalized twice for the same
  resident: a double-administration or double-finalization attempt is
  held off this actor's own resident facts alone, with no upstream
  comparison needed

## Nursing Care Governor: decision rule

`blueprint.edn` fixes `:itonami.blueprint/governor` to `:nursing-care-
governor` -- this is not a generic "review step," it is the gate the
two real-world acts this business performs (administering medication,
finalizing an incident response) must pass. The governor sits between
the NursingOps-LLM and execution, per the README's Core Contract:

```text
NursingOps-LLM -> Nursing Care Governor -> hold, proceed, or human approval
```

**Approves**: routine nursing-care actions proposed against a resident
that already has a consented care plan on file, a medication that is
neither on the resident's own contraindication list nor above their
own recorded maximum dosage, and a current nursing-staff credential.
These proceed straight to the care ledger.

**Rejects or escalates**: the governor refuses to let the advisor
administer medication or finalize an incident response on its own
authority when any of the following hold -- a fabricated jurisdiction
spec-basis; incomplete evidence; a medication on the resident's own
contraindication list; a dosage above the resident's own recorded
maximum; a not-current nursing-staff credential. A clean proposal
still always routes to a human -- `:actuation/administer-medication`/
`:actuation/finalize-incident-response` are never auto-committed, at
any rollout phase.
