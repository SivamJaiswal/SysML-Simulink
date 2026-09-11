package tools.vitruv.methodologisttemplate.vsum;

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

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

/**
 * Tests Existence rules (E1, E2, E5-E7, E11-E12) and the Rule D one-directional
 * asymmetry where changes originate on the SysML side.
 */
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

    @Test
    @DisplayName("Rule B note – PortUsage(direction=inout) has no Simulink counterpart")
    void ruleBNote_inoutPortUsage_notPropagated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addPartUsage(vsum, "Transceiver");
        util.addPortUsage(vsum, "Transceiver", "bus", FeatureDirectionKind.INOUT);

        assertNull(util.getCorrespondingInSimulink(vsum, "bus", InPort.class));
        assertNull(util.getCorrespondingInSimulink(vsum, "bus", OutPort.class));
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
        assertEquals("in1", outPort.getConnection().getTo().getSimulinkRef().getName(),
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
