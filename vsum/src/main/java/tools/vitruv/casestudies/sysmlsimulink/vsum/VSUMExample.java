package tools.vitruv.casestudies.sysmlsimulink.vsum;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.emf.common.util.URI;

import org.omg.sysml.lang.sysml.FeatureDirectionKind;
import org.omg.sysml.lang.sysml.FeatureMembership;
import org.omg.sysml.lang.sysml.Package;
import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.PortUsage;
import org.omg.sysml.lang.sysml.SysMLFactory;

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

// Reproduces the paper's worked example (Grycz et al. §4.2): adding a Simulink Block with no SysML counterpart triggers the Requirement/Function/Architecture cascade (Rule D) automatically.
public class VSUMExample {

  public static void main(String[] args) throws IOException {
    Path storageFolder = Path.of("vsum/sample-data").toAbsolutePath();
    VirtualModel vsum = createDefaultVirtualModel(storageFolder);

    // E3 + Rule D cascade — see README.md §3.4.
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

    // Rule B's one ambiguous case — a PortUsage with no fixed InPort/OutPort target — prompts interactively instead of staying silently unmapped. See README.md §3.2 Rule B note.
    modifyView(
        getDefaultView(vsum).withChangeRecordingTrait(),
        (CommittableView v) -> {
          Package sysmlRoot = v.getRootObjects(Package.class).iterator().next();
          PartUsage part = SysMLFactory.eINSTANCE.createPartUsage();
          part.setDeclaredName("SensorBus");
          attach(sysmlRoot, part);

          PortUsage port = SysMLFactory.eINSTANCE.createPortUsage();
          port.setDeclaredName("dataLine");
          port.setDirection(FeatureDirectionKind.INOUT);
          attach(part, port);
        });
  }

  private static void attach(org.omg.sysml.lang.sysml.Element parent, org.omg.sysml.lang.sysml.Element member) {
    FeatureMembership membership = SysMLFactory.eINSTANCE.createFeatureMembership();
    parent.getOwnedRelationship().add(membership);
    membership.getOwnedRelatedElement().add(member);
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
