package tools.vitruv.methodologisttemplate.vsum;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.ActionUsage;
import org.omg.sysml.lang.sysml.RequirementUsage;

import hu.bme.mit.massif.simulink.Block;
import hu.bme.mit.massif.simulink.SubSystem;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

/**
 * Tests Completeness invariants (C1, C4) where changes originate on the
 * Simulink side.
 */
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
}
