package tools.vitruv.methodologisttemplate.vsum;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.omg.sysml.lang.sysml.PartUsage;

import hu.bme.mit.massif.simulink.Block;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

// Tests Structural rules (S3 — Block re-parenting) where changes originate on the Simulink side.
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class SimulinkToSysMLStructuralTest {

    VSUMRunner util = new VSUMRunner();

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("*", new XMIResourceFactoryImpl());
    }

    @Test
    @DisplayName("S3 – Block moved into a SubSystem.subBlocks (gains a parent) → PartUsage re-parented to match")
    void s3_blockReparented_partUsageReparented(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        // Housing and Bracket start as two independent root blocks, so re-parenting below exercises S3, not E3's own parent-lookup path.
        util.addBlock(vsum, tempDir, "Housing", true);
        util.addBlock(vsum, tempDir, "Bracket");
        assertNull(util.getCorrespondingInSysml(vsum, "Bracket", PartUsage.class).getOwningRelationship(),
                "Bracket must start as a root-level PartUsage, with no owning relationship yet");

        util.reparentBlock(vsum, "Bracket", "Housing");

        PartUsage bracket = util.getCorrespondingInSysml(vsum, "Bracket", PartUsage.class);
        assertNotNull(bracket.getOwningRelationship(), "Bracket's PartUsage must now have an owning relationship");
        // compare by name, not reference — getCorrespondingInSysml builds a fresh view per call.
        assertEquals("Housing", ((org.omg.sysml.lang.sysml.Element) bracket.getOwningRelationship().getOwningRelatedElement()).getDeclaredName(),
                "Bracket's PartUsage must be nested under Housing's PartUsage, mirroring the Simulink subBlocks move");
    }

    @Test
    @DisplayName("S3 note – Rule G block (BusSelector) moved into a SubSystem → its PartUsage re-parents too")
    void s3note_ruleGBlockReparented_partUsageReparented(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "Cabinet", true);
        util.addBusSelector(vsum, tempDir, "Splitter3");
        assertNull(util.getCorrespondingInSysml(vsum, "Splitter3", PartUsage.class).getOwningRelationship(),
                "Splitter3's PartUsage must start as root-level, with no owning relationship yet");

        util.reparentBlock(vsum, "Splitter3", "Cabinet");

        PartUsage splitter = util.getCorrespondingInSysml(vsum, "Splitter3", PartUsage.class);
        assertNotNull(splitter.getOwningRelationship(), "Splitter3's PartUsage must now have an owning relationship");
        assertEquals("Cabinet", ((org.omg.sysml.lang.sysml.Element) splitter.getOwningRelationship().getOwningRelatedElement()).getDeclaredName(),
                "S3 must re-parent Rule G blocks the same way it does plain Blocks — the shared creation routine means the shared structural rule should apply too");
    }
}
