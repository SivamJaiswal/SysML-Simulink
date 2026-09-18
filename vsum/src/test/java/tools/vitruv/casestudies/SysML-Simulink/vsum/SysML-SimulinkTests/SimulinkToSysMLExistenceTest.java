package tools.vitruv.methodologisttemplate.vsum;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.PortUsage;
import org.omg.sysml.lang.sysml.ActionUsage;
import org.omg.sysml.lang.sysml.RequirementUsage;
import org.omg.sysml.lang.sysml.FeatureDirectionKind;

import org.omg.sysml.lang.sysml.FlowUsage;

import hu.bme.mit.massif.simulink.Block;
import hu.bme.mit.massif.simulink.OutPort;
import hu.bme.mit.massif.simulink.From;

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

    // Rule G note (block family) — BusSelector/BusCreator/Goto/From/GotoTagVisibility/ModelReference are all still
    // real Block instances an engineer would see, so they deliberately get a generic PartUsage the same as any Block.

    @Test
    @DisplayName("Rule G note – BusSelector created → exactly one PartUsage")
    void ruleGNote_busSelectorCreated_partUsageCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBusSelector(vsum, tempDir, "SignalSplitter2");

        assertNotNull(util.getCorrespondingInSysml(vsum, "SignalSplitter2", PartUsage.class));
        assertEquals(1, util.countMatchingInSysml(vsum, "SignalSplitter2", PartUsage.class));
    }

    @Test
    @DisplayName("Rule G note – BusCreator created → exactly one PartUsage")
    void ruleGNote_busCreatorCreated_partUsageCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBusCreator(vsum, tempDir, "SignalBundler");

        assertNotNull(util.getCorrespondingInSysml(vsum, "SignalBundler", PartUsage.class));
        assertEquals(1, util.countMatchingInSysml(vsum, "SignalBundler", PartUsage.class));
    }

    @Test
    @DisplayName("Rule G note – Goto created → exactly one PartUsage")
    void ruleGNote_gotoCreated_partUsageCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addGoto(vsum, tempDir, "TagSender");

        assertNotNull(util.getCorrespondingInSysml(vsum, "TagSender", PartUsage.class));
        assertEquals(1, util.countMatchingInSysml(vsum, "TagSender", PartUsage.class));
    }

    @Test
    @DisplayName("Rule G note – From created → exactly one PartUsage")
    void ruleGNote_fromCreated_partUsageCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addFrom(vsum, tempDir, "TagReceiver");

        assertNotNull(util.getCorrespondingInSysml(vsum, "TagReceiver", PartUsage.class));
        assertEquals(1, util.countMatchingInSysml(vsum, "TagReceiver", PartUsage.class));
    }

    @Test
    @DisplayName("Rule G note – GotoTagVisibility created → exactly one PartUsage")
    void ruleGNote_gotoTagVisibilityCreated_partUsageCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addGotoTagVisibility(vsum, tempDir, "ScopeMarker");

        assertNotNull(util.getCorrespondingInSysml(vsum, "ScopeMarker", PartUsage.class));
        assertEquals(1, util.countMatchingInSysml(vsum, "ScopeMarker", PartUsage.class));
    }

    @Test
    @DisplayName("Rule G note – ModelReference created → exactly one PartUsage")
    void ruleGNote_modelReferenceCreated_partUsageCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addModelReference(vsum, tempDir, "ExternalBrakeModel");

        assertNotNull(util.getCorrespondingInSysml(vsum, "ExternalBrakeModel", PartUsage.class));
        assertEquals(1, util.countMatchingInSysml(vsum, "ExternalBrakeModel", PartUsage.class));
    }

    // Rule H — Goto/From tag-based virtual wire <-> FlowUsage

    @Test
    @DisplayName("Rule H – From linked to Goto → FlowUsage from the Goto's InPort to the From's OutPort")
    void ruleH_fromLinkedToGoto_flowUsageCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addGoto(vsum, tempDir, "SendTag");
        util.addInPort(vsum, "SendTag", "gotoIn");
        // before the From exists, no flow can exist yet — the "no match yet" case.
        assertNull(util.getFlowUsageBetween(vsum, "gotoIn", "fromOut"));

        util.addFromLinkedToGoto(vsum, tempDir, "ReceiveTag", "SendTag");
        // OutPort added afterward, in a separate transaction — see the FromOutPortCreated reaction's comment.
        util.addOutPort(vsum, "ReceiveTag", "fromOut");

        FlowUsage flow = util.getFlowUsageBetween(vsum, "gotoIn", "fromOut");
        assertNotNull(flow, "a FlowUsage must exist between the Goto's InPort and the From's OutPort");
    }

    @Test
    @DisplayName("Rule H – multiple From blocks sharing one Goto → one FlowUsage each")
    void ruleH_multipleFromBlocksShareOneGoto_oneFlowUsageEach(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addGoto(vsum, tempDir, "BroadcastTag");
        util.addInPort(vsum, "BroadcastTag", "broadcastIn");

        util.addFromLinkedToGoto(vsum, tempDir, "Receiver1", "BroadcastTag");
        util.addOutPort(vsum, "Receiver1", "out1");
        util.addFromLinkedToGoto(vsum, tempDir, "Receiver2", "BroadcastTag");
        util.addOutPort(vsum, "Receiver2", "out2");

        FlowUsage flow1 = util.getFlowUsageBetween(vsum, "broadcastIn", "out1");
        FlowUsage flow2 = util.getFlowUsageBetween(vsum, "broadcastIn", "out2");
        assertNotNull(flow1, "the first From must get its own FlowUsage from the shared Goto");
        assertNotNull(flow2, "the second From must get its own FlowUsage from the shared Goto");
    }

    @Test
    @DisplayName("E19 – From deleted → virtual-wire FlowUsage removed")
    void e19_fromDeleted_flowUsageRemoved(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addGoto(vsum, tempDir, "CleanupTag");
        util.addInPort(vsum, "CleanupTag", "cleanupIn");
        util.addFromLinkedToGoto(vsum, tempDir, "CleanupReceiver", "CleanupTag");
        util.addOutPort(vsum, "CleanupReceiver", "cleanupOut");
        assertNotNull(util.getFlowUsageBetween(vsum, "cleanupIn", "cleanupOut"));

        util.deleteFromSimulink(vsum, "CleanupReceiver", From.class);

        assertNull(util.getFlowUsageBetween(vsum, "cleanupIn", "cleanupOut"), "FlowUsage must be removed when the From is deleted");
    }

    // Rule I — PortBlock family shares its wrapped Port's PortUsage, no duplicate PartUsage

    @Test
    @DisplayName("Rule I – OutPortBlock wraps an existing OutPort → shares its PortUsage, no duplicate")
    void ruleI_outPortBlockCreated_sharesPortUsage(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "Container1", true);
        util.addOutPort(vsum, "Container1", "boundaryOut1");
        assertEquals(1, util.countMatchingInSysml(vsum, "boundaryOut1", PortUsage.class));

        util.addOutPortBlock(vsum, "Container1", "OutportDiagramBlock", "boundaryOut1");

        assertEquals(1, util.countMatchingInSysml(vsum, "boundaryOut1", PortUsage.class), "the PortBlock must not create a duplicate PortUsage");
        assertNull(util.getCorrespondingInSysml(vsum, "OutportDiagramBlock", PartUsage.class), "the PortBlock itself must not get its own PartUsage");
    }

    @Test
    @DisplayName("Rule I – InPortBlock wraps an existing InPort → shares its PortUsage, no duplicate")
    void ruleI_inPortBlockCreated_sharesPortUsage(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "Container2", true);
        util.addInPort(vsum, "Container2", "boundaryIn2");
        assertEquals(1, util.countMatchingInSysml(vsum, "boundaryIn2", PortUsage.class));

        util.addInPortBlock(vsum, "Container2", "InportDiagramBlock", "boundaryIn2");

        assertEquals(1, util.countMatchingInSysml(vsum, "boundaryIn2", PortUsage.class), "the PortBlock must not create a duplicate PortUsage");
        assertNull(util.getCorrespondingInSysml(vsum, "InportDiagramBlock", PartUsage.class), "the PortBlock itself must not get its own PartUsage");
    }

    @Test
    @DisplayName("Rule I – TriggerBlock wraps an existing Trigger port → shares its PortUsage, no duplicate")
    void ruleI_triggerBlockCreated_sharesPortUsage(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "Container3", true);
        util.addTrigger(vsum, "Container3", "triggerPort3");
        assertEquals(1, util.countMatchingInSysml(vsum, "triggerPort3", PortUsage.class));

        util.addTriggerBlock(vsum, "Container3", "TriggerDiagramBlock", "triggerPort3");

        assertEquals(1, util.countMatchingInSysml(vsum, "triggerPort3", PortUsage.class), "the PortBlock must not create a duplicate PortUsage");
        assertNull(util.getCorrespondingInSysml(vsum, "TriggerDiagramBlock", PartUsage.class), "the PortBlock itself must not get its own PartUsage");
    }

    @Test
    @DisplayName("Rule I – EnableBlock wraps an existing Enable port → shares its PortUsage, no duplicate")
    void ruleI_enableBlockCreated_sharesPortUsage(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "Container4", true);
        util.addEnable(vsum, "Container4", "enablePort4");
        assertEquals(1, util.countMatchingInSysml(vsum, "enablePort4", PortUsage.class));

        util.addEnableBlock(vsum, "Container4", "EnableDiagramBlock", "enablePort4");

        assertEquals(1, util.countMatchingInSysml(vsum, "enablePort4", PortUsage.class), "the PortBlock must not create a duplicate PortUsage");
        assertNull(util.getCorrespondingInSysml(vsum, "EnableDiagramBlock", PartUsage.class), "the PortBlock itself must not get its own PartUsage");
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
    @DisplayName("E8 note – Trigger created (InPort subtype) → PortUsage(direction=in)")
    void e8b_triggerCreated_portUsageCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "ConditionalSubsystem");
        util.addTrigger(vsum, "ConditionalSubsystem", "trigger");

        PortUsage port = util.getCorrespondingInSysml(vsum, "trigger", PortUsage.class);
        assertNotNull(port, "PortUsage must be created for the Trigger, same as any other InPort");
        assertEquals(FeatureDirectionKind.IN, port.getDirection());
    }

    @Test
    @DisplayName("E8 note – Enable created (InPort subtype) → PortUsage(direction=in)")
    void e8c_enableCreated_portUsageCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "ConditionalSubsystem2");
        util.addEnable(vsum, "ConditionalSubsystem2", "enable");

        PortUsage port = util.getCorrespondingInSysml(vsum, "enable", PortUsage.class);
        assertNotNull(port, "PortUsage must be created for the Enable port, same as any other InPort");
        assertEquals(FeatureDirectionKind.IN, port.getDirection());
    }

    @Test
    @DisplayName("E9 note – State created (OutPort subtype) → PortUsage(direction=out)")
    void e9b_stateCreated_portUsageCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "Integrator");
        util.addState(vsum, "Integrator", "state");

        PortUsage port = util.getCorrespondingInSysml(vsum, "state", PortUsage.class);
        assertNotNull(port, "PortUsage must be created for the State port, same as any other OutPort");
        assertEquals(FeatureDirectionKind.OUT, port.getDirection());
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

    // E17 — MultiConnection branches <-> FlowUsage

    @Test
    @DisplayName("E17 – MultiConnection branches → one FlowUsage per branch, source resolved via the parent")
    void e17_multiConnectionBranches_flowUsagesCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "FanoutSender", true);
        util.addOutPort(vsum, "FanoutSender", "outMulti");
        util.addSubBlock(vsum, "FanoutSender", "Receiver1", false);
        util.addInPort(vsum, "Receiver1", "in1");
        util.addSubBlock(vsum, "FanoutSender", "Receiver2", false);
        util.addInPort(vsum, "Receiver2", "in2");

        util.addMultiConnection(vsum, "outMulti", "in1", "in2");

        FlowUsage flow1 = util.getFlowUsageBetween(vsum, "outMulti", "in1");
        FlowUsage flow2 = util.getFlowUsageBetween(vsum, "outMulti", "in2");
        assertNotNull(flow1, "a FlowUsage must exist for the first MultiConnection branch");
        assertNotNull(flow2, "a FlowUsage must exist for the second MultiConnection branch");
    }

    // Rule F — BusSignalMapping <-> FlowUsage

    @Test
    @DisplayName("Rule F – BusSignalMapping created → FlowUsage between the corresponding PortUsages")
    void ruleF_busSignalMappingCreated_flowUsageCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBusSelector(vsum, tempDir, "SignalSplitter");
        util.addOutPort(vsum, "SignalSplitter", "incomingBusOut");
        util.addOutPort(vsum, "SignalSplitter", "selectedOut");

        util.addBusSignalMapping(vsum, "SignalSplitter", "incomingBusOut", "selectedOut");

        FlowUsage flow = util.getFlowUsageBetween(vsum, "incomingBusOut", "selectedOut");
        assertNotNull(flow, "a FlowUsage must exist for the BusSignalMapping");
    }

    // E15/E16 — Parameter <-> AttributeUsage

    @Test
    @DisplayName("E15 – Parameter on a Block created → AttributeUsage added under the corresponding PartUsage")
    void e15_blockParameterCreated_attributeUsageCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "OilPump");
        util.addBlockParameter(vsum, "OilPump", "flowRate");

        org.omg.sysml.lang.sysml.AttributeUsage attr = util.getCorrespondingInSysml(vsum, "flowRate", org.omg.sysml.lang.sysml.AttributeUsage.class);
        assertNotNull(attr, "AttributeUsage must be created for the new Parameter");
    }

    @Test
    @DisplayName("E15 – Parameter on a Port created → AttributeUsage added under the corresponding PortUsage")
    void e15b_portParameterCreated_attributeUsageCreated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "SpeedSensor");
        util.addOutPort(vsum, "SpeedSensor", "speed");
        util.addPortParameter(vsum, "speed", "sampleRate");

        org.omg.sysml.lang.sysml.AttributeUsage attr = util.getCorrespondingInSysml(vsum, "sampleRate", org.omg.sysml.lang.sysml.AttributeUsage.class);
        assertNotNull(attr, "AttributeUsage must be created for the Parameter on a Port");
    }

    @Test
    @DisplayName("E16 – Parameter deleted → corresponding AttributeUsage removed")
    void e16_parameterDeleted_attributeUsageRemoved(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "Radiator");
        util.addBlockParameter(vsum, "Radiator", "coolantTemp");
        assertNotNull(util.getCorrespondingInSysml(vsum, "coolantTemp", org.omg.sysml.lang.sysml.AttributeUsage.class));

        util.deleteFromSimulink(vsum, "coolantTemp", hu.bme.mit.massif.simulink.Parameter.class);

        assertNull(util.getCorrespondingInSysml(vsum, "coolantTemp", org.omg.sysml.lang.sysml.AttributeUsage.class), "AttributeUsage must be removed");
    }
}
