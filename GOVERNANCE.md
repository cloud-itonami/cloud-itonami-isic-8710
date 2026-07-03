# Governance

`cloud-itonami-8710` is an OSS open-business blueprint for residential nursing care -- combined nursing, supervisory and personal care for residents requiring ongoing medical attention.
Governance covers both the capability layer and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the Nursing Care Governor remains independent of the advisor.
- hard policy violations (fabricated assessment, incomplete records) cannot be
  overridden by human approval.
- administering medication or finalizing an incident response always escalates to a human -- never automated.
- every hold, approval and care-action path is auditable.
- patient/resident and client data stay outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:

- bypassing the Nursing Care Governor's policy checks
- mishandling patient/resident/client data
- misrepresenting certification status
- failing to respond to security incidents
- hiding material changes to customer-facing operation
