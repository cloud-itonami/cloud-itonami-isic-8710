# cloud-itonami-isic-8710

Open Business Blueprint for **ISIC Rev.5 8710**: Residential nursing care facilities.

This repository designs a forkable OSS business for residential nursing care -- combined nursing, supervisory and personal care for residents requiring ongoing medical attention -- run by a qualified, licensed operator so a community or
independent provider never surrenders patient/resident data and ledgers to a
closed SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a resident-monitoring and mobility-assist robot supports physical care tasks,
under an actor that proposes actions and an independent **Nursing Care Governor**
that gates them. The governor never dispatches hardware itself;
`:high`/`:safety-critical` actions require human sign-off.

## Core Contract

```text
intake + identity + care records
        |
        v
NursingOps-LLM -> Nursing Care Governor -> hold, proceed, or human approval
        |
        v
care ledger + evidence record + audit
```

No automated proposal, by itself, can complete the following without governor
approval and audit evidence: administering medication or finalizing an incident response.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`8710`). This vertical's care/case records are practice-specific rather
than a shared cross-operator data contract, so it runs on the generic
identity/forms/dmn/bpmn/audit-ledger stack -- no bespoke domain capability lib.

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Maturity

`:blueprint` -- this repository is the published business/operator design.
The governed actor implementation (`NursingOps-LLM` + `Nursing Care Governor` as
running code) is a follow-up, same as any other `:blueprint`-tier
`cloud-itonami-*` entry in `kotoba-lang/industry`'s registry.

## License

Code and implementation templates are AGPL-3.0-or-later.
