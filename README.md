# SysML v2 ↔ Simulink

**Vitruv-Based Bidirectional Model Consistency Preservation**

A Maven project implementing bidirectional change propagation between the SysML v2 metamodel (OMG Pilot Implementation) and the Simulink metamodel (MASSIF) using the Vitruv framework, grounded in a real KIT/SFB 1608 "Convide" research case study.

## Table of Contents

- [1. SysML v2 Metamodel Description](#1-sysml-v2-metamodel-description)
- [2. Simulink Metamodel Description](#2-simulink-metamodel-description)
- [3. Semantic Overlaps and Consistency Preservation Rules](#3-semantic-overlaps-and-consistency-preservation-rules)
- [4. Building and Running](#4-building-and-running)

---

## 1. SysML v2 Metamodel Description

*OMG SysML v2 Pilot Implementation*

URI: `https://www.omg.org/spec/SysML/20250201` | Source: [Systems-Modeling/SysML-v2-Pilot-Implementation](https://github.com/Systems-Modeling/SysML-v2-Pilot-Implementation)

### 1.1 Overview

SysML v2 is built on top of **KerML** (Kernel Modeling Language): almost every SysML concept (`PartUsage`, `PortUsage`, `AttributeUsage`, `ActionUsage`, `RequirementUsage`, ...) is a specialization of the generic KerML `Feature`/`Usage`/`Type`/`Namespace`/`Element` hierarchy. This is architecturally very different from a purpose-built metamodel like AMALTHEA or ASEM: there are almost no metaclass-specific containment references (e.g. there is no `Block.parts` list). Instead, containment is expressed generically through **`Relationship`** objects (`OwningMembership`, `FeatureMembership`, ...) whose `ownedRelatedElement` holds the actual member, and convenience accessors such as `nestedPart`, `nestedPort`, `ownedMember` are all `derived`/`transient`/`volatile` — they are *computed*, not real EMF slots you can listen to or write into directly.

This has a direct, practical consequence for consistency preservation (see §3.4): a Vitruv reaction cannot listen on `"after element inserted in PartUsage[nestedPart]"` the way the AMALTHEA/ASEM project listened on `"after element inserted in model::System[components]"`, because `nestedPart` is derived. Reactions must instead listen on the real, non-derived features: the `declaredName` attribute (not the derived `name`) and `ownedRelatedElement`/`Feature.direction`.

For the SysML ↔ Simulink case study, the relevant sub-model is the **technical architecture / interface layer**: `PartUsage`/`PartDefinition` (structural blocks), `PortUsage`/`PortDefinition` (interaction points), `FlowUsage`/`FlowDefinition` (signal/item flow), and, for the requirement–function–architecture traceability cascade described in Grycz et al. (KIT/SFB 1608, "Convide" brake-system research platform), `RequirementUsage`/`RequirementDefinition` and `ActionUsage`/`ActionDefinition`.

### 1.2 Scope: Classes Relevant to Simulink Mapping

| SysML Class | Key Supertypes | Role in Mapping |
|---|---|---|
| `PartUsage` | `ItemUsage → OccurrenceUsage → Usage → Feature → Type → Namespace → Element` | Maps to Simulink `Block` / `SubSystem` (Rule A) |
| `PartDefinition` | `ItemDefinition → OccurrenceDefinition → Definition/Class` | Type of a `PartUsage`; no direct Simulink counterpart |
| `PortUsage` | `OccurrenceUsage → Usage → Feature → ...` | Maps to Simulink `InPort` / `OutPort`, disambiguated by `direction` (Rule B) |
| `PortDefinition` | `OccurrenceDefinition → Structure → Class` | Type of a `PortUsage` |
| `FlowUsage` | `ConnectorAsUsage, ActionUsage, Flow → Connector, Step` | Maps to Simulink `SingleConnection` (Rule C) |
| `FlowDefinition` | `ActionDefinition, Interaction → Association, Behavior` | Type of a `FlowUsage` |
| `AttributeUsage` | `Usage → Feature → ...` | Maps to Simulink `Parameter` (name/type only; documented, not implemented) |
| `ActionUsage` | `OccurrenceUsage, Step → Feature` | Represents a "function"; created by the traceability cascade (Rule D) |
| `RequirementUsage` | `ConstraintUsage, BooleanExpression → OccurrenceUsage` | Represents a "requirement"; created by the traceability cascade (Rule D) |
| `Package` | `Namespace → Element` | VSUM root anchor on the SysML side |

### 1.3 Element — the KerML root

Every SysML class ultimately inherits from `Element`. Two facts drive every reaction in this project:

| Attribute / Reference | Type | Derived? | Description |
|---|---|---|---|
| `declaredName` | `EString` | **No** — real, settable | The name a modeler actually types in. |
| `name` | `EString` | **Yes** (`effectiveName()`, defaults to `declaredName`) | What you'd naively expect to bind a rename-reaction to — but it is computed, so Vitruv cannot observe changes to it directly. **Reactions must listen on `declaredName`.** |
| `elementId` | `EString`, `lowerBound=1` | No | Tooling-assigned globally unique id. |
| `ownedRelationship` | `Relationship`, containment | No | The only real containment edge out of an `Element`; every "owned member" is reached by walking through a `Relationship` (typically `OwningMembership`/`FeatureMembership`), not directly. |
| `owningRelationship` | `Relationship` | No (opposite of above) | Used to walk *up* from a leaf `Element` to find its logical container, since there is no direct `eContainer`-style "owning PartUsage" reference. |
| `qualifiedName` | `EString` | Yes | Derived from `owningNamespace.qualifiedName + '::' + escapedName()`. |

### 1.4 Namespace / Type / Feature / Usage (KerML base classes)

| Class | Adds | Notes |
|---|---|---|
| `Namespace` | `member`, `membership`, `ownedMember` (all derived) | A container of `Membership` relationships, not a container of `Element`s directly. |
| `Type` | `feature`, `ownedFeature` (derived) | Base for anything that can own typed features. |
| `Feature` | **`direction`** (`FeatureDirectionKind`: `in` / `out` / `inout`) — real, non-derived attribute | This is the one non-derived, directly reactable attribute that distinguishes an input from an output. Used by Rule B to disambiguate `PortUsage` → `InPort`/`OutPort`. |
| `Usage` | `nestedPart`, `nestedPort`, `nestedAttribute`, `nestedAction`, ... (all derived filters over `nestedUsage`/`ownedFeature`) | Convenience read-only views; cannot be inserted into directly. |

### 1.5 PartUsage / PartDefinition

`PartUsage` (`itemDefinition->selectByKind(PartDefinition)->notEmpty()`) represents "a system or a part of a system" — the SysML equivalent of a Simulink `Block`. A `PartUsage` may itself own nested `PartUsage`s (`Usage.nestedPart`, derived from `ownedFeature`), directly analogous to a Simulink `SubSystem` owning `subBlocks`.

| Attribute / Reference | Type | Derived? | Description |
|---|---|---|---|
| `declaredName` (inherited) | `EString` | No | Propagated to the corresponding Simulink element's name. |
| `partDefinition` | `PartDefinition[*]` | Yes | The `PartDefinition`(s) typing this usage. |
| `nestedPart` | `PartUsage[*]` | Yes (`nestedUsage->selectByKind(PartUsage)`) | If non-empty, the corresponding Simulink element must be a `SubSystem`, not a plain `Block` (Rule A / S1). |
| `isComposite` (from `Usage`) | `EBoolean` | No | Whether this part is owned (contained) vs. referenced. |

`PartDefinition` is the `Classifier` a `PartUsage` is typed by; it has no Simulink counterpart — Simulink does not separate "definition" from "usage" the way SysML does (a `Block` combines both roles).

### 1.6 PortUsage / PortDefinition

A `PortUsage` (`OccurrenceUsage`) is "a point at which external entities can connect to and interact with a system." Unlike `PartUsage`, direction is what disambiguates the Simulink target:

| Attribute / Reference | Type | Derived? | Description |
|---|---|---|---|
| `declaredName` (inherited) | `EString` | No | Propagated to the corresponding Simulink `Port`'s name. |
| `direction` (inherited from `Feature`) | `FeatureDirectionKind` (`in`/`out`/`inout`) | **No** | `in` → Simulink `InPort`; `out` → Simulink `OutPort`; `inout` has **no Simulink equivalent** — Simulink ports are strictly one-directional (see §3.4, Rule B note). |
| `portDefinition` | `PortDefinition[*]` | Yes | The `PortDefinition`(s) typing this usage. |

### 1.7 FlowUsage / FlowDefinition

A `FlowUsage` is both a KerML `Flow` (a kind of `Connector`) and an `ActionUsage` — it represents an item/signal transfer between two `flowEnd`s (`Usage.flowEnd`, redefining `Association.associationEnd`). The OCL invariant `flowEnd->size() <= 2` means a `FlowUsage` connects at most two features, which maps naturally onto Simulink's `SingleConnection` (exactly one `OutPort` → one `InPort`).

| Attribute / Reference | Type | Derived? | Description |
|---|---|---|---|
| `flowEnd` | `Usage[0..2]` | Yes | The two connected features — nominally two `PortUsage`s, one `out` and one `in`. |
| `flowDefinition` | `Interaction[*]` | Yes | The `FlowDefinition` typing this usage. |

Simulink's `MultiConnection` (one `OutPort` fanning out to many `InPort`s via nested `SingleConnection`s) has **no single-`FlowUsage` equivalent** — it would require several `FlowUsage`s sharing the same source `flowEnd`. This asymmetry is intentionally left unimplemented (see §3.4, Rule C note).

### 1.8 AttributeUsage / AttributeDefinition

| Attribute / Reference | Type | Derived? | Description |
|---|---|---|---|
| `declaredName` (inherited) | `EString` | No | Propagated to the corresponding Simulink `Parameter.name`. |
| `attributeDefinition` | `DataType[*]` | Yes | The `AttributeDefinition`/`DataType` typing this usage; its name is propagated (as a string) to `Parameter.type`. |

Note: an `AttributeUsage`'s *value* is expressed via a nested `FeatureValue`/`LiteralExpression` subtree several relationship-hops away, not a direct attribute. Value propagation is out of scope for this project, mirroring the AMALTHEA/ASEM project's decision not to propagate `Constant`/`Message` values either.

### 1.9 ActionUsage / RequirementUsage (traceability cascade classes)

These two classes are not mapped 1:1 to any Simulink class. They exist purely on the SysML side and are auto-generated, together with a `PartUsage`, whenever a Simulink `Block`/`SubSystem` is discovered without a corresponding SysML architecture element — mirroring the exact scenario described in Grycz et al.: *"when a new technical architecture block is introduced [from the Simulink side], a corresponding requirement, function ... are created"* (paraphrased; see §3.4 Rule D).

| Class | Key Supertypes | Used In |
|---|---|---|
| `ActionUsage` | `OccurrenceUsage, Step → Feature` | Rule D (as a stand-in for "function") |
| `RequirementUsage` | `ConstraintUsage, BooleanExpression` | Rule D (as a stand-in for "requirement") |

The formal `Satisfy`/`Perform`/`Allocate` relationships that would properly wire Requirement → Function → Architecture together (`SatisfyRequirementUsage`, `PerformActionUsage`, `AllocationUsage`) are **not** created by the cascade in this project — see the omission note under Rule D in §3.4.

### 1.10 Package

`Package` (`Namespace`) is the SysML-side VSUM root anchor, analogous to AMALTHEA's `ComponentsModel` or ASEM's `Dummy` root. Top-level `PartUsage`s owned (via the generic `Membership` mechanism) by a `Package` correspond to root-level `Block`s contained directly in a Simulink `SimulinkModel.contains`.

### 1.11 Inheritance Summary (Classes Relevant to Mapping)

| Class | Key Supertypes | Used in Rule(s) |
|---|---|---|
| `PartUsage` | `ItemUsage → OccurrenceUsage → Usage → Feature → Type → Namespace → Element` | Rule A |
| `PartDefinition` | `ItemDefinition → OccurrenceDefinition → Definition, Class` | Rule A (type-side, no direct mapping) |
| `PortUsage` | `OccurrenceUsage → Usage → Feature → ...` | Rule B |
| `FlowUsage` | `ConnectorAsUsage, ActionUsage, Flow` | Rule C |
| `AttributeUsage` | `Usage → Feature → ...` | documented, not implemented |
| `ActionUsage` | `OccurrenceUsage, Step` | Rule D |
| `RequirementUsage` | `ConstraintUsage, BooleanExpression` | Rule D |
| `Package` | `Namespace → Element` | VSUM root anchor |

---

## 2. Simulink Metamodel Description

*MASSIF Simulink Ecore*

URI: `http://hu.bme.mit.massif/simulink/1.0` | Source: [viatra/massif](https://github.com/viatra/massif)

### 2.1 Overview

This Ecore metamodel represents the core structural concepts of MATLAB Simulink models: block diagrams with hierarchical decomposition, typed ports, signal connections, and block parameters. Per its own root-level documentation, it has "a strong focus on the structure and less focus on the behavior, simulation and layout specific details."

Unlike SysML v2, Simulink's containment is almost entirely **direct, non-derived EReferences** — `Block.ports`, `Block.parameters`, `SubSystem.subBlocks` are all plain, settable, `containment=true` features. This makes the Simulink side of the consistency rules structurally much simpler than the SysML side (see §1.1). The one exception, described below, is `SimulinkElement.name` itself.

### 2.2 Scope: Classes Relevant to SysML Mapping

| Simulink Class | Location | Role in Mapping |
|---|---|---|
| `Block` | `SimulinkModel.contains` / `SubSystem.subBlocks` | Maps to SysML `PartUsage` (Rule A) |
| `SubSystem` | extends `Block` | Maps to SysML `PartUsage` with `nestedPart` (Rule A) |
| `InPort` / `OutPort` | `Block.ports` | Map to SysML `PortUsage` with `direction = in` / `out` (Rule B) |
| `SingleConnection` | `OutPort.connection` (containment) | Maps to SysML `FlowUsage` (Rule C) |
| `Parameter` | `Block.parameters` / `Port.parameters` | Maps to SysML `AttributeUsage` (documented, name/type only) |
| `SimulinkModel` | root | VSUM root anchor, corresponds to SysML `Package` |

### 2.3 SimulinkElement (abstract root)

| Attribute / Reference | Type | Derived? | Description |
|---|---|---|---|
| `simulinkRef` | `IdentifierReference`, containment | No | Holds the element's real, settable name/qualifier. |
| `name` | `EString` | **Yes** — `changeable=false`, `volatile=true`, `derived=true`, computed via a VIATRA query-based feature (`patternFQN = hu.bme.mit.massif.models.simulink.derived.name`) from `simulinkRef.name` | Cannot be set or listened to directly. **Reactions must operate on `simulinkRef.name`.** This mirrors the SysML side's `name`/`declaredName` split (§1.3) — both metamodels turn out to compute their "friendly" name from an underlying reference object, for different reasons (VIATRA query-based features here; KerML `effectiveName()` there). |

### 2.4 Block

Location: `SimulinkModel.contains[]` or `SubSystem.subBlocks[]`. "The basic building block of Simulink systems." In the SysML ↔ Simulink mapping, a `Block` (without children) or `SubSystem` (with children) is the top-level structural unit that maps to a SysML `PartUsage`.

| Attribute / Reference | Type | Multiplicity | Description |
|---|---|---|---|
| `simulinkRef.name` (via `SimulinkElement`) | `EString` | 0..1 | Propagated to `PartUsage.declaredName`. |
| `parameters` | `Parameter`, containment | 0..* | Block-level configuration values. Maps to `AttributeUsage` (documented only). |
| `ports` | `Port`, containment | 0..* | Interface ports of the block. Maps to `PortUsage` (Rule B). |
| `parent` | `SubSystem` (`eOpposite` of `subBlocks`) | 0..1 | Real, non-derived opposite reference — used to find the logical container without indirection. |
| `sourceBlockRef` / `sourceBlock` | `LibraryLinkReference` / `Block` (derived) | 0..1 | Link back to a library block template; out of scope for this mapping. |
| `trigger`, `enabler`, `inports`, `outports` | derived, filtered views of `ports` | 0..* | Convenience read-only accessors (VIATRA query-based features), analogous to SysML's `nestedPart`/`nestedPort`. |

### 2.5 SubSystem (extends Block)

"A Simulink block that may contain subblocks that specify its internal structure and behavior."

| Attribute / Reference | Type | Multiplicity | Description |
|---|---|---|---|
| `subBlocks` | `Block`, containment, `eOpposite = Block.parent` | 0..* | Real, non-derived containment. Maps to `PartUsage.nestedPart` (derived on the SysML side — see §3.4 Rule A / S1 for the resulting asymmetry). |
| `tag` | `EString` | 0..1 | Free-text subsystem tag; no SysML equivalent. |

### 2.6 Port (abstract) / InPort / OutPort

| Class | Extends | Key Feature | Description |
|---|---|---|---|
| `Port` (abstract) | `SimulinkElement` | `container` (`eOpposite` of `Block.ports`), `portBlock`, `parameters` | Base type. |
| `InPort` | `Port` | `connection` (`eOpposite` of `SingleConnection.to`) | Maps to SysML `PortUsage` with `direction = in` (Rule B). |
| `OutPort` | `Port` | `connection` (containment, `eOpposite` of `Connection.from`) | Maps to SysML `PortUsage` with `direction = out` (Rule B). |
| `Trigger` | `InPort` | `triggerType`, `statesWhenEnabling` | Specialized control-flow port; **no SysML equivalent** in this mapping (one-directional gap, see §3.4 Rule B note). |
| `Enable` | `InPort` | `statesWhenEnabling` | Same gap as `Trigger`. |
| `State` | `OutPort` | — | Stateport (e.g. for `Integrator` blocks); same gap. |

### 2.7 Connection (abstract) / SingleConnection / MultiConnection

| Class | Extends | Key Features | Description |
|---|---|---|---|
| `Connection` (abstract) | `SimulinkElement` | `from` (`OutPort`), `lineName` | Base type for signal connections. |
| `SingleConnection` | `Connection` | `to` (`InPort`, `eOpposite` of `InPort.connection`), `parent` (`MultiConnection`) | "A simple connection between a single `OutPort` and a single `InPort`." Maps to SysML `FlowUsage` (Rule C). |
| `MultiConnection` | `Connection` | `connections` (`SingleConnection[]`, containment) | "A connection between a single `OutPort` and multiple `InPort`s." **No single-`FlowUsage` equivalent** — see §1.7. |

### 2.8 Parameter

Location: `Block.parameters[]` / `Port.parameters[]`. Not a map — a block may have multiple parameters with the same name.

| Attribute / Reference | Type | Multiplicity | Description |
|---|---|---|---|
| `name` | `EString` | 0..1 | Plain (non-derived) attribute — unlike `SimulinkElement.name`, this one is directly settable. Maps to `AttributeUsage.declaredName`. |
| `type` | `EString` | 0..1 | String-typed; maps loosely to the name of the `AttributeUsage`'s `attributeDefinition`. |
| `value` | `EString` | 0..1 | Free-text value. Not propagated (no reliable SysML-side counterpart — see §1.8). |
| `readOnly` | `EBoolean` | 0..1 | No SysML equivalent. |

### 2.9 SimulinkModel (root)

"The root of an imported Simulink system that contains blocks." Corresponds to the SysML `Package` as the VSUM root anchor.

| Attribute / Reference | Type | Multiplicity | Description |
|---|---|---|---|
| `contains` | `Block`, containment | 0..* | Root-level blocks. Corresponds to top-level `PartUsage`s owned by the SysML `Package`. |
| `version`, `file`, `library` | `EString`/`EString`/`EBoolean` | — | Import metadata; no SysML equivalent. |

### 2.10 Inheritance Summary (Classes Relevant to Mapping)

| Class | Key Supertypes | Used in Rule(s) |
|---|---|---|
| `Block` | `SimulinkElement` | Rule A |
| `SubSystem` | `Block` | Rule A |
| `InPort` | `Port → SimulinkElement` | Rule B |
| `OutPort` | `Port → SimulinkElement` | Rule B |
| `Trigger`, `Enable`, `State` | `InPort`/`OutPort` | Rule B (documented gap, not implemented) |
| `SingleConnection` | `Connection → SimulinkElement` | Rule C |
| `MultiConnection` | `Connection → SimulinkElement` | Rule C (documented gap, not implemented) |
| `Parameter` | (none, plain `EObject`) | documented only |
| `SimulinkModel` | `SimulinkElement` | VSUM root anchor |

---

## 3. Semantic Overlaps and Consistency Preservation Rules

Unlike the AMALTHEA ↔ ASEM case study, there is no published correspondence table (no equivalent of Mazkatli et al. 2016, Table 5.1) for this metamodel pair. The rules below are instead derived directly from the two `.ecore` files, cross-checked class by class, and grounded in the real engineering scenario described in Grycz, Hagel, Eger, Reussner, Düser — *"Model-Based Activities for Supporting the Synthesis of Validation Environments Using the V-SUM Approach"* (KIT/SFB 1608 "Convide", submitted TdSEE Dortmund 2025/2026): a Brake-System-in-the-Loop validation environment in which a brake-disc-temperature sensor was implemented in the physical rig and represented in Simulink, but never propagated back into the descriptive SysML system model maintained in Cameo Systems Modeler.

Three things to read this section with in mind:

- Rules A–C are bidirectional. Rule D (the requirement/function traceability cascade) is **one-directional (Simulink → SysML only)** — Simulink has no Requirement or Function concept to originate the reverse direction from. This mirrors the paper's own demonstrated scenario.
- Several sub-rules are **intentionally omitted** where the source `.ecore` files don't support a faithful mapping without deeper, out-of-scope modeling. Each omission is documented with its reason, following the same convention as the AMALTHEA ↔ ASEM project's P11/P12 notes.
- Open questions equivalent to "confirm with Benedikt" are marked as *(needs domain-owner confirmation)*.

### 3.1 Semantic Overlap Summary

| Rule | SysML | Simulink / Condition |
|---|---|---|
| **A** | `PartUsage` (no children) | `Block` — no filter |
| **A** | `PartUsage` (`nestedPart->notEmpty()`) | `SubSystem` — has `subBlocks` |
| **B** | `PortUsage` (`direction = in`) | `InPort` |
| **B** | `PortUsage` (`direction = out`) | `OutPort` |
| **B** | `PortUsage` (`direction = inout`) | *(no mapping — see Rule B note)* |
| **C** | `FlowUsage` (`flowEnd->size() = 2`) | `SingleConnection` |
| **D** | `PartUsage` + sibling `ActionUsage` + sibling `RequirementUsage` | `Block`/`SubSystem` created with no corresponding architecture element (Simulink → SysML only) |

### 3.2 Correspondence Rules in Detail

#### Rule A — PartUsage/PartDefinition ↔ Block/SubSystem

- SysML: `PartUsage`, typed by a `PartDefinition` (`itemDefinition->selectByKind(PartDefinition)->notEmpty()`)
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

Just as AMALTHEA `Label.constant` flipping forced an ASEM `Message`↔`Constant` swap (Rule 5/6, P8/P9 in the ASEM-Amalthea project), a `PartUsage` gaining its first nested part — or losing its last one — forces a `Block`↔`SubSystem` swap on the Simulink side, since `SubSystem` is a distinct EClass, not a flag on `Block`.

#### Rule B — PortUsage ↔ InPort / OutPort

- SysML: `PortUsage`, disambiguated by the inherited `Feature.direction` attribute
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

**Note** — `direction = inout` has no Simulink target. Simulink's `Port` hierarchy is strictly one-directional by class (`InPort` vs `OutPort` are disjoint EClasses); there is no bidirectional port EClass to map to. Left unmapped. *(needs domain-owner confirmation on whether `inout` ports should be split into a paired in/out port on the Simulink side.)*

**Note** — the reverse direction has a gap of its own: Simulink's `Trigger`, `Enable`, and `State` (all subtypes of `InPort`/`OutPort`) carry control-flow/simulation semantics that a plain SysML `PortUsage` + `direction` cannot express. These are intentionally **not** propagated to SysML.

#### Rule C — FlowUsage ↔ SingleConnection

- SysML: `FlowUsage`, `flowEnd->size() = 2`, ends typed by an `out`- and an `in`-directioned `PortUsage`
- Simulink: `SingleConnection`, `from` (`OutPort`) → `to` (`InPort`)
- No OCL filter beyond the two-ended constraint (already enforced by `FlowUsage`'s own invariant `flowEnd->size() <= 2`)

| SysML | Simulink |
|---|---|
| `FlowUsage.flowEnd[1]` (end typed by the `out` `PortUsage`) | `SingleConnection.from` (via the corresponding `OutPort`) |
| `FlowUsage.flowEnd[2]` (end typed by the `in` `PortUsage`) | `SingleConnection.to` (via the corresponding `InPort`) |

**Note** — Simulink's `MultiConnection` (one `OutPort` fanning out to several `InPort`s via nested `SingleConnection`s) has no single-`FlowUsage` equivalent in this rule. A faithful mapping would require either (a) several `FlowUsage`s sharing the same source `flowEnd`, or (b) a dedicated multi-ended `FlowUsage`. Neither is implemented — **omitted**, analogous to the ASEM-Amalthea project's P11/P12 notes.

#### Rule D — Traceability cascade: Block/SubSystem → PartUsage + ActionUsage + RequirementUsage

This rule is not a metaclass-to-metaclass correspondence like A–C; it reproduces the specific scenario from Grycz et al. §3.3–4.2: when a Simulink `Block`/`SubSystem` exists with **no** corresponding SysML architecture element, propagation does not stop at creating a `PartUsage` (Rule A, E3) — it also creates a sibling `ActionUsage` ("function") and a sibling `RequirementUsage` ("requirement"), all three named consistently after the originating block, in the same owning `Package`. This is a direct implementation of the paper's own worked example: a new Simulink block named `TemperatureMonitor` results in *"a newly created requirement capturing the brake disc temperature monitoring functionality, a corresponding function for processing the temperature information, a technical architecture element ... consistent naming across all generated elements"* (§4.2, verbatim).

- Direction: **Simulink → SysML only.** Simulink has no requirement/function concept to originate the reverse cascade from; a SysML-side `PartUsage` created directly (Rule A, E1) does *not* trigger this cascade — only propagation originating from an *unmatched* Simulink block does.

| Simulink (trigger) | SysML (generated) |
|---|---|
| `Block.simulinkRef.name` = `"X"` | `PartUsage.declaredName` = `"X"` (Rule A, unchanged) |
| — | `ActionUsage.declaredName` = `"process" + X` |
| — | `RequirementUsage.declaredName` = `X + "Requirement"` |

**Omission, honestly flagged (same spirit as the ASEM-Amalthea project's P11/P12 notes):** the cascade creates three *sibling* elements with consistent names, but does **not** create the formal `SatisfyRequirementUsage` / `PerformActionUsage` / `AllocationUsage` relationship objects that would properly wire Requirement → Function → Architecture together in SysML's traceability model. Grycz et al. flag exactly this same gap themselves (§5.2, discussing Figure 6): *"a further refining and detailing could be accomplished ... additional relationships such as «satisfy», «allocate», and trace links between requirements, functions, and technical architecture elements can be established"* — i.e. the paper's own case study also stops at generating the three elements and leaves the relationship wiring as manual/future work. This project does the same, deliberately, rather than inventing an under-specified auto-wiring policy.

### 3.3 OCL Invariant Summary

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

### 3.4 Consistency Preservation Rules

Rules are grouped into four categories: **Existence (E)**, **Property (P)**, **Structural (S)**, and **Completeness (C)**.

#### Existence Rules — element creation and deletion

| ID | Trigger | Action |
|---|---|---|
| E1 | SysML `PartUsage` created (no children) | create Simulink `Block` with same name; add correspondence |
| E2 | SysML `PartUsage` deleted | delete corresponding `Block`/`SubSystem`; remove correspondence |
| E3 | Simulink `Block`/`SubSystem` created | create SysML `PartUsage` with same name **and** trigger Rule D's cascade (`ActionUsage` + `RequirementUsage`); add correspondence |
| E4 | Simulink `Block`/`SubSystem` deleted | delete corresponding `PartUsage` (and its Rule D siblings, if present) |
| E5 | SysML `PortUsage` (`direction = in`) created | create Simulink `InPort` in parent `Block.ports` |
| E6 | SysML `PortUsage` (`direction = out`) created | create Simulink `OutPort` in parent `Block.ports` |
| E7 | SysML `PortUsage` deleted | delete corresponding `InPort`/`OutPort` |
| E8 | Simulink `InPort` created | create SysML `PortUsage` (`direction = in`) |
| E9 | Simulink `OutPort` created | create SysML `PortUsage` (`direction = out`) |
| E10 | Simulink `InPort`/`OutPort` deleted | delete corresponding `PortUsage` |
| E11 | SysML `FlowUsage` (2-ended, out→in) created | create Simulink `SingleConnection` linking the corresponding `OutPort`/`InPort` |
| E12 | SysML `FlowUsage` deleted | delete corresponding `SingleConnection` |
| E13 | Simulink `SingleConnection` created | create SysML `FlowUsage` between the corresponding `PortUsage`s |
| E14 | Simulink `SingleConnection` deleted | delete corresponding `FlowUsage` |

#### Property Rules — attribute value changes

| ID | Trigger | Action |
|---|---|---|
| P1 | `PartUsage.declaredName` changed | set `Block.simulinkRef.name` = new name |
| P2 | `Block.simulinkRef.name` changed | set `PartUsage.declaredName` = new name |
| P3 | `PortUsage.declaredName` changed | set `InPort`/`OutPort`'s `simulinkRef.name` = new name |
| P4 | `InPort`/`OutPort`'s `simulinkRef.name` changed | set `PortUsage.declaredName` = new name |
| P5 | `PartUsage.declaredName` changed (where Rule D siblings exist) | cascade-rename the corresponding `ActionUsage`/`RequirementUsage` to keep `'process' + name` / `name + 'Requirement'` consistent |

#### Structural Rules — containment and type-migration changes

| ID | Trigger | Action |
|---|---|---|
| S1 | `PartUsage` gains its first `nestedPart` (was childless) | replace corresponding `Block` with a `SubSystem` of the same name; move existing port/parameter correspondences over; update correspondence |
| S2 | `PartUsage` loses its last `nestedPart` | replace corresponding `SubSystem` with a plain `Block`; update correspondence |
| S3 | Simulink `Block` moved into a `SubSystem.subBlocks` (i.e. gains a `parent`) | add corresponding `PartUsage` as a `nestedPart` of the corresponding parent `PartUsage` |

#### Completeness Rules — model-wide invariants

| ID | Invariant |
|---|---|
| C1 | Every childless `PartUsage` has a corresponding `Block`, and vice versa; every `PartUsage` with `nestedPart->notEmpty()` has a corresponding `SubSystem`, and vice versa |
| C2 | Every `PortUsage` with `direction ∈ {in, out}` has a corresponding `InPort`/`OutPort`, and vice versa (excluding `Trigger`/`Enable`/`State`, which have no SysML counterpart) |
| C3 | Every 2-ended `FlowUsage` has a corresponding `SingleConnection`, and vice versa (excluding `MultiConnection`, which is out of scope) |
| C4 | Every SysML `PartUsage` created via Rule D's cascade has exactly one sibling `ActionUsage` and one sibling `RequirementUsage` with names consistent with `declaredName` |

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
> Unlike the ASEM-Amalthea case study (where ASEM was clean enough to generate locally and only AMALTHEA needed an external artifact), **both** `SysML.ecore` and `simulink.ecore` declare custom delegate factories for their derived features (see the NOTE in `model/pom.xml`). This project's reactions/tests deliberately avoid touching any derived feature, so this doesn't block building or running — it only means derived-feature *values* (e.g. `PartUsage.nestedPart` read directly rather than through a test helper) would be unset at runtime without the upstream projects' own delegate runtimes on the classpath.

### 4.1 Running Interactively (Real User Input)

`./mvnw clean verify` runs the automated test suite — no human is ever prompted. To actually see the V-SUM in action end-to-end, run `VSUMExample`'s `main()` method directly.

`vsum/sample-data/` holds generated, machine-specific data, so the first time you do this, build it locally:

```bash
./mvnw -pl vsum org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass="tools.vitruv.methodologisttemplate.vsum.VSUMSampleDataGenerator" -Dexec.classpathScope=compile
```

Then run the interactive demo itself:

```bash
./mvnw -pl vsum org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass="tools.vitruv.methodologisttemplate.vsum.VSUMExample" -Dexec.classpathScope=compile
```

Run both from the project root. `VSUMExample` loads the baseline model built by the step above, then adds a Simulink `Block` named `TemperatureMonitor` with no corresponding SysML element — reproducing Grycz et al.'s worked example (§4.2) and letting you inspect the resulting `PartUsage`/`ActionUsage`/`RequirementUsage` cascade (Rule D) directly in `vsum/sample-data/`.

Re-running `VSUMExample` repeatedly against the same `vsum/sample-data/` folder adds another same-named `Block` each time, since the demo's object name is hardcoded. Re-running `VSUMSampleDataGenerator` against an already-populated folder will similarly conflict — only run it once, right after a fresh copy is needed.
