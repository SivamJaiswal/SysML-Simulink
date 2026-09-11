package tools.vitruv.methodologisttemplate.vsum;

// Shared test-side helpers for the SysML <-> Simulink VSUM; every test under SysML-SimulinkTests/ goes through these methods instead of touching the EMF model API directly.

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

import org.omg.sysml.lang.sysml.SysMLFactory;
import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.PortUsage;
import org.omg.sysml.lang.sysml.FlowUsage;
import org.omg.sysml.lang.sysml.ActionUsage;
import org.omg.sysml.lang.sysml.RequirementUsage;
import org.omg.sysml.lang.sysml.FeatureDirectionKind;
import org.omg.sysml.lang.sysml.FeatureMembership;
import org.omg.sysml.lang.sysml.Package;

import hu.bme.mit.massif.simulink.SimulinkFactory;
import hu.bme.mit.massif.simulink.Block;
import hu.bme.mit.massif.simulink.SubSystem;
import hu.bme.mit.massif.simulink.InPort;
import hu.bme.mit.massif.simulink.OutPort;
import hu.bme.mit.massif.simulink.SingleConnection;
import hu.bme.mit.massif.simulink.IdentifierReference;

import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import mir.reactions.sysmlToSimulink.SysmlToSimulinkChangePropagationSpecification;
import mir.reactions.simulinkToSysml.SimulinkToSysmlChangePropagationSpecification;

public class VSUMRunner {

	public InternalVirtualModel createDefaultVirtualModel(Path projectPath) throws Exception {
		InternalVirtualModel vsum = new VirtualModelBuilder()
				.withStorageFolder(projectPath)
				.withUserInteractorForResultProvider(
						new TestUserInteraction.ResultProvider(new TestUserInteraction()))
				.withChangePropagationSpecifications(List.of(
						new SysmlToSimulinkChangePropagationSpecification(),
						new SimulinkToSysmlChangePropagationSpecification()))
				.buildAndInitialize();
		vsum.setChangePropagationMode(tools.vitruv.change.propagation.ChangePropagationMode.TRANSITIVE_CYCLIC);
		return vsum;
	}

	// root registration

	public void registerRootObjects(VirtualModel vsum, Path projectPath) {
		CommittableView view = getDefaultView(vsum, List.of(Package.class)).withChangeRecordingTrait();
		modifyView(view, v -> {
			Package pkg = SysMLFactory.eINSTANCE.createPackage();
			v.registerRoot(pkg, URI.createFileURI(projectPath.toString() + "/example.sysml"));
		});
	}

	// SysML-side mutators

