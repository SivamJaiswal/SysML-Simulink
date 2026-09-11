package tools.vitruv.sysmlsimulink.vsum;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.omg.sysml.lang.sysml.FeatureDirectionKind;

import hu.bme.mit.massif.simulink.Block;
import hu.bme.mit.massif.simulink.InPort;
import hu.bme.mit.massif.simulink.OutPort;
import hu.bme.mit.massif.simulink.SingleConnection;

import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

/**
 * Exercises consistency_preservation_SysML-Simulink.md's Rules A-C in the
 * SysML -> Simulink propagation direction, i.e. reactions defined in
 * consistency/SysMLToSimulink.reactions. Structured after the reference
 * AMALTHEA <-> ASEM project's AmaltheaToAsemTest.java: one @Test per rule ID,
 * assertions compare model elements by name (never by object reference — see
 * VSUMRunner's correspondence-lookup note).
 */
public class SysMLToSimulinkTest {

	private final VSUMRunner util = new VSUMRunner();

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
	}

	@Test
	@DisplayName("E1 - childless PartUsage created -> Block with same name exists in Simulink view")
	void e1_partUsageCreated_blockCreated(@TempDir Path tempDir) throws Exception {
		InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		util.addPartUsage(vsum, "BrakeCaliper");

		Block block = util.getCorrespondingInSimulink(vsum, "BrakeCaliper", Block.class);
		assertNotNull(block, "Block must be created for the new PartUsage");
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
}
