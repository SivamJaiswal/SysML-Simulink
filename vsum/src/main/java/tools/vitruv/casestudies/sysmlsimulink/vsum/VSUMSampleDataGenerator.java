package tools.vitruv.casestudies.sysmlsimulink.vsum;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.emf.common.util.URI;

import org.omg.sysml.lang.sysml.Package;
import org.omg.sysml.lang.sysml.SysMLFactory;

import mir.reactions.sysmlToSimulink.SysmlToSimulinkChangePropagationSpecification;
import mir.reactions.simulinkToSysml.SimulinkToSysmlChangePropagationSpecification;

import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;

// Builds the vsum/sample-data baseline used by VSUMExample — run this once before the first interactive run.
public class VSUMSampleDataGenerator {
  private static final String SYSML_FILE = "/sysml/example.sysml";

  public static void main(String[] args) throws IOException {
    Path storageFolder = Path.of("vsum/sample-data").toAbsolutePath();
    VirtualModel vsum = createDefaultVirtualModel(storageFolder);

    // only the SysML-side root is registered up front; VSUMExample.main() adds the Simulink Block that triggers the cascade.
    modifyView(
        getDefaultView(vsum).withChangeRecordingTrait(),
        (CommittableView v) -> {
          Package root = SysMLFactory.eINSTANCE.createPackage();
          v.registerRoot(root, URI.createFileURI(storageFolder + SYSML_FILE));
        });
  }

  private static VirtualModel createDefaultVirtualModel(Path storageFolder) throws IOException {
    tools.vitruv.dsls.reactions.runtime.correspondence.CorrespondencePackage.eINSTANCE.eClass();
    Iterable<ChangePropagationSpecification> specs =
        List.of(
            new SysmlToSimulinkChangePropagationSpecification(),
            new SimulinkToSysmlChangePropagationSpecification());
    return new VirtualModelBuilder()
        .withStorageFolder(storageFolder)
        .withUserInteractorForResultProvider(
            new TestUserInteraction.ResultProvider(new TestUserInteraction()))
        .withChangePropagationSpecifications(specs)
        .buildAndInitialize();
  }

  private static View getDefaultView(VirtualModel vsum) {
    var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("default"));
    selector.getSelectableElements().forEach(it -> selector.setSelected(it, true));
    return selector.createView();
  }

  private static void modifyView(
      CommittableView view, Consumer<CommittableView> modificationFunction) {
    modificationFunction.accept(view);
    view.commitChanges();
  }
}
