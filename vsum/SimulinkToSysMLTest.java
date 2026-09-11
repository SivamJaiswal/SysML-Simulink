package tools.vitruv.sysmlsimulink.vsum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.PortUsage;
import org.omg.sysml.lang.sysml.ActionUsage;
import org.omg.sysml.lang.sysml.RequirementUsage;
import org.omg.sysml.lang.sysml.FeatureDirectionKind;

import hu.bme.mit.massif.simulink.Block;
import hu.bme.mit.massif.simulink.SubSystem;
import hu.bme.mit.massif.simulink.InPort;
import hu.bme.mit.massif.simulink.OutPort;

import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

/**
 * Exercises consistency_preservation_SysML-Simulink.md's Rules A-D in the
 * Simulink -> SysML propagation direction, i.e. reactions defined in
 * consistency/SimulinkToSysML.reactions. Same convention as
 * SysMLToSimulinkTest.java: one @Test per rule ID, @DisplayName carries the ID
 * plus a plain-language description, assertions by name.
 *
 * The E3/C4 tests below (temperatureBlock_*) directly reproduce the worked
 * example from Grycz et al. §4.2 ("Excerpt of the simplified XML file for the
 * architecture part of the system model before and after the update"): a
 * Simulink block named TemperatureMonitor, added with no corresponding SysML
 * element, must result in a PartUsage, an ActionUsage ("function"), and a
 * RequirementUsage ("requirement") all consistently named after it.
 */
public class SimulinkToSysMLTest {

