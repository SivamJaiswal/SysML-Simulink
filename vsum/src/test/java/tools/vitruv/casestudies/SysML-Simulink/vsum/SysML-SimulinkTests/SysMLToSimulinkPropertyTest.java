package tools.vitruv.methodologisttemplate.vsum;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.PortUsage;
import org.omg.sysml.lang.sysml.FeatureDirectionKind;

import hu.bme.mit.massif.simulink.Block;
import hu.bme.mit.massif.simulink.OutPort;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

/**
 * Tests Property rules (P1, P3) where changes originate on the SysML side, plus
 * bidirectional round-trip renames.
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class SysMLToSimulinkPropertyTest {

    VSUMRunner util = new VSUMRunner();

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("*", new XMIResourceFactoryImpl());
    }

    @Test
    @DisplayName("P1 – PartUsage renamed → Block.simulinkRef.name updated")
    void p1_partUsageRenamed_blockNameUpdated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addPartUsage(vsum, "OldName");
        util.renameInSysml(vsum, "OldName", PartUsage.class, "NewName");

        assertNull(util.getCorrespondingInSimulink(vsum, "OldName", Block.class), "old Block name must not exist");
        assertNotNull(util.getCorrespondingInSimulink(vsum, "NewName", Block.class), "Block with new name must exist");
    }

    @Test
    @DisplayName("Bidirectional – PartUsage/Block names stay in sync after alternating renames")
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
    @DisplayName("P3 – PortUsage renamed → Port's simulinkRef.name updated")
    void p3_portUsageRenamed_portNameUpdated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addPartUsage(vsum, "ABS");
        util.addPortUsage(vsum, "ABS", "oldSignal", FeatureDirectionKind.OUT);
        util.renameInSysml(vsum, "oldSignal", PortUsage.class, "newSignal");

        assertNull(util.getCorrespondingInSimulink(vsum, "oldSignal", OutPort.class));
        assertNotNull(util.getCorrespondingInSimulink(vsum, "newSignal", OutPort.class));
    }
}
