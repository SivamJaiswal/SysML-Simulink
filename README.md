# SysML ↔ Simulink

**Vitruv-Based Bidirectional Model Consistency Preservation**

A Maven project implementing bidirectional change propagation between the **SysML v2** metamodel (OMG Pilot Implementation) and the Simulink metamodel (MASSIF) using the Vitruv framework, grounded in a real KIT/SFB 1608 "Convide" research case study.

## Table of Contents

- [1. SysML Metamodel Description](#1-sysml-metamodel-description)
- [2. Simulink Metamodel Description](#2-simulink-metamodel-description)
- [3. Semantic Overlaps and Consistency Preservation Rules](#3-semantic-overlaps-and-consistency-preservation-rules)
- [4. Building and Running](#4-building-and-running)
- [License](#license)

---

## 1. SysML Metamodel Description

*OMG SysML v2 Pilot Implementation*

URI: `https://www.omg.org/spec/SysML/20250201` | Source: [Systems-Modeling/SysML-v2-Pilot-Implementation](https://github.com/Systems-Modeling/SysML-v2-Pilot-Implementation)

This project targets **SysML v2** specifically, not v1 — the OMG's ground-up language redesign built on a shared kernel foundation, not an extension of the older SysML v1/UML profile.

### 1.1 Overview

Every SysML element used in this mapping shares three facts, stated here in plain terms without assuming any other background:

- **A settable name and a computed display name.** `declaredName` is what a modeler actually types in and the only one that's real/settable; `name` is a computed convenience that always mirrors it. Reactions always work with `declaredName`.
- **Indirect containment.** There is no `PartUsage.parts`-style list. A part/port/attribute is reached by walking through an intermediate relationship object (`ownedRelationship`, see §1.3), not a direct parent→child reference.
- **Read-only convenience views.** `nestedPart`, `nestedPort`, `nestedAttribute`, and similar accessors are computed filters over the real containment structure — useful for reading the structure, but you can never insert into them directly; you create the underlying relationship instead.

That last point is the one practical consequence worth internalizing before touching this project's `.reactions` files (see §3.4): a reaction cannot listen on `"after element inserted in PartUsage[nestedPart]"`, because `nestedPart` is a computed view, not a real containment slot. Reactions instead listen on the real, non-derived features — `declaredName` for renames, and the underlying relationship-creation events for structure.

*For readers already familiar with KerML* (the OMG Kernel Modeling Language SysML v2 is built on): every class named in this document is a specialization of KerML's generic `Feature`/`Usage`/`Type`/`Namespace`/`Element` hierarchy, and the containment/derivation behavior above is exactly KerML's `Relationship`-based model. That background isn't required to read this document — the full inheritance chains are collected separately in §1.11 for reference.

For the SysML ↔ Simulink case study, the relevant sub-model is the **technical architecture / interface layer**: `PartUsage`/`PartDefinition` (structural blocks), `PortUsage`/`PortDefinition` (interaction points), `FlowUsage`/`FlowDefinition` (signal/item flow), and, for the requirement–function–architecture traceability cascade described in Grycz et al. (KIT/SFB 1608, "Convide" brake-system research platform), `RequirementUsage`/`RequirementDefinition` and `ActionUsage`/`ActionDefinition`.

### 1.2 Scope: Classes Relevant to Simulink Mapping

| SysML Class | Extends | Role in Mapping |
|---|---|---|
| `PartUsage` | `ItemUsage` | Maps to Simulink `Block` / `SubSystem` (Rule A) |
| `PartDefinition` | `ItemDefinition` | Type of a `PartUsage`; no direct Simulink counterpart |
| `PortUsage` | `OccurrenceUsage` | Maps to Simulink `InPort` / `OutPort`, disambiguated by `direction` (Rule B) |
| `PortDefinition` | `OccurrenceDefinition` | Type of a `PortUsage` |
| `FlowUsage` | `Connector`, `ActionUsage` | Maps to Simulink `SingleConnection` (Rule C) |
| `FlowDefinition` | `ActionDefinition`, `Interaction` | Type of a `FlowUsage` |
| `AttributeUsage` | `Usage` | Maps to Simulink `Parameter` (Rule E, name only) |
| `ActionUsage` | `OccurrenceUsage` | Represents a "function"; created by the traceability cascade (Rule D) |
| `RequirementUsage` | `ConstraintUsage` | Represents a "requirement"; created by the traceability cascade (Rule D) |
| `Package` | `Namespace` | VSUM root anchor on the SysML side |

(Full multi-level KerML inheritance chains are in §1.11, for readers who want them — this table shows only the immediate, SysML-facing parent.)

### 1.3 Element (the root class every SysML object here inherits from)

Two facts about `Element` drive every reaction in this project:

| Name | Type | Multiplicity | Description | Derivation |
|---|---|---|---|---|
| `declaredName` | `EString` | 0..1 | The name a modeler actually types in. Real, settable. | — |
| `name` | `EString` | 0..1 | The "display" name a reaction would naively want to bind a rename to — but it's computed, so Vitruv cannot observe changes to it directly. **Reactions must listen on `declaredName` instead.** | `effectiveName()`, defaults to `declaredName` |
| `elementId` | `EString` | 1 | Tooling-assigned globally unique id. | — |
| `ownedRelationship` | `Relationship` (containment) | 0..* | The only real containment edge out of an `Element` — every "owned member" (a nested part, a port, ...) is reached by walking through one of these (typically an `OwningMembership`/`FeatureMembership`), not directly. | — |
| `owningRelationship` | `Relationship` | 0..1 | The opposite direction — walks *up* from a leaf `Element` to find its logical container, since there's no direct `eContainer`-style "owning PartUsage" reference. | — |
| `qualifiedName` | `EString` | 0..1 | Fully-qualified dotted name. | `owningNamespace.qualifiedName + '::' + escapedName()` |

### 1.4 Structural Building Blocks: Containment, Typing, and Direction

Three facts, stated plainly, explain how every part/port/attribute in this mapping is built and read:

| Fact | What it means here |
|---|---|
| Containment goes through a relationship object. | A `PartUsage`'s children aren't in a `children` list — they're reached via `ownedRelationship` objects (§1.3) whose `ownedRelatedElement` holds the actual member. |
| Every usage element can be typed. | A `PartUsage` can be typed by a `PartDefinition`, a `PortUsage` by a `PortDefinition`, and so on — the "definition" side has no Simulink counterpart, since Simulink doesn't separate definition from usage. |
| `direction` is the one attribute Rule B depends on. | Every port-like usage carries a real, settable `direction` (`in` / `out` / `inout`) — this is what Rule B reads to decide `InPort` vs. `OutPort`. |
| Convenience views are read-only. | `nestedPart`, `nestedPort`, `nestedAttribute`, etc. are computed filters over the real containment structure — useful for reading, but never writable directly; you create the underlying relationship instead (see §1.5's `nestedPart` row). |

### 1.5 PartUsage / PartDefinition

A `PartUsage` represents "a system or a part of a system" — the SysML equivalent of a Simulink `Block`. A `PartUsage` may itself own nested `PartUsage`s, directly analogous to a Simulink `SubSystem` owning `subBlocks`.

| Name | Type | Multiplicity | Description | Derivation |
|---|---|---|---|---|
| `declaredName` (inherited) | `EString` | 0..1 | Propagated to the corresponding Simulink element's name. | — |
| `partDefinition` | `PartDefinition` | 0..* | The `PartDefinition`(s) typing this usage. | Yes |
| `nestedPart` | `PartUsage` | 0..* | If non-empty, the corresponding Simulink element must be a `SubSystem`, not a plain `Block` (Rule A / S1). | `nestedUsage->selectByKind(PartUsage)` |
| `isComposite` (from `Usage`) | `EBoolean` | 0..1 | Whether this part is owned (contained) vs. referenced. | — |

`PartDefinition` is the type a `PartUsage` is typed by; it has no Simulink counterpart — Simulink does not separate "definition" from "usage" the way SysML does (a `Block` combines both roles).

### 1.6 PortUsage / PortDefinition

A `PortUsage` is "a point at which external entities can connect to and interact with a system." Unlike `PartUsage`, `direction` is what disambiguates the Simulink target:

| Name | Type | Multiplicity | Description | Derivation |
|---|---|---|---|---|
| `declaredName` (inherited) | `EString` | 0..1 | Propagated to the corresponding Simulink `Port`'s name. | — |
| `direction` (inherited) | `FeatureDirectionKind` (`in`/`out`/`inout`) | 0..1 | `in` → Simulink `InPort`; `out` → Simulink `OutPort`; `inout` has no deterministic target — Simulink ports are strictly one-directional by class, so this is resolved interactively at runtime (see §3.4, Rule B note). | — |
| `portDefinition` | `PortDefinition` | 0..* | The `PortDefinition`(s) typing this usage. | Yes |

### 1.7 FlowUsage / FlowDefinition

A `FlowUsage` represents an item/signal transfer between two `flowEnd`s, connecting at most two features — which maps naturally onto Simulink's `SingleConnection` (exactly one `OutPort` → one `InPort`).

| Name | Type | Multiplicity | Description | Derivation |
|---|---|---|---|---|
| `flowEnd` | `Usage` | 0..2 | The two connected features — nominally two `PortUsage`s, one `out` and one `in`. | Yes |
| `flowDefinition` | `Interaction` | 0..* | The `FlowDefinition` typing this usage. | Yes |

Simulink's `MultiConnection` (one `OutPort` fanning out to many `InPort`s via nested `SingleConnection`s) has no single-`FlowUsage` equivalent — instead, each branch `SingleConnection` gets its own `FlowUsage` sharing the same source (see §3.4, E17).

### 1.8 AttributeUsage / AttributeDefinition

| Name | Type | Multiplicity | Description | Derivation |
|---|---|---|---|---|
| `declaredName` (inherited) | `EString` | 0..1 | Propagated to the corresponding Simulink `Parameter.name` (Rule E). | — |
| `attributeDefinition` | `DataType` | 0..* | The `AttributeDefinition`/`DataType` typing this usage; not propagated. | Yes |

Note: an `AttributeUsage`'s *value* is expressed via a nested `FeatureValue`/`LiteralExpression` subtree several relationship-hops away, not a direct attribute. Value propagation is out of scope for this project — Rule E only propagates existence and name, same scope as every other rule here (P1–P6 only ever sync a name, never a literal value).

### 1.9 ActionUsage / RequirementUsage (traceability cascade classes)

These two classes are not mapped 1:1 to any Simulink class. They exist purely on the SysML side and are auto-generated, together with a `PartUsage`, whenever a Simulink `Block`/`SubSystem` is discovered without a corresponding SysML architecture element — mirroring the exact scenario described in Grycz et al.: *"when a new technical architecture block is introduced [from the Simulink side], a corresponding requirement, function ... are created"* (paraphrased; see §3.4 Rule D).

| Class | Extends | Used In |
|---|---|---|
| `ActionUsage` | `OccurrenceUsage` | Rule D (as a stand-in for "function") |
| `RequirementUsage` | `ConstraintUsage` | Rule D (as a stand-in for "requirement") |

The formal `Satisfy`/`Perform`/`Allocate` relationships that would properly wire Requirement → Function → Architecture together (`SatisfyRequirementUsage`, `PerformActionUsage`, `AllocationUsage`) are **not** created by the cascade in this project — see the omission note under Rule D in §3.2.

### 1.10 Package

`Package` (a kind of `Namespace`) is the SysML-side VSUM root anchor — the single top-level object every other SysML element is reachable from. Top-level `PartUsage`s owned (via the generic containment mechanism, §1.3) by a `Package` correspond to root-level `Block`s. Note this is *not* a `Package`↔`SimulinkModel` correspondence — see §2.9's note on why that pairing is deliberately not implemented.

### 1.11 Full KerML Inheritance Chains (Reference)

For readers who want the complete supertype lineage behind §1.2's simplified "Extends" column:

| Class | Full Supertype Chain | Used in Rule(s) |
|---|---|---|
| `PartUsage` | `ItemUsage → OccurrenceUsage → Usage → Feature → Type → Namespace → Element` | Rule A |
| `PartDefinition` | `ItemDefinition → OccurrenceDefinition → Definition, Class` | Rule A (type-side, no direct mapping) |
| `PortUsage` | `OccurrenceUsage → Usage → Feature → ...` | Rule B |
| `FlowUsage` | `ConnectorAsUsage, ActionUsage, Flow` | Rule C, plus Rules F/H (reused as the target for BusSignalMapping and Goto/From) |
| `AttributeUsage` | `Usage → Feature → ...` | Rule E |
| `ActionUsage` | `OccurrenceUsage, Step` | Rule D |
| `RequirementUsage` | `ConstraintUsage, BooleanExpression` | Rule D |
| `Package` | `Namespace → Element` | VSUM root anchor |

---

## 2. Simulink Metamodel Description

*MASSIF Simulink Ecore*

URI: `http://hu.bme.mit.massif/simulink/1.0` | Source: [viatra/massif](https://github.com/viatra/massif)

### 2.1 Overview

This Ecore metamodel represents the core structural concepts of MATLAB Simulink models: block diagrams with hierarchical decomposition, typed ports, signal connections, and block parameters. Per its own root-level documentation, it has "a strong focus on the structure and less focus on the behavior, simulation and layout specific details."

Unlike SysML, Simulink's containment is almost entirely **direct, non-derived EReferences** — `Block.ports`, `Block.parameters`, `SubSystem.subBlocks` are all plain, settable, `containment=true` features. This makes the Simulink side of the consistency rules structurally much simpler than the SysML side (see §1.1). The one exception, described below, is `SimulinkElement.name` itself.

### 2.2 Scope: Classes Relevant to SysML Mapping

| Simulink Class | Location | Role in Mapping |
|---|---|---|
| `Block` | `SimulinkModel.contains` / `SubSystem.subBlocks` | Maps to SysML `PartUsage` (Rule A) |
| `SubSystem` | extends `Block` | Maps to SysML `PartUsage` with `nestedPart` (Rule A) |
| `InPort` / `OutPort` | `Block.ports` | Map to SysML `PortUsage` with `direction = in` / `out` (Rule B) |
| `Trigger` / `Enable` | `InPort` subtypes | Map to SysML `PortUsage(direction=in)`, same as any `InPort` (Rule B) |
| `State` | `OutPort` subtype | Maps to SysML `PortUsage(direction=out)`, same as any `OutPort` (Rule B) |
| `SingleConnection` | `OutPort.connection` (containment) | Maps to SysML `FlowUsage` (Rule C) |
| `MultiConnection` | `OutPort.connection` (containment) | Each branch `SingleConnection` maps to its own `FlowUsage` (Rule C, E17) |
| `Parameter` | `Block.parameters` / `Port.parameters` | Maps to SysML `AttributeUsage` (Rule E, name only) |
| `BusSelector` / `BusCreator` | `Block` subtypes | Generic `PartUsage`, same as any `Block` (Rule G) |
| `BusSignalMapping` | `BusSelector.mappings` (containment) | Maps to SysML `FlowUsage` between its `mappingFrom`/`mappingTo` ports (Rule F) |
| `Goto` / `From` | `Block` subtypes | Generic `PartUsage` (Rule G) **plus** a `FlowUsage` between the linked pair's ports (Rule H) |
| `GotoTagVisibility` | `Block` subtype | Generic `PartUsage`, same as any `Block` (Rule G) |
| `ModelReference` | `Block` subtype | Generic `PartUsage`, same as any `Block` (Rule G) |
| `OutPortBlock` / `InPortBlock` / `TriggerBlock` / `EnableBlock` | `Block` subtypes, wrap a `Port` | Share the wrapped `Port`'s `PortUsage` — no new SysML object (Rule I) |
| `SimulinkModel` | root | No correspondence — see §2.9 |

`LibraryLinkReference`/`IdentifierReference`/`SimulinkReference` are omitted from this table deliberately: they're the identity/name-carriers embedded in whichever `SimulinkElement` already has its own correspondence above, not separate domain objects — see the note in `SimulinkToSysML.reactions`.

### 2.3 SimulinkElement (abstract root)

| Name | Type | Multiplicity | Description | Derivation |
|---|---|---|---|---|
| `simulinkRef` | `IdentifierReference` (containment) | 0..1 | Holds the element's real, settable name/qualifier. | — |
| `name` | `EString` | 0..1 | Cannot be set or listened to directly. **Reactions must operate on `simulinkRef.name`.** This mirrors the SysML side's `name`/`declaredName` split (§1.3) — both metamodels compute their "friendly" name from an underlying reference object, for different reasons (VIATRA query-based features here; KerML `effectiveName()` there). | VIATRA query-based feature (`patternFQN = hu.bme.mit.massif.models.simulink.derived.name`) from `simulinkRef.name`; `changeable=false`, `volatile=true` |

### 2.4 Block

Location: `SimulinkModel.contains[]` or `SubSystem.subBlocks[]`. "The basic building block of Simulink systems." In the SysML ↔ Simulink mapping, a `Block` (without children) or `SubSystem` (with children) is the top-level structural unit that maps to a SysML `PartUsage`.

| Name | Type | Multiplicity | Description | Derivation |
|---|---|---|---|---|
| `simulinkRef.name` (via `SimulinkElement`) | `EString` | 0..1 | Propagated to `PartUsage.declaredName`. | — |
| `parameters` | `Parameter` (containment) | 0..* | Block-level configuration values. Maps to `AttributeUsage` (Rule E). | — |
| `ports` | `Port` (containment) | 0..* | Interface ports of the block. Maps to `PortUsage` (Rule B). | — |
| `parent` | `SubSystem` (`eOpposite` of `subBlocks`) | 0..1 | Real, non-derived opposite reference — used to find the logical container without indirection. | — |
| `sourceBlockRef` / `sourceBlock` | `LibraryLinkReference` / `Block` | 0..1 | Link back to a library block template; out of scope for this mapping. | Yes (`sourceBlock`) |
| `trigger`, `enabler`, `inports`, `outports` | filtered views of `ports` | 0..* | Convenience read-only accessors, analogous to SysML's `nestedPart`/`nestedPort`. | VIATRA query-based feature |

### 2.5 SubSystem (extends Block)

"A Simulink block that may contain subblocks that specify its internal structure and behavior."

| Name | Type | Multiplicity | Description | Derivation |
|---|---|---|---|---|
| `subBlocks` | `Block` (containment, `eOpposite = Block.parent`) | 0..* | Real, non-derived containment. Maps to `PartUsage.nestedPart` (derived on the SysML side — see §3.4 Rule A / S1 for the resulting asymmetry). | — |
| `tag` | `EString` | 0..1 | Free-text subsystem tag; no SysML equivalent. | — |

### 2.6 Port (abstract) / InPort / OutPort

| Class | Extends | Key Features | Description |
|---|---|---|---|
| `Port` (abstract) | `SimulinkElement` | `container` (`eOpposite` of `Block.ports`), `portBlock`, `parameters` | Base type. |
| `InPort` | `Port` | `connection` (`eOpposite` of `SingleConnection.to`) | Maps to SysML `PortUsage` with `direction = in` (Rule B). |
| `OutPort` | `Port` | `connection` (containment, `eOpposite` of `Connection.from`) | Maps to SysML `PortUsage` with `direction = out` (Rule B). |
| `Trigger` | `InPort` | `triggerType`, `statesWhenEnabling` | Specialized control-flow port; maps to `PortUsage(direction=in)`, same as any other `InPort` (Rule B) — the control/simulation semantics themselves (`triggerType` etc.) aren't propagated, only existence and name. |
| `Enable` | `InPort` | `statesWhenEnabling` | Same treatment as `Trigger`. |
| `State` | `OutPort` | — | Stateport (e.g. for `Integrator` blocks); maps to `PortUsage(direction=out)`, same as any other `OutPort` (Rule B). |

### 2.7 Connection (abstract) / SingleConnection / MultiConnection

| Class | Extends | Key Features | Description |
|---|---|---|---|
| `Connection` (abstract) | `SimulinkElement` | `from` (`OutPort`), `lineName` | Base type for signal connections. |
| `SingleConnection` | `Connection` | `to` (`InPort`, `eOpposite` of `InPort.connection`), `parent` (`MultiConnection`) | "A simple connection between a single `OutPort` and a single `InPort`." Maps to SysML `FlowUsage` (Rule C). |
| `MultiConnection` | `Connection` | `connections` (`SingleConnection[]`, containment) | "A connection between a single `OutPort` and multiple `InPort`s." No single-`FlowUsage` equivalent, but each contained `SingleConnection` branch gets its own `FlowUsage` sharing the same source (Rule C, E17) — see §1.7. |

### 2.8 Parameter

Location: `Block.parameters[]` / `Port.parameters[]`. Not a map — a block may have multiple parameters with the same name.

| Name | Type | Multiplicity | Description | Derivation |
|---|---|---|---|---|
| `name` | `EString` | 0..1 | Plain (non-derived) attribute — unlike `SimulinkElement.name`, this one is directly settable. Maps to `AttributeUsage.declaredName` (Rule E). | — |
| `type` | `EString` | 0..1 | Not propagated. | — |
| `value` | `EString` | 0..1 | Free-text value. Not propagated (no reliable SysML-side counterpart — see §1.8). | — |
| `readOnly` | `EBoolean` | 0..1 | No SysML equivalent. | — |

### 2.9 SimulinkModel (root)

"The root of an imported Simulink system that contains blocks." Used as the root in `VSUMExample.java` (wrapping its `Block`s in `contains`); the test harness (`VSUMRunner`) skips this wrapper for convenience and registers each `Block` as its own resource root directly.

| Name | Type | Multiplicity | Description | Derivation |
|---|---|---|---|---|
| `contains` | `Block` (containment) | 0..* | Root-level blocks. Each contained `Block` gets its own `PartUsage` correspondence via Rule A, same as if it were registered as an independent root. | — |
| `version`, `file`, `library` | `EString`/`EString`/`EBoolean` | 0..1 each | Import metadata; no SysML equivalent. | — |

**`SimulinkModel` itself has no correspondence to the SysML `Package` root — deliberately, not by oversight.** Unlike every other rule in this project, there's no derivable relationship between the two: a `SimulinkModel` and a `Package` are independently-created top-level roots, neither built in response to the other, so there's no source feature a reaction could navigate from one to reach the other. More fundamentally, no reaction would ever need to query such a correspondence, since every actual propagation in this project happens between the *contained* elements (`Block`↔`PartUsage` etc.), which already have their own correspondences — a root-to-root link here would be inert bookkeeping nobody reads.

### 2.10 Inheritance Summary (Classes Relevant to Mapping)

| Class | Extends | Used in Rule(s) |
|---|---|---|
| `Block` | `SimulinkElement` | Rule A |
| `SubSystem` | `Block` | Rule A |
| `InPort` | `Port → SimulinkElement` | Rule B |
| `OutPort` | `Port → SimulinkElement` | Rule B |
| `Trigger`, `Enable`, `State` | `InPort`/`OutPort` | Rule B |
| `SingleConnection` | `Connection → SimulinkElement` | Rule C |
| `MultiConnection` | `Connection → SimulinkElement` | Rule C (via its branches, E17) |
| `Parameter` | (none, plain `EObject`) | Rule E |
| `BusSelector`, `BusCreator` | `BusSpecification → Block` | Rule G |
| `BusSignalMapping` | (none, plain `EObject`) | Rule F |
| `Goto`, `From`, `GotoTagVisibility` | `VirtualBlock → Block` | Rule G (all three), Rule H (Goto/From only) |
| `ModelReference` | `Block` | Rule G |
| `OutPortBlock`, `InPortBlock`, `TriggerBlock`, `EnableBlock` | `PortBlock → VirtualBlock → Block` | Rule I |
| `SimulinkModel` | `SimulinkElement` | none — see §2.9 |
| `LibraryLinkReference`, `IdentifierReference` | `SimulinkReference` | none — carried by the parent's correspondence, see §2.2 |

---

## 3. Semantic Overlaps and Consistency Preservation Rules

There is no published correspondence table for this metamodel pair. The rules below are instead derived directly from the two `.ecore` files, cross-checked class by class, and grounded in the real engineering scenario described in Grycz, Hagel, Eger, Reussner, Düser — *"Model-Based Activities for Supporting the Synthesis of Validation Environments Using the V-SUM Approach"* (KIT/SFB 1608 "Convide", submitted TdSEE Dortmund 2025/2026): a Brake-System-in-the-Loop validation environment in which a brake-disc-temperature sensor was implemented in the physical rig and represented in Simulink, but never propagated back into the descriptive SysML system model maintained in Cameo Systems Modeler.

Three things to read this section with in mind:

- Rules A–C and E are bidirectional. Rules D and F–I are **one-directional (Simulink → SysML only)** — in each case the SysML side has no concept (requirement/function cascade trigger, bus signal mapping, these specific Block subtypes, tag-based routing, port-boundary diagram blocks) to originate the reverse direction from. Rule D mirrors the paper's own demonstrated scenario; Rules F–I are this project's own extension beyond the paper, applying the same "every class needs a correspondence" standard to the rest of the Simulink metamodel.
- Several sub-rules are **intentionally omitted** where the source `.ecore` files don't support a faithful mapping without deeper, out-of-scope modeling. Each omission is documented with its reason rather than left as a silent gap.
- The one genuinely ambiguous decision point in the whole rule set — `PortUsage(direction=inout)`, previously marked *(needs domain-owner confirmation)* — is now resolved interactively at runtime rather than left as an open question; see the Rule B note.

### 3.1 Semantic Overlap Summary

| Rule | SysML | Simulink / Condition |
|---|---|---|
| **A** | `PartUsage` (no children) | `Block` — no filter |
| **A** | `PartUsage` (`nestedPart->notEmpty()`) | `SubSystem` — has `subBlocks` |
| **B** | `PortUsage` (`direction = in`) | `InPort` |
| **B** | `PortUsage` (`direction = out`) | `OutPort` |
| **B** | `PortUsage` (`direction = inout`) | resolved interactively at runtime — see Rule B note |
| **C** | `FlowUsage` (`flowEnd->size() = 2`) | `SingleConnection` |
| **D** | `PartUsage` + sibling `ActionUsage` + sibling `RequirementUsage` | `Block`/`SubSystem` created with no corresponding architecture element (Simulink → SysML only) |
| **E** | `AttributeUsage` | `Parameter` — on a `Block` or a `Port` |
| **F** | `FlowUsage` | `BusSignalMapping` (Simulink → SysML only) |
| **G** | `PartUsage` (generic) | `BusSelector`, `BusCreator`, `Goto`, `From`, `GotoTagVisibility`, `ModelReference` (Simulink → SysML only) |
| **H** | `FlowUsage` | `From` with a resolved `gotoBlock` link (Simulink → SysML only) |
| **I** | shares the wrapped `Port`'s `PortUsage` (no new object) | `OutPortBlock`, `InPortBlock`, `TriggerBlock`, `EnableBlock` (Simulink → SysML only) |

### 3.2 Correspondence Rules in Detail

#### Rule A — PartUsage ↔ Block/SubSystem

- SysML: `PartUsage`
- Simulink: `Block` (leaf) or `SubSystem` (has children)
- Guard: `PartUsage.nestedPart->notEmpty()` ⇔ corresponding element must be `SubSystem`, not plain `Block`

| SysML | Simulink |
|---|---|
| `PartUsage.declaredName` | `Block.simulinkRef.name` |
| `PartUsage.nestedPart[]` | `SubSystem.subBlocks[]` (only when non-empty) |

Sub-constraint — type migration on child-count crossing zero:

```
Context sysml::PartUsage
  Inv: nestedPart->notEmpty() implies
       corresponding(simulink::SubSystem) and not corresponding(simulink::Block)
  Inv: nestedPart->isEmpty() implies
       corresponding(simulink::Block) and not corresponding(simulink::SubSystem)
```

A `PartUsage` gaining its first nested part — or losing its last one — forces a `Block`↔`SubSystem` swap on the Simulink side, since `SubSystem` is a distinct EClass, not a flag on `Block`.

#### Rule B — PortUsage ↔ InPort / OutPort

- SysML: `PortUsage`, disambiguated by the inherited `direction` attribute
- Simulink: `InPort` / `OutPort`, both subtypes of the abstract `Port`

```
Context sysml::PortUsage
  Inv is_input:  self.direction = FeatureDirectionKind::_'in'
  Inv is_output: self.direction = FeatureDirectionKind::out
```

| SysML | Simulink |
|---|---|
| `PortUsage.declaredName` | `InPort.simulinkRef.name` / `OutPort.simulinkRef.name` |
| `PortUsage` nested under `PartUsage` | `Port` in `Block.ports[]` |

**Note** — `direction = inout` has no deterministic Simulink target: `InPort`/`OutPort` are disjoint EClasses, so there's no bidirectional port EClass to map to, and no rule of the kind used elsewhere in this project (a structural guard, a resolved reference) can settle it automatically. Rather than leave it silently unmapped, this is the one case in the whole rule set resolved **interactively**: when a `PortUsage(direction=inout)` is created, the user is prompted to choose — map as `InPort`, map as `OutPort`, create a paired `InPort` + `OutPort`, or skip (leave unmapped, same as the old default). This uses Vitruv's own `UserInteractor` API, already wired into the project via `CliInteractionResultProviderImpl` (real prompts, used by `VSUMExample`) and `TestUserInteraction` (scripted answers, used by the automated test suite — `mvn clean verify` never blocks on real input). See `resolveInoutPortUsage` in `SysMLToSimulink.reactions`.

**Note** — Simulink's `Trigger`, `Enable`, and `State` (all subtypes of `InPort`/`OutPort`) carry control-flow/simulation semantics that a plain SysML `PortUsage` + `direction` cannot express. Rather than leave them unpropagated, they get the same `PortUsage` treatment as any other `InPort`/`OutPort` (E8/E9 already match on the abstract `Port` supertype) — only the specialized semantics themselves (`triggerType`, `statesWhenEnabling`) are not propagated, matching this project's existence/name-only scope everywhere else.

**Note** — the SysML→Simulink name-sync direction (P3) stays split into `syncInPortName`/`syncOutPortName` rather than one routine matching generically on `simulink::Port` (unlike E10/P4, which do match generically) — the paired `inout` choice above can give a single `PortUsage` two simultaneous correspondences (an `InPort` and an `OutPort`), and a single generic `retrieve` throws `IllegalStateException` the moment more than one correspondence of the requested type exists for the same source. Confirmed empirically with a dedicated test; see `syncPortUsageName` in `SysMLToSimulink.reactions`.

#### Rule C — FlowUsage ↔ SingleConnection

- SysML: `FlowUsage`, `flowEnd->size() = 2`, ends typed by an `out`- and an `in`-directioned `PortUsage`
- Simulink: `SingleConnection`, `from` (`OutPort`) → `to` (`InPort`)
- No OCL filter beyond the two-ended constraint (already enforced by `FlowUsage`'s own invariant `flowEnd->size() <= 2`)

| SysML | Simulink |
|---|---|
| `FlowUsage.flowEnd[1]` (end typed by the `out` `PortUsage`) | `SingleConnection.from` (via the corresponding `OutPort`) |
| `FlowUsage.flowEnd[2]` (end typed by the `in` `PortUsage`) | `SingleConnection.to` (via the corresponding `InPort`) |

**Note** — Simulink's `MultiConnection` (one `OutPort` fanning out to several `InPort`s via nested `SingleConnection`s) has no single-`FlowUsage` equivalent, since `FlowUsage.flowEnd->size() <= 2`. Option (a) from the original plan — several `FlowUsage`s sharing the same source — is what's implemented (E17): each contained `SingleConnection` branch gets its own `FlowUsage`, resolving its source via the parent `MultiConnection.from` (a branch's own `from` is never populated — only the top `MultiConnection`'s is, via the `OutPort.connection` eOpposite).

#### Rule D — Traceability cascade: Block/SubSystem → PartUsage + ActionUsage + RequirementUsage

This rule is not a metaclass-to-metaclass correspondence like A–C; it reproduces the specific scenario from Grycz et al. §3.3–4.2: when a Simulink `Block`/`SubSystem` exists with **no** corresponding SysML architecture element, propagation does not stop at creating a `PartUsage` (Rule A, E3) — it also creates a sibling `ActionUsage` ("function") and a sibling `RequirementUsage` ("requirement"), all three named consistently after the originating block, in the same owning `Package`. This is a direct implementation of the paper's own worked example: a new Simulink block named `TemperatureMonitor` results in *"a newly created requirement capturing the brake disc temperature monitoring functionality, a corresponding function for processing the temperature information, a technical architecture element ... consistent naming across all generated elements"* (§4.2, verbatim).

- Direction: **Simulink → SysML only.** Simulink has no requirement/function concept to originate the reverse cascade from; a SysML-side `PartUsage` created directly (Rule A, E1) does *not* trigger this cascade — only propagation originating from an *unmatched* Simulink block does.

| Simulink (trigger) | SysML (generated) |
|---|---|
| `Block.simulinkRef.name` = `"X"` | `PartUsage.declaredName` = `"X"` (Rule A, unchanged) |
| — | `ActionUsage.declaredName` = `"process" + X` |
| — | `RequirementUsage.declaredName` = `X + "Requirement"` |

**Omission, honestly flagged:** the cascade creates three *sibling* elements with consistent names, but does **not** create the formal `SatisfyRequirementUsage` / `PerformActionUsage` / `AllocationUsage` relationship objects that would properly wire Requirement → Function → Architecture together in SysML's traceability model. Grycz et al. flag exactly this same gap themselves (§5.2, discussing Figure 6): *"a further refining and detailing could be accomplished ... additional relationships such as «satisfy», «allocate», and trace links between requirements, functions, and technical architecture elements can be established"* — i.e. the paper's own case study also stops at generating the three elements and leaves the relationship wiring as manual/future work. This project does the same, deliberately, rather than inventing an under-specified auto-wiring policy.

**Scope guard:** the cascade fires only for real architecture blocks — a plain `Block`/`SubSystem` with no corresponding `PartUsage` yet. Rule G's block family (`BusSelector`/`BusCreator`/`Goto`/`From`/`GotoTagVisibility`/`ModelReference`) shares the same underlying creation routine (they're `Block` subtypes too, matched by the same unguarded `E3`), but they're diagram plumbing, not architecture elements a requirement or function would trace to — so the cascade is explicitly excluded for anything that's a `BusSpecification`, `VirtualBlock`, or `ModelReference`. `C6` (§3.4) checks this invariant across the whole block family at once.

E4's deletion routine looks up `PartUsage`, `ActionUsage`, and `RequirementUsage` as three independently-gated routines rather than one `match` block chaining all three `retrieve`s. `retrieve` is a silent match-gate, not a nullable lookup, so a single chained match would only fire when all three correspondences exist — which is never true for Rule G blocks, since the scope guard above means they never get Rule D siblings. Same pattern P2's rename routine uses (`syncBlockNameToPartUsage`/`syncCascadeFunctionName`/`syncCascadeRequirementName`).

#### Rule E — AttributeUsage ↔ Parameter

- SysML: `AttributeUsage`, nested under a `PartUsage` or a `PortUsage`
- Simulink: `Parameter`, in `Block.parameters[]` or `Port.parameters[]`
- Bidirectional, same scope as Rules A–C: only existence and `declaredName`/`name` are synced. `Parameter.value`/`type`/`readOnly` and `AttributeUsage.attributeDefinition` are not propagated — see §1.8/§2.8.

| SysML | Simulink |
|---|---|
| `AttributeUsage.declaredName` | `Parameter.name` |
| `AttributeUsage` nested under `PartUsage`/`PortUsage` | `Parameter` in `Block.parameters[]`/`Port.parameters[]` |

#### Rule F — BusSignalMapping → FlowUsage (Simulink → SysML only)

`BusSignalMapping` records that a `BusSelector`'s `mappingFrom` `OutPort` supplies the signal that appears on its `mappingTo` `OutPort`. That's the same "signal appears on this port" semantic Rule C already models with `FlowUsage`, so it reuses the same target class: a `FlowUsage` is created between the two ports' `PortUsage`s. There's no SysML-side `BusSignalMapping` concept to originate a reverse direction from, so this rule is one-directional, like Rule D.

`BusSignalMapping` isn't a `SimulinkElement` (it has no `simulinkRef`), so the generated `FlowUsage` is given a synthetic `declaredName` (`<mappingFrom>_to_<mappingTo>`) purely so it's locatable by name — every other auto-created `FlowUsage` in this project is left unnamed.

#### Rule G — Generic Block-subtype → PartUsage (Simulink → SysML only)

`BusSelector`, `BusCreator`, `Goto`, `From`, `GotoTagVisibility`, and `ModelReference` are all `Block` subtypes that don't fit Rule A's `Block`/`SubSystem` distinction, but are still real blocks an engineer would see in the diagram. Each gets a generic `PartUsage` via the same mechanism as Rule A (E3), just with its own explicit reaction guard rather than relying on `BlockCreated`'s unguarded type match. This is deliberately guarded, not accidental — see the `PortBlock` exclusion note under Rule I.

The class-specific semantics beyond existence and name are not modeled: `BusSelector`/`BusCreator`'s internal bus routing is Rule F's job (at the signal-mapping level, not the block level); `Goto`/`From`'s tag-based link is Rule H's job; `GotoTagVisibility`'s scoping and `ModelReference`'s external-model link have no further propagation.

**`ModelReference.referencedModel` is deliberately unwired, not by oversight.** It's `changeable="false"` and computed via a VIATRA query-based feature (`patternFQN = hu.bme.mit.massif.models.simulink.derived.referencedModel`), the same derived-feature pattern already excluded elsewhere in this project (see `SimulinkElement.name` in §2.3). A reaction can't listen on or set a derived reference, and even if it could, the target — another whole `SimulinkModel` root — has no correspondence of its own to navigate to (see the `SimulinkModel`↔`Package` note above), so there's no clean SysML-side object to point at anyway. `ModelReference` still gets its own generic `PartUsage` like every other Rule G block; only the nested `referencedModel` link is out of scope.

#### Rule H — Goto/From tag-based virtual wire → FlowUsage (Simulink → SysML only)

Simulink's `Goto`/`From` blocks pass a signal without an explicit wire — `From.gotoBlock` (eOpposite `Goto.fromBlocks`) is the resolved link, presumably established by whatever produced the model via `gotoTag`/`TagVisibility` matching. This rule trusts that link as-is rather than independently re-deriving it from tag strings, and models it as a `FlowUsage` even though no `Connection` object backs it:

| SysML | Simulink |
|---|---|
| `FlowUsage.source` | the linked `Goto`'s own `InPort`'s `PortUsage` (the relay source) |
| `FlowUsage.target` | the `From`'s own `OutPort`'s `PortUsage` |

Flow direction follows the real signal path (Goto receives, From re-emits) — source is an `in`-directioned `PortUsage` and target is `out`-directioned, the reverse type pattern from Rule C. That's correct for this case, not a mistake. Fan-out (several `From` blocks sharing one `Goto`) falls out naturally, since each `From` triggers its own independent match.

Deleting the `Goto` side is covered too: `gotoBlock` going `null` is itself an unreactable `EReference` change (the DSL only exposes `EAttribute`-level replace events, see §1.3/§2.3), so this rule doesn't react to that attribute directly — instead it reacts to the structural consequence, `From removed from Goto[fromBlocks]`, which fires once per linked `From` as the eOpposite is severed and still carries a live reference to the `From` being detached, in time to remove its `FlowUsage` before the correspondence goes stale.

#### Rule I — PortBlock family → shares its wrapped Port's PortUsage, not a new PartUsage (Simulink → SysML only)

`OutPortBlock`, `InPortBlock`, `TriggerBlock`, `EnableBlock` (all `PortBlock` subtypes) are diagram-only stand-ins representing where a `SubSystem`'s boundary port appears inside that subsystem's own internal view — the same SysML-visible interface point as the `Port` it wraps (`PortBlock.port`), not a second one. Giving each its own `PartUsage` (as Rule G would, if left unguarded) would create a spurious duplicate, so `BlockCreated`/`BlockDeleted`/`BlockRenamed` explicitly exclude the `PortBlock` family, and a dedicated rule instead registers a *second correspondence* pointing the `PortBlock` at the **same** `PortUsage` its wrapped `Port` already corresponds to:

| SysML | Simulink |
|---|---|
| (no new object) | `PortBlock.port`'s existing `PortUsage` correspondence, shared |

Deleting a `PortBlock` removes only its own (shared) correspondence entry — the `PortUsage` itself is not deleted, since it's still owned by the real `Port`'s own lifecycle (Rule B / E10).

### 3.3 OCL Invariant Summary

```
-- Rule A: PartUsage ↔ Block/SubSystem
Context sysml::PartUsage
  Inv subsystem_swap: nestedPart->notEmpty() implies corresponding(simulink::SubSystem)

-- Rule B: PortUsage ↔ InPort/OutPort
Context sysml::PortUsage
  Inv is_input:  self.direction = FeatureDirectionKind::_'in'
  Inv is_output: self.direction = FeatureDirectionKind::out
  -- direction = inout has no fixed invariant here — the outcome (InPort, OutPort, both, or neither)
  -- depends on a runtime user choice (resolveInoutPortUsage), not a static structural rule.

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

-- Rule E: AttributeUsage <-> Parameter name consistency
Context sysml::AttributeUsage
  Inv name_sync: corresponding(simulink::Parameter) implies
      corresponding(simulink::Parameter).name = self.declaredName
```

Rules F–I are intentionally left out of this OCL summary — they're guard/structural rules (generic block treatment, a resolved reference link, a shared correspondence) rather than metaclass-level invariants like A–E, so a formal OCL statement wouldn't add clarity beyond their §3.2 descriptions.

These invariants are illustrative pseudo-OCL, kept intentionally close to the actual `.reactions` guard conditions — §3.5 below has real, syntactically-checkable constraint files for the same rules, in `MultiModel-OCL`'s concrete syntax.

### 3.4 Consistency Preservation Rules

Rules are grouped into four categories: **Existence (E)**, **Property (P)**, **Structural (S)**, and **Completeness (C)**. Every row's **Rule** column links back to the lettered rule (A–I, §3.2) it implements.

#### Existence Rules — element creation and deletion

| ID | Rule | Trigger | Action |
|---|---|---|---|
| E1 | A | SysML `PartUsage` created (no children) | create Simulink `Block` with same name; add correspondence |
| E2 | A | SysML `PartUsage` deleted | delete corresponding `Block`/`SubSystem`; remove correspondence |
| E3 | A, D | Simulink `Block`/`SubSystem` created | create SysML `PartUsage` with same name **and** trigger Rule D's cascade (`ActionUsage` + `RequirementUsage`); add correspondence |
| E4 | A, D | Simulink `Block`/`SubSystem` deleted | delete corresponding `PartUsage` (and its Rule D siblings, if present) |
| E5 | B | SysML `PortUsage` (`direction = in`) created | create Simulink `InPort` in parent `Block.ports` |
| E6 | B | SysML `PortUsage` (`direction = out`) created | create Simulink `OutPort` in parent `Block.ports` |
| E7 | B | SysML `PortUsage` deleted | delete corresponding `InPort`/`OutPort` |
| E8 | B | Simulink `InPort` created | create SysML `PortUsage` (`direction = in`) |
| E9 | B | Simulink `OutPort` created | create SysML `PortUsage` (`direction = out`) |
| E10 | B | Simulink `InPort`/`OutPort` deleted | delete corresponding `PortUsage` |
| E11 | C | SysML `FlowUsage` (2-ended, out→in) created | create Simulink `SingleConnection` linking the corresponding `OutPort`/`InPort` |
| E12 | C | SysML `FlowUsage` deleted | delete corresponding `SingleConnection` |
| E13 | C | Simulink `SingleConnection` created | create SysML `FlowUsage` between the corresponding `PortUsage`s |
| E14 | C | Simulink `SingleConnection` deleted | delete corresponding `FlowUsage` |
| E15 | E | SysML `AttributeUsage` created (on a `PartUsage` or `PortUsage`) / Simulink `Parameter` created (on a `Block` or `Port`) | create the corresponding `Parameter` / `AttributeUsage`; add correspondence (Rule E, bidirectional) |
| E16 | E | SysML `AttributeUsage` deleted / Simulink `Parameter` deleted | delete the corresponding `Parameter` / `AttributeUsage` |
| E17 | C | Simulink `SingleConnection` created as a `MultiConnection` branch | create a `FlowUsage`, resolving the source via the parent `MultiConnection.from` (Rule C) |
| E18 | H | Simulink `From` created with a resolved `gotoBlock` link | create a `FlowUsage` from the linked `Goto`'s `InPort` to the `From`'s own `OutPort` (Rule H) |
| E19 | H | Simulink `From` deleted | delete the corresponding virtual-wire `FlowUsage` (Rule H) |
| E20 | I | Simulink `PortBlock` created (wrapping an already-corresponded `Port`) | add a second correspondence to the same `PortUsage` (Rule I) |
| E21 | I | Simulink `PortBlock` deleted | remove only the `PortBlock`'s own correspondence entry, not the shared `PortUsage` (Rule I) |

`BusSelector`/`BusCreator`/`Goto`/`From`/`GotoTagVisibility`/`ModelReference` creation/deletion (Rule G) reuse E3/E4 directly — no new rule IDs, just additional guarded matches on the same reactions. `BusSignalMapping` creation/deletion (Rule F) similarly reuses the same create/delete shape as E13/E14 without a new ID, since it's the same target class (`FlowUsage`). Deleting a `Goto` (rather than the `From`) reuses E19's own cleanup routine too — reached via `From removed from Goto[fromBlocks]` instead of `From deleted`, see the Rule H note in §3.2.

#### Property Rules — attribute value changes

| ID | Rule | Trigger | Action |
|---|---|---|---|
| P1 | A | `PartUsage.declaredName` changed | set `Block.simulinkRef.name` = new name |
| P2 | A | `Block.simulinkRef.name` changed | set `PartUsage.declaredName` = new name |
| P3 | B | `PortUsage.declaredName` changed | set `InPort`/`OutPort`'s `simulinkRef.name` = new name |
| P4 | B | `InPort`/`OutPort`'s `simulinkRef.name` changed | set `PortUsage.declaredName` = new name |
| P5 | D | `PartUsage.declaredName` changed (where Rule D siblings exist) | cascade-rename the corresponding `ActionUsage`/`RequirementUsage` to keep `'process' + name` / `name + 'Requirement'` consistent |
| P6 | E | `AttributeUsage.declaredName` changed / `Parameter.name` changed | set the other side's name to match (Rule E, bidirectional) |

#### Structural Rules — containment and type-migration changes

| ID | Rule | Trigger | Action |
|---|---|---|---|
| S1 | A | `PartUsage` gains its first `nestedPart` (was childless) | replace corresponding `Block` with a `SubSystem` of the same name; move existing port/parameter correspondences over; update correspondence |
| S2 | A | `PartUsage` loses its last `nestedPart` | replace corresponding `SubSystem` with a plain `Block`; update correspondence |
| S3 | A | Simulink `Block` moved into a `SubSystem.subBlocks` (i.e. gains a `parent`) | add corresponding `PartUsage` as a `nestedPart` of the corresponding parent `PartUsage` |

S3's trigger matches generically on `simulink::Block`, the same supertype `BlockCreated`/E3 matches on — so it applies to Rule G's block family too, not just plain `Block`/`SubSystem`, without any extra guarding needed.

#### Completeness Rules — model-wide invariants

| ID | Rule | Invariant |
|---|---|---|
| C1 | A | Every childless `PartUsage` has a corresponding `Block`, and vice versa; every `PartUsage` with `nestedPart->notEmpty()` has a corresponding `SubSystem`, and vice versa |
| C2 | B | Every `PortUsage` with `direction ∈ {in, out}` has a corresponding `InPort`/`OutPort`/`Trigger`/`Enable`/`State`, and vice versa |
| C3 | C | Every 2-ended `FlowUsage` has a corresponding `SingleConnection` (or `MultiConnection` branch, or `BusSignalMapping`, or Goto/From link), and vice versa |
| C4 | D | Every SysML `PartUsage` created via Rule D's cascade has exactly one sibling `ActionUsage` and one sibling `RequirementUsage` with names consistent with `declaredName` |
| C5 | E | Every Simulink `Parameter` (on a `Block` or a `Port`) has exactly one corresponding `AttributeUsage`, and vice versa |
| C6 | G | Every Rule G block-family instance has exactly one `PartUsage`, and none of them trigger Rule D's cascade (no stray `ActionUsage`/`RequirementUsage`) |
| C7 | I | Rule I's `PortBlock` family never adds a `PartUsage` of its own, and never doubles up the wrapped `Port`'s `PortUsage` |

C1–C4 predate Rules E–I; C5–C7 close that gap, one completeness check per rule added since. Note that `FlowUsage` is itself an `ActionUsage` subtype in the SysML ecore (§1.9/§1.11) — a raw type-based count of "every `ActionUsage` in the model" also counts any legitimate Rule H `FlowUsage`s present, so C6's own test asserts on the cascade's specific `process<name>` naming pattern instead of a bare count, to avoid conflating the two.

### 3.5 Checkable Invariants (MultiModel-OCL)

The OCL blocks in §3.3 are illustrative — they document intent but nothing executes them. `consistency/src/main/ocl/` has real constraint files for Rules A–E, written in [MultiModel-OCL](https://github.com/vitruv-tools/MultiModel-OCL)'s actual syntax (unified dot notation, no `->`, fully-qualified metamodel names) against this project's own `SysML.ecore`/`simulink.ecore`. Rules F–I stay out of scope here too, same reasoning as §3.3.

**Honestly flagged limitation:** MultiModel-OCL is an experimental vitruv-tools project (no Maven Central artifact, distributed as a standalone CLI jar / VSCode extension, no documented direct VSUM hook) — these constraints are checked by running the external tool against this project's ecores and a model instance, not by `mvn verify`. See the tool's own README for the `multimodelocl.jar` CLI usage; point it at `model/src/main/ecore/SysML.ecore` / `simulink.ecore` and the `.ocl` files in this repo.

---

## 4. Building and Running

This is a four-module Maven project. To build all modules, run the tests, and verify the change propagation rules, use:

```bash
./mvnw clean verify
```

- `clean` removes any previous build artifacts (`target/` directories) across all modules.
- `verify` runs the full lifecycle up through integration tests — compiling, running unit tests (the eight `SysMLToSimulink*Test`/`SimulinkToSysML*Test` classes under `vsum/src/test/`), and executing `VSUMRunner` to validate bidirectional consistency.

Run it from the project root, where the parent `pom.xml` lives:

```bash
cd path/to/project-root
./mvnw clean verify
```

If a specific module needs to be built in isolation (e.g. while iterating on the Reactions DSL rules), use `-pl` with `-am` to also build its dependencies:

```bash
./mvnw clean verify -pl <module-name> -am
```

> [!TIP]
> If the build fails on a fresh clone with an MWE2 URI resolver error, make sure the `.genmodel`/`.ecore` files have been generated first (`./mvnw clean install` on the `model` module before running `verify` on the full reactor).

> [!NOTE]
> **Both** `SysML.ecore` and `simulink.ecore` declare custom delegate factories for their derived features (see the NOTE in `model/pom.xml`). This project's reactions/tests deliberately avoid touching any derived feature, so this doesn't block building or running — it only means derived-feature *values* (e.g. `PartUsage.nestedPart` read directly rather than through a test helper) would be unset at runtime without the upstream projects' own delegate runtimes on the classpath.

### 4.1 Running Interactively (Real User Input)

`./mvnw clean verify` runs the automated test suite — no human is ever prompted. To actually see the V-SUM in action end-to-end, run `VSUMExample`'s `main()` method directly.

`exec:java` invoked as a standalone goal (below) resolves `model`/`consistency` from your **local Maven repository**, not from the reactor's in-memory build — so if you've only ever run `clean verify` (which never runs `install`), it'll silently pick up whatever old jar happens to be sitting in `~/.m2`, if anything, and any recent `.reactions` change (including the interactive `inout` resolution itself) won't be there. Install them first, every time you've touched `model/` or `consistency/`:

```bash
./mvnw -pl model,consistency -am install -DskipTests
```

`vsum/sample-data/` holds generated, machine-specific data. `VSUMExample` builds it itself the first time it runs against an empty folder — no separate setup step needed:

```bash
./mvnw -pl vsum org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass="tools.vitruv.casestudies.sysmlsimulink.vsum.VSUMExample" -Dexec.classpathScope=compile
```

Run from the project root. `VSUMExample` registers the SysML root if none exists yet, then adds a Simulink `Block` named `TemperatureMonitor` with no corresponding SysML element — reproducing Grycz et al.'s worked example (§4.2) and letting you inspect the resulting `PartUsage`/`ActionUsage`/`RequirementUsage` cascade (Rule D) directly in `vsum/sample-data/`.

Re-running it repeatedly against the same `vsum/sample-data/` folder adds another same-named `Block` each time, since the demo's object name is hardcoded — delete `vsum/sample-data/` first for a clean run.

---

## License

This project is licensed under the [Eclipse Public License 1.0](LICENSE), the same license used by the Vitruv framework it builds on.
