# SysML v2 Metamodel Description

OMG SysML v2 Pilot Implementation — `org.omg.sysml/model/SysML.ecore`
URI: `https://www.omg.org/spec/SysML/20250201`  |  Source: [Systems-Modeling/SysML-v2-Pilot-Implementation](https://github.com/Systems-Modeling/SysML-v2-Pilot-Implementation)

## 1. Overview

SysML v2 is built on top of **KerML** (Kernel Modeling Language): almost every SysML concept
(`PartUsage`, `PortUsage`, `AttributeUsage`, `ActionUsage`, `RequirementUsage`, ...) is a
specialization of the generic KerML `Feature`/`Usage`/`Type`/`Namespace`/`Element` hierarchy. This
is architecturally very different from a purpose-built metamodel like AMALTHEA or ASEM: there are
almost no metaclass-specific containment references (e.g. there is no `Block.parts` list). Instead,
containment is expressed generically through **`Relationship`** objects (`OwningMembership`,
`FeatureMembership`, ...) whose `ownedRelatedElement` holds the actual member, and convenience
accessors such as `nestedPart`, `nestedPort`, `ownedMember` are all `derived`/`transient`/`volatile`
— they are *computed*, not real EMF slots you can listen to or write into directly.

This has a direct, practical consequence for consistency preservation (see
`consistency_preservation_SysML-Simulink.md` §6): a Vitruv reaction cannot listen on
`"after element inserted in PartUsage[nestedPart]"` the way the AMALTHEA/ASEM project listened on
`"after element inserted in model::System[components]"`, because `nestedPart` is derived. Reactions
must instead listen on the real, non-derived features: the `declaredName` attribute (not the
derived `name`) and `ownedRelatedElement`/`Feature.direction`.

For the SysML ↔ Simulink case study, the relevant sub-model is the **technical architecture /
interface layer**: `PartUsage`/`PartDefinition` (structural blocks), `PortUsage`/`PortDefinition`
(interaction points), `FlowUsage`/`FlowDefinition` (signal/item flow), and, for the requirement–
function–architecture traceability cascade described in Grycz et al. (KIT/SFB 1608, "Convide"
brake-system research platform), `RequirementUsage`/`RequirementDefinition` and
`ActionUsage`/`ActionDefinition`.

## 2. Scope: Classes Relevant to Simulink Mapping

| SysML Class | Key Supertypes | Role in Mapping |
|---|---|---|
| `PartUsage` | `ItemUsage → OccurrenceUsage → Usage → Feature → Type → Namespace → Element` | Maps to Simulink `Block` / `SubSystem` (Rule A) |
| `PartDefinition` | `ItemDefinition → OccurrenceDefinition → Definition/Class` | Type of a `PartUsage`; no direct Simulink counterpart (Simulink has no definition/usage split) |
| `PortUsage` | `OccurrenceUsage → Usage → Feature → ...` | Maps to Simulink `InPort` / `OutPort`, disambiguated by `direction` (Rule B) |
| `PortDefinition` | `OccurrenceDefinition → Structure → Class` | Type of a `PortUsage` |
| `FlowUsage` | `ConnectorAsUsage, ActionUsage, Flow → Connector, Step` | Maps to Simulink `SingleConnection` (Rule C) |
| `FlowDefinition` | `ActionDefinition, Interaction → Association, Behavior` | Type of a `FlowUsage` |
| `AttributeUsage` | `Usage → Feature → ...` | Maps to Simulink `Parameter` (name/type only; no cross-model value sync) |
| `ActionUsage` | `OccurrenceUsage, Step → Feature` | Represents a "function"; created as part of the traceability cascade (Rule D) |
| `RequirementUsage` | `ConstraintUsage, BooleanExpression → OccurrenceUsage` | Represents a "requirement"; created as part of the traceability cascade (Rule D) |
| `Package` | `Namespace → Element` | VSUM root anchor on the SysML side |

## 3. Element — the KerML root

Every SysML class ultimately inherits from `Element`. Two facts drive every reaction in this
project:

| Attribute / Reference | Type | Derived? | Description |
|---|---|---|---|
| `declaredName` | `EString` | **No** — real, settable | The name a modeler actually types in. |
| `name` | `EString` | **Yes** (`effectiveName()`, defaults to `declaredName`) | What you'd naively expect to bind a rename-reaction to — but it is computed, so Vitruv cannot observe changes to it directly. **Reactions must listen on `declaredName`.** |
| `elementId` | `EString`, `lowerBound=1` | No | Tooling-assigned globally unique id. |
| `ownedRelationship` | `Relationship`, containment | No | The only real containment edge out of an `Element`; every "owned member" is reached by walking through a `Relationship` (typically `OwningMembership`/`FeatureMembership`), not directly. |
| `owningRelationship` | `Relationship` | No (opposite of above) | Used to walk *up* from a leaf `Element` to find its logical container, since there is no direct `eContainer`-style "owning PartUsage" reference. |
| `qualifiedName` | `EString` | Yes | Derived from `owningNamespace.qualifiedName + '::' + escapedName()`. |

## 4. Namespace / Type / Feature / Usage (KerML base classes)

| Class | Adds | Notes |
|---|---|---|
| `Namespace` | `member`, `membership`, `ownedMember` (all derived) | A container of `Membership` relationships, not a container of `Element`s directly. |
| `Type` | `feature`, `ownedFeature` (derived) | Base for anything that can own typed features. |
| `Feature` | **`direction`** (`FeatureDirectionKind`: `in` / `out` / `inout`) — real, non-derived attribute | This is the one non-derived, directly reactable attribute that distinguishes an input from an output. Used by Rule B to disambiguate `PortUsage` → `InPort`/`OutPort`. |
| `Usage` | `nestedPart`, `nestedPort`, `nestedAttribute`, `nestedAction`, ... (all derived filters over `nestedUsage`/`ownedFeature`) | Convenience read-only views; cannot be inserted into directly. |

## 5. PartUsage / PartDefinition

`PartUsage` (`itemDefinition->selectByKind(PartDefinition)->notEmpty()`) represents "a system or a
part of a system" — the SysML equivalent of a Simulink `Block`. A `PartUsage` may itself own nested
`PartUsage`s (`Usage.nestedPart`, derived from `ownedFeature`), directly analogous to a Simulink
`SubSystem` owning `subBlocks`.

| Attribute / Reference | Type | Derived? | Description |
|---|---|---|---|
| `declaredName` (inherited) | `EString` | No | Propagated to the corresponding Simulink element's name. |
| `partDefinition` | `PartDefinition[*]` | Yes | The `PartDefinition`(s) typing this usage. |
| `nestedPart` | `PartUsage[*]` | Yes (`nestedUsage->selectByKind(PartUsage)`) | If non-empty, the corresponding Simulink element must be a `SubSystem`, not a plain `Block` (Rule A / S1). |
| `isComposite` (from `Usage`) | `EBoolean` | No | Whether this part is owned (contained) vs. referenced. |

`PartDefinition` is the `Classifier` a `PartUsage` is typed by; it has no Simulink counterpart —
Simulink does not separate "definition" from "usage" the way SysML does (a `Block` combines both
roles).

## 6. PortUsage / PortDefinition

A `PortUsage` (`OccurrenceUsage`) is "a point at which external entities can connect to and
interact with a system." Unlike `PartUsage`, direction is what disambiguates the Simulink target:

| Attribute / Reference | Type | Derived? | Description |
|---|---|---|---|
| `declaredName` (inherited) | `EString` | No | Propagated to the corresponding Simulink `Port`'s name. |
| `direction` (inherited from `Feature`) | `FeatureDirectionKind` (`in`/`out`/`inout`) | **No** | `in` → Simulink `InPort`; `out` → Simulink `OutPort`; `inout` has **no Simulink equivalent** — Simulink ports are strictly one-directional (see consistency doc, Rule B note). |
| `portDefinition` | `PortDefinition[*]` | Yes | The `PortDefinition`(s) typing this usage. |

## 7. FlowUsage / FlowDefinition

A `FlowUsage` is both a KerML `Flow` (a kind of `Connector`) and an `ActionUsage` — it represents an
item/signal transfer between two `flowEnd`s (`Usage.flowEnd`, redefining `Association.associationEnd`).
The OCL invariant `flowEnd->size() <= 2` means a `FlowUsage` connects at most two features, which
maps naturally onto Simulink's `SingleConnection` (exactly one `OutPort` → one `InPort`).

| Attribute / Reference | Type | Derived? | Description |
|---|---|---|---|
| `flowEnd` | `Usage[0..2]` | Yes | The two connected features — nominally two `PortUsage`s, one `out` and one `in`. |
| `flowDefinition` | `Interaction[*]` | Yes | The `FlowDefinition` typing this usage. |

Simulink's `MultiConnection` (one `OutPort` fanning out to many `InPort`s via nested
`SingleConnection`s) has **no single-`FlowUsage` equivalent** — it would require several
`FlowUsage`s sharing the same source `flowEnd`. This asymmetry is intentionally left unimplemented
(see consistency doc §6, note on E13/E14).

