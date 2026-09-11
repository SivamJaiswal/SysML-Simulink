# SysML  ↔  Simulink
Semantic Overlaps and Consistency Preservation Rules

Based on Grycz, Hagel, Eger, Reussner, Düser — *"Model-Based Activities for Supporting the
Synthesis of Validation Environments Using the V-SUM Approach"* (KIT / SFB 1608 "Convide",
submitted TdSEE Dortmund 2025/2026) · OMG SysML v2 Pilot Implementation / MASSIF Simulink Ecore ·
V-SUM / Vitruv Framework

## 1  Introduction

This document describes the semantic overlap between the **SysML v2** metamodel (OMG Pilot
Implementation, `SysML.ecore`) and the **Simulink** metamodel (MASSIF, `simulink.ecore`), and
specifies the resulting consistency preservation rules.

Unlike the AMALTHEA ↔ ASEM case study, there is no published correspondence table (no equivalent
of Mazkatli et al. 2016, Table 5.1) for this metamodel pair. The rules below are instead derived
directly from the two `.ecore` files, cross-checked class by class, and grounded in the real
engineering scenario described in Grycz et al.: a Brake-System-in-the-Loop validation environment
in which a brake-disc-temperature sensor was implemented in the physical rig and represented in
Simulink, but never propagated back into the descriptive SysML system model maintained in Cameo
Systems Modeler. Three things to read this document with in mind:

- Rules A–C are bidirectional. Rule D (the requirement/function traceability cascade) is
  **one-directional (Simulink → SysML only)** — Simulink has no Requirement or Function concept to
  originate the reverse direction from. This mirrors the paper's own demonstrated scenario.
- Several sub-rules are **intentionally omitted** where the source `.ecore` files don't support a
  faithful mapping without deeper, out-of-scope modeling. Each omission is documented with its
  reason, following the same convention as the AMALTHEA ↔ ASEM project's P11/P12 notes.
- Open questions equivalent to "confirm with Benedikt" are marked as *(needs domain-owner
  confirmation)*.

## 2  Metamodel Anchor Points

### 2.1  SysML elements in scope

- `Package` — VSUM root anchor; top-level `PartUsage`s correspond to root Simulink `Block`s
- `PartUsage` / `PartDefinition` — "technical architecture element"; nested via `nestedPart` (derived)
- `PortUsage` / `PortDefinition` — interaction points; disambiguated by `direction` (`in`/`out`/`inout`)
- `FlowUsage` / `FlowDefinition` — item/signal flow between two `flowEnd`s
- `ActionUsage` — stand-in for "function" (Rule D only)
- `RequirementUsage` — stand-in for "requirement" (Rule D only)
- All of the above ultimately inherit `declaredName` (real, settable) and `name` (derived,
  **not** reactable) from `Element`

### 2.2  Simulink elements in scope

- `SimulinkModel` — VSUM root anchor; `contains[]` holds root `Block`s
- `Block` / `SubSystem` — structural unit; `SubSystem.subBlocks[]` is real, non-derived containment
- `InPort` / `OutPort` — typed, directional ports in `Block.ports[]`
- `SingleConnection` — links one `OutPort` to one `InPort`
- `Parameter` — in `Block.parameters[]`; name/type/value/readOnly
- All `SimulinkElement`s have a derived `name`, computed from the real, settable
  `simulinkRef.name` (an `IdentifierReference`)

## 3  Semantic Overlap Summary

| Rule | SysML | Simulink / Condition |
|---|---|---|
| A | `PartUsage` (no children) | `Block` — no filter |
| A | `PartUsage` (`nestedPart->notEmpty()`) | `SubSystem` — has `subBlocks` |
| B | `PortUsage` (`direction = in`) | `InPort` |
| B | `PortUsage` (`direction = out`) | `OutPort` |
| B | `PortUsage` (`direction = inout`) | *(no mapping — see Rule B note)* |
| C | `FlowUsage` (`flowEnd->size() = 2`) | `SingleConnection` |
| D | `PartUsage` + sibling `ActionUsage` + sibling `RequirementUsage` | `Block`/`SubSystem` created with no corresponding architecture element (Simulink → SysML only) |

