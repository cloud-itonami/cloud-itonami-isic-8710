# Business Model: Residential nursing care facilities

## Classification

- Repository: `cloud-itonami-8710`
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