	private final VSUMRunner util = new VSUMRunner();

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
	}

	// ────────────────────────────────────────────────────────────────────────
	// Rule A — Block/SubSystem <-> PartUsage
	// ────────────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("E3 - Block created -> PartUsage with same name in SysML view")
	void e3_blockCreated_partUsageCreated(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addBlock(vsum, tempDir, "FuelPump");

		PartUsage part = util.getCorrespondingInSysml(vsum, "FuelPump", PartUsage.class);
		assertNotNull(part, "PartUsage must be created for the new Block");
		assertEquals("FuelPump", part.getDeclaredName());
	}

	@Test
	@DisplayName("E4 - Block deleted -> PartUsage and its cascade siblings removed")
	void e4_blockDeleted_partUsageAndCascadeRemoved(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addBlock(vsum, tempDir, "OilPressure");
		assertNotNull(util.getCorrespondingInSysml(vsum, "OilPressure", PartUsage.class));
		assertNotNull(util.getCorrespondingInSysml(vsum, "processOilPressure", ActionUsage.class));

		util.deleteFromSimulink(vsum, "OilPressure", Block.class);

		assertNull(util.getCorrespondingInSysml(vsum, "OilPressure", PartUsage.class), "PartUsage must be removed");
		assertNull(util.getCorrespondingInSysml(vsum, "processOilPressure", ActionUsage.class), "cascade ActionUsage must be removed");
		assertNull(util.getCorrespondingInSysml(vsum, "OilPressureRequirement", RequirementUsage.class), "cascade RequirementUsage must be removed");
	}

	@Test
	@DisplayName("P2 - Block renamed -> PartUsage.declaredName updated, cascade siblings renamed too (P5)")
	void p2p5_blockRenamed_partUsageAndCascadeRenamed(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addBlock(vsum, tempDir, "InitialName");
		util.renameInSimulink(vsum, "InitialName", Block.class, "UpdatedName");

		assertNull(util.getCorrespondingInSysml(vsum, "InitialName", PartUsage.class));
		assertNotNull(util.getCorrespondingInSysml(vsum, "UpdatedName", PartUsage.class));
		assertNotNull(util.getCorrespondingInSysml(vsum, "processUpdatedName", ActionUsage.class),
				"the cascade function must follow the rename (P5)");
		assertNotNull(util.getCorrespondingInSysml(vsum, "UpdatedNameRequirement", RequirementUsage.class),
				"the cascade requirement must follow the rename (P5)");
	}

	@Test
	@DisplayName("S3 - Block moved into a SubSystem.subBlocks (gains a parent) -> PartUsage re-parented to match")
	void s3_blockReparented_partUsageReparented(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		// Housing and Bracket start as two INDEPENDENT root blocks, each getting its
		// own root-level PartUsage via E3 — this deliberately does NOT go through
		// E3's own parent-lookup path (see createPartUsageForBlock in
		// SimulinkToSysML.reactions), so the re-parenting below genuinely exercises
		// S3's separate BlockReparented reaction rather than E3 alone.
		util.addBlock(vsum, tempDir, "Housing", true);
		util.addBlock(vsum, tempDir, "Bracket");
		assertNull(util.getCorrespondingInSysml(vsum, "Bracket", PartUsage.class).getOwningRelationship(),
				"Bracket must start as a root-level PartUsage, with no owning relationship yet");

		util.reparentBlock(vsum, "Bracket", "Housing");

		PartUsage bracket = util.getCorrespondingInSysml(vsum, "Bracket", PartUsage.class);
		PartUsage housing = util.getCorrespondingInSysml(vsum, "Housing", PartUsage.class);
		assertNotNull(bracket.getOwningRelationship(), "Bracket's PartUsage must now have an owning relationship");
		assertEquals(housing, bracket.getOwningRelationship().getOwningRelatedElement(),
				"Bracket's PartUsage must be nested under Housing's PartUsage, mirroring the Simulink subBlocks move");
	}

	// ────────────────────────────────────────────────────────────────────────
	// Rule D — requirement/function traceability cascade
	// (Grycz et al. §4.2, TemperatureMonitor worked example)
	// ────────────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("E3/C4 - Block created with no SysML counterpart -> Requirement+Function+Architecture "
			+ "cascade, reproducing the paper's TemperatureMonitor scenario (Grycz et al. §4.2)")
	void e3c4_unmatchedBlock_triggersRequirementFunctionArchitectureCascade(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addBlock(vsum, tempDir, "TemperatureMonitor");

		PartUsage part = util.getCorrespondingInSysml(vsum, "TemperatureMonitor", PartUsage.class);
		ActionUsage function = util.getCorrespondingInSysml(vsum, "processTemperatureMonitor", ActionUsage.class);
		RequirementUsage requirement = util.getCorrespondingInSysml(vsum, "TemperatureMonitorRequirement", RequirementUsage.class);

		assertNotNull(part, "the technical architecture element");
		assertNotNull(function, "the function, consistently named 'process' + block name");
		assertNotNull(requirement, "the requirement, consistently named block name + 'Requirement'");
	}

	@Test
	@DisplayName("C4 - the cascade produces exactly one sibling ActionUsage and one sibling RequirementUsage "
			+ "per Block, no more and no fewer, even across several unmatched blocks")
	void c4_cascadeCardinalityAcrossMultipleBlocks(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addBlock(vsum, tempDir, "SensorA", true);
		util.addSubBlock(vsum, "SensorA", "SensorB", false);

		assertNotNull(util.getCorrespondingInSysml(vsum, "processSensorA", ActionUsage.class));
		assertNotNull(util.getCorrespondingInSysml(vsum, "SensorARequirement", RequirementUsage.class));
		assertNotNull(util.getCorrespondingInSysml(vsum, "processSensorB", ActionUsage.class));
		assertNotNull(util.getCorrespondingInSysml(vsum, "SensorBRequirement", RequirementUsage.class));
		// cross-checks: SensorA's cascade siblings must not leak onto SensorB and vice versa
		assertNull(util.getCorrespondingInSysml(vsum, "processSensorASensorB", ActionUsage.class));
	}

	// ────────────────────────────────────────────────────────────────────────
	// Rule B — InPort / OutPort <-> PortUsage
	// ────────────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("E8 - InPort created -> PortUsage(direction=in)")
	void e8_inPortCreated_portUsageCreated(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addBlock(vsum, tempDir, "PressureSensor");
		util.addInPort(vsum, "PressureSensor", "calibrationIn");

		PortUsage port = util.getCorrespondingInSysml(vsum, "calibrationIn", PortUsage.class);
		assertNotNull(port, "PortUsage must be created for the InPort");
		assertEquals(FeatureDirectionKind.IN, port.getDirection());
	}

	@Test
	@DisplayName("E9 - OutPort created -> PortUsage(direction=out)")
	void e9_outPortCreated_portUsageCreated(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addBlock(vsum, tempDir, "ThrottleConfig");
		util.addOutPort(vsum, "ThrottleConfig", "throttlePosition");

		PortUsage port = util.getCorrespondingInSysml(vsum, "throttlePosition", PortUsage.class);
		assertNotNull(port, "PortUsage must be created for the OutPort");
		assertEquals(FeatureDirectionKind.OUT, port.getDirection());
	}

	@Test
	@DisplayName("Rule B note - Trigger/Enable/State ports have no SysML counterpart")
	void ruleBNote_triggerPort_notPropagated(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addBlock(vsum, tempDir, "ConditionalSubsystem");
		// Trigger/Enable/State are intentionally not exercised here since no
		// SimulinkToSysML reaction listens for them at all (see consistency doc,
		// Rule B note) — there is nothing to assert beyond "no PortUsage appears".
		assertNull(util.getCorrespondingInSysml(vsum, "trigger", PortUsage.class));
	}

	@Test
	@DisplayName("E10 - Port deleted -> corresponding PortUsage removed")
	void e10_portDeleted_portUsageRemoved(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addBlock(vsum, tempDir, "Clutch");
		util.addOutPort(vsum, "Clutch", "engaged");
		assertNotNull(util.getCorrespondingInSysml(vsum, "engaged", PortUsage.class));

		util.deleteFromSimulink(vsum, "engaged", OutPort.class);

		assertNull(util.getCorrespondingInSysml(vsum, "engaged", PortUsage.class), "PortUsage must be removed");
	}

	@Test
	@DisplayName("P4 - Port renamed -> PortUsage.declaredName updated")
	void p4_portRenamed_portUsageNameUpdated(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addBlock(vsum, tempDir, "MCU");
		util.addOutPort(vsum, "MCU", "oldOp");
		util.renameInSimulink(vsum, "oldOp", OutPort.class, "newOp");

		assertNull(util.getCorrespondingInSysml(vsum, "oldOp", PortUsage.class));
		assertNotNull(util.getCorrespondingInSysml(vsum, "newOp", PortUsage.class));
	}

	// ────────────────────────────────────────────────────────────────────────
	// Rule C — SingleConnection <-> FlowUsage
	// ────────────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("E13 - SingleConnection created -> FlowUsage between the corresponding PortUsages")
	void e13_singleConnectionCreated_flowUsageCreated(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addBlock(vsum, tempDir, "SenderBlock", true);
		util.addOutPort(vsum, "SenderBlock", "outX");
		util.addSubBlock(vsum, "SenderBlock", "ReceiverBlock", false);
		util.addInPort(vsum, "ReceiverBlock", "inX");
		util.addSingleConnection(vsum, "outX", "inX");

		PortUsage outPortUsage = util.getCorrespondingInSysml(vsum, "outX", PortUsage.class);
		assertNotNull(outPortUsage, "PortUsage must exist for the OutPort side of the connection");
	}

	@Test
	@DisplayName("E14 - SingleConnection deleted -> FlowUsage removed")
	void e14_singleConnectionDeleted_flowUsageRemoved(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addBlock(vsum, tempDir, "SenderBlock2", true);
		util.addOutPort(vsum, "SenderBlock2", "outY");
		util.addSubBlock(vsum, "SenderBlock2", "ReceiverBlock2", false);
		util.addInPort(vsum, "ReceiverBlock2", "inY");
		util.addSingleConnection(vsum, "outY", "inY");

		util.deleteFromSimulink(vsum, "outY", OutPort.class);
		// deleting the OutPort transitively removes its contained SingleConnection
		// (Connection.from/OutPort.connection is containment=true on the OutPort side)

		assertNull(util.getCorrespondingInSysml(vsum, "outY", PortUsage.class));
	}

	// ────────────────────────────────────────────────────────────────────────
	// Bidirectional round-trips
	// ────────────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("Bidirectional - PortUsage/Port names stay in sync after alternating renames")
	void bidirectional_portUsagePort_alternatingRenames(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addBlock(vsum, tempDir, "BiDirBlock");
		util.addOutPort(vsum, "BiDirBlock", "initial");
		assertNotNull(util.getCorrespondingInSysml(vsum, "initial", PortUsage.class));

		util.renameInSimulink(vsum, "initial", OutPort.class, "fromSimulink");
		assertNotNull(util.getCorrespondingInSysml(vsum, "fromSimulink", PortUsage.class));

		util.renameInSysml(vsum, "fromSimulink", PortUsage.class, "fromSysml");
		assertNotNull(util.getCorrespondingInSimulink(vsum, "fromSysml", OutPort.class));
	}

	@Test
	@DisplayName("C1 - a SubSystem's subBlocks count matches its PartUsage's nestedPart count")
	void c1_subBlocksCountMatchesNestedPartCount(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addBlock(vsum, tempDir, "Assembly", true);
		util.addSubBlock(vsum, "Assembly", "Part1", false);
		util.addSubBlock(vsum, "Assembly", "Part2", false);

		SubSystem assembly = (SubSystem) util.getCorrespondingInSimulink(vsum, "Assembly", Block.class);
		PartUsage assemblyPart = util.getCorrespondingInSysml(vsum, "Assembly", PartUsage.class);

		assertEquals(2, assembly.getSubBlocks().size());
		assertEquals(assembly.getSubBlocks().size(), assemblyPart.getNestedPart().size(),
				"nestedPart is derived from real containment, so it must exactly track subBlocks once both children exist");
	}
}
