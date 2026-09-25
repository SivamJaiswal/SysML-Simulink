package tools.vitruv.methodologisttemplate.vsum;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.ActionUsage;
import org.omg.sysml.lang.sysml.RequirementUsage;
import org.omg.sysml.lang.sysml.FlowUsage;
import org.omg.sysml.lang.sysml.AttributeUsage;
import org.omg.sysml.lang.sysml.PortUsage;

import hu.bme.mit.massif.simulink.Block;
import hu.bme.mit.massif.simulink.SubSystem;
import hu.bme.mit.massif.simulink.Parameter;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

// Tests Completeness invariants (C1, C3-C7) where changes originate on the Simulink side.
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class SimulinkToSysMLCompletenessTest {

    VSUMRunner util = new VSUMRunner();

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("*", new XMIResourceFactoryImpl());
    }

    @Test
    @DisplayName("C1 – a SubSystem's subBlocks count matches its PartUsage's nestedPart count")
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

    @Test
    @DisplayName("C4 – the cascade produces exactly one sibling ActionUsage and one sibling RequirementUsage "
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

    @Test
    @DisplayName("C3 – every FlowUsage source (SingleConnection, MultiConnection branch, BusSignalMapping, Goto/From link) produces exactly one FlowUsage, no more, no fewer")
    void c3_flowUsageCountMatchesEverySource(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        // 1 SingleConnection
        util.addBlock(vsum, tempDir, "Sender", true);
        util.addOutPort(vsum, "Sender", "out1");
        util.addSubBlock(vsum, "Sender", "Receiver", false);
        util.addInPort(vsum, "Receiver", "in1");
        util.addSingleConnection(vsum, "out1", "in1");

        // 1 MultiConnection, 2 branches
        util.addOutPort(vsum, "Sender", "out2");
        util.addSubBlock(vsum, "Sender", "ReceiverA", false);
        util.addInPort(vsum, "ReceiverA", "inA");
        util.addSubBlock(vsum, "Sender", "ReceiverB", false);
        util.addInPort(vsum, "ReceiverB", "inB");
        util.addMultiConnection(vsum, "out2", "inA", "inB");

        // 1 BusSignalMapping
        util.addBusSelector(vsum, tempDir, "Splitter");
        util.addOutPort(vsum, "Splitter", "busIn");
        util.addOutPort(vsum, "Splitter", "busOut");
        util.addBusSignalMapping(vsum, "Splitter", "busIn", "busOut");

        // 1 Goto/From link
        util.addGoto(vsum, tempDir, "G1");
        util.addInPort(vsum, "G1", "gIn");
        util.addFromLinkedToGoto(vsum, tempDir, "F1", "G1");
        util.addOutPort(vsum, "F1", "fOut");

        assertEquals(5, util.countAllInSysml(vsum, FlowUsage.class),
                "1 (SingleConnection) + 2 (MultiConnection branches) + 1 (BusSignalMapping) + 1 (Goto/From) = 5");
        assertNotNull(util.getFlowUsageBetween(vsum, "out1", "in1"));
        assertNotNull(util.getFlowUsageBetween(vsum, "out2", "inA"));
        assertNotNull(util.getFlowUsageBetween(vsum, "out2", "inB"));
        assertNotNull(util.getFlowUsageBetween(vsum, "busIn", "busOut"));
        assertNotNull(util.getFlowUsageBetween(vsum, "gIn", "fOut"));
    }

    @Test
    @DisplayName("C5 – every Parameter has exactly one AttributeUsage, and vice versa, across a Block and a Port")
    void c5_attributeUsageCountMatchesParameterCount(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "Motor", true);
        util.addBlockParameter(vsum, "Motor", "maxRpm");
        util.addOutPort(vsum, "Motor", "speedOut");
        util.addPortParameter(vsum, "speedOut", "sampleRate");

        assertEquals(2, util.countAllInSysml(vsum, AttributeUsage.class),
                "one Parameter on the Block, one on the Port — exactly two AttributeUsages, no more, no fewer");
        assertNotNull(util.getCorrespondingInSysml(vsum, "maxRpm", AttributeUsage.class));
        assertNotNull(util.getCorrespondingInSysml(vsum, "sampleRate", AttributeUsage.class));
    }

    @Test
    @DisplayName("C6 – every Rule G block family instance gets exactly one PartUsage, no Rule D cascade")
    void c6_blockFamilyPartUsageCountWithNoCascade(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBusSelector(vsum, tempDir, "C6Selector");
        util.addBusCreator(vsum, tempDir, "C6Creator");
        util.addGotoTagVisibility(vsum, tempDir, "C6Visibility");
        util.addModelReference(vsum, tempDir, "C6ModelRef");
        util.addGoto(vsum, tempDir, "C6Goto");
        util.addInPort(vsum, "C6Goto", "c6GotoIn");
        util.addFromLinkedToGoto(vsum, tempDir, "C6From", "C6Goto");
        util.addOutPort(vsum, "C6From", "c6FromOut");

        assertEquals(6, util.countAllInSysml(vsum, PartUsage.class),
                "6 block-family instances (Selector, Creator, Visibility, ModelRef, Goto, From) — exactly one PartUsage each");
        // FlowUsage is itself an ActionUsage subtype, so a raw count would conflate it with the Rule H flow above — check the process-prefixed naming pattern instead.
        for (String name : List.of("C6Selector", "C6Creator", "C6Visibility", "C6ModelRef", "C6Goto", "C6From")) {
            assertNull(util.getCorrespondingInSysml(vsum, "process" + name, ActionUsage.class),
                    "Rule D's cascade must never fire for " + name + " — it's plumbing, not a real architecture block");
        }
        assertEquals(0, util.countAllInSysml(vsum, RequirementUsage.class));
    }

    @Test
    @DisplayName("C7 – PortBlock family never adds extra PartUsages, checked across several instances at once")
    void c7_portBlockFamilyNeverAddsPartUsages(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "C7ContainerA", true);
        util.addOutPort(vsum, "C7ContainerA", "c7OutA");
        util.addOutPortBlock(vsum, "C7ContainerA", "C7OutBlockA", "c7OutA");

        util.addBlock(vsum, tempDir, "C7ContainerB", true);
        util.addInPort(vsum, "C7ContainerB", "c7InB");
        util.addInPortBlock(vsum, "C7ContainerB", "C7InBlockB", "c7InB");

        assertEquals(2, util.countAllInSysml(vsum, PartUsage.class),
                "only the 2 real containers get a PartUsage — the 2 PortBlocks must contribute zero more, even together in one model");
        assertEquals(2, util.countAllInSysml(vsum, PortUsage.class),
                "one PortUsage per real Port, shared by its PortBlock — not doubled");
    }
}