## 4  Correspondence Rules in Detail

### Rule A — PartUsage/PartDefinition  ↔  Block/SubSystem

- SysML: `PartUsage`, typed by a `PartDefinition` (`itemDefinition->selectByKind(PartDefinition)->notEmpty()`)
- Simulink: `Block` (leaf) or `SubSystem` (has children)
- Guard: `PartUsage.nestedPart->notEmpty()` ⇔ corresponding element must be `SubSystem`, not plain `Block`

Attribute correspondences

| SysML | Simulink |
|---|---|
| `PartUsage.declaredName` | `Block.simulinkRef.name` |
| `PartUsage.nestedPart[]` | `SubSystem.subBlocks[]` (only when non-empty) |

Sub-constraint — type migration on child-count crossing zero

```
Context sysml::PartUsage
  Inv: nestedPart->notEmpty() implies
       corresponding(simulink::SubSystem) and not corresponding(simulink::Block)
  Inv: nestedPart->isEmpty() implies
       corresponding(simulink::Block) and not corresponding(simulink::SubSystem)
```

Just as AMALTHEA `Label.constant` flipping forced an ASEM `Message`↔`Constant` swap (Rule 5/6,
P8/P9 in the reference project), a `PartUsage` gaining its first nested part — or losing its last
one — forces a `Block`↔`SubSystem` swap on the Simulink side, since `SubSystem` is a distinct
EClass, not a flag on `Block`.

### Rule B — PortUsage  ↔  InPort / OutPort

- SysML: `PortUsage`, disambiguated by the inherited `Feature.direction` attribute
- Simulink: `InPort` / `OutPort`, both subtypes of the abstract `Port`

OCL guard

```
Context sysml::PortUsage
  Inv is_input:  self.direction = FeatureDirectionKind::_'in'
  Inv is_output: self.direction = FeatureDirectionKind::out
```

Attribute correspondences

| SysML | Simulink |
|---|---|
| `PortUsage.declaredName` | `InPort.simulinkRef.name` / `OutPort.simulinkRef.name` |
| `PortUsage` nested under `PartUsage` | `Port` in `Block.ports[]` |

Note — `direction = inout` has no Simulink target. Simulink's `Port` hierarchy is strictly
one-directional by class (`InPort` vs `OutPort` are disjoint EClasses); there is no bidirectional
port EClass to map to. Left unmapped. *(needs domain-owner confirmation on whether `inout` ports
should be split into a paired in/out port on the Simulink side.)*

Note — the reverse direction has a gap of its own: Simulink's `Trigger`, `Enable`, and `State`
(all subtypes of `InPort`/`OutPort`) carry control-flow/simulation semantics that a plain SysML
`PortUsage` + `direction` cannot express. These are intentionally **not** propagated to SysML.

### Rule C — FlowUsage  ↔  SingleConnection

- SysML: `FlowUsage`, `flowEnd->size() = 2`, ends typed by an `out`- and an `in`-directioned `PortUsage`
- Simulink: `SingleConnection`, `from` (`OutPort`) → `to` (`InPort`)
- No OCL filter beyond the two-ended constraint (already enforced by `FlowUsage`'s own invariant
  `flowEnd->size() <= 2`)

Attribute correspondences

| SysML | Simulink |
|---|---|
| `FlowUsage.flowEnd[1]` (end typed by the `out` `PortUsage`) | `SingleConnection.from` (via the corresponding `OutPort`) |
| `FlowUsage.flowEnd[2]` (end typed by the `in` `PortUsage`) | `SingleConnection.to` (via the corresponding `InPort`) |