## 8. AttributeUsage / AttributeDefinition

| Attribute / Reference | Type | Derived? | Description |
|---|---|---|---|
| `declaredName` (inherited) | `EString` | No | Propagated to the corresponding Simulink `Parameter.name`. |
| `attributeDefinition` | `DataType[*]` | Yes | The `AttributeDefinition`/`DataType` typing this usage; its name is propagated (as a string) to `Parameter.type`. |

Note: an `AttributeUsage`'s *value* is expressed via a nested `FeatureValue`/`LiteralExpression`
subtree several relationship-hops away, not a direct attribute. Value propagation is out of scope
for this project, mirroring the AMALTHEA/ASEM project's decision not to propagate `Constant`/
`Message` values either.

## 9. ActionUsage / RequirementUsage (traceability cascade classes)

These two classes are not mapped 1:1 to any Simulink class. They exist purely on the SysML side and
are auto-generated, together with a `PartUsage`, whenever a Simulink `Block`/`SubSystem` is
discovered without a corresponding SysML architecture element — mirroring the exact scenario
described in Grycz et al.: *"when a new technical architecture block is introduced [from the
Simulink side], a corresponding requirement, function ... are created"* (paraphrased; see
`consistency_preservation_SysML-Simulink.md` Rule D).

| Class | Key Supertypes | Used In |
|---|---|---|
| `ActionUsage` | `OccurrenceUsage, Step → Feature` | Rule D (as a stand-in for "function") |
| `RequirementUsage` | `ConstraintUsage, BooleanExpression` | Rule D (as a stand-in for "requirement") |