	public String addPartUsage(VirtualModel vsum, String name) {
		CommittableView view = getSysmlView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			Package pkg = getSysmlRoot(v);
			PartUsage part = SysMLFactory.eINSTANCE.createPartUsage();
			part.setDeclaredName(name);
			attach(pkg, part);
		});
		return name;
	}

	public String addNestedPartUsage(VirtualModel vsum, String parentName, String childName) {
		CommittableView view = getSysmlView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			PartUsage parent = findByNameAndType(getSysmlRoot(v), PartUsage.class, parentName);
			PartUsage child = SysMLFactory.eINSTANCE.createPartUsage();
			child.setDeclaredName(childName);
			attach(parent, child);
		});
		return childName;
	}

	public String addPortUsage(VirtualModel vsum, String partName, String portName, FeatureDirectionKind direction) {
		CommittableView view = getSysmlView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			PartUsage part = findByNameAndType(getSysmlRoot(v), PartUsage.class, partName);
			PortUsage port = SysMLFactory.eINSTANCE.createPortUsage();
			port.setDeclaredName(portName);
			port.setDirection(direction);
			attach(part, port);
		});
		return portName;
	}

	public String addFlowUsage(VirtualModel vsum, String outPortName, String inPortName) {
		CommittableView view = getSysmlView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			PortUsage outPort = findByNameAndType(getSysmlRoot(v), PortUsage.class, outPortName);
			PortUsage inPort = findByNameAndType(getSysmlRoot(v), PortUsage.class, inPortName);
			FlowUsage flow = SysMLFactory.eINSTANCE.createFlowUsage();
			// declaredName is required so deleteFromSysml can find this flow by name later.
			flow.setDeclaredName(outPortName + "->" + inPortName);
			// FlowUsage.flowEnd is derived — Relationship.source/target are the real, settable references used here.
			flow.getSource().add(outPort);
			flow.getTarget().add(inPort);
			attach(getSysmlRoot(v), flow);
		});
		return outPortName + "->" + inPortName;
	}

	public void renameInSysml(VirtualModel vsum, String oldName, Class<? extends EObject> type, String newName) {
		// searches every sysml root, not just the Package — a root-level PartUsage's nested elements aren't reachable from the Package tree.
		CommittableView view = getSysmlSearchView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> setDeclaredName(findAnywhereInSysml(v, type, oldName), newName));
	}

	public void deleteFromSysml(VirtualModel vsum, String name, Class<? extends EObject> type) {
		CommittableView view = getSysmlSearchView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			EObject el = findAnywhereInSysml(v, type, name);
			// EcoreUtil.delete (not remove) cleans up cross-references like nestedPart, or XMI save rejects them as dangling.
			if (el != null) EcoreUtil.delete(el, true);
		});
	}

	// mirrors SimulinkToSysML.reactions' attachAsMember — SysML has no direct "parent.getNestedPart().add(child)".
	private void attach(org.omg.sysml.lang.sysml.Element parent, org.omg.sysml.lang.sysml.Element member) {
		FeatureMembership membership = SysMLFactory.eINSTANCE.createFeatureMembership();
		parent.getOwnedRelationship().add(membership);
		membership.getOwnedRelatedElement().add(member);
	}

	// Simulink-side mutators

	public String addBlock(VirtualModel vsum, Path filePath, String name) {
		return addBlock(vsum, filePath, name, false);
	}

	// asSubSystem=true lets a later addSubBlock call attach a child — SubSystem is a distinct EClass, not a flag on Block.
	public String addBlock(VirtualModel vsum, Path filePath, String name, boolean asSubSystem) {
		// registers the Block itself as the resource root (not a SimulinkModel wrapper), so registerRoot actually tracks it.
		CommittableView view = getDefaultView(vsum, List.of(Block.class)).withChangeRecordingTrait();
		modifyView(view, v -> {
			Block block = asSubSystem ? SimulinkFactory.eINSTANCE.createSubSystem() : SimulinkFactory.eINSTANCE.createBlock();
			setSimulinkName(block, name);
			v.registerRoot(block, URI.createFileURI(filePath.toString() + "/" + name + ".simulink"));
		});
		return name;
	}

	public String addSubBlock(VirtualModel vsum, String parentName, String childName, boolean asSubSystem) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			SubSystem parent = (SubSystem) findByNameAndType(getSimulinkRoot(v), Block.class, parentName);
			Block child = asSubSystem ? SimulinkFactory.eINSTANCE.createSubSystem() : SimulinkFactory.eINSTANCE.createBlock();
			setSimulinkName(child, childName);
			parent.getSubBlocks().add(child);
		});
		return childName;
	}

	public String addInPort(VirtualModel vsum, String blockName, String portName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			Block block = findByNameAndType(getSimulinkRoot(v), Block.class, blockName);
			InPort port = SimulinkFactory.eINSTANCE.createInPort();
			setSimulinkName(port, portName);
			block.getPorts().add(port);
		});
		return portName;
	}

	public String addOutPort(VirtualModel vsum, String blockName, String portName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			Block block = findByNameAndType(getSimulinkRoot(v), Block.class, blockName);
			OutPort port = SimulinkFactory.eINSTANCE.createOutPort();
			setSimulinkName(port, portName);
			block.getPorts().add(port);
		});
		return portName;
	}

	// moves a Block found across ANY registered root into a SubSystem, exercising S3 rather than E3's creation-time parent lookup.
	public void reparentBlock(VirtualModel vsum, String blockName, String newParentName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			Block block = findBlockAcrossRoots(v, blockName);
			SubSystem newParent = (SubSystem) findBlockAcrossRoots(v, newParentName);
			newParent.getSubBlocks().add(block);
		});
	}

	private Block findBlockAcrossRoots(View v, String name) {
		for (EObject root : v.getRootObjects(Block.class)) {
			Block found = findByNameAndType(root, Block.class, name);
			if (found != null) return found;
		}
		return null;
	}

	public void addSingleConnection(VirtualModel vsum, String outPortName, String inPortName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			OutPort outPort = findByNameAndType(getSimulinkRoot(v), OutPort.class, outPortName);
			InPort inPort = findByNameAndType(getSimulinkRoot(v), InPort.class, inPortName);
			SingleConnection connection = SimulinkFactory.eINSTANCE.createSingleConnection();
			connection.setFrom(outPort);
			connection.setTo(inPort);
			outPort.setConnection(connection);
		});
	}

	public void renameInSimulink(VirtualModel vsum, String oldName, Class<? extends EObject> type, String newName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> setSimulinkName((hu.bme.mit.massif.simulink.SimulinkElement) findByNameAndType(getSimulinkRoot(v), type, oldName), newName));
	}

	public void deleteFromSimulink(VirtualModel vsum, String name, Class<? extends EObject> type) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			EObject el = findByNameAndType(getSimulinkRoot(v), type, name);
			// EcoreUtil.delete cleans up cross-references (e.g. a surviving InPort.connection) that remove() would leave dangling.
			if (el != null) EcoreUtil.delete(el, true);
		});
	}

	// simulinkRef.name, not SimulinkElement.name — the latter is derived.
	private void setSimulinkName(hu.bme.mit.massif.simulink.SimulinkElement element, String name) {
		// must mutate the existing IdentifierReference in place, or the P2/P4 rename reactions never see an "attribute replaced" event.
		IdentifierReference ref = element.getSimulinkRef();
		if (ref == null) {
			ref = SimulinkFactory.eINSTANCE.createIdentifierReference();
			element.setSimulinkRef(ref);
		}
		ref.setName(name);
	}

	// correspondence lookup used by every test — each call builds a fresh view, so assertions compare by name, never by reference.

	public <T extends EObject> T getCorrespondingInSimulink(VirtualModel vsum, String sourceName, Class<T> targetType) {
		for (EObject root : getSimulinkView(vsum).getRootObjects()) {
			T found = findByNameAndType(root, targetType, sourceName);
			if (found != null) return found;
		}
		for (EObject root : getDefaultView(vsum, List.of(Block.class)).getRootObjects()) {
			T found = findByNameAndType(root, targetType, sourceName);
			if (found != null) return found;
		}
		return null;
	}

	public <T extends EObject> T getCorrespondingInSysml(VirtualModel vsum, String sourceName, Class<T> targetType) {
		for (EObject root : getSysmlView(vsum).getRootObjects()) {
			T found = findByNameAndType(root, targetType, sourceName);
			if (found != null) return found;
		}
		for (EObject root : getDefaultView(vsum, List.of(PartUsage.class, ActionUsage.class, RequirementUsage.class)).getRootObjects()) {
			T found = findByNameAndType(root, targetType, sourceName);
			if (found != null) return found;
		}
		return null;
	}

	// plumbing

	private View getSysmlView(VirtualModel vsum) {
		return getDefaultView(vsum, List.of(Package.class));
	}

	private View getSimulinkView(VirtualModel vsum) {
		return getDefaultView(vsum, List.of(Block.class));
	}

	// selects every sysml root kind at once (Package plus any independently-persisted PartUsage/ActionUsage/RequirementUsage root).
	private View getSysmlSearchView(VirtualModel vsum) {
		return getDefaultView(vsum, List.of(Package.class, PartUsage.class, ActionUsage.class, RequirementUsage.class));
	}

	private <T extends EObject> T findAnywhereInSysml(View v, Class<T> type, String name) {
		for (EObject root : v.getRootObjects()) {
			T found = findByNameAndType(root, type, name);
			if (found != null) return found;
		}
		return null;
	}

	private Package getSysmlRoot(View v) {
		return v.getRootObjects(Package.class).iterator().next();
	}

	private Block getSimulinkRoot(View v) {
		return v.getRootObjects(Block.class).iterator().next();
	}

	private View getDefaultView(VirtualModel vsum, Collection<Class<?>> rootTypes) {
		var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("default"));
		selector.getSelectableElements().stream()
				.filter(element -> rootTypes.stream().anyMatch(it -> it.isInstance(element)))
				.forEach(it -> selector.setSelected(it, true));
		return selector.createView();
	}

	@SuppressWarnings("unchecked")
	private <T> T findByNameAndType(EObject root, Class<T> type, String name) {
		if (type.isInstance(root) && name.equals(effectiveName(root))) {
			return (T) root;
		}
		for (EObject child : root.eContents()) {
			T found = findByNameAndType(child, type, name);
			if (found != null) return found;
		}
		return null;
	}

	private String effectiveName(EObject el) {
		if (el instanceof org.omg.sysml.lang.sysml.Element e) return e.getDeclaredName();
		if (el instanceof hu.bme.mit.massif.simulink.SimulinkElement e) return e.getSimulinkRef() == null ? null : e.getSimulinkRef().getName();
		return null;
	}

	private void setDeclaredName(EObject el, String name) {
		((org.omg.sysml.lang.sysml.Element) el).setDeclaredName(name);
	}

	private void modifyView(CommittableView view, Consumer<CommittableView> modificationFunction) {
		modificationFunction.accept(view);
		view.commitChanges();
	}

	public boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
		return viewAssertionFunction.apply(view);
	}
}
