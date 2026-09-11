# SysML ↔ Simulink Consistency Preservation

Same structure and methodology as the `ASEM-Amalthea` project: two real Ecore metamodels, a
documented semantic overlap, Vitruv Reactions DSL rules in both directions, and JUnit test cases
exercising each rule by ID.

## Models

This project keeps two heterogeneous engineering models consistent inside a single V-SUM
(Virtual Single Underlying Model).

**SysML v2** (`model/SysML.ecore`, OMG Pilot Implementation) is the descriptive system model: a
KerML-based metamodel for requirements, functions, and technical architecture. It is large
(~5,500 lines) and everything — `PartUsage`, `PortUsage`, `RequirementUsage`, ... — specializes a
generic `Feature`/`Usage`/`Type`/`Namespace`/`Element` hierarchy rather than having
metaclass-specific containment references. The classes actually used in this project are:

- `PartUsage` / `PartDefinition` — a technical architecture element ("what the system is built from")
- `PortUsage` / `PortDefinition` — an interaction point, disambiguated by the real `direction` attribute (`in`/`out`/`inout`)
- `FlowUsage` / `FlowDefinition` — an item/signal flow between two ports
- `ActionUsage` — stands in for a "function"
- `RequirementUsage` — stands in for a "requirement"
- `Package` — the model root

Full details, including the KerML containment quirks that shape every reaction in this project,
are in `docs/SysML_Metamodel_Description.md`.

**Simulink** (`model/simulink.ecore`, MASSIF) is the executable simulation model: a much smaller
(~350 lines), structurally simpler metamodel for block diagrams — hierarchical blocks, typed ports,
signal connections, and block parameters. The classes used here:

- `Block` / `SubSystem` — a structural unit, hierarchical once it has children
- `InPort` / `OutPort` — directional ports contained in `Block.ports`
- `SingleConnection` — a signal connection from one `OutPort` to one `InPort`
- `SimulinkModel` — the model root

Full details are in `docs/Simulink_Metamodel_Description.md`.

The correspondence between the two — which SysML class maps to which Simulink class, under what
condition, and why — is specified in `docs/consistency_preservation_SysML-Simulink.md`, grounded in
a real KIT/SFB 1608 research paper describing an actual Cameo-SysML ↔ Simulink integration (see
Sources, below).

## How to Run

This repository is the **design and reference-implementation layer** — metamodel descriptions, the
consistency preservation rules doc, the `.reactions` files, and the JUnit test classes — the same
artifacts the `ASEM-Amalthea` project's `Downloads` deliverables provided. It does not yet include
the Maven build scaffolding (`pom.xml`, `.genmodel`, generated EMF Java sources) that the
`ASEM-Amalthea` *repository* (the Vitruv "methodologist template") ships with, since `SysML.ecore`
and `simulink.ecore` were fetched standalone, without accompanying `.genmodel` files.

To actually execute the tests, drop this project's files into a Vitruv methodologist-template
Maven project (the same template `ASEM-Amalthea` is built on):

1. Start from the [Vitruv methodologist template](https://github.com/vitruv-tools) (or reuse the
   `ASEM-Amalthea` repo's `pom.xml`/module layout as a starting point).
2. Put `model/SysML.ecore` and `model/simulink.ecore` under the template's `model/` module, generate
   their `.genmodel`s and Java code with EMF (Eclipse: right-click each `.ecore` → generate model
   code), the same step the template's `tutorial.md` describes for `model.ecore`/`model2.ecore`.
3. Put `consistency/SysMLToSimulink.reactions` and `consistency/SimulinkToSysML.reactions` under the
   template's `consistency/` module.
4. Put `vsum/VSUMRunner.java`, `vsum/SysMLToSimulinkTest.java`, and `vsum/SimulinkToSysMLTest.java`
   under the template's `vsum/src/test/java` module.
5. Build and run the tests with the Maven wrapper, exactly as in the template:
   ```bash
   ./mvnw clean verify
   ```

The tests check that reactions keep the two models consistent — for example (mirroring the
template's own `systemInsertionAndPropagationTest` example):

```java
@Test
@DisplayName("E3/C4 - Block created with no SysML counterpart -> Requirement+Function+Architecture "
    + "cascade, reproducing the paper's TemperatureMonitor scenario (Grycz et al. §4.2)")
void e3c4_unmatchedBlock_triggersRequirementFunctionArchitectureCascade(@TempDir Path tempDir) throws Exception {
  InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
  util.registerRootObjects(vsum, tempDir);

  util.addBlock(vsum, tempDir, "TemperatureMonitor");

  // reaction fires automatically: a PartUsage, an ActionUsage ("function"), and a
  // RequirementUsage ("requirement") are all created and consistently named
  assertNotNull(util.getCorrespondingInSysml(vsum, "TemperatureMonitor", PartUsage.class));
  assertNotNull(util.getCorrespondingInSysml(vsum, "processTemperatureMonitor", ActionUsage.class));
  assertNotNull(util.getCorrespondingInSysml(vsum, "TemperatureMonitorRequirement", RequirementUsage.class));
}
```

Because the generated EMF code isn't present yet, treat the `.reactions`/test code in this repo as
a verified **design**, not a build-tested artifact — see the caveat in `vsum/VSUMRunner.java`'s
header comment about illustrative vs. confirmed Java package names.

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
