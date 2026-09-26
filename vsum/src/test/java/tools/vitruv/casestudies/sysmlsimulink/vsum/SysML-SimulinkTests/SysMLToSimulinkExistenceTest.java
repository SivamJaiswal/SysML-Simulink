package tools.vitruv.casestudies.sysmlsimulink.vsum;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

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
import hu.bme.mit.massif.simulink.SingleConnection;
import hu.bme.mit.massif.simulink.Parameter;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

// Tests Existence rules (E1, E2, E5-E7, E11-E12) and the Rule D one-directional asymmetry where changes originate on the SysML side.
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class SysMLToSimulinkExistenceTest {

    VSUMRunner util = new VSUMRunner();

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("*", new XMIResourceFactoryImpl());
    }

    // E1/E2 — PartUsage <-> Block

    @Test
    @DisplayName("E1 – childless PartUsage created → Block with same name exists in Simulink view")
    void e1_partUsageCreated_blockCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addPartUsage(vsum, "BrakeCaliper");

        Block block = util.getCorrespondingInSimulink(vsum, "BrakeCaliper", Block.class);
        assertNotNull(block, "Block must be created for the new PartUsage");
        assertFalse(block instanceof SubSystem, "a childless PartUsage must map to a plain Block, not a SubSystem");
    }

    @Test
    @DisplayName("E2 – PartUsage deleted → Block is removed from Simulink view")
    void e2_partUsageDeleted_blockRemoved(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addPartUsage(vsum, "OilUnit");
        assertNotNull(util.getCorrespondingInSimulink(vsum, "OilUnit", Block.class));

        util.deleteFromSysml(vsum, "OilUnit", PartUsage.class);

        assertNull(util.getCorrespondingInSimulink(vsum, "OilUnit", Block.class),
                "Block must be removed after PartUsage is deleted");
    }

    // E5/E6/E7 — PortUsage <-> InPort/OutPort

    @Test
    @DisplayName("E5 – PortUsage(direction=in) created → InPort added to Block.ports")
    void e5_inputPortUsageCreated_inPortCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addPartUsage(vsum, "TemperatureMonitor");
        util.addPortUsage(vsum, "TemperatureMonitor", "measuredTemp", FeatureDirectionKind.IN);

        InPort port = util.getCorrespondingInSimulink(vsum, "measuredTemp", InPort.class);
        assertNotNull(port, "InPort must be created for direction=in PortUsage");
    }

    @Test
    @DisplayName("E6 – PortUsage(direction=out) created → OutPort added to Block.ports")
    void e6_outputPortUsageCreated_outPortCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addPartUsage(vsum, "TempSensor");
        util.addPortUsage(vsum, "TempSensor", "tempSignal", FeatureDirectionKind.OUT);

        OutPort port = util.getCorrespondingInSimulink(vsum, "tempSignal", OutPort.class);
        assertNotNull(port, "OutPort must be created for direction=out PortUsage");
    }

    // Rule B note — PortUsage(direction=inout) has no deterministic Simulink target, so it's resolved
    // interactively (resolveInoutPortUsage). These tests script the answer via TestUserInteraction so
    // mvn clean verify stays fully automated — see VSUMRunner.getUserInteraction().

    @Test
    @DisplayName("Rule B note – inout PortUsage, user picks \"Map as InPort\" → InPort created")
    void ruleBNote_inoutPortUsage_choiceInPort(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);
        util.addPartUsage(vsum, "Transceiver1");

        util.getUserInteraction().addNextSingleSelection(0);
        util.addPortUsage(vsum, "Transceiver1", "bus1", FeatureDirectionKind.INOUT);

        assertNotNull(util.getCorrespondingInSimulink(vsum, "bus1", InPort.class));
        assertNull(util.getCorrespondingInSimulink(vsum, "bus1", OutPort.class));
    }

    @Test
    @DisplayName("Rule B note – inout PortUsage, user picks \"Map as OutPort\" → OutPort created")
    void ruleBNote_inoutPortUsage_choiceOutPort(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);
        util.addPartUsage(vsum, "Transceiver2");

        util.getUserInteraction().addNextSingleSelection(1);
        util.addPortUsage(vsum, "Transceiver2", "bus2", FeatureDirectionKind.INOUT);

        assertNull(util.getCorrespondingInSimulink(vsum, "bus2", InPort.class));
        assertNotNull(util.getCorrespondingInSimulink(vsum, "bus2", OutPort.class));
    }

    @Test
    @DisplayName("Rule B note – inout PortUsage, user picks \"Create paired InPort + OutPort\" → both created")
    void ruleBNote_inoutPortUsage_choiceBoth(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);
        util.addPartUsage(vsum, "Transceiver3");

        util.getUserInteraction().addNextSingleSelection(2);
        util.addPortUsage(vsum, "Transceiver3", "bus3", FeatureDirectionKind.INOUT);

        assertNotNull(util.getCorrespondingInSimulink(vsum, "bus3", InPort.class));
        assertNotNull(util.getCorrespondingInSimulink(vsum, "bus3", OutPort.class));
    }

    @Test
    @DisplayName("Rule B note – inout PortUsage renamed after \"paired\" choice → both InPort and OutPort renamed")
    void ruleBNote_inoutPortUsagePairedThenRenamed_bothPortsRenamed(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);
        util.addPartUsage(vsum, "Transceiver5");

        util.getUserInteraction().addNextSingleSelection(2);
        util.addPortUsage(vsum, "Transceiver5", "bus5old", FeatureDirectionKind.INOUT);
        assertNotNull(util.getCorrespondingInSimulink(vsum, "bus5old", InPort.class));
        assertNotNull(util.getCorrespondingInSimulink(vsum, "bus5old", OutPort.class));

        util.renameInSysml(vsum, "bus5old", PortUsage.class, "bus5new");

        assertNotNull(util.getCorrespondingInSimulink(vsum, "bus5new", InPort.class), "P3 must rename both correspondences the paired choice created, not just one");
        assertNotNull(util.getCorrespondingInSimulink(vsum, "bus5new", OutPort.class), "P3 must rename both correspondences the paired choice created, not just one");
    }

    @Test
    @DisplayName("Rule B note – inout PortUsage, user picks \"Skip\" → nothing created")
    void ruleBNote_inoutPortUsage_choiceSkip(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);
        util.addPartUsage(vsum, "Transceiver4");

        util.getUserInteraction().addNextSingleSelection(3);
        util.addPortUsage(vsum, "Transceiver4", "bus4", FeatureDirectionKind.INOUT);

        assertNull(util.getCorrespondingInSimulink(vsum, "bus4", InPort.class));
        assertNull(util.getCorrespondingInSimulink(vsum, "bus4", OutPort.class));
    }

    @Test
    @DisplayName("Rule B note – inout PortUsage deleted after \"paired\" choice → both InPort and OutPort removed")
    void ruleBNote_inoutPortUsagePairedThenDeleted_bothPortsRemoved(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);
        util.addPartUsage(vsum, "Transceiver5");

        util.getUserInteraction().addNextSingleSelection(2);
        util.addPortUsage(vsum, "Transceiver5", "bus5", FeatureDirectionKind.INOUT);
        assertNotNull(util.getCorrespondingInSimulink(vsum, "bus5", InPort.class));
        assertNotNull(util.getCorrespondingInSimulink(vsum, "bus5", OutPort.class));

        util.deleteFromSysml(vsum, "bus5", PortUsage.class);

        assertNull(util.getCorrespondingInSimulink(vsum, "bus5", InPort.class), "E7 must clean up both ports the paired choice created, not just one");
        assertNull(util.getCorrespondingInSimulink(vsum, "bus5", OutPort.class), "E7 must clean up both ports the paired choice created, not just one");
    }

    @Test
    @DisplayName("E7 – PortUsage deleted → corresponding Port removed")
    void e7_portUsageDeleted_portRemoved(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addPartUsage(vsum, "Gearbox");
        util.addPortUsage(vsum, "Gearbox", "shiftSignal", FeatureDirectionKind.OUT);
        assertNotNull(util.getCorrespondingInSimulink(vsum, "shiftSignal", OutPort.class));

        util.deleteFromSysml(vsum, "shiftSignal", PortUsage.class);

        assertNull(util.getCorrespondingInSimulink(vsum, "shiftSignal", OutPort.class), "Port must be removed");
    }

    // E11/E12 — FlowUsage <-> SingleConnection

    @Test
    @DisplayName("E11 – 2-ended FlowUsage created → SingleConnection linking the corresponding OutPort/InPort")
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
        assertInstanceOf(SingleConnection.class, outPort.getConnection());
        SingleConnection connection = (SingleConnection) outPort.getConnection();
        assertEquals("in1", connection.getTo().getSimulinkRef().getName(),
                "the connection must target the corresponding InPort");
    }

    @Test
    @DisplayName("E12 – FlowUsage deleted → SingleConnection removed")
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

    // E15/E16 — AttributeUsage <-> Parameter

    @Test
    @DisplayName("E15 – AttributeUsage on a PartUsage created → Parameter added to Block.parameters")
    void e15_partAttributeUsageCreated_blockParameterCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addPartUsage(vsum, "FuelPump");
        util.addPartAttributeUsage(vsum, "FuelPump", "maxPressure");

        Parameter param = util.getCorrespondingInSimulink(vsum, "maxPressure", Parameter.class);
        assertNotNull(param, "Parameter must be created for the new AttributeUsage");
    }

    @Test
    @DisplayName("E15 – AttributeUsage on a PortUsage created → Parameter added to Port.parameters")
    void e15b_portAttributeUsageCreated_portParameterCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addPartUsage(vsum, "PressureSensor");
        util.addPortUsage(vsum, "PressureSensor", "reading", FeatureDirectionKind.OUT);
        util.addPortAttributeUsage(vsum, "reading", "unit");

        Parameter param = util.getCorrespondingInSimulink(vsum, "unit", Parameter.class);
        assertNotNull(param, "Parameter must be created for the AttributeUsage on a PortUsage");
    }

    @Test
    @DisplayName("E16 – AttributeUsage deleted → corresponding Parameter removed")
    void e16_attributeUsageDeleted_parameterRemoved(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addPartUsage(vsum, "Clutch");
        util.addPartAttributeUsage(vsum, "Clutch", "engageForce");
        assertNotNull(util.getCorrespondingInSimulink(vsum, "engageForce", Parameter.class));

        util.deleteFromSysml(vsum, "engageForce", org.omg.sysml.lang.sysml.AttributeUsage.class);

        assertNull(util.getCorrespondingInSimulink(vsum, "engageForce", Parameter.class), "Parameter must be removed");
    }

    // Rule D — the requirement/function cascade is Simulink -> SysML only

    @Test
    @DisplayName("Rule D note – PartUsage created directly on the SysML side does NOT trigger the "
            + "requirement/function cascade (cascade is Simulink → SysML only)")
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
