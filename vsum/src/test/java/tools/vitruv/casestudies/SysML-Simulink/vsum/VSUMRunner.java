package tools.vitruv.methodologisttemplate.vsum;

// Shared test-side helpers for the SysML <-> Simulink VSUM; every test under SysML-SimulinkTests/ goes through these methods instead of touching the EMF model API directly.

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

import org.omg.sysml.lang.sysml.SysMLFactory;
import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.PortUsage;
import org.omg.sysml.lang.sysml.FlowUsage;
import org.omg.sysml.lang.sysml.AttributeUsage;
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
import hu.bme.mit.massif.simulink.Port;
import hu.bme.mit.massif.simulink.Port;
import hu.bme.mit.massif.simulink.Trigger;
import hu.bme.mit.massif.simulink.Enable;
import hu.bme.mit.massif.simulink.State;
import hu.bme.mit.massif.simulink.Parameter;
import hu.bme.mit.massif.simulink.SingleConnection;
import hu.bme.mit.massif.simulink.MultiConnection;
import hu.bme.mit.massif.simulink.BusSelector;
import hu.bme.mit.massif.simulink.BusCreator;
import hu.bme.mit.massif.simulink.BusSignalMapping;
import hu.bme.mit.massif.simulink.Goto;
import hu.bme.mit.massif.simulink.From;
import hu.bme.mit.massif.simulink.GotoTagVisibility;
import hu.bme.mit.massif.simulink.ModelReference;
import hu.bme.mit.massif.simulink.PortBlock;
import hu.bme.mit.massif.simulink.OutPortBlock;
import hu.bme.mit.massif.simulink.InPortBlock;
import hu.bme.mit.massif.simulink.TriggerBlock;
import hu.bme.mit.massif.simulink.EnableBlock;
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

	// kept alive after createDefaultVirtualModel so tests can script responses before an interactive change — see resolveInoutPortUsage in SysMLToSimulink.reactions.
	private TestUserInteraction userInteraction;

	public InternalVirtualModel createDefaultVirtualModel(Path projectPath) throws Exception {
		userInteraction = new TestUserInteraction();
		InternalVirtualModel vsum = new VirtualModelBuilder()
				.withStorageFolder(projectPath)
				.withUserInteractorForResultProvider(
						new TestUserInteraction.ResultProvider(userInteraction))
				.withChangePropagationSpecifications(List.of(
						new SysmlToSimulinkChangePropagationSpecification(),
						new SimulinkToSysmlChangePropagationSpecification()))
				.buildAndInitialize();
		vsum.setChangePropagationMode(tools.vitruv.change.propagation.ChangePropagationMode.TRANSITIVE_CYCLIC);
		return vsum;
	}

	public TestUserInteraction getUserInteraction() {
		return userInteraction;
	}

	// root registration
	//
	// SimulinkModel <-> Package has no correspondence, deliberately — they're independent top-level roots, and propagation only ever needs the contained elements' own correspondences.
	// VSUMExample wraps Blocks in SimulinkModel.contains for real usage; this harness skips that wrapper and registers each Block as its own root directly, for test convenience.

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

	public String addPartAttributeUsage(VirtualModel vsum, String partName, String attrName) {
		CommittableView view = getSysmlView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			PartUsage part = findByNameAndType(getSysmlRoot(v), PartUsage.class, partName);
			AttributeUsage attr = SysMLFactory.eINSTANCE.createAttributeUsage();
			attr.setDeclaredName(attrName);
			attach(part, attr);
		});
		return attrName;
	}

	public String addPortAttributeUsage(VirtualModel vsum, String portName, String attrName) {
		CommittableView view = getSysmlView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			PortUsage port = findByNameAndType(getSysmlRoot(v), PortUsage.class, portName);
			AttributeUsage attr = SysMLFactory.eINSTANCE.createAttributeUsage();
			attr.setDeclaredName(attrName);
			attach(port, attr);
		});
		return attrName;
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

	// mirrors SimulinkToSysML.reactions' attachAsMember — SysML has no direct parent.getNestedPart().add(child).
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
			Block block = findBlockAcrossRoots(v, blockName);
			InPort port = SimulinkFactory.eINSTANCE.createInPort();
			setSimulinkName(port, portName);
			block.getPorts().add(port);
		});
		return portName;
	}

	public String addOutPort(VirtualModel vsum, String blockName, String portName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			Block block = findBlockAcrossRoots(v, blockName);
			OutPort port = SimulinkFactory.eINSTANCE.createOutPort();
			setSimulinkName(port, portName);
			block.getPorts().add(port);
		});
		return portName;
	}

	public String addTrigger(VirtualModel vsum, String blockName, String portName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			Block block = findByNameAndType(getSimulinkRoot(v), Block.class, blockName);
			Trigger port = SimulinkFactory.eINSTANCE.createTrigger();
			setSimulinkName(port, portName);
			block.getPorts().add(port);
		});
		return portName;
	}

	public String addEnable(VirtualModel vsum, String blockName, String portName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			Block block = findByNameAndType(getSimulinkRoot(v), Block.class, blockName);
			Enable port = SimulinkFactory.eINSTANCE.createEnable();
			setSimulinkName(port, portName);
			block.getPorts().add(port);
		});
		return portName;
	}

	public String addState(VirtualModel vsum, String blockName, String portName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			Block block = findByNameAndType(getSimulinkRoot(v), Block.class, blockName);
			State port = SimulinkFactory.eINSTANCE.createState();
			setSimulinkName(port, portName);
			block.getPorts().add(port);
		});
		return portName;
	}

	public String addBlockParameter(VirtualModel vsum, String blockName, String paramName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			Block block = findByNameAndType(getSimulinkRoot(v), Block.class, blockName);
			Parameter param = SimulinkFactory.eINSTANCE.createParameter();
			param.setName(paramName);
			block.getParameters().add(param);
		});
		return paramName;
	}

	public String addPortParameter(VirtualModel vsum, String portName, String paramName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			Port port = findByNameAndType(getSimulinkRoot(v), Port.class, portName);
			Parameter param = SimulinkFactory.eINSTANCE.createParameter();
			param.setName(paramName);
			port.getParameters().add(param);
		});
		return paramName;
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

	private <T> T findPortAcrossRoots(View v, Class<T> type, String name) {
		for (EObject root : v.getRootObjects(Block.class)) {
			T found = findByNameAndType(root, type, name);
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

	public void addMultiConnection(VirtualModel vsum, String outPortName, String... inPortNames) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			OutPort outPort = findByNameAndType(getSimulinkRoot(v), OutPort.class, outPortName);
			MultiConnection multi = SimulinkFactory.eINSTANCE.createMultiConnection();
			// sets multi.from via the OutPort.connection eOpposite — branches never get their own `from`.
			outPort.setConnection(multi);
			for (String inPortName : inPortNames) {
				InPort inPort = findByNameAndType(getSimulinkRoot(v), InPort.class, inPortName);
				SingleConnection branch = SimulinkFactory.eINSTANCE.createSingleConnection();
				branch.setTo(inPort);
				multi.getConnections().add(branch);
			}
		});
	}

	// MultiConnection has no name of its own — found via the OutPort it's attached to instead.
	public void deleteMultiConnection(VirtualModel vsum, String outPortName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			OutPort outPort = findByNameAndType(getSimulinkRoot(v), OutPort.class, outPortName);
			if (outPort != null && outPort.getConnection() != null) {
				EcoreUtil.delete(outPort.getConnection(), true);
			}
		});
	}

	// deletes just one branch, found by its `to` InPort's name, leaving the MultiConnection and its other branches intact.
	public void deleteMultiConnectionBranch(VirtualModel vsum, String outPortName, String toInPortName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			OutPort outPort = findByNameAndType(getSimulinkRoot(v), OutPort.class, outPortName);
			MultiConnection multi = (MultiConnection) outPort.getConnection();
			for (SingleConnection branch : multi.getConnections()) {
				if (toInPortName.equals(effectiveName(branch.getTo()))) {
					EcoreUtil.delete(branch, true);
					return;
				}
			}
		});
	}

	// shared by every Block-subtype-as-root helper below — same shape as addBlock, different factory.
	private String addRootBlockLike(VirtualModel vsum, Path filePath, String name, Supplier<? extends Block> factory) {
		CommittableView view = getDefaultView(vsum, List.of(Block.class)).withChangeRecordingTrait();
		modifyView(view, v -> {
			Block block = factory.get();
			setSimulinkName(block, name);
			v.registerRoot(block, URI.createFileURI(filePath.toString() + "/" + name + ".simulink"));
		});
		return name;
	}

	public String addBusSelector(VirtualModel vsum, Path filePath, String name) {
		return addRootBlockLike(vsum, filePath, name, SimulinkFactory.eINSTANCE::createBusSelector);
	}

	public String addBusCreator(VirtualModel vsum, Path filePath, String name) {
		return addRootBlockLike(vsum, filePath, name, SimulinkFactory.eINSTANCE::createBusCreator);
	}

	public String addGoto(VirtualModel vsum, Path filePath, String name) {
		return addRootBlockLike(vsum, filePath, name, SimulinkFactory.eINSTANCE::createGoto);
	}

	public String addFrom(VirtualModel vsum, Path filePath, String name) {
		return addRootBlockLike(vsum, filePath, name, SimulinkFactory.eINSTANCE::createFrom);
	}

	public String addGotoTagVisibility(VirtualModel vsum, Path filePath, String name) {
		return addRootBlockLike(vsum, filePath, name, SimulinkFactory.eINSTANCE::createGotoTagVisibility);
	}

	public String addModelReference(VirtualModel vsum, Path filePath, String name) {
		return addRootBlockLike(vsum, filePath, name, SimulinkFactory.eINSTANCE::createModelReference);
	}

	// shared by every PortBlock-subtype helper below — wraps an existing Port and nests the PortBlock as one of the subsystem's subBlocks.
	private void addPortBlockLike(VirtualModel vsum, String subsystemName, String portBlockName, String wrappedPortName, Supplier<? extends PortBlock> factory) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			SubSystem subsystem = (SubSystem) findBlockAcrossRoots(v, subsystemName);
			Port wrappedPort = findByNameAndType(getSimulinkRoot(v), Port.class, wrappedPortName);
			PortBlock portBlock = factory.get();
			setSimulinkName(portBlock, portBlockName);
			portBlock.setPort(wrappedPort);
			subsystem.getSubBlocks().add(portBlock);
		});
	}

	public void addOutPortBlock(VirtualModel vsum, String subsystemName, String portBlockName, String wrappedPortName) {
		addPortBlockLike(vsum, subsystemName, portBlockName, wrappedPortName, SimulinkFactory.eINSTANCE::createOutPortBlock);
	}

	public void addInPortBlock(VirtualModel vsum, String subsystemName, String portBlockName, String wrappedPortName) {
		addPortBlockLike(vsum, subsystemName, portBlockName, wrappedPortName, SimulinkFactory.eINSTANCE::createInPortBlock);
	}

	public void addTriggerBlock(VirtualModel vsum, String subsystemName, String portBlockName, String wrappedPortName) {
		addPortBlockLike(vsum, subsystemName, portBlockName, wrappedPortName, SimulinkFactory.eINSTANCE::createTriggerBlock);
	}

	public void addEnableBlock(VirtualModel vsum, String subsystemName, String portBlockName, String wrappedPortName) {
		addPortBlockLike(vsum, subsystemName, portBlockName, wrappedPortName, SimulinkFactory.eINSTANCE::createEnableBlock);
	}

	// gotoBlock must be set before the From is rooted, in the same transaction — it can't be set later, gotoBlock is an unreactable EReference. OutPort is added separately afterward via addOutPort.
	public String addFromLinkedToGoto(VirtualModel vsum, Path filePath, String fromName, String gotoName) {
		CommittableView view = getDefaultView(vsum, List.of(Block.class)).withChangeRecordingTrait();
		modifyView(view, v -> {
			Goto gotoBlock = (Goto) findBlockAcrossRoots(v, gotoName);
			From fromBlock = SimulinkFactory.eINSTANCE.createFrom();
			setSimulinkName(fromBlock, fromName);
			fromBlock.setGotoBlock(gotoBlock);
			v.registerRoot(fromBlock, URI.createFileURI(filePath.toString() + "/" + fromName + ".simulink"));
		});
		return fromName;
	}

	public String addBusSignalMapping(VirtualModel vsum, String selectorName, String mappingFromPortName, String mappingToPortName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			BusSelector selector = (BusSelector) findBlockAcrossRoots(v, selectorName);
			OutPort from = findPortAcrossRoots(v, OutPort.class, mappingFromPortName);
			OutPort to = findPortAcrossRoots(v, OutPort.class, mappingToPortName);
			BusSignalMapping mapping = SimulinkFactory.eINSTANCE.createBusSignalMapping();
			mapping.setMappingFrom(from);
			mapping.setMappingTo(to);
			selector.getMappings().add(mapping);
		});
		return mappingFromPortName + "_to_" + mappingToPortName;
	}

	// BusSignalMapping has no name of its own, so it can't go through deleteFromSimulink — found by searching every BusSelector root for the mapping with matching port names.
	public void deleteBusSignalMapping(VirtualModel vsum, String mappingFromPortName, String mappingToPortName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			for (EObject root : v.getRootObjects(Block.class)) {
				BusSignalMapping mapping = findMappingByPorts(root, mappingFromPortName, mappingToPortName);
				if (mapping != null) {
					EcoreUtil.delete(mapping, true);
					return;
				}
			}
		});
	}

	private BusSignalMapping findMappingByPorts(EObject root, String fromName, String toName) {
		if (root instanceof BusSelector selector) {
			for (BusSignalMapping m : selector.getMappings()) {
				if (fromName.equals(effectiveName(m.getMappingFrom())) && toName.equals(effectiveName(m.getMappingTo()))) {
					return m;
				}
			}
		}
		for (EObject child : root.eContents()) {
			BusSignalMapping found = findMappingByPorts(child, fromName, toName);
			if (found != null) return found;
		}
		return null;
	}

	public void renameInSimulink(VirtualModel vsum, String oldName, Class<? extends EObject> type, String newName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> setSimulinkName((hu.bme.mit.massif.simulink.SimulinkElement) findAnywhereInSimulink(v, type, oldName), newName));
	}

	// Parameter isn't a SimulinkElement, so it can't go through renameInSimulink/setSimulinkName.
	public void renameParameterInSimulink(VirtualModel vsum, String oldName, String newName) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			Parameter param = findByNameAndType(getSimulinkRoot(v), Parameter.class, oldName);
			param.setName(newName);
		});
	}

	public void deleteFromSimulink(VirtualModel vsum, String name, Class<? extends EObject> type) {
		CommittableView view = getSimulinkView(vsum).withChangeRecordingTrait();
		modifyView(view, v -> {
			EObject el = findAnywhereInSimulink(v, type, name);
			// EcoreUtil.delete cleans up cross-references (e.g. a surviving InPort.connection) that remove() would leave dangling.
			if (el != null) EcoreUtil.delete(el, true);
		});
	}

	// searches every registered Block root, not just the first — same fix as findAnywhereInSysml on the SysML side.
	private <T extends EObject> T findAnywhereInSimulink(View v, Class<T> type, String name) {
		for (EObject root : v.getRootObjects()) {
			T found = findByNameAndType(root, type, name);
			if (found != null) return found;
		}
		return null;
	}

	// simulinkRef.name, not SimulinkElement.name — the latter is derived.
	private void setSimulinkName(hu.bme.mit.massif.simulink.SimulinkElement element, String name) {
		// must mutate the existing IdentifierReference in place, or the P2/P4 rename reactions never see an attribute-replaced event.
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

	// counts every sysml object of the given type and name — used to assert exactly one exists, no duplicates.
	public <T extends EObject> int countMatchingInSysml(VirtualModel vsum, String name, Class<T> type) {
		int count = 0;
		for (EObject root : getSysmlView(vsum).getRootObjects()) {
			count += countByNameAndType(root, type, name);
		}
		for (EObject root : getDefaultView(vsum, List.of(PartUsage.class, ActionUsage.class, RequirementUsage.class)).getRootObjects()) {
			count += countByNameAndType(root, type, name);
		}
		return count;
	}

	private <T> int countByNameAndType(EObject root, Class<T> type, String name) {
		int count = (type.isInstance(root) && name.equals(effectiveName(root))) ? 1 : 0;
		for (EObject child : root.eContents()) {
			count += countByNameAndType(child, type, name);
		}
		return count;
	}

	// counts every sysml object of the given type, regardless of name — used for model-wide completeness checks (C3 etc.).
	public <T extends EObject> int countAllInSysml(VirtualModel vsum, Class<T> type) {
		int count = 0;
		for (EObject root : getSysmlView(vsum).getRootObjects()) {
			count += countByType(root, type);
		}
		for (EObject root : getDefaultView(vsum, List.of(PartUsage.class, ActionUsage.class, RequirementUsage.class)).getRootObjects()) {
			count += countByType(root, type);
		}
		return count;
	}

	private <T> int countByType(EObject root, Class<T> type) {
		int count = type.isInstance(root) ? 1 : 0;
		for (EObject child : root.eContents()) {
			count += countByType(child, type);
		}
		return count;
	}

	// finds a FlowUsage by its source/target PortUsage names — needed for FlowUsages with no name of their own (branch connections, bus signal mappings).
	// Compares by name, not identity — every getDefaultView/getSysmlView call opens a fresh view with its own object instances.
	public FlowUsage getFlowUsageBetween(VirtualModel vsum, String fromPortName, String toPortName) {
		for (EObject root : getSysmlView(vsum).getRootObjects()) {
			FlowUsage found = findFlowUsageBetween(root, fromPortName, toPortName);
			if (found != null) return found;
		}
		for (EObject root : getDefaultView(vsum, List.of(PartUsage.class, ActionUsage.class, RequirementUsage.class)).getRootObjects()) {
			FlowUsage found = findFlowUsageBetween(root, fromPortName, toPortName);
			if (found != null) return found;
		}
		return null;
	}

	private FlowUsage findFlowUsageBetween(EObject root, String fromPortName, String toPortName) {
		if (root instanceof FlowUsage flow
				&& flow.getSource().stream().anyMatch(e -> fromPortName.equals(effectiveName(e)))
				&& flow.getTarget().stream().anyMatch(e -> toPortName.equals(effectiveName(e)))) {
			return flow;
		}
		for (EObject child : root.eContents()) {
			FlowUsage found = findFlowUsageBetween(child, fromPortName, toPortName);
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
		// Parameter isn't a SimulinkElement (no simulinkRef indirection) — it has a plain settable name attribute.
		if (el instanceof Parameter p) return p.getName();
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
