package tools.vitruv.methodologisttemplate.vsum;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.PortUsage;
import org.omg.sysml.lang.sysml.ActionUsage;
import org.omg.sysml.lang.sysml.RequirementUsage;

import org.omg.sysml.lang.sysml.AttributeUsage;

import hu.bme.mit.massif.simulink.Block;
import hu.bme.mit.massif.simulink.OutPort;
import hu.bme.mit.massif.simulink.Parameter;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

// Tests Property rules (P2, P4, P5, P6) where changes originate on the Simulink side, plus bidirectional round-trip renames.
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class SimulinkToSysMLPropertyTest {

    VSUMRunner util = new VSUMRunner();

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("*", new XMIResourceFactoryImpl());
    }

    @Test
    @DisplayName("P2/P5 – Block renamed → PartUsage.declaredName updated, cascade siblings renamed too")
    void p2p5_blockRenamed_partUsageAndCascadeRenamed(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "InitialName");
        util.renameInSimulink(vsum, "InitialName", Block.class, "UpdatedName");

        assertNull(util.getCorrespondingInSysml(vsum, "InitialName", PartUsage.class));
        assertNotNull(util.getCorrespondingInSysml(vsum, "UpdatedName", PartUsage.class));
        assertNotNull(util.getCorrespondingInSysml(vsum, "processUpdatedName", ActionUsage.class),
                "the cascade function must follow the rename (P5)");
        assertNotNull(util.getCorrespondingInSysml(vsum, "UpdatedNameRequirement", RequirementUsage.class),
                "the cascade requirement must follow the rename (P5)");
    }

    @Test
    @DisplayName("P4 – Port renamed → PortUsage.declaredName updated")
    void p4_portRenamed_portUsageNameUpdated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "MCU");
        util.addOutPort(vsum, "MCU", "oldOp");
        util.renameInSimulink(vsum, "oldOp", OutPort.class, "newOp");

        assertNull(util.getCorrespondingInSysml(vsum, "oldOp", PortUsage.class));
        assertNotNull(util.getCorrespondingInSysml(vsum, "newOp", PortUsage.class));
    }

    @Test
    @DisplayName("Bidirectional – PortUsage/Port names stay in sync after alternating renames")
    void bidirectional_portUsagePort_alternatingRenames(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "BiDirBlock");
        util.addOutPort(vsum, "BiDirBlock", "initial");
        assertNotNull(util.getCorrespondingInSysml(vsum, "initial", PortUsage.class));

        util.renameInSimulink(vsum, "initial", OutPort.class, "fromSimulink");
        assertNotNull(util.getCorrespondingInSysml(vsum, "fromSimulink", PortUsage.class));

        util.renameInSysml(vsum, "fromSimulink", PortUsage.class, "fromSysml");
        assertNotNull(util.getCorrespondingInSimulink(vsum, "fromSysml", OutPort.class));
    }

    @Test
    @DisplayName("P6 – Parameter renamed → AttributeUsage.declaredName updated")
    void p6_parameterRenamed_attributeUsageNameUpdated(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addBlock(vsum, tempDir, "Alternator");
        util.addBlockParameter(vsum, "Alternator", "oldOutput");
        util.renameParameterInSimulink(vsum, "oldOutput", "newOutput");

        assertNull(util.getCorrespondingInSysml(vsum, "oldOutput", AttributeUsage.class));
        assertNotNull(util.getCorrespondingInSysml(vsum, "newOutput", AttributeUsage.class));
    }
}
