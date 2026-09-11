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

/**
 * Tests Structural rules (S3 — Block re-parenting) where changes originate on
 * the Simulink side.
 */
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

        // Housing and Bracket start as two INDEPENDENT root blocks, each getting its
        // own root-level PartUsage via E3 — this deliberately does NOT go through
        // E3's own parent-lookup path, so the re-parenting below genuinely exercises
        // S3's separate BlockReparented reaction rather than E3 alone.
        util.addBlock(vsum, tempDir, "Housing", true);
        util.addBlock(vsum, tempDir, "Bracket");
        assertNull(util.getCorrespondingInSysml(vsum, "Bracket", PartUsage.class).getOwningRelationship(),
                "Bracket must start as a root-level PartUsage, with no owning relationship yet");

        util.reparentBlock(vsum, "Bracket", "Housing");

        PartUsage bracket = util.getCorrespondingInSysml(vsum, "Bracket", PartUsage.class);
        assertNotNull(bracket.getOwningRelationship(), "Bracket's PartUsage must now have an owning relationship");
        // Compare by name, not by reference: getCorrespondingInSysml builds a fresh
        // view per call (see VSUMRunner's header comment on that method), so an
        // independently-queried "housing" PartUsage is not guaranteed to be the same
        // Java object as the one reached by navigating from bracket in this view.
        assertEquals("Housing", ((org.omg.sysml.lang.sysml.Element) bracket.getOwningRelationship().getOwningRelatedElement()).getDeclaredName(),
                "Bracket's PartUsage must be nested under Housing's PartUsage, mirroring the Simulink subBlocks move");
    }
}
