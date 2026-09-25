package tools.vitruv.methodologisttemplate.vsum;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.FeatureDirectionKind;

import hu.bme.mit.massif.simulink.Block;
import hu.bme.mit.massif.simulink.SubSystem;
import hu.bme.mit.massif.simulink.OutPort;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

// Tests Structural rules (S1, S2 — the Block/SubSystem type migration) where changes originate on the SysML side.
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class SysMLToSimulinkStructuralTest {

    VSUMRunner util = new VSUMRunner();

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("*", new XMIResourceFactoryImpl());
    }

    @Test
    @DisplayName("S1 – PartUsage gains its first nestedPart → corresponding Block becomes a SubSystem")
    void s1_partUsageGainsFirstChild_blockBecomesSubSystem(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addPartUsage(vsum, "Sensor");
        assertFalse(util.getCorrespondingInSimulink(vsum, "Sensor", Block.class) instanceof SubSystem,
                "still childless → still a plain Block");

        util.addNestedPartUsage(vsum, "Sensor", "SensorElement");

        Block converted = util.getCorrespondingInSimulink(vsum, "Sensor", Block.class);
        assertInstanceOf(SubSystem.class, converted, "gaining a first child must upgrade Block to SubSystem");
        assertEquals(1, ((SubSystem) converted).getSubBlocks().size());
        assertNotNull(util.getCorrespondingInSimulink(vsum, "SensorElement", Block.class),
                "the child PartUsage must still get its own Block");
    }

    @Test
    @DisplayName("S1 – existing ports survive the Block → SubSystem conversion")
    void s1_existingPortsSurviveConversion(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addPartUsage(vsum, "Controller");
        util.addPortUsage(vsum, "Controller", "statusOut", FeatureDirectionKind.OUT);
        assertNotNull(util.getCorrespondingInSimulink(vsum, "statusOut", OutPort.class));

        util.addNestedPartUsage(vsum, "Controller", "InnerLogic");

        Block converted = util.getCorrespondingInSimulink(vsum, "Controller", Block.class);
        assertInstanceOf(SubSystem.class, converted);
        assertTrue(converted.getPorts().stream().anyMatch(p -> p instanceof OutPort),
                "the port that existed before conversion must be moved onto the new SubSystem, not lost");
    }

    @Test
    @DisplayName("S2 – PartUsage loses its last nestedPart → corresponding SubSystem reverts to a plain Block")
    void s2_partUsageLosesLastChild_subSystemBecomesBlock(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addPartUsage(vsum, "Housing");
        util.addNestedPartUsage(vsum, "Housing", "OnlyChild");
        assertInstanceOf(SubSystem.class, util.getCorrespondingInSimulink(vsum, "Housing", Block.class));

        util.deleteFromSysml(vsum, "OnlyChild", PartUsage.class);

        Block afterRemoval = util.getCorrespondingInSimulink(vsum, "Housing", Block.class);
        assertNotNull(afterRemoval, "Housing must still have a corresponding element");
        assertFalse(afterRemoval instanceof SubSystem, "losing the last child must downgrade SubSystem back to Block");
    }
}
