package tools.vitruv.methodologisttemplate.vsum;

/*
 * Shared test-side helpers for the SysML <-> Simulink VSUM, mirroring the role
 * VSUMRunner.java plays in the ASEM-Amalthea reference project: every test under
 * SysML-SimulinkTests/ goes through these methods instead of touching the EMF
 * model API directly.
 */

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
import hu.bme.mit.massif.simulink.SimulinkModel;
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

	// ── root registration ──────────────────────────────────────────────────

	public void registerRootObjects(VirtualModel vsum, Path projectPath) {
		CommittableView view = getDefaultView(vsum, List.of(Package.class)).withChangeRecordingTrait();
		modifyView(view, v -> {
			Package pkg = SysMLFactory.eINSTANCE.createPackage();
			v.registerRoot(pkg, URI.createFileURI(projectPath.toString() + "/example.sysml"));
		});
	}

	// ── SysML-side mutators ────────────────────────────────────────────────

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
			flow.getFlowEnd().add(outPort);
			flow.getFlowEnd().add(inPort);
			attach(getSysmlRoot(v), flow);
		});
		return outPortName + "->" + inPortName;
	}

	public void renameInSysml(VirtualModel vsum, String oldName, Class<? extends EObject> type, String newName) {
		CommittableView view = getSysmlView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> setDeclaredName(findByNameAndType(getSysmlRoot(v), type, oldName), newName));
	}

	public void deleteFromSysml(VirtualModel vsum, String name, Class<? extends EObject> type) {
		CommittableView view = getSysmlView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			EObject el = findByNameAndType(getSysmlRoot(v), type, name);
			if (el != null) EcoreUtil.remove(el);
		});
	}

	// attach() constructs the real FeatureMembership containment structure described
	// in SimulinkToSysML.reactions' attachAsMember routine — see that file's header
	// comment for why a direct "parent.getNestedPart().add(child)" is not possible.
	private void attach(org.omg.sysml.lang.sysml.Element parent, org.omg.sysml.lang.sysml.Element member) {
		FeatureMembership membership = SysMLFactory.eINSTANCE.createFeatureMembership();
		parent.getOwnedRelationship().add(membership);
		membership.getOwnedRelatedElement().add(member);
	}

	// ── Simulink-side mutators ─────────────────────────────────────────────

	public String addBlock(VirtualModel vsum, Path filePath, String name) {
		return addBlock(vsum, filePath, name, false);
	}

	// asSubSystem=true when the test will nest a child under this root block later
	// (addSubBlock below requires an actual SubSystem to attach to, since plain
	// Block has no subBlocks feature at all — SubSystem is not a flag on Block, it
	// is its own EClass, same as on the reactions side, see S1 in
	// SysMLToSimulink.reactions).
	public String addBlock(VirtualModel vsum, Path filePath, String name, boolean asSubSystem) {
		CommittableView view = getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeRecordingTrait();
		modifyView(view, v -> {
			SimulinkModel model = SimulinkFactory.eINSTANCE.createSimulinkModel();
			Block block = asSubSystem ? SimulinkFactory.eINSTANCE.createSubSystem() : SimulinkFactory.eINSTANCE.createBlock();
			setSimulinkName(block, name);
			model.getContains().add(block);
			v.registerRoot(model, URI.createFileURI(filePath.toString() + "/example.simulink"));
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

	// Moves an existing Block (found in ANY registered SimulinkModel root, not just
	// the first) into an existing SubSystem's subBlocks, exercising the S3 reaction
	// (BlockReparented, listening on "attribute replaced at simulink::Block[parent]")
	// rather than E3's creation-time parent lookup.
	public void reparentBlock(VirtualModel vsum, String blockName, String newParentName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			Block block = findBlockAcrossRoots(v, blockName);
			SubSystem newParent = (SubSystem) findBlockAcrossRoots(v, newParentName);
			newParent.getSubBlocks().add(block);
		});
	}

	private Block findBlockAcrossRoots(View v, String name) {
		for (EObject root : v.getRootObjects(SimulinkModel.class)) {
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
			if (el != null) EcoreUtil.remove(el);
		});
	}

	// simulinkRef.name, not SimulinkElement.name — the latter is derived, see
	// Simulink_Metamodel_Description.md §3.
	private void setSimulinkName(hu.bme.mit.massif.simulink.SimulinkElement element, String name) {
		IdentifierReference ref = SimulinkFactory.eINSTANCE.createIdentifierReference();
		ref.setName(name);
		element.setSimulinkRef(ref);
	}

	// ── correspondence lookup used by every test ───────────────────────────
	// Each call builds a fresh view, so assertions compare by name, never by
	// reference — same rationale as the reference project's VSUMRunner.

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

	// ── plumbing ────────────────────────────────────────────────────────────

	private View getSysmlView(VirtualModel vsum) {
		return getDefaultView(vsum, List.of(Package.class));
	}

	private View getSimulinkView(VirtualModel vsum) {
		return getDefaultView(vsum, List.of(SimulinkModel.class));
	}

	private Package getSysmlRoot(View v) {
		return v.getRootObjects(Package.class).iterator().next();
	}

	private SimulinkModel getSimulinkRoot(View v) {
		return v.getRootObjects(SimulinkModel.class).iterator().next();
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
