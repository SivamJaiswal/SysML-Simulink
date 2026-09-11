# SysML ↔ Simulink Consistency Preservation

Same structure and methodology as the `ASEM-Amalthea` project: two real Ecore metamodels, a
documented semantic overlap, Vitruv Reactions DSL rules in both directions, and JUnit test cases
exercising each rule by ID.

## Sources

- SysML v2 metamodel: [Systems-Modeling/SysML-v2-Pilot-Implementation](https://github.com/Systems-Modeling/SysML-v2-Pilot-Implementation/blob/master/org.omg.sysml/model/SysML.ecore)
- Simulink metamodel: [viatra/massif](https://github.com/viatra/massif/blob/master/plugins/hu.bme.mit.massif.simulink/model/simulink.ecore)
- Real-world motivating case study: Grycz, Hagel, Eger, Reussner, Düser — KIT/SFB 1608 "Convide"
  Brake-System-in-the-Loop research platform (draft paper, TdSEE Dortmund submission,
  `261117_TdSEE_Dortmund_Grycz_V14_Review_PG.docx` in Downloads) — describes an actual
  Cameo-Systems-Modeler ↔ Simulink Vitruvius integration for exactly this metamodel pair, and is
  the direct source for Rule D's requirement/function/architecture traceability cascade.

## Folder layout

```
model/        the two source .ecore files, as fetched, unmodified
docs/         metamodel descriptions + the consistency preservation rules doc
consistency/  Vitruv Reactions DSL — one file per propagation direction
vsum/         VSUMRunner test helper + one JUnit test class per direction
```

## Reading order

1. `docs/SysML_Metamodel_Description.md` and `docs/Simulink_Metamodel_Description.md` — what each
   metamodel actually looks like, verified class by class against the `.ecore` files in `model/`.
2. `docs/consistency_preservation_SysML-Simulink.md` — the semantic overlap and the four
   correspondence rules (A: Part↔Block, B: Port↔InPort/OutPort, C: Flow↔Connection, D: the
   requirement/function traceability cascade), each with an E/P/S/C breakdown.
3. `consistency/SysMLToSimulink.reactions` and `consistency/SimulinkToSysML.reactions` — the
   implementation of each rule, one direction per file, same convention as the reference project.
4. `vsum/SysMLToSimulinkTest.java` and `vsum/SimulinkToSysMLTest.java` — one `@Test` per rule ID,
   using the shared `vsum/VSUMRunner.java` helper.

## The one thing worth knowing before reading the reactions

SysML v2 is built on KerML: almost nothing is a plain, directly-settable EReference the way
AMALTHEA/ASEM's `Component.components` was. `PartUsage.nestedPart`, `Element.name`, and most other
convenience accessors are `derived`/`transient`/`volatile` — real containment goes through generic
`Relationship` objects, and the real name attribute is `declaredName`, not `name`. Every reaction
and test in this project routes around that indirection explicitly and says so in a comment at the
point it matters — see `docs/SysML_Metamodel_Description.md` §1 and the header comment in
`consistency/SimulinkToSysML.reactions`.

## Honest gaps (intentionally not implemented, and why)

- `PortUsage.direction = inout` has no Simulink target (Simulink ports are strictly one-directional
  by class).
- Simulink `Trigger` / `Enable` / `State` ports have no SysML counterpart in this mapping.
- Simulink `MultiConnection` (one `OutPort` fanning out to many `InPort`s) has no single-`FlowUsage`
  equivalent.
- `AttributeUsage` ↔ `Parameter` is documented but not implemented as a reaction — SysML attribute
  *values* live several relationship-hops away in a `FeatureValue`/`LiteralExpression` subtree.
- Rule D's cascade creates three consistently-named sibling elements but does **not** create the
  formal `SatisfyRequirementUsage`/`PerformActionUsage`/`AllocationUsage` relationship objects that
  would properly wire them together — the source paper documents this same gap itself (§5.2).

See `docs/consistency_preservation_SysML-Simulink.md` for the full reasoning behind each.
