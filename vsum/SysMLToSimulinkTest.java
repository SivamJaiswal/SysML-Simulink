package tools.vitruv.sysmlsimulink.vsum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.PortUsage;
import org.omg.sysml.lang.sysml.FlowUsage;
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
 * SysML -> Simulink propagation direction, i.e. reactions defined in
 * consistency/SysMLToSimulink.reactions. One @Test per rule ID, following the
 * exact convention of the reference AMALTHEA <-> ASEM project's
 * AmaltheaToAsemTest.java: @DisplayName carries the rule ID and a plain-language
 * description, method names are lower-case rule-id_description, assertions
 * compare model elements by name rather than by object reference (see
 * VSUMRunner's correspondence-lookup note — every lookup call builds a fresh
 * view), and every "creation" test is paired with a "deletion" and, where a
 * property exists, a "renamed" test.
 */
public class SysMLToSimulinkTest {

	private final VSUMRunner util = new VSUMRunner();

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
	}

	// ────────────────────────────────────────────────────────────────────────
	// Rule A — PartUsage <-> Block / SubSystem
	// ────────────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("E1 - childless PartUsage created -> Block with same name exists in Simulink view")
	void e1_partUsageCreated_blockCreated(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "BrakeCaliper");

		Block block = util.getCorrespondingInSimulink(vsum, "BrakeCaliper", Block.class);
		assertNotNull(block, "Block must be created for the new PartUsage");
		assertFalse(block instanceof SubSystem, "a childless PartUsage must map to a plain Block, not a SubSystem");
	}

	@Test
	@DisplayName("E2 - PartUsage deleted -> Block is removed from Simulink view")
	void e2_partUsageDeleted_blockRemoved(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "OilUnit");
		assertNotNull(util.getCorrespondingInSimulink(vsum, "OilUnit", Block.class));

		util.deleteFromSysml(vsum, "OilUnit", PartUsage.class);

		assertNull(util.getCorrespondingInSimulink(vsum, "OilUnit", Block.class),
				"Block must be removed after PartUsage is deleted");
	}

	@Test
	@DisplayName("P1 - PartUsage renamed -> Block.simulinkRef.name updated")
	void p1_partUsageRenamed_blockNameUpdated(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "OldName");
		util.renameInSysml(vsum, "OldName", PartUsage.class, "NewName");

		assertNull(util.getCorrespondingInSimulink(vsum, "OldName", Block.class), "old Block name must not exist");
		assertNotNull(util.getCorrespondingInSimulink(vsum, "NewName", Block.class), "Block with new name must exist");
	}

	@Test
	@DisplayName("Bidirectional - PartUsage/Block names stay in sync after alternating renames")
	void bidirectional_partUsageBlock_alternatingRenames(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "Start");
		assertNotNull(util.getCorrespondingInSimulink(vsum, "Start", Block.class));

		util.renameInSysml(vsum, "Start", PartUsage.class, "Middle");
		assertNotNull(util.getCorrespondingInSimulink(vsum, "Middle", Block.class),
				"Block must follow PartUsage rename");

		util.renameInSimulink(vsum, "Middle", Block.class, "End");
		assertNotNull(util.getCorrespondingInSysml(vsum, "End", PartUsage.class),
				"PartUsage must follow Block rename");
	}

	@Test
	@DisplayName("S1 - PartUsage gains its first nestedPart -> corresponding Block becomes a SubSystem")
	void s1_partUsageGainsFirstChild_blockBecomesSubSystem(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "Sensor");
		assertFalse(util.getCorrespondingInSimulink(vsum, "Sensor", Block.class) instanceof SubSystem,
				"still childless -> still a plain Block");

		util.addNestedPartUsage(vsum, "Sensor", "SensorElement");

		Block converted = util.getCorrespondingInSimulink(vsum, "Sensor", Block.class);
		assertInstanceOf(SubSystem.class, converted, "gaining a first child must upgrade Block to SubSystem");
		assertEquals(1, ((SubSystem) converted).getSubBlocks().size());
		assertNotNull(util.getCorrespondingInSimulink(vsum, "SensorElement", Block.class),
				"the child PartUsage must still get its own Block");
	}

	@Test
	@DisplayName("S1 - existing ports survive the Block -> SubSystem conversion")
	void s1_existingPortsSurviveConversion(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "Controller");
		util.addPortUsage(vsum, "Controller", "statusOut", FeatureDirectionKind.OUT);
		assertNotNull(util.getCorrespondingInSimulink(vsum, "statusOut", OutPort.class));

		util.addNestedPartUsage(vsum, "Controller", "InnerLogic");

		Block converted = util.getCorrespondingInSimulink(vsum, "Controller", Block.class);
		assertInstanceOf(SubSystem.class, converted);
		assertTrue(converted.getPorts().stream().anyMatch(p -> p instanceof OutPort),
				"the port that existed before conversion must be moved onto the new SubSystem, not lost");
	}

	@Test
	@DisplayName("S2 - PartUsage loses its last nestedPart -> corresponding SubSystem reverts to a plain Block")
	void s2_partUsageLosesLastChild_subSystemBecomesBlock(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "Housing");
		util.addNestedPartUsage(vsum, "Housing", "OnlyChild");
		assertInstanceOf(SubSystem.class, util.getCorrespondingInSimulink(vsum, "Housing", Block.class));

		util.deleteFromSysml(vsum, "OnlyChild", PartUsage.class);

		Block afterRemoval = util.getCorrespondingInSimulink(vsum, "Housing", Block.class);
		assertNotNull(afterRemoval, "Housing must still have a corresponding element");
		assertFalse(afterRemoval instanceof SubSystem, "losing the last child must downgrade SubSystem back to Block");
	}

	// ────────────────────────────────────────────────────────────────────────
	// Rule B — PortUsage <-> InPort / OutPort
	// ────────────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("E5 - PortUsage(direction=in) created -> InPort added to Block.ports")
	void e5_inputPortUsageCreated_inPortCreated(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "TemperatureMonitor");
		util.addPortUsage(vsum, "TemperatureMonitor", "measuredTemp", FeatureDirectionKind.IN);

		InPort port = util.getCorrespondingInSimulink(vsum, "measuredTemp", InPort.class);
		assertNotNull(port, "InPort must be created for direction=in PortUsage");
	}

	@Test
	@DisplayName("E6 - PortUsage(direction=out) created -> OutPort added to Block.ports")
	void e6_outputPortUsageCreated_outPortCreated(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "TempSensor");
		util.addPortUsage(vsum, "TempSensor", "tempSignal", FeatureDirectionKind.OUT);

		OutPort port = util.getCorrespondingInSimulink(vsum, "tempSignal", OutPort.class);
		assertNotNull(port, "OutPort must be created for direction=out PortUsage");
	}

	@Test
	@DisplayName("Rule B note - PortUsage(direction=inout) has no Simulink counterpart")
	void ruleBNote_inoutPortUsage_notPropagated(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "Transceiver");
		util.addPortUsage(vsum, "Transceiver", "bus", FeatureDirectionKind.INOUT);

		assertNull(util.getCorrespondingInSimulink(vsum, "bus", InPort.class));
		assertNull(util.getCorrespondingInSimulink(vsum, "bus", OutPort.class));
	}

	@Test
	@DisplayName("E7 - PortUsage deleted -> corresponding Port removed")
	void e7_portUsageDeleted_portRemoved(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "Gearbox");
		util.addPortUsage(vsum, "Gearbox", "shiftSignal", FeatureDirectionKind.OUT);
		assertNotNull(util.getCorrespondingInSimulink(vsum, "shiftSignal", OutPort.class));

		util.deleteFromSysml(vsum, "shiftSignal", PortUsage.class);

		assertNull(util.getCorrespondingInSimulink(vsum, "shiftSignal", OutPort.class), "Port must be removed");
	}

	@Test
	@DisplayName("P3 - PortUsage renamed -> Port's simulinkRef.name updated")
	void p3_portUsageRenamed_portNameUpdated(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "ABS");
		util.addPortUsage(vsum, "ABS", "oldSignal", FeatureDirectionKind.OUT);
		util.renameInSysml(vsum, "oldSignal", PortUsage.class, "newSignal");

		assertNull(util.getCorrespondingInSimulink(vsum, "oldSignal", OutPort.class));
		assertNotNull(util.getCorrespondingInSimulink(vsum, "newSignal", OutPort.class));
	}

	@Test
	@DisplayName("C2 - every direction-typed PortUsage on a part has exactly one, correctly-typed corresponding Port")
	void c2_portDirectionCompletenessAcrossMultiplePorts(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "ECU");
		util.addPortUsage(vsum, "ECU", "in1", FeatureDirectionKind.IN);
		util.addPortUsage(vsum, "ECU", "in2", FeatureDirectionKind.IN);
		util.addPortUsage(vsum, "ECU", "out1", FeatureDirectionKind.OUT);

		assertNotNull(util.getCorrespondingInSimulink(vsum, "in1", InPort.class));
		assertNotNull(util.getCorrespondingInSimulink(vsum, "in2", InPort.class));
		assertNotNull(util.getCorrespondingInSimulink(vsum, "out1", OutPort.class));
		assertNull(util.getCorrespondingInSimulink(vsum, "in1", OutPort.class), "in1 must not also appear as an OutPort");
		assertNull(util.getCorrespondingInSimulink(vsum, "out1", InPort.class), "out1 must not also appear as an InPort");
	}

	// ────────────────────────────────────────────────────────────────────────
	// Rule C — FlowUsage <-> SingleConnection
	// ────────────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("E11 - 2-ended FlowUsage created -> SingleConnection linking the corresponding OutPort/InPort")
	void e11_flowUsageCreated_singleConnectionCreated(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "Sender");
		util.addPortUsage(vsum, "Sender", "out1", FeatureDirectionKind.OUT);
		util.addPartUsage(vsum, "Receiver");
		util.addPortUsage(vsum, "Receiver", "in1", FeatureDirectionKind.IN);

		util.addFlowUsage(vsum, "out1", "in1");

		OutPort outPort = util.getCorrespondingInSimulink(vsum, "out1", OutPort.class);
		assertNotNull(outPort, "OutPort must exist before the connection can be asserted");
		assertNotNull(outPort.getConnection(), "SingleConnection must be created for the FlowUsage");
		assertEquals("in1", outPort.getConnection().getTo().getSimulinkRef().getName(),
				"the connection must target the corresponding InPort");
	}

	@Test
	@DisplayName("E12 - FlowUsage deleted -> SingleConnection removed")
	void e12_flowUsageDeleted_connectionRemoved(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "Sender2");
		util.addPortUsage(vsum, "Sender2", "outA", FeatureDirectionKind.OUT);
		util.addPartUsage(vsum, "Receiver2");
		util.addPortUsage(vsum, "Receiver2", "inA", FeatureDirectionKind.IN);
		util.addFlowUsage(vsum, "outA", "inA");

		util.deleteFromSysml(vsum, "outA->inA", FlowUsage.class);

		OutPort outPort = util.getCorrespondingInSimulink(vsum, "outA", OutPort.class);
		assertNull(outPort.getConnection(), "SingleConnection must be removed when the FlowUsage is deleted");
	}

	// ────────────────────────────────────────────────────────────────────────
	// Rule D — the requirement/function cascade is one-directional
	// (Simulink -> SysML only); creating a PartUsage directly from the SysML
	// side must NOT trigger it.
	// ────────────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("Rule D note - PartUsage created directly on the SysML side does NOT trigger the "
			+ "requirement/function cascade (cascade is Simulink -> SysML only)")
	void ruleDNote_partUsageCreatedDirectly_doesNotTriggerCascade(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "DirectlyModeledPart");

		assertNotNull(util.getCorrespondingInSimulink(vsum, "DirectlyModeledPart", Block.class));
		assertNull(util.getCorrespondingInSysml(vsum, "processDirectlyModeledPart", ActionUsage.class),
				"no cascade function must appear for a part created on the SysML side");
		assertNull(util.getCorrespondingInSysml(vsum, "DirectlyModeledPartRequirement", RequirementUsage.class),
				"no cascade requirement must appear for a part created on the SysML side");
	}
}
