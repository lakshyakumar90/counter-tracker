# P00 Completion Matrix

P00 is the product scope and acceptance phase. Its exit gate is approved requirements and measurable acceptance criteria. The phase evidence is the product documentation in this directory.

| Roadmap task | Evidence | Result |
| --- | --- | --- |
| P00-T001 Write one-page product vision | `vision-and-scope.md` — vision, principles and audience | Complete |
| P00-T002 Freeze supported sports | `vision-and-scope.md` — four outdoor sports and home mode boundary | Complete |
| P00-T003 Define non-goals | `vision-and-scope.md` — explicit V1 exclusions | Complete |
| P00-T004 Define user types | `vision-and-scope.md` — local, returning, cloud, outdoor and home users | Complete |
| P00-T005 List user journeys | `vision-and-scope.md` — seven main journeys including failures | Complete |
| P00-T006 Define terminology | `vision-and-scope.md` — activity and metric vocabulary | Complete |
| P00-T007 Choose primary unit conventions | `vision-and-scope.md` — SI storage, metric default, imperial option | Complete (provisional India-first default) |
| P00-T008 Specify offline-first contract | `offline-privacy-and-recovery.md` — offline capability matrix | Complete |
| P00-T009 Define cloud restore contract | `offline-privacy-and-recovery.md` — backup, merge, deletion and retry rules | Complete |
| P00-T010 Define AI boundaries | `vision-and-scope.md` — opt-in, post-core, server-mediated AI | Complete |
| P00-T011 Define activity state machine | `activity-state-machine.md` — states and transition behavior | Complete |
| P00-T012 Define permissions strategy | `offline-privacy-and-recovery.md` — just-in-time requests and denial handling | Complete |
| P00-T013 Define data sensitivity policy | `offline-privacy-and-recovery.md` — classification and handling | Complete |
| P00-T014 Define MVP acceptance matrix | `acceptance-and-quality.md` — binary exit criteria | Complete (thresholds to validate in implementation) |
| P00-T015 Define release channels | `planning-and-gates.md` — debug, internal, closed beta, production | Complete |
| P00-T016 Create risk register | `planning-and-gates.md` — risks, mitigations and single owner | Complete |
| P00-T017 Set up issue labels and priorities | `planning-and-gates.md` — priority and severity taxonomy | Complete (documented; apply to tracker when one exists) |
| P00-T018 Define analytics calculation glossary | `acceptance-and-quality.md` — formulas, source and edge behavior | Complete |
| P00-T019 Define user-facing error policy | `acceptance-and-quality.md` — visible errors, saved-state and recovery | Complete |
| P00-T020 Approve phase-gate checklist | `planning-and-gates.md` — P00 exit and downstream gates | Complete |
| P00-T101 Record capacity and velocity baseline | `planning-and-gates.md` — provisional 8h/week, 4 points/week; reforecast after P05 | Complete (assumption, not measured velocity) |
| P00-T102 Define MVP cut line and deferral list | `vision-and-scope.md` — P13–P16 initial V1.1 deferrals; P17 backup-first | Complete (provisional; review after P05) |
| P00-T103 Write the Definition of Done | `acceptance-and-quality.md` — task-level quality checklist | Complete |
| P00-T104 Write threat model for GPS and personal data | `offline-privacy-and-recovery.md` — threat scenarios and controls | Complete (product baseline; technical validation in P19) |
| P00-T105 Review privacy-law applicability | `offline-privacy-and-recovery.md` — dated review of India DPDP phased commencement and EU GDPR territorial trigger with official sources | Complete as a product planning review; counsel review required before launch |

## Gate disposition

**Documentation gate: ready for product-owner review.** This phase defines concrete defaults and acceptance evidence, but legal review, user research, capacity measurement, and final confirmation of provisional scope/capacity/launch-region assumptions remain explicit checkpoints. No public release or legal compliance claim follows from this documentation alone.
