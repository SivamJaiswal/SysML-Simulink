package tools.vitruv.methodologisttemplate.vsum;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.emf.common.util.URI;

import hu.bme.mit.massif.simulink.Block;
import hu.bme.mit.massif.simulink.SimulinkFactory;
import hu.bme.mit.massif.simulink.SimulinkModel;

import mir.reactions.sysmlToSimulink.SysmlToSimulinkChangePropagationSpecification;
import mir.reactions.simulinkToSysml.SimulinkToSysmlChangePropagationSpecification;

import tools.vitruv.change.interaction.CliInteractionResultProviderImpl;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;

/**
 * Example how to define and use a VSUM, run interactively — requires a real terminal.
 *
 * Unlike VSUMExample in the ASEM-Amalthea case study, no reaction in this project
 * asks an interactive question (there is no ambiguous "which Task subtype?" case
 * here) — this demo simply reproduces the paper's own worked example (Grycz et al.
 * §4.2): adding a Simulink Block with no SysML counterpart, and observing the
 * Requirement/Function/Architecture cascade (Rule D) fire automatically.
 */
public class VSUMExample {

  public static void main(String[] args) throws IOException {
    Path storageFolder = Path.of("vsum/sample-data").toAbsolutePath();
    VirtualModel vsum = createDefaultVirtualModel(storageFolder);

    // E3 + Rule D — adding an unmatched Block triggers the Requirement/Function/
    // Architecture cascade described in README.md §3.4 Rule D.
    modifyView(
        getDefaultView(vsum).withChangeRecordingTrait(),
        (CommittableView v) -> {
          SimulinkModel model = SimulinkFactory.eINSTANCE.createSimulinkModel();
          Block block = SimulinkFactory.eINSTANCE.createBlock();
          var ref = SimulinkFactory.eINSTANCE.createIdentifierReference();
          ref.setName("TemperatureMonitor");
          block.setSimulinkRef(ref);
          model.getContains().add(block);
          v.registerRoot(model, URI.createFileURI(storageFolder + "/simulink/temperature_monitor.simulink"));
        });
  }

  private static VirtualModel createDefaultVirtualModel(Path storageFolder) throws IOException {
    // Registers the correspondence metamodel before any existing correspondences are loaded from disk.
    tools.vitruv.dsls.reactions.runtime.correspondence.CorrespondencePackage.eINSTANCE.eClass();
    Iterable<ChangePropagationSpecification> specs =
        List.of(
            new SysmlToSimulinkChangePropagationSpecification(),
            new SimulinkToSysmlChangePropagationSpecification());
    return new VirtualModelBuilder()
        .withStorageFolder(storageFolder)
        .withUserInteractorForResultProvider(new CliInteractionResultProviderImpl())
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
