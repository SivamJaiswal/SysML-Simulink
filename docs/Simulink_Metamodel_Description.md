# Simulink Metamodel Description

MASSIF Simulink Ecore — `hu.bme.mit.massif.simulink/model/simulink.ecore`
URI: `http://hu.bme.mit.massif/simulink/1.0`  |  Source: [viatra/massif](https://github.com/viatra/massif)

## 1. Overview

This Ecore metamodel represents the core structural concepts of MATLAB Simulink models: block
diagrams with hierarchical decomposition, typed ports, signal connections, and block parameters.
Per its own root-level documentation, it has "a strong focus on the structure and less focus on
the behavior, simulation and layout specific details."

Unlike SysML v2, Simulink's containment is almost entirely **direct, non-derived EReferences** —
`Block.ports`, `Block.parameters`, `SubSystem.subBlocks` are all plain, settable, `containment=true`
features. This makes the Simulink side of the consistency rules structurally much simpler than the
SysML side (see `SysML_Metamodel_Description.md` §1). The one exception, described below, is
`SimulinkElement.name` itself.

## 2. Scope: Classes Relevant to SysML Mapping

| Simulink Class | Location | Role in Mapping |
|---|---|---|
| `Block` | `SimulinkModel.contains` / `SubSystem.subBlocks` | Maps to SysML `PartUsage` (Rule A) |
| `SubSystem` | extends `Block` | Maps to SysML `PartUsage` with `nestedPart` (Rule A) |
| `InPort` / `OutPort` | `Block.ports` | Map to SysML `PortUsage` with `direction = in` / `out` (Rule B) |
| `SingleConnection` | `OutPort.connection` (containment) | Maps to SysML `FlowUsage` (Rule C) |
| `Parameter` | `Block.parameters` / `Port.parameters` | Maps to SysML `AttributeUsage` (documented, name/type only) |
| `SimulinkModel` | root | VSUM root anchor, corresponds to SysML `Package` |

## 3. SimulinkElement (abstract root)

| Attribute / Reference | Type | Derived? | Description |
|---|---|---|---|
| `simulinkRef` | `IdentifierReference`, containment | No | Holds the element's real, settable name/qualifier. |
| `name` | `EString` | **Yes** — `changeable=false`, `volatile=true`, `derived=true`, computed via a VIATRA query-based feature (`patternFQN = hu.bme.mit.massif.models.simulink.derived.name`) from `simulinkRef.name` | Cannot be set or listened to directly. **Reactions must operate on `simulinkRef.name`.** This mirrors the SysML side's `name`/`declaredName` split (see SysML doc §3) — both metamodels turn out to compute their "friendly" name from an underlying reference object, for different reasons (VIATRA query-based features here; KerML `effectiveName()` there). |

## 4. Block

Location: `SimulinkModel.contains[]` or `SubSystem.subBlocks[]`. "The basic building block of
Simulink systems." In the SysML ↔ Simulink mapping, a `Block` (without children) or `SubSystem`
(with children) is the top-level structural unit that maps to a SysML `PartUsage`.

| Attribute / Reference | Type | Multiplicity | Description |
|---|---|---|---|
| `simulinkRef.name` (via `SimulinkElement`) | `EString` | 0..1 | Propagated to `PartUsage.declaredName`. |
| `parameters` | `Parameter`, containment | 0..* | Block-level configuration values. Maps to `AttributeUsage` (documented only). |
| `ports` | `Port`, containment | 0..* | Interface ports of the block. Maps to `PortUsage` (Rule B). |
| `parent` | `SubSystem` (`eOpposite` of `subBlocks`) | 0..1 | Real, non-derived opposite reference — used to find the logical container without indirection. |
| `sourceBlockRef` / `sourceBlock` | `LibraryLinkReference` / `Block` (derived) | 0..1 | Link back to a library block template; out of scope for this mapping. |
| `trigger`, `enabler`, `inports`, `outports` | derived, filtered views of `ports` | 0..* | Convenience read-only accessors (VIATRA query-based features), analogous to SysML's `nestedPart`/`nestedPort`. |

## 5. SubSystem (extends Block)

"A Simulink block that may contain subblocks that specify its internal structure and behavior."

| Attribute / Reference | Type | Multiplicity | Description |
|---|---|---|---|
| `subBlocks` | `Block`, containment, `eOpposite = Block.parent` | 0..* | Real, non-derived containment. Maps to `PartUsage.nestedPart` (derived on the SysML side — see Rule A / S1 for the resulting asymmetry). |
| `tag` | `EString` | 0..1 | Free-text subsystem tag; no SysML equivalent. |

## 6. Port (abstract) / InPort / OutPort

| Class | Extends | Key Feature | Description |
|---|---|---|---|
| `Port` (abstract) | `SimulinkElement` | `container` (`eOpposite` of `Block.ports`), `portBlock`, `parameters` | Base type. |
| `InPort` | `Port` | `connection` (`eOpposite` of `SingleConnection.to`) | Maps to SysML `PortUsage` with `direction = in` (Rule B). |
| `OutPort` | `Port` | `connection` (containment, `eOpposite` of `Connection.from`) | Maps to SysML `PortUsage` with `direction = out` (Rule B). |
| `Trigger` | `InPort` | `triggerType`, `statesWhenEnabling` | Specialized control-flow port; **no SysML equivalent** in this mapping (one-directional gap, see consistency doc Rule B note). |
| `Enable` | `InPort` | `statesWhenEnabling` | Same gap as `Trigger`. |
| `State` | `OutPort` | — | Stateport (e.g. for `Integrator` blocks); same gap. |

## 7. Connection (abstract) / SingleConnection / MultiConnection

| Class | Extends | Key Features | Description |
|---|---|---|---|
| `Connection` (abstract) | `SimulinkElement` | `from` (`OutPort`), `lineName` | Base type for signal connections. |
| `SingleConnection` | `Connection` | `to` (`InPort`, `eOpposite` of `InPort.connection`), `parent` (`MultiConnection`) | "A simple connection between a single `OutPort` and a single `InPort`." Maps to SysML `FlowUsage` (Rule C). |
| `MultiConnection` | `Connection` | `connections` (`SingleConnection[]`, containment) | "A connection between a single `OutPort` and multiple `InPort`s." **No single-`FlowUsage` equivalent** — see SysML doc §7. |

## 8. Parameter

Location: `Block.parameters[]` / `Port.parameters[]`. Not a map — a block may have multiple
parameters with the same name.

| Attribute / Reference | Type | Multiplicity | Description |
|---|---|---|---|
| `name` | `EString` | 0..1 | Plain (non-derived) attribute — unlike `SimulinkElement.name`, this one is directly settable. Maps to `AttributeUsage.declaredName`. |
| `type` | `EString` | 0..1 | String-typed; maps loosely to the name of the `AttributeUsage`'s `attributeDefinition`. |
| `value` | `EString` | 0..1 | Free-text value. Not propagated (no reliable SysML-side counterpart — see SysML doc §8). |
| `readOnly` | `EBoolean` | 0..1 | No SysML equivalent. |

## 9. SimulinkModel (root)

"The root of an imported Simulink system that contains blocks." Corresponds to the SysML `Package`
as the VSUM root anchor.

| Attribute / Reference | Type | Multiplicity | Description |
|---|---|---|---|
| `contains` | `Block`, containment | 0..* | Root-level blocks. Corresponds to top-level `PartUsage`s owned by the SysML `Package`. |
| `version`, `file`, `library` | `EString`/`EString`/`EBoolean` | — | Import metadata; no SysML equivalent. |

## 10. Inheritance Summary (Classes Relevant to Mapping)

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