The formal `Satisfy`/`Perform`/`Allocate` relationships that would properly wire
Requirement → Function → Architecture together (`SatisfyRequirementUsage`, `PerformActionUsage`,
`AllocationUsage`) are **not** created by the cascade in this project — see the omission note under
Rule D in the consistency doc.

## 10. Package

`Package` (`Namespace`) is the SysML-side VSUM root anchor, analogous to AMALTHEA's
`ComponentsModel` or ASEM's `Dummy` root. Top-level `PartUsage`s owned (via the generic
`Membership` mechanism) by a `Package` correspond to root-level `Block`s contained directly in a
Simulink `SimulinkModel.contains`.

## 11. Inheritance Summary (Classes Relevant to Mapping)

| Class | Key Supertypes | Used in Rule(s) |
|---|---|---|
| `PartUsage` | `ItemUsage → OccurrenceUsage → Usage → Feature → Type → Namespace → Element` | Rule A |
| `PartDefinition` | `ItemDefinition → OccurrenceDefinition → Definition, Class` | Rule A (type-side, no direct mapping) |
| `PortUsage` | `OccurrenceUsage → Usage → Feature → ...` | Rule B |
| `FlowUsage` | `ConnectorAsUsage, ActionUsage, Flow` | Rule C |
| `AttributeUsage` | `Usage → Feature → ...` | (documented, not implemented as a reaction) |
| `ActionUsage` | `OccurrenceUsage, Step` | Rule D |
| `RequirementUsage` | `ConstraintUsage, BooleanExpression` | Rule D |
| `Package` | `Namespace → Element` | VSUM root anchor |
