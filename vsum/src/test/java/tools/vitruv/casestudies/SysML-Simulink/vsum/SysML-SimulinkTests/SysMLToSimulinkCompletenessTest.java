package tools.vitruv.methodologisttemplate.vsum;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.omg.sysml.lang.sysml.FeatureDirectionKind;

import hu.bme.mit.massif.simulink.InPort;
import hu.bme.mit.massif.simulink.OutPort;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

// Tests Completeness invariants (C2) where changes originate on the SysML side.
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class SysMLToSimulinkCompletenessTest {

    VSUMRunner util = new VSUMRunner();

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("*", new XMIResourceFactoryImpl());
    }

    @Test
    @DisplayName("C2 – every direction-typed PortUsage on a part has exactly one, correctly-typed corresponding Port")
    void c2_portDirectionCompletenessAcrossMultiplePorts(@TempDir Path tempDir) throws Exception {
        InternalVirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);

        util.addPartUsage(vsum, "ECU");
        util.addPortUsage(vsum, "ECU", "in1", FeatureDirectionKind.IN);
        util.addPortUsage(vsum, "ECU", "in2", FeatureDirectionKind.IN);
        util.addPortUsage(vsum, "ECU", "out1", FeatureDirectionKind.OUT);

        assertNotNull(util.getCorrespondingInSimulink(vsum, "in1", InPort.class));
        assertNotNull(util.getCorrespondingInSimulink(vsum, "in2", InPort.class));
        assertNotNull(util.getCorrespondingInSimulink(vsum, "out1", OutPort.class));
        assertNull(util.getCorrespondingInSimulink(vsum, "in1", OutPort.class), "in1 must not also appear as an OutPort");
        assertNull(util.getCorrespondingInSimulink(vsum, "out1", InPort.class), "out1 must not also appear as an InPort");
    }
}
