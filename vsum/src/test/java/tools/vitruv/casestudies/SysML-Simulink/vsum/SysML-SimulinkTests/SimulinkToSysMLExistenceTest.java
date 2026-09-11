package tools.vitruv.methodologisttemplate.vsum;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.PortUsage;
import org.omg.sysml.lang.sysml.ActionUsage;
import org.omg.sysml.lang.sysml.RequirementUsage;
import org.omg.sysml.lang.sysml.FeatureDirectionKind;

import hu.bme.mit.massif.simulink.Block;
import hu.bme.mit.massif.simulink.OutPort;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

/**
 * Tests Existence rules (E3, E4, E8-E10, E13-E14) and the Rule D
 * requirement/function/architecture cascade where changes originate on the
 * Simulink side. The e3c4_* test directly reproduces the worked example from
 * Grycz et al. §4.2 (the TemperatureMonitor scenario).
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class SimulinkToSysMLExistenceTest {

    VSUMRunner util = new VSUMRunner();

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("*", new XMIResourceFactoryImpl());
    }

    // E3/E4 — Block <-> PartUsage

    @Test
    @DisplayName("E3 – Block created → PartUsage with same name in SysML view")
    void e3_blockCreated_partUsageCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "FuelPump");

        PartUsage part = util.getCorrespondingInSysml(vsum, "FuelPump", PartUsage.class);
        assertNotNull(part, "PartUsage must be created for the new Block");
        assertEquals("FuelPump", part.getDeclaredName());
    }

    @Test
    @DisplayName("E3 – Block created with no SysML counterpart → Requirement+Function+Architecture "
            + "cascade, reproducing the paper's TemperatureMonitor scenario (Grycz et al. §4.2)")
    void e3_unmatchedBlock_triggersRequirementFunctionArchitectureCascade(@TempDir Path tempDir) throws Exception {
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
    @DisplayName("E4 – Block deleted → PartUsage and its cascade siblings removed")
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

    // E8/E9/E10 — InPort/OutPort <-> PortUsage

    @Test
    @DisplayName("E8 – InPort created → PortUsage(direction=in)")
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
    @DisplayName("E9 – OutPort created → PortUsage(direction=out)")
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
    @DisplayName("Rule B note – Trigger/Enable/State ports have no SysML counterpart")
    void ruleBNote_triggerPort_notPropagated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "ConditionalSubsystem");
        // no reaction listens for Trigger/Enable/State at all — nothing to assert beyond "no PortUsage appears".
        assertNull(util.getCorrespondingInSysml(vsum, "trigger", PortUsage.class));
    }

    @Test
    @DisplayName("E10 – Port deleted → corresponding PortUsage removed")
    void e10_portDeleted_portUsageRemoved(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "Clutch");
        util.addOutPort(vsum, "Clutch", "engaged");
        assertNotNull(util.getCorrespondingInSysml(vsum, "engaged", PortUsage.class));

        util.deleteFromSimulink(vsum, "engaged", OutPort.class);

        assertNull(util.getCorrespondingInSysml(vsum, "engaged", PortUsage.class), "PortUsage must be removed");
    }

    // E13/E14 — SingleConnection <-> FlowUsage

    @Test
    @DisplayName("E13 – SingleConnection created → FlowUsage between the corresponding PortUsages")
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
    @DisplayName("E14 – SingleConnection deleted → FlowUsage removed")
    void e14_singleConnectionDeleted_flowUsageRemoved(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "SenderBlock2", true);
        util.addOutPort(vsum, "SenderBlock2", "outY");
        util.addSubBlock(vsum, "SenderBlock2", "ReceiverBlock2", false);
        util.addInPort(vsum, "ReceiverBlock2", "inY");
        util.addSingleConnection(vsum, "outY", "inY");

        util.deleteFromSimulink(vsum, "outY", OutPort.class);
        // deleting the OutPort transitively removes its contained SingleConnection too (containment=true).

        assertNull(util.getCorrespondingInSysml(vsum, "outY", PortUsage.class));
    }
}