Note — Simulink's `MultiConnection` (one `OutPort` fanning out to several `InPort`s via nested
`SingleConnection`s) has no single-`FlowUsage` equivalent in this rule. A faithful mapping would
require either (a) several `FlowUsage`s sharing the same source `flowEnd`, or (b) a dedicated
multi-ended `FlowUsage`. Neither is implemented — **omitted**, analogous to the reference project's
P11/P12 notes.

### Rule D — Traceability cascade: Block/SubSystem → PartUsage + ActionUsage + RequirementUsage

This rule is not a metaclass-to-metaclass correspondence like A–C; it reproduces the specific
scenario from Grycz et al. §3.3–4.2: when a Simulink `Block`/`SubSystem` exists with **no**
corresponding SysML architecture element, propagation does not stop at creating a `PartUsage`
(Rule A, E3) — it also creates a sibling `ActionUsage` ("function") and a sibling
`RequirementUsage` ("requirement"), all three named consistently after the originating block, in
the same owning `Package`. This is a direct implementation of the paper's own worked example: a
new Simulink block named `TemperatureMonitor` results in *"a newly created requirement capturing
the brake disc temperature monitoring functionality, a corresponding function for processing the
temperature information, a technical architecture element ... consistent naming across all
generated elements"* (§4.2, verbatim).

- Direction: **Simulink → SysML only.** Simulink has no requirement/function concept to originate
  the reverse cascade from; a SysML-side `PartUsage` created directly (Rule A, E1) does *not*
  trigger this cascade — only propagation originating from an *unmatched* Simulink block does.

Attribute correspondences

| Simulink (trigger) | SysML (generated) |
|---|---|
| `Block.simulinkRef.name` = `"X"` | `PartUsage.declaredName` = `"X"` (Rule A, unchanged) |
| — | `ActionUsage.declaredName` = `"process" + X` |
| — | `RequirementUsage.declaredName` = `X + "Requirement"` |

**Omission, honestly flagged (same spirit as the reference project's P11/P12 notes):** the cascade
creates three *sibling* elements with consistent names, but does **not** create the formal
`SatisfyRequirementUsage` / `PerformActionUsage` / `AllocationUsage` relationship objects that
would properly wire Requirement → Function → Architecture together in SysML's traceability model.
Grycz et al. flag exactly this same gap themselves (§5.2, discussing Figure 6): *"a further
refining and detailing could be accomplished ... additional relationships such as «satisfy»,
«allocate», and trace links between requirements, functions, and technical architecture elements
can be established"* — i.e. the paper's own case study also stops at generating the three
elements and leaves the relationship wiring as manual/future work. This project does the same,
deliberately, rather than inventing an under-specified auto-wiring policy.

## 5  OCL Invariant Summary

```
-- Rule A: PartUsage ↔ Block/SubSystem
Context sysml::PartUsage
  Inv part_definition: itemDefinition->selectByKind(PartDefinition)->notEmpty()
  Inv subsystem_swap:  nestedPart->notEmpty() implies corresponding(simulink::SubSystem)

-- Rule B: PortUsage ↔ InPort/OutPort
Context sysml::PortUsage
  Inv is_input:  self.direction = FeatureDirectionKind::_'in'
  Inv is_output: self.direction = FeatureDirectionKind::out
  Inv no_target: self.direction = FeatureDirectionKind::inout implies not corresponding(simulink::Port)

-- Rule C: FlowUsage ↔ SingleConnection
Context sysml::FlowUsage
  Inv two_ended: flowEnd->size() = 2

-- Rule D: cascade naming consistency
Context sysml::PartUsage
  Inv cascade_names:
      correspondingActionUsage()->notEmpty() implies
          correspondingActionUsage().declaredName = 'process' + self.declaredName
      correspondingRequirementUsage()->notEmpty() implies
          correspondingRequirementUsage().declaredName = self.declaredName + 'Requirement'
```

## 6  Consistency Preservation Rules

Grouped, as in the AMALTHEA ↔ ASEM project, into Existence (E), Property (P), Structural (S), and
Completeness (C).

### 6.1  Existence Rules — element creation and deletion

- **E1**  SysML `PartUsage` created (no children) → create Simulink `Block` with same name; add correspondence
- **E2**  SysML `PartUsage` deleted → delete corresponding `Block`/`SubSystem`; remove correspondence
- **E3**  Simulink `Block`/`SubSystem` created → create SysML `PartUsage` with same name **and** trigger Rule D's cascade (`ActionUsage` + `RequirementUsage`); add correspondence
- **E4**  Simulink `Block`/`SubSystem` deleted → delete corresponding `PartUsage` (and its Rule D siblings, if present)

- **E5**  SysML `PortUsage` (`direction = in`) created → create Simulink `InPort` in parent `Block.ports`
- **E6**  SysML `PortUsage` (`direction = out`) created → create Simulink `OutPort` in parent `Block.ports`
- **E7**  SysML `PortUsage` deleted → delete corresponding `InPort`/`OutPort`
- **E8**  Simulink `InPort` created → create SysML `PortUsage` (`direction = in`)
- **E9**  Simulink `OutPort` created → create SysML `PortUsage` (`direction = out`)
- **E10** Simulink `InPort`/`OutPort` deleted → delete corresponding `PortUsage`

- **E11** SysML `FlowUsage` (2-ended, out→in) created → create Simulink `SingleConnection` linking the corresponding `OutPort`/`InPort`
- **E12** SysML `FlowUsage` deleted → delete corresponding `SingleConnection`
- **E13** Simulink `SingleConnection` created → create SysML `FlowUsage` between the corresponding `PortUsage`s
- **E14** Simulink `SingleConnection` deleted → delete corresponding `FlowUsage`

### 6.2  Property Rules — attribute value changes

- **P1** `PartUsage.declaredName` changed → set `Block.simulinkRef.name` = new name
- **P2** `Block.simulinkRef.name` changed → set `PartUsage.declaredName` = new name
- **P3** `PortUsage.declaredName` changed → set `InPort`/`OutPort`'s `simulinkRef.name` = new name
- **P4** `InPort`/`OutPort`'s `simulinkRef.name` changed → set `PortUsage.declaredName` = new name
- **P5** `PartUsage.declaredName` changed (where Rule D siblings exist) → cascade-rename the corresponding `ActionUsage`/`RequirementUsage` to keep `'process' + name` / `name + 'Requirement'` consistent

### 6.3  Structural Rules — containment and type-migration changes

- **S1** `PartUsage` gains its first `nestedPart` (was childless) → replace corresponding `Block` with a `SubSystem` of the same name; move existing port/parameter correspondences over; update correspondence
- **S2** `PartUsage` loses its last `nestedPart` → replace corresponding `SubSystem` with a plain `Block`; update correspondence
- **S3** Simulink `Block` moved into a `SubSystem.subBlocks` (i.e. gains a `parent`) → add corresponding `PartUsage` as a `nestedPart` of the corresponding parent `PartUsage`

### 6.4  Completeness Rules — model-wide invariants

- **C1** Every childless `PartUsage` has a corresponding `Block`, and vice versa; every `PartUsage` with `nestedPart->notEmpty()` has a corresponding `SubSystem`, and vice versa
- **C2** Every `PortUsage` with `direction ∈ {in, out}` has a corresponding `InPort`/`OutPort`, and vice versa (excluding `Trigger`/`Enable`/`State`, which have no SysML counterpart)
- **C3** Every 2-ended `FlowUsage` has a corresponding `SingleConnection`, and vice versa (excluding `MultiConnection`, which is out of scope)
- **C4** Every SysML `PartUsage` created via Rule D's cascade has exactly one sibling `ActionUsage` and one sibling `RequirementUsage` with names consistent with `declaredName`

---
Prepared for V-SUM consistency preservation implementation · Real-world scenario source: Grycz et
al., KIT/SFB 1608 "Convide" · OMG SysML v2 Pilot Implementation / MASSIF Simulink Ecore
