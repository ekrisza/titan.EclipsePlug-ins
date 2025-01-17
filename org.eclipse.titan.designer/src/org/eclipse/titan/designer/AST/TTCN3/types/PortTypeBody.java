/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.AST.TTCN3.types;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jface.text.templates.Template;
import org.eclipse.titan.designer.AST.ASTNode;
import org.eclipse.titan.designer.AST.ASTVisitor;
import org.eclipse.titan.designer.AST.Assignment;
import org.eclipse.titan.designer.AST.Assignment.Assignment_type;
import org.eclipse.titan.designer.AST.BridgingNamedNode;
import org.eclipse.titan.designer.AST.ILocateableNode;
import org.eclipse.titan.designer.AST.INamedNode;
import org.eclipse.titan.designer.AST.ISubReference;
import org.eclipse.titan.designer.AST.ISubReference.Subreference_type;
import org.eclipse.titan.designer.AST.IType;
import org.eclipse.titan.designer.AST.IType.Type_type;
import org.eclipse.titan.designer.AST.Location;
import org.eclipse.titan.designer.AST.NULL_Location;
import org.eclipse.titan.designer.AST.Reference;
import org.eclipse.titan.designer.AST.ReferenceFinder;
import org.eclipse.titan.designer.AST.ReferenceFinder.Hit;
import org.eclipse.titan.designer.AST.Scope;
import org.eclipse.titan.designer.AST.Type;
import org.eclipse.titan.designer.AST.TTCN3.IIncrementallyUpdateable;
import org.eclipse.titan.designer.AST.TTCN3.attributes.AttributeSpecification;
import org.eclipse.titan.designer.AST.TTCN3.attributes.ExtensionAttribute;
import org.eclipse.titan.designer.AST.TTCN3.attributes.ExtensionAttribute.ExtensionAttribute_type;
import org.eclipse.titan.designer.AST.TTCN3.attributes.FunctionTypeMappingTarget;
import org.eclipse.titan.designer.AST.TTCN3.attributes.PortTypeAttribute;
import org.eclipse.titan.designer.AST.TTCN3.attributes.Qualifiers;
import org.eclipse.titan.designer.AST.TTCN3.attributes.SingleWithAttribute;
import org.eclipse.titan.designer.AST.TTCN3.attributes.SingleWithAttribute.Attribute_Type;
import org.eclipse.titan.designer.AST.TTCN3.attributes.TypeMapping;
import org.eclipse.titan.designer.AST.TTCN3.attributes.TypeMappingTarget;
import org.eclipse.titan.designer.AST.TTCN3.attributes.TypeMappingTarget.TypeMapping_type;
import org.eclipse.titan.designer.AST.TTCN3.attributes.TypeMappings;
import org.eclipse.titan.designer.AST.TTCN3.attributes.UserPortTypeAttribute;
import org.eclipse.titan.designer.AST.TTCN3.attributes.WithAttributesPath;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Def_Function;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Def_Var;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Def_Var_Template;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Definition;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Definitions;
import org.eclipse.titan.designer.AST.TTCN3.definitions.FormalParameterList;
import org.eclipse.titan.designer.AST.TTCN3.definitions.TTCN3Module;
import org.eclipse.titan.designer.AST.TTCN3.types.PortGenerator.FunctionPrototype_Type;
import org.eclipse.titan.designer.AST.TTCN3.types.PortGenerator.MessageMappedTypeInfo;
import org.eclipse.titan.designer.AST.TTCN3.types.PortGenerator.MessageTypeMappingTarget;
import org.eclipse.titan.designer.AST.TTCN3.types.PortGenerator.PortDefinition;
import org.eclipse.titan.designer.AST.TTCN3.types.PortGenerator.PortType;
import org.eclipse.titan.designer.AST.TTCN3.types.PortGenerator.TestportType;
import org.eclipse.titan.designer.AST.TTCN3.types.PortGenerator.messageTypeInfo;
import org.eclipse.titan.designer.AST.TTCN3.types.PortGenerator.procedureSignatureInfo;
import org.eclipse.titan.designer.compiler.JavaGenData;
import org.eclipse.titan.designer.editors.ProposalCollector;
import org.eclipse.titan.designer.editors.ttcn3editor.TTCN3CodeSkeletons;
import org.eclipse.titan.designer.graphics.ImageCache;
import org.eclipse.titan.designer.parsers.CompilationTimeStamp;
import org.eclipse.titan.designer.parsers.extensionattributeparser.ExtensionAttributeAnalyzer;
import org.eclipse.titan.designer.parsers.ttcn3parser.ReParseException;
import org.eclipse.titan.designer.parsers.ttcn3parser.TTCN3ReparseUpdater;

/**
 * @author Kristof Szabados
 * */
public final class PortTypeBody extends ASTNode implements ILocateableNode, IIncrementallyUpdateable {
	private static final String FULLNAMEPART1 = ".<in_list>";
	private static final String FULLNAMEPART2 = ".<out_list>";
	private static final String FULLNAMEPART3 = ".<inout_list>";
	private static final String FULLNAMEPART4 = ".<incoming_signatures>";
	private static final String FULLNAMEPART5 = ".<outgoing_signatures>";
	private static final String FULLNAMEPART6 = ".<incoming_messages>";
	private static final String FULLNAMEPART7 = ".<outgoing_messages>";

	private static final String REDUNDANTINALL = "Redundant `in all' and `inout all'";
	private static final String REDUNDANTOUTALL = "Redundant `out all' and `inout all' directives";
	private static final String UNSUPPORTEDINOUTALL = "Unsupported `inout all' directive was ignored";
	private static final String UNSUPPORTEDINALL = "Unsupported `in all' directive was ignored";
	private static final String UNSUPPORTEDOUTALL = "Unsupported `out all' directive was ignored";
	private static final String SIGNATUREONMESSAGEPORT = "Signature `{0}'' cannot be used on a message based port";
	private static final String DATAONPROCEDUREPORT = "Data type `{0}'' cannot be {1} on procedure based port";
	private static final String DUPLICATEDINSIGNATURE = "Duplicate incoming signature `{0}''";
	private static final String DUPLICATEDOUTSIGNATURE = "Duplicate outgoing signature `{0}''";
	private static final String DUPLICATEDINMESSAGE = "Duplicate incoming message type `{0}''";
	private static final String DUPLICATEDOUTMESSAGE = "Duplicate outgoing message type `{0}''";

	public enum OperationModes {
		OP_Message, OP_Procedure, OP_Mixed
	}

	public enum TestPortAPI_type {
		/* regular test port API */			TP_REGULAR,
		/* no test port (only connection allowed)*/	TP_INTERNAL,
		/* usage of the address type is supported*/	TP_ADDRESS
	}

	public enum PortType_type {
		/* regular port type*/							PT_REGULAR,
		/* provides the external interface for other port types*/		PT_PROVIDER,
		/* the port type uses another port type as external interface */	PT_USER
	}

	private final OperationModes operationMode;
	private TestPortAPI_type testportType;

	/** the port type as calculated during semantic checking */
	private PortType_type portType;

	/** the port type as parsed from TTCN-3 files */
	private PortType_type parsedPortType;

	private Port_Type myType;
	private boolean legacy = true;

	private List<IType> inTypes = null;
	private boolean inAll = false;
	private List<IType> outTypes = null;
	private boolean outAll = false;
	private List<IType> inoutTypes = null;
	private boolean inoutAll = false;

	private TypeSet inMessages;
	private TypeSet outMessages;
	private TypeSet inSignatures;
	private TypeSet outSignatures;

	private final ArrayList<Reference> providerReferences = new ArrayList<Reference>();
	private final ArrayList<Port_Type> providerTypes = new ArrayList<Port_Type>();
	private TypeMappings inMappings;
	private TypeMappings outMappings;

	private Definitions vardefs;
	private FormalParameterList mapParams;
	private FormalParameterList unmapParams;

	private boolean realtime;

	/** the time when this assignment was checked the last time. */
	private CompilationTimeStamp lastTimeChecked;
	private CompilationTimeStamp lastTimeAttributesChecked;

	/**
	 * The location of the whole statement. This location encloses the statement
	 * fully, as it is used to report errors to.
	 **/
	private Location location = NULL_Location.INSTANCE;

	public PortTypeBody(final OperationModes operationMode) {
		this.operationMode = operationMode;
		testportType = TestPortAPI_type.TP_REGULAR;
		portType = PortType_type.PT_REGULAR;
		parsedPortType = PortType_type.PT_REGULAR;
	}

	public void setMyType(final Port_Type myType) {
		this.myType = myType;
	}

	@Override
	/** {@inheritDoc} */
	public StringBuilder getFullName(final INamedNode child) {
		final StringBuilder builder = super.getFullName(child);

		if (inTypes != null) {
			for (final IType inType : inTypes) {
				if (inType == child) {
					return builder.append(FULLNAMEPART1);
				}
			}
		}
		if (outTypes != null) {
			for (final IType outType : outTypes) {
				if (outType == child) {
					return builder.append(FULLNAMEPART2);
				}
			}
		}
		if (inoutTypes != null) {
			for (final IType inoutType : inoutTypes) {
				if (inoutType == child) {
					return builder.append(FULLNAMEPART3);
				}
			}
		}

		if (inMessages == child) {
			return builder.append(FULLNAMEPART4);
		} else if (outMessages == child) {
			return builder.append(FULLNAMEPART5);
		} else if (inSignatures == child) {
			return builder.append(FULLNAMEPART6);
		} else if (outSignatures == child) {
			return builder.append(FULLNAMEPART7);
		}
		for (int i = 0; i < providerReferences.size(); i++) {
			if (providerReferences.get(i) == child) {
				return builder.append(".<provider_ref>");
			}
		}
		if (inMappings == child) {
			return builder.append(".<inMappings>");
		}
		if (outMappings == child) {
			return builder.append(".<outMappings>");
		}
		if (vardefs == child) {
			return builder.append(".<port_var>");
		}
		if (mapParams == child) {
			return builder.append(".<map_params>");
		}
		if (unmapParams == child) {
			return builder.append(".<unmap_params>");
		}

		return builder;
	}

	@Override
	/** {@inheritDoc} */
	public void setLocation(final Location location) {
		this.location = location;
	}

	@Override
	/** {@inheritDoc} */
	public Location getLocation() {
		return location;
	}

	public void addInTypes(final List<IType> types) {
		if (types == null) {
			inAll = true;
		} else {
			if (inTypes == null) {
				inTypes = new ArrayList<IType>();
			}
			inTypes.addAll(types);
		}
	}

	public void addOutTypes(final List<IType> types) {
		if (types == null) {
			outAll = true;
		} else {
			if (outTypes == null) {
				outTypes = new ArrayList<IType>();
			}
			outTypes.addAll(types);
		}
	}

	public void addInoutTypes(final List<IType> types) {
		if (types == null) {
			inoutAll = true;
		} else {
			if (inoutTypes == null) {
				inoutTypes = new ArrayList<IType>();
			}
			inoutTypes.addAll(types);
		}
	}

	public void addDefinitions(final List<Definition> definitions) {
		if (vardefs == null) {
			vardefs = new Definitions();
		}

		vardefs.addDefinitions(definitions);
	}

	public void setMapParams(final FormalParameterList params) {
		if (mapParams != null) {
			location.reportSemanticError("Multiple `map' parameter lists in port type definition");

			return;
		}

		mapParams = params;
	}

	public FormalParameterList getMapParameters() {
		return mapParams;
	}

	public void setUnmapParams(final FormalParameterList params) {
		if (unmapParams != null) {
			location.reportSemanticError("Multiple `unmap' parameter lists in port type definition");

			return;
		}

		unmapParams = params;
	}

	public FormalParameterList getUnmapParameters() {
		return unmapParams;
	}

	public void setRealtime() {
		realtime = true;
	}

	@Override
	/** {@inheritDoc} */
	public void setMyScope(final Scope scope) {
		super.setMyScope(scope);
		if (inTypes != null) {
			for (final IType inType : inTypes) {
				inType.setMyScope(scope);
			}
		}
		if (outTypes != null) {
			for (final IType outType : outTypes) {
				outType.setMyScope(scope);
			}
		}
		if (inoutTypes != null) {
			for (final IType inoutType : inoutTypes) {
				inoutType.setMyScope(scope);
			}
		}
		for (final Reference reference : providerReferences) {
			reference.setMyScope(scope);
		}
		if (inMappings != null) {
			inMappings.setMyScope(scope);
		}
		if (outMappings != null) {
			outMappings.setMyScope(scope);
		}
		if (vardefs != null) {
			vardefs.setParentScope(scope);
		}
		if (mapParams != null) {
			mapParams.setMyScope(scope);
		}
		if (unmapParams != null) {
			unmapParams.setMyScope(scope);
		}
	}

	public OperationModes getOperationMode() {
		return operationMode;
	}

	public TestPortAPI_type getTestportType() {
		return testportType;
	}

	public PortType_type getPortType() {
		return portType;
	}

	public IType getProviderType() {
		return providerTypes.get(0);
	}

	/**
	 * @return a set of those message types than be received on this port
	 * */
	public TypeSet getInMessages() {
		return inMessages;
	}

	/**
	 * @return a set of those message types than be sent on this port
	 * */
	public TypeSet getOutMessage() {
		return outMessages;
	}

	/**
	 * @return a set of those signature types than be received on this port
	 * */
	public TypeSet getInSignatures() {
		return inSignatures;
	}

	/**
	 * @return a set of those signature types than be sent on this port
	 * */
	public TypeSet getOutSignatures() {
		return outSignatures;
	}

	/**
	 * @return the list of variable definitions in this port
	 * */
	public Definitions getVariableDefinitions() {
		return vardefs;
	}

	/**
	 * @return whether the port is a realtime port or not.
	 * */
	public boolean isRealtime() {
		return realtime;
	}

	/** @returns true if this port is internal, false otherwise */
	public boolean isInternal() {
		return TestPortAPI_type.TP_INTERNAL.equals(testportType);
	}

	/**
	 * Calculates the address type that can be used in communication operations on this port type.
	 * @param timestamp the time stamp of the actual semantic check cycle.
	 *
	 * @return   null is returned if addressing inside SUT is not supported or the address type does not exist.
	 * */
	public IType getAddressType(final CompilationTimeStamp timestamp) {
		if (lastTimeChecked == null || !TestPortAPI_type.TP_ADDRESS.equals(testportType)) {
			return null;
		}

		IType t = null;
		// in case of 'user' port types the address visible and supported by the 'provider' port type is relevant
		if (PortType_type.PT_USER.equals(portType) && !providerTypes.isEmpty()) {
			t = providerTypes.get(0);
		} else {
			t = myType;
		}

		return ((TTCN3Module) t.getMyScope().getModuleScope()).getAddressType(timestamp);
	}

	/**
	 * Marks that this port type body belongs to a provider port.
	 * Also clears all mappings set previously, in case of errors.
	 * */
	private void addProviderAttribute() {
		portType = PortType_type.PT_PROVIDER;
		providerReferences.clear();
		providerTypes.clear();
		inMappings = null;
		outMappings = null;
	}

	/**
	 * Marks that this port type body belongs to a user port.
	 *
	 * @param providerReference the reference pointing to the provider port
	 * @param legacy is it in legacy syntax?
	 * */
	public void addUserAttribute(final List<Reference> providerReferences, final boolean legacy) {
		parsedPortType = PortType_type.PT_USER;
		this.providerReferences.clear();
		for (int i = 0; i < providerReferences.size(); i++) {
			final Reference temp = providerReferences.get(i);

			this.providerReferences.add(temp);
			temp.setFullNameParent(new BridgingNamedNode(this, ".<provider_ref>"));
			temp.setMyScope(myType.getMyScope());
		}
		providerTypes.clear();

		this.legacy = legacy;
	}

	/**
	 * Marks that this port type body belongs to a user port.
	 * Also sets all mappings using the provided data.
	 *
	 * @param providerReference the reference pointing to the provider port
	 * @param inMappings the incoming mappings.
	 * @param outMappings the outgoing mappings.
	 * */
	public void addUserAttribute(final ArrayList<Reference> providerReferences, final TypeMappings inMappings, final TypeMappings outMappings, final boolean legacy) {
		portType = PortType_type.PT_USER;
		this.providerReferences.clear();
		for (int i = 0; i < providerReferences.size(); i++) {
			final Reference temp = providerReferences.get(i);

			this.providerReferences.add(temp);
			temp.setFullNameParent(new BridgingNamedNode(this, ".<provider_ref>"));
			temp.setMyScope(myType.getMyScope());
		}
		providerTypes.clear();

		this.inMappings = inMappings;
		if (inMappings != null) {
			this.inMappings.setFullNameParent(new BridgingNamedNode(this, ".<inMappings>"));
			this.inMappings.setMyScope(myType.getMyScope());
		}

		this.outMappings = outMappings;
		if (outMappings != null) {
			this.outMappings.setFullNameParent(new BridgingNamedNode(this, ".<outMappings>"));
			this.outMappings.setMyScope(myType.getMyScope());
		}

		this.legacy = legacy;
	}

	/**
	 * Adds an in mapping to the list of known mappings.
	 *
	 * @param providerReference the reference pointing to the provider port
	 * @param legacy is it in legacy syntax?
	 * */
	public void addInMapping(final TypeMapping inMapping) {
		if (inMappings == null) {
			inMappings = new TypeMappings();
		}

		inMappings.addMapping(inMapping);
		if (inMapping != null) {
			inMapping.setFullNameParent(new BridgingNamedNode(this, ".<inMappings>"));
			inMapping.setMyScope(myType.getMyScope());
		}
	}

	/**
	 * Adds an in mapping to the list of known mappings.
	 *
	 * @param providerReference the reference pointing to the provider port
	 * @param legacy is it in legacy syntax?
	 * */
	public void addOutMapping(final TypeMapping outMapping) {
		if (outMappings == null) {
			outMappings = new TypeMappings();
		}

		outMappings.addMapping(outMapping);
		if (outMapping != null) {
			outMapping.setFullNameParent(new BridgingNamedNode(this, ".<outMappings>"));
			outMapping.setMyScope(myType.getMyScope());
		}
	}

	/**
	 * Does the semantic checking of the body of the port type.
	 * Essentially this is the semantic checking of the port type, minus attributes.
	 *
	 * @param timestamp the time stamp of the actual semantic check cycle.
	 * */
	public void check(final CompilationTimeStamp timestamp) {
		if (lastTimeChecked != null && !lastTimeChecked.isLess(timestamp)) {
			return;
		}

		inMessages = null;
		outMessages = null;
		inSignatures = null;
		outSignatures = null;
		testportType = TestPortAPI_type.TP_REGULAR;
		portType = parsedPortType;
		lastTimeChecked = timestamp;

		if (inoutAll) {
			if (inAll) {
				location.reportSemanticWarning(REDUNDANTINALL);
				inAll = false;
			}
			if (outAll) {
				location.reportSemanticWarning(REDUNDANTOUTALL);
				outAll = false;
			}

			location.reportSemanticWarning(UNSUPPORTEDINOUTALL);
		} else {
			if (inAll) {
				location.reportSemanticWarning(UNSUPPORTEDINALL);
			}
			if (outAll) {
				location.reportSemanticWarning(UNSUPPORTEDOUTALL);
			}
		}

		if (inTypes != null) {
			checkList(timestamp, inTypes, true, false);
		}
		if (outTypes != null) {
			checkList(timestamp, outTypes, false, true);
		}
		if (inoutTypes != null) {
			checkList(timestamp, inoutTypes, true, true);
		}
		if (vardefs != null) {
			if (providerReferences.isEmpty() && vardefs.getNofAssignments() > 0) {
				getLocation().reportSemanticError("Port variables can only be used when the port is a translation port.");
			}
			vardefs.check(timestamp);
		}
		if (mapParams != null) {
			mapParams.check(timestamp, Assignment_type.A_PORT);
		}
		if (unmapParams != null) {
			unmapParams.check(timestamp, Assignment_type.A_PORT);
		}
	}

	/**
	 * Check the translation mapping reated attributes.
	 *
	 * @param timestamp the timestamp of the actual semantic checking iteration.
	 * */
	private void checkMapTranslation(final CompilationTimeStamp timestamp) {
		final TypeSet mappingIns = new TypeSet();

		if (inMappings != null) {
			for (int i = 0; i < inMappings.getNofMappings(); i++) {
				final TypeMapping mapping = inMappings.getMappingByIndex(i);
				for (int j = 0; j < mapping.getNofTargets(); j++) {
					final Type targetType = mapping.getTargetByIndex(j).getTargetType();
					if (!mappingIns.hasType(timestamp, targetType)) {
						mappingIns.addType(targetType);
					}
				}
			}
		}

		for (int i = 0; i < providerTypes.size(); i++) {
			final Port_Type providerType = providerTypes.get(i);
			final PortTypeBody providerBody = providerType.getPortBody();
			if (providerBody.inMessages != null) {
				for (int j = 0; j < providerBody.inMessages.getNofTypes(); j++) {
					boolean found = false;
					if (inoutTypes != null) {
						for (int k = 0; k < inoutTypes.size(); k++) {
							if (providerBody.inMessages.hasType(timestamp, inoutTypes.get(k))) {
								found = true;
								break;
							}
						}
					}

					final IType inMessageType = providerBody.inMessages.getTypeByIndex(j);
					if (((inMessages != null && inMessages.hasType(timestamp, inMessageType)) // Provider in message is present in the port in message
							|| mappingIns.hasType(timestamp, inMessageType) // Provider in message is present in one of the in mappings
							|| found // Provider in message is present in the inout list of the port
							) == false) {
						getLocation().reportSemanticError(MessageFormat.format("Incoming message type `{0}'' is not present in the in(out) message list or in the from mapping types, coming from port: `{1}''.", inMessageType.getTypename(), providerType.getGenNameOwn()));
					}
				}
			}

			if (inoutTypes != null) {
				for (int j = 0; j < inoutTypes.size(); j++) {
					final IType inoutType = inoutTypes.get(j);
					boolean foundIn = false;
					if (providerBody.inMessages != null) {
						// If the inout message of the port is present on the provider in message list
						for (int k = 0; k < providerBody.inMessages.getNofTypes(); k++) {
							final IType inType = providerBody.inMessages.getTypeByIndex(k);
							if (inoutType.getTypename().equals(inType.getTypename())) {
								foundIn = true;
								break;
							}
						}
					}
					boolean foundOut = false;
					if (providerBody.outMessages != null) {
						// If the inout message of the port is present on the provider out message list
						for (int k = 0; k < providerBody.outMessages.getNofTypes(); k++) {
							final IType outType = providerBody.outMessages.getTypeByIndex(k);
							if (inoutType.getTypename().equals(outType.getTypename())) {
								foundOut = true;
								break;
							}
						}
					}

					if (!foundIn || !foundOut) {
						getLocation().reportSemanticError(MessageFormat.format("Inout message type `{0}'' is not present on the in and out messages or the inout messages of port `{1}''.", inoutType.getTypename(), providerType.getGenNameOwn()));
					}
				}
			}

			if (outTypes != null) {
				for (int j = 0; j < outTypes.size(); j++) {
					boolean found = false;
					if (providerBody.outMessages != null) {
						// If the inout message of the port is present on the provider out message list
						for (int k = 0; k < providerBody.outMessages.getNofTypes(); k++) {
							if (outMessages.hasType(timestamp, providerBody.outMessages.getTypeByIndex(k))) {
								found = true;
								break;
							}
						}
					}

					// Check if the port's out message list contains at least one of the
					// type's target mappings.
					if (!found && outMappings != null) {
						if (outMappings.hasMappingForType(timestamp, outMessages.getTypeByIndex(j))) {
							final TypeMapping typeMapping = outMappings.getMappingForType(timestamp, outMessages.getTypeByIndex(j));
							for (int k = 0; k < typeMapping.getNofTargets(); k++) {
								if (providerBody.outMessages.hasType(timestamp, typeMapping.getTargetByIndex(k).getTargetType())) {
									found = true;
									break;
								}
							}
						}
					}

					if (!found) {
						getLocation().reportSemanticError(MessageFormat.format("Neither out message type `{0}'', nor one of its target mappings are present in the out or inout message list of the port `{1}''.", outTypes.get(j).getTypename(), providerType.getGenNameOwn()));
					}
				}
			}
		}
	}

	/**
	 * Checks the attributes for the specific case when the port is of user type.
	 *
	 * @param timestamp the time stamp of the actual semantic check cycle.
	 * */
	private void checkUserAttribute(final CompilationTimeStamp timestamp) {
		providerTypes.clear();
		PortTypeBody providerBody = null;
		for (int p = 0; p < providerReferences.size(); p++) {
			providerBody = null;
			final Assignment assignment = providerReferences.get(p).getRefdAssignment(timestamp, true);
			if (assignment != null) {
				if (Assignment_type.A_TYPE.semanticallyEquals(assignment.getAssignmentType())) {
					final IType type = assignment.getType(timestamp).getTypeRefdLast(timestamp);
					if (Type_type.TYPE_PORT.equals(type.getTypetype())) {
						boolean found = false;
						// Provider types can only be given once.
						for (int i = 0; i < providerTypes.size(); i++) {
							if (providerTypes.get(i) == type) {
								found = true;
								myType.getLocation().reportSemanticError(MessageFormat.format("Duplicate port mappings, the type `{0}'' appears more than once.", type.getTypename()));
								break;
							}
						}
						if (!found) {
							providerTypes.add((Port_Type)type);
							providerBody = ((Port_Type) type).getPortBody();
						}
					} else {
						providerReferences.get(0).getLocation().reportSemanticError(
								MessageFormat.format("Type reference `{0}'' does not refer to a port type", providerReferences.get(0).getDisplayName()));
					}
				} else {
					providerReferences.get(0).getLocation().reportSemanticError(
							MessageFormat.format("Reference `{0}'' does not refer to a type", providerReferences.get(0).getDisplayName()));
				}
			}

			// checking the consistency of attributes in this and provider_body
			if (providerBody != null && !TestPortAPI_type.TP_INTERNAL.equals(testportType)) {
				if (!PortType_type.PT_PROVIDER.equals(providerBody.portType)) {
					providerReferences.get(p).getLocation().reportSemanticError(
							MessageFormat.format("The referenced port type `{0}'' must have the `provider'' attribute", providerTypes.get(providerTypes.size() - 1).getTypename()));
				}
				switch (providerBody.testportType) {
				case TP_REGULAR:
					if (TestPortAPI_type.TP_ADDRESS.equals(testportType)) {
						providerReferences.get(p).getLocation().reportSemanticError(
								MessageFormat.format("Attribute `address'' cannot be used because the provider port type `{0}''"
										+ " does not have attribute `address''", providerTypes.get(providerTypes.size() - 1).getTypename()));
					}
					break;
				case TP_INTERNAL:
					providerReferences.get(p).getLocation().reportSemanticError(
							MessageFormat.format("Missing attribute `internal''. Provider port type `{0}'' has attribute `internal'',"
									+ " which must be also present here", providerTypes.get(providerTypes.size() - 1).getTypename()));
					break;
				case TP_ADDRESS:
					break;
				default:
					break;
				}
				// inherit the test port API type from the provider
				testportType = providerBody.testportType;
			}
		}

		// check the incoming mappings
		if (legacy && inMappings != null && inMappings.getNofMappings() != 0) {
			inMappings.check(timestamp, myType, legacy, true);

			if (providerBody != null) {
				if (providerBody.inMessages != null) {
					// check if all source types are present on the `in' list of the provider
					for (int i = 0, size = inMappings.getNofMappings(); i < size; i++) {
						final Type sourceType = inMappings.getMappingByIndex(i).getSourceType();
						if (sourceType != null && !providerBody.inMessages.hasType(timestamp, sourceType)) {
							sourceType.getLocation().reportSemanticError(MessageFormat.format(
									"Source type `{0}'' of the `in'' mapping is not present "
											+ "on the list of incoming messages in provider port type `{1}''",
											sourceType.getTypename(), providerTypes.get(0).getTypename()));
						}
					}

					// check if all types of the `in' list of the provider are handled by the mappings
					for (int i = 0, size = providerBody.inMessages.getNofTypes(); i < size; i++) {
						final IType messageType = providerBody.inMessages.getTypeByIndex(i);
						if (!inMappings.hasMappingForType(timestamp, messageType)) {
							inMappings.getLocation().reportSemanticError(MessageFormat.format(
									"Incoming message type `{0}'' of provider port type `{1}'' is not handled by the incoming mappings",
									messageType.getTypename(), providerTypes.get(0).getTypename()));
							inMappings.hasMappingForType(timestamp, messageType);
						}
					}
				} else {
					inMappings.getLocation().reportSemanticError(MessageFormat.format(
							"Invalid incoming mappings. Provider port type `{0}' does not have incoming message types'",
							providerTypes.get(0).getTypename()));
				}
			}

			// checking target types
			for (int i = 0, size = inMappings.getNofMappings(); i < size; i++) {
				final TypeMapping mapping = inMappings.getMappingByIndex(i);
				for (int j = 0, nofTargets = mapping.getNofTargets(); j < nofTargets; j++) {
					final Type targetType = mapping.getTargetByIndex(j).getTargetType();
					if (targetType != null && (inMessages == null || !inMessages.hasType(timestamp, targetType))) {
						targetType.getLocation().reportSemanticError(MessageFormat.format(
								"Target type `{0}'' of the `in'' mapping is not present on the list of incoming messages in user port type `{1}''",
								targetType.getTypename(), myType.getTypename()));
					}
				}
			}
		} else if (legacy && providerBody != null && providerBody.inMessages != null) {
			location.reportSemanticError(MessageFormat.format(
					"Missing `in'' mappings to handle the incoming message types of provider port type `{0}''", providerTypes.get(0).getTypename()));
		}

		if (legacy && outMappings != null && outMappings.getNofMappings() != 0) {
			outMappings.check(timestamp, myType, legacy, false);

			if (outMessages != null) {
				// check if all source types are present on the `in' list of the provider
				for (int i = 0, size = outMappings.getNofMappings(); i < size; i++) {
					final Type sourceType = outMappings.getMappingByIndex(i).getSourceType();
					if (sourceType != null && !outMessages.hasType(timestamp, sourceType)) {
						sourceType.getLocation().reportSemanticError(MessageFormat.format(
								"Source type `{0}'' of the `out'' mapping is not present on the list of outgoing messages in user port type `{1}''",
								sourceType.getTypename(), myType.getTypename()));
					}
				}

				// check if all types of the `in' list of the provider are handled by the mappings
				for (int i = 0, size = outMessages.getNofTypes(); i < size; i++) {
					final IType messageType = outMessages.getTypeByIndex(i);
					if (!outMappings.hasMappingForType(timestamp, messageType)) {
						outMappings.getLocation().reportSemanticError(MessageFormat.format(
								"Outgoing message type `{0}'' of user port type `{1}'' is not handled by the outgoing mappings",
								messageType.getTypename(), myType.getTypename()));
					}
				}
			} else {
				outMappings.getLocation().reportSemanticError(MessageFormat.format(
						"Invalid outgoing mappings. User port type `{0}'' does not have outgoing message types", myType.getTypename()));
			}

			// checking target types
			if (providerBody != null) {
				for (int i = 0, size = outMappings.getNofMappings(); i < size; i++) {
					final TypeMapping mapping = outMappings.getMappingByIndex(i);
					for (int j = 0, nofTargets = mapping.getNofTargets(); j < nofTargets; j++) {
						final Type targetType = mapping.getTargetByIndex(j).getTargetType();
						if (targetType != null && (providerBody.outMessages == null || !providerBody.outMessages.hasType(timestamp, targetType))) {
							targetType.getLocation().reportSemanticError(MessageFormat.format(
									"Target type `{0}'' of the `out'' mapping is not present "
											+ "on the list of outgoing messages in provider port type `{1}''",
											targetType.getTypename(), providerTypes.get(0).getTypename()));
						}
					}
				}
			}
		} else if (legacy && outMessages != null) {
			location.reportSemanticError(MessageFormat.format(
					"Missing `out'' mapping to handle the outgoing message types of user port type `{0}''", myType.getTypename()));
		}

		// checking the compatibility of signature lists
		if (providerBody == null) {
			return;
		}

		if (legacy && inSignatures != null) {
			for (int i = 0, size = inSignatures.getNofTypes(); i < size; i++) {
				final IType signatureType = inSignatures.getTypeByIndex(i);
				if (providerBody.inSignatures == null || !providerBody.inSignatures.hasType(timestamp, signatureType)) {
					final IType last = signatureType.getTypeRefdLast(timestamp);
					if (!last.getIsErroneous(timestamp) && Type_type.TYPE_SIGNATURE.equals(last.getTypetype())) {
						final Signature_Type lastSignature = (Signature_Type) last;
						if (!lastSignature.isNonblocking() || lastSignature.getSignatureExceptions() != null) {
							signatureType.getLocation().reportSemanticError(MessageFormat.format(
									"Incoming signature `{0}'' of user port type `{1}'' is not present on the list "
											+ "of incoming signatures in provider port type `{2}''",
											signatureType.getTypename(), myType.getTypename(), providerTypes.get(0).getTypename()));
						}
					}
				}
			}
		}
		if (providerBody.inSignatures != null) {
			for (int i = 0, size = providerBody.inSignatures.getNofTypes(); i < size; i++) {
				final IType signatureType = providerBody.inSignatures.getTypeByIndex(i);
				if (inSignatures == null || !inSignatures.hasType(timestamp, signatureType)) {
					location.reportSemanticError(MessageFormat.format(
							"Incoming signature `{0}'' of provider port type `{1}'' "
									+ "is not present on the list of incoming signatures in user port type `{2}''",
									signatureType.getTypename(), providerTypes.get(0).getTypename(), myType.getTypename()));
				}
			}
		}
		if (outSignatures != null) {
			for (int i = 0, size = outSignatures.getNofTypes(); i < size; i++) {
				final IType signatureType = outSignatures.getTypeByIndex(i);
				if (providerBody.outSignatures == null || !providerBody.outSignatures.hasType(timestamp, signatureType)) {
					signatureType.getLocation().reportSemanticError(MessageFormat.format(
							"Outgoing signature `{0}'' of user port type `{1}'' is not present "
									+ "on the list of outgoing signatures in provider port type `{2}''",
									signatureType.getTypename(), myType.getTypename(), providerTypes.get(0).getTypename()));
				}
			}
		}
		if (providerBody.outSignatures != null) {
			for (int i = 0, size = providerBody.outSignatures.getNofTypes(); i < size; i++) {
				final IType signatureType = providerBody.outSignatures.getTypeByIndex(i);
				if (outSignatures == null || !outSignatures.hasType(timestamp, signatureType)) {
					final IType last = signatureType.getTypeRefdLast(timestamp);
					if (!last.getIsErroneous(timestamp) && Type_type.TYPE_SIGNATURE.equals(last.getTypetype())) {
						final Signature_Type lastSignature = (Signature_Type) last;
						if (!lastSignature.isNonblocking() || lastSignature.getSignatureExceptions() != null) {
							location.reportSemanticError(MessageFormat.format(
									"Outgoing signature `{0}'' of provider port type `{1}'' is not present "
											+ "on the list of outgoing signatures in user port type `{2}''",
											signatureType.getTypename(), providerTypes.get(0).getTypename(), myType.getTypename()));
						}
					}
				}
			}
		}

		if (!legacy) {
			if (outMappings != null) {
				outMappings.check(timestamp, myType, legacy, false);
			}
			if (inMappings != null) {
				inMappings.check(timestamp, myType, legacy, true);
			}
			checkMapTranslation(timestamp);
			if (vardefs != null) {
				vardefs.check(timestamp);
			}
		}
	}

	/**
	 * Does the semantic checking of the attributes assigned to the port type having this body.
	 *
	 * @param timestamp the time stamp of the actual semantic check cycle.
	 * @param withAttributesPath the withAttributesPath assigned to the port type.
	 * */
	public void checkAttributes(final CompilationTimeStamp timestamp, final WithAttributesPath withAttributesPath) {
		if (lastTimeAttributesChecked != null && !lastTimeAttributesChecked.isLess(timestamp)) {
			return;
		}

		lastTimeAttributesChecked = lastTimeChecked;

		final List<SingleWithAttribute> realAttributes = withAttributesPath.getRealAttributes(timestamp);
		final List<ExtensionAttribute> attributes = new ArrayList<ExtensionAttribute>();

		SingleWithAttribute attribute;
		List<AttributeSpecification> specifications = null;
		for (int i = 0; i < realAttributes.size(); i++) {
			attribute = realAttributes.get(i);
			if (Attribute_Type.Extension_Attribute.equals(attribute.getAttributeType())) {
				final Qualifiers qualifiers = attribute.getQualifiers();
				if (qualifiers == null || qualifiers.getNofQualifiers() == 0) {
					if (specifications == null) {
						specifications = new ArrayList<AttributeSpecification>();
					}

					final AttributeSpecification specification = attribute.getAttributeSpecification();
					if ( specification.getSpecification() != null ) {
						// there is nothing to parse if specification string is null,
						// anyway it would cause NPE in ExtensionAttributeAnalyzer.parse()
						specifications.add( specification );
					}
				}
			}
		}

		if (specifications != null) {
			AttributeSpecification specification;
			for (int i = 0; i < specifications.size(); i++) {
				specification = specifications.get(i);
				final ExtensionAttributeAnalyzer analyzer = new ExtensionAttributeAnalyzer();
				analyzer.parse(specification);
				final List<ExtensionAttribute> temp = analyzer.getAttributes();
				if (temp != null) {
					attributes.addAll(temp);
				}
			}
		}

		//clear the old attributes
		testportType = TestPortAPI_type.TP_REGULAR;
		//portType = PortType_type.PT_REGULAR;

		// check the new attributes
		for (int i = 0; i < attributes.size(); i++) {
			final ExtensionAttribute extensionAttribute = attributes.get(i);
			if (ExtensionAttribute_type.PORTTYPE.equals(extensionAttribute.getAttributeType())) {
				final PortTypeAttribute portAttribute = (PortTypeAttribute) extensionAttribute;
				switch (portAttribute.getPortTypeType()) {
				case INTERNAL:
					switch (testportType) {
					case TP_REGULAR:
						break;
					case TP_INTERNAL:
						extensionAttribute.getLocation().reportSemanticWarning("Duplicate attribute `internal'");
						break;
					case TP_ADDRESS:
						extensionAttribute.getLocation().reportSemanticError("Attributes `address' and `internal' cannot be used at the same time");
						break;
					default:
						break;
					}
					testportType = TestPortAPI_type.TP_INTERNAL;
					break;
				case ADDRESS:
					switch (testportType) {
					case TP_REGULAR:
						break;
					case TP_INTERNAL:
						extensionAttribute.getLocation().reportSemanticError("Attributes `address' and `internal' cannot be used at the same time");
						break;
					case TP_ADDRESS:
						extensionAttribute.getLocation().reportSemanticWarning("Duplicate attribute `address'");
						break;
					default:
						break;
					}
					testportType = TestPortAPI_type.TP_ADDRESS;
					break;
				case PROVIDER:
					switch (portType) {
					case PT_REGULAR:
						break;
					case PT_PROVIDER:
						extensionAttribute.getLocation().reportSemanticWarning("Duplicate attribute `provider'");
						break;
					case PT_USER:
						if (legacy) {
							extensionAttribute.getLocation().reportSemanticError("Attributes `user' and `provider' cannot be used at the same time");
						} else {
							extensionAttribute.getLocation().reportSemanticError("The `provider' attribute cannot be used on translation ports");
						}
						break;
					default:
						break;
					}
					addProviderAttribute();
					break;
				case USER:
					switch (portType) {
					case PT_REGULAR:
						break;
					case PT_PROVIDER:
						extensionAttribute.getLocation().reportSemanticError("Attributes `provider' and `user' cannot be used at the same time");
						break;
					case PT_USER:
						if (legacy) {
							extensionAttribute.getLocation().reportSemanticError("Duplicate attribute `user'");
						} else {
							extensionAttribute.getLocation().reportSemanticError("Attribute `user' cannot be used on translation ports.");
						}
						break;
					default:
						break;
					}

					final UserPortTypeAttribute user = (UserPortTypeAttribute) portAttribute;
					final ArrayList<Reference> references = new ArrayList<Reference>();
					references.add(user.getReference());
					addUserAttribute(references, user.getInMappings(), user.getOutMappings(), true);
					break;
				default:
					break;
				}
			}
		}

		if (PortType_type.PT_USER.equals(portType)) {
			checkUserAttribute(timestamp);
		} else if (TestPortAPI_type.TP_ADDRESS.equals(testportType)) {
			final TTCN3Module module = (TTCN3Module) myType.getMyScope().getModuleScope();
			if (module.getAddressType(timestamp) == null) {
				location.reportSemanticError(MessageFormat.format("Type `address'' is not defined in module `{0}''", module.getIdentifier().getDisplayName()));
			}
		}
	}

	/**
	 * Checks a list of types.
	 *
	 * @param timestamp the timestamp of the actual semantic check cycle
	 * @param list the list of types to check
	 * @param isIn is the list an in or inout list.
	 * @param isOut is the list an out or inout list.
	 * */
	private void checkList(final CompilationTimeStamp timestamp, final List<IType> list, final boolean isIn, final boolean isOut) {
		String errorMessage;
		if (isIn) {
			if (isOut) {
				errorMessage = "sent or received";
			} else {
				errorMessage = "received";
			}
		} else {
			errorMessage = "sent";
		}

		for (final IType type : list) {
			type.check(timestamp);

			if (type.isComponentInternal(timestamp)) {
				//check if a value or template of this type can leave the component.
				final Set<IType> typeSet = new HashSet<IType>();
				type.checkComponentInternal(timestamp, typeSet, "sent or received on a port");
			}

			final IType last = type.getTypeRefdLast(timestamp);
			if (last != null && !last.getIsErroneous(timestamp)) {
				switch (last.getTypetype()) {
				case TYPE_SIGNATURE:
					if (OperationModes.OP_Message.equals(operationMode)) {
						type.getLocation().reportSemanticError(MessageFormat.format(SIGNATUREONMESSAGEPORT, last.getTypename()));
					}
					if (isIn) {
						if (inSignatures != null && inSignatures.hasType(timestamp, last)) {
							type.getLocation().reportSemanticError(MessageFormat.format(DUPLICATEDINSIGNATURE, last.getTypename()));
						} else {
							if (inSignatures == null) {
								inSignatures = new TypeSet();
								inSignatures.setFullNameParent(this);
							}
							inSignatures.addType(type);
						}
					}
					if (isOut) {
						if (outSignatures != null && outSignatures.hasType(timestamp, last)) {
							type.getLocation().reportSemanticError(MessageFormat.format(DUPLICATEDOUTSIGNATURE, last.getTypename()));
						} else {
							if (outSignatures == null) {
								outSignatures = new TypeSet();
								outSignatures.setFullNameParent(this);
							}
							outSignatures.addType(type);
						}
					}
					break;
				default:
					if (OperationModes.OP_Procedure.equals(operationMode)) {
						type.getLocation().reportSemanticError(MessageFormat.format(DATAONPROCEDUREPORT, last.getTypename(), errorMessage));
					}
					if (isIn) {
						if (inMessages != null && inMessages.hasType(timestamp, last)) {
							type.getLocation().reportSemanticError(MessageFormat.format(DUPLICATEDINMESSAGE, type.getTypename()));
						} else {
							if (inMessages == null) {
								inMessages = new TypeSet();
								inMessages.setFullNameParent(this);
							}

							inMessages.addType(type);
						}
					}
					if (isOut) {
						if (outMessages != null && outMessages.hasType(timestamp, last)) {
							type.getLocation().reportSemanticError(MessageFormat.format(DUPLICATEDOUTMESSAGE, type.getTypename()));
						} else {
							if (outMessages == null) {
								outMessages = new TypeSet();
								outMessages.setFullNameParent(this);
							}

							outMessages.addType(type);
						}
					}
					break;
				}
			}
		}
	}

	/**
	 * Checks if the port of this port type body has a queue or not. A queue is
	 * only used if there is at least one blocking signature, or at least one
	 * signature with exceptions.
	 *
	 * @param timestamp the timestamp of the actual semantic check cycle.
	 *
	 * @return true if the port has a queue, false otherwise
	 * */
	public boolean hasQueue(final CompilationTimeStamp timestamp) {
		check(timestamp);

		if (inMessages != null || inSignatures != null) {
			return true;
		}

		if (outSignatures != null) {
			for (int i = 0, size = outSignatures.getNofTypes(); i < size; i++) {
				final Signature_Type signature = (Signature_Type) outSignatures.getTypeByIndex(i).getTypeRefdLast(timestamp);
				if (!signature.isNonblocking() || signature.getSignatureExceptions() != null) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Checks if a getreply operation can be used on the port this body belongs
	 * to.
	 *
	 * @param timestamp the timestamp of the actual semantic cycle
	 *
	 * @return true if there is at least one outgoing signature which is not
	 *         blocking, false otherwise
	 * */
	public boolean getreplyAllowed(final CompilationTimeStamp timestamp) {
		check(timestamp);

		if (outSignatures != null) {
			IType tempType = null;
			for (int i = 0, size = outSignatures.getNofTypes(); i < size; i++) {
				tempType = outSignatures.getTypeByIndex(i).getTypeRefdLast(timestamp);
				if (!((Signature_Type) tempType).isNonblocking()) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Checks if a catch operation can be used on the port this body belongs to.
	 *
	 * @param timestamp the timestamp of the actual semantic cycle.
	 *
	 * @return true if there is at least one outgoing signature which can throw
	 *         an exception, false otherwise
	 * */
	public boolean catchAllowed(final CompilationTimeStamp timestamp) {
		check(timestamp);

		if (outSignatures != null) {
			IType tempType = null;
			for (int i = 0, size = outSignatures.getNofTypes(); i < size; i++) {
				tempType = outSignatures.getTypeByIndex(i).getTypeRefdLast(timestamp);
				if (((Signature_Type) tempType).getSignatureExceptions() != null) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Checks if this as the type of a test component port can be mapped to a
	 * system port with an other port type.
	 *
	 * @param timestamp the timestamp of the actual semantic cycle.
	 * @param other the other port type body to compare to.
	 *
	 * @return true if this as the type of a test component port can be mapped
	 *         to a system port with an other port type, false otherwise.
	 * */
	public boolean isMappable(final CompilationTimeStamp timestamp, final PortTypeBody other) {
		if (this == other) {
			return true;
		}

		// the outgoing lists should be covered by the other port
		if (outMessages != null) {
			if (other.outMessages == null) {
				return false;
			}
			for (int i = 0, size = outMessages.getNofTypes(); i < size; i++) {
				if (!other.outMessages.hasType(timestamp, outMessages.getTypeByIndex(i))) {
					return false;
				}
			}
		}

		if (outSignatures != null) {
			if (other.outSignatures == null) {
				return false;
			}
			for (int i = 0, size = outSignatures.getNofTypes(); i < size; i++) {
				if (!other.outSignatures.hasType(timestamp, outSignatures.getTypeByIndex(i))) {
					return false;
				}
			}
		}

		// the incoming list of the other should be covered by local incoming lists.
		if (other.inMessages != null) {
			if (inMessages == null) {
				return false;
			}
			for (int i = 0, size = other.inMessages.getNofTypes(); i < size; i++) {
				if (!inMessages.hasType(timestamp, other.inMessages.getTypeByIndex(i))) {
					return false;
				}
			}
		}

		if (other.inSignatures != null) {
			if (inSignatures == null) {
				return false;
			}
			for (int i = 0, size = other.inSignatures.getNofTypes(); i < size; i++) {
				if (!inSignatures.hasType(timestamp, other.inSignatures.getTypeByIndex(i))) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Reports all errors that prevent mapping of this as the type of a test
	 * component port to system port an other port type.
	 *
	 * @param timestamp the timestamp of the actual semantic cycle.
	 * @param other the other port type body to compare to.
	 * */
	public void reportMappingErrors(final CompilationTimeStamp timestamp, final PortTypeBody other) {
		if (outMessages != null) {
			for (int i = 0, size = outMessages.getNofTypes(); i < size; i++) {
				final IType messageType = outMessages.getTypeByIndex(i);
				if (other.outMessages == null || !other.outMessages.hasType(timestamp, messageType)) {
					messageType.getLocation().reportSemanticError(MessageFormat.format(
							"Outgoing message type `{0}'' of test component port type `{1}'' is not present on the outgoing list of system port type `{2}''"
							, messageType.getTypename(), myType.getTypename(), other.myType.getTypename()));
				}
			}
		}

		if (outSignatures != null) {
			for (int i = 0, size = outSignatures.getNofTypes(); i < size; i++) {
				final IType signatureType = outSignatures.getTypeByIndex(i);
				if (other.outSignatures == null || !other.outSignatures.hasType(timestamp, signatureType)) {
					signatureType.getLocation().reportSemanticError(MessageFormat.format(
							"Outgoing signature type `{0}'' of test component port type `{1}'' is not present on the outgoing list of system port type `{2}''"
							, signatureType.getTypename(), myType.getTypename(), other.myType.getTypename()));
				}
			}
		}

		if (other.inMessages != null) {
			for (int i = 0, size = other.inMessages.getNofTypes(); i < size; i++) {
				final IType messageType = other.inMessages.getTypeByIndex(i);
				if (inMessages == null || !inMessages.hasType(timestamp, messageType)) {
					messageType.getLocation().reportSemanticError(MessageFormat.format(
							"Incoming message type `{0}'' of system port type `{1}'' is not present on the incoming list of test component port type `{2}''"
							, messageType.getTypename(), other.myType.getTypename(), myType.getTypename()));
				}
			}
		}

		if (other.inSignatures != null) {
			for (int i = 0, size = other.inSignatures.getNofTypes(); i < size; i++) {
				final IType signatureType = other.inSignatures.getTypeByIndex(i);
				if (inSignatures == null || !inSignatures.hasType(timestamp, signatureType)) {
					signatureType.getLocation().reportSemanticError(MessageFormat.format(
							"Incoming signature type `{0}'' of system port type `{1}'' is not present on the incoming list of test component port type `{2}''"
							, signatureType.getTypename(), other.myType.getTypename(), myType.getTypename()));
				}
			}
		}
	}

	/**
	 * @other the other porttypebody to compare to.
	 *
	 * @return true if this porttypeBody has translation capabilities towards the other.
	 * */
	public boolean isTranslate(final PortTypeBody other) {
		for (int i = 0; i < providerTypes.size(); i++) {
			if (providerTypes.get(i) == other.getMyType()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Special case when mapping a port that has out procedure/message but
	 * the other does not have any, and other has in procedure/message but 'this'
	 * does not have any. In that case it is not possible to send or receive anything.
	 *
	 * @param other the other porttypebody
	 *
	 * @return true if a map operation could use this porttypebody for sending/receiving.
	 * */
	public boolean mapCanReceiveOrSend(final PortTypeBody other) {
		if (operationMode == OperationModes.OP_Message && (outMessages == null || outMessages.getNofTypes() == 0) &&
				(other.inMessages == null || other.inMessages.getNofTypes() == 0)) {
			return false;
		}

		if (operationMode == OperationModes.OP_Procedure && (outSignatures == null || outSignatures.getNofTypes() == 0) &&
				(other.inSignatures == null || other.inSignatures.getNofTypes() == 0)) {
			return false;
		}

		if (operationMode == OperationModes.OP_Mixed &&
				(outMessages == null || outMessages.getNofTypes() == 0) &&
				(other.inMessages == null || other.inMessages.getNofTypes() == 0) &&
				(outSignatures == null || outSignatures.getNofTypes() == 0) &&
				(other.inSignatures == null || other.inSignatures.getNofTypes() == 0)) {
			return false;
		}

		return true;
	}

	public Port_Type getMyType() {
		return myType;
	}

	public boolean isLegacy() {
		return legacy;
	}

	/**
	 * Checks if the outgoing messages and signatures of this are on the
	 * incoming lists of the other port type body.
	 *
	 * @param timestamp the timestamp of the actual semantic cycle.
	 * @param other the other port type body to compare to.
	 *
	 * @return true if the outgoing messages and signatures of this are on the
	 *         incoming lists of the other port type body, false otherwise.
	 * */
	public boolean isConnectable(final CompilationTimeStamp timestamp, final PortTypeBody other) {
		if (outMessages != null) {
			if (other.inMessages == null) {
				return false;
			}
			for (int i = 0, size = outMessages.getNofTypes(); i < size; i++) {
				if (!other.inMessages.hasType(timestamp, outMessages.getTypeByIndex(i))) {
					return false;
				}
			}
		} else if ((OperationModes.OP_Message.equals(operationMode) || OperationModes.OP_Mixed.equals(operationMode)) && other.outMessages == null) {
			return false;
		}

		if (outSignatures != null) {
			if (other.inSignatures == null) {
				return false;
			}
			for (int i = 0, size = outSignatures.getNofTypes(); i < size; i++) {
				if (!other.inSignatures.hasType(timestamp, outSignatures.getTypeByIndex(i))) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Reports the error messages about the outgoing types of this that are not
	 * handled by the incoming lists of the other port type.
	 *
	 * @param timestamp the timestamp of the actual semantic cycle.
	 * @param other the other port type body to compare to.
	 * @param connectionLocation the location of the connection to report the error messages to.
	 * */
	public void reportConnectionErrors(final CompilationTimeStamp timestamp, final PortTypeBody other, final Location connectionLocation) {
		if (outMessages != null) {
			for (int i = 0, size = outMessages.getNofTypes(); i < size; i++) {
				final IType messageType = outMessages.getTypeByIndex(i);
				if (other.inMessages == null || !other.inMessages.hasType(timestamp, messageType)) {
					connectionLocation.reportSemanticError(MessageFormat.format(
							"Outgoing message type `{0}'' of port type `{1}'' is not present on the incoming list of port type `{2}''"
							, messageType.getTypename(), myType.getTypename(), other.myType.getTypename()));
				}
			}
		} else if ((OperationModes.OP_Message.equals(operationMode) || OperationModes.OP_Mixed.equals(operationMode)) && other.outMessages == null) {
			connectionLocation.reportSemanticError(MessageFormat.format(
					"Neither port type `{0}'' nor port type `{1}'' can send messages"
					, myType.getTypename(), other.myType.getTypename()));
		}

		if (outSignatures != null) {
			for (int i = 0, size = outSignatures.getNofTypes(); i < size; i++) {
				final IType signatureType = outSignatures.getTypeByIndex(i);
				if (other.inSignatures == null || !other.inSignatures.hasType(timestamp, outSignatures.getTypeByIndex(i))) {
					connectionLocation.reportSemanticError(MessageFormat.format(
							"Outgoing signature type `{0}'' of port type `{1}'' is not present on the incoming list of port type `{2}''"
							, signatureType.getTypename(), myType.getTypename(), other.myType.getTypename()));
				}
			}
		}
	}

	/**
	 * Adds the port related proposals.
	 *
	 * handles the following proposals:
	 *
	 * <ul>
	 * <li>in message mode:
	 * <ul>
	 * <li>send(template), receive, trigger
	 * </ul>
	 * <li>in procedure mode:
	 * <ul>
	 * <li>call, getcall, reply, raise, getreply, catch
	 * </ul>
	 * <li>general:
	 * <ul>
	 * <li>check
	 * <li>clear, start, stop, halt
	 * </ul>
	 * </ul>
	 *
	 * @param propCollector the proposal collector.
	 * @param i the index of a part of the full reference, for which we wish to find completions.
	 * */
	public void addProposal(final ProposalCollector propCollector, final int i) {
		final List<ISubReference> subrefs = propCollector.getReference().getSubreferences();
		if (subrefs.size() != i + 1 || Subreference_type.arraySubReference.equals(subrefs.get(i).getReferenceType())) {
			return;
		}

		if (OperationModes.OP_Message.equals(operationMode) || OperationModes.OP_Mixed.equals(operationMode)) {
			propCollector.addTemplateProposal("send", new Template("send( templateInstance )", "", propCollector.getContextIdentifier(),
					"send( ${templateInstance} );", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
			propCollector.addTemplateProposal("send", new Template("send( templateInstance ) to location", "", propCollector.getContextIdentifier(),
					"send( ${templateInstance} ) to ${location};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);

			addReceiveProposals(propCollector, i);
			addTriggerProposals(propCollector, i);
		}

		if (OperationModes.OP_Procedure.equals(operationMode) || OperationModes.OP_Mixed.equals(operationMode)) {
			propCollector.addTemplateProposal("call", new Template("call( templateInstance )", "", propCollector.getContextIdentifier(),
					"call( ${templateInstance} );", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
			propCollector.addTemplateProposal("call", new Template("call( templateInstance , callTimer )", "with timer", propCollector
					.getContextIdentifier(), "call( ${templateInstance} , ${callTimer} );", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
			propCollector.addTemplateProposal("call", new Template("call( templateInstance ) to location", "with to clause", propCollector
					.getContextIdentifier(), "call( ${templateInstance} ) to ${location};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
			propCollector.addTemplateProposal("call", new Template("call( templateInstance , callTimer ) to location", "with timer and to clause",
					propCollector.getContextIdentifier(), "call( ${templateInstance} , ${callTimer} ) to ${location};", false),
					TTCN3CodeSkeletons.SKELETON_IMAGE);

			addGetcallProposals(propCollector, i);

			propCollector.addTemplateProposal("reply", new Template("reply( templateInstance )", "", propCollector.getContextIdentifier(),
					"reply( ${templateInstance} );", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
			propCollector.addTemplateProposal("reply", new Template("reply( templateInstance ) to location", "",
					propCollector.getContextIdentifier(), "reply( ${templateInstance} ) to ${location};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);

			addGetreplyProposals(propCollector, i);

			propCollector.addTemplateProposal("raise", new Template("raise( signature, templateInstance )", "", propCollector.getContextIdentifier(),
					"raise( ${signature}, ${templateInstance} );", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
			propCollector.addTemplateProposal("raise", new Template("raise( signature, templateInstance ) to location", "with to clause",
					propCollector.getContextIdentifier(), "raise( ${signature}, ${templateInstance} ) to ${location};", false),
					TTCN3CodeSkeletons.SKELETON_IMAGE);

			addCatchProposals(propCollector, i);
		}

		addCheckProposals(propCollector, i);

		propCollector.addProposal("clear;", "clear", ImageCache.getImage("port.gif"), "");
		propCollector.addProposal("start;", "start", ImageCache.getImage("port.gif"), "");
		propCollector.addProposal("stop;", "stop", ImageCache.getImage("port.gif"), "");
		propCollector.addProposal("halt;", "halt", ImageCache.getImage("port.gif"), "");
	}

	public static void addAnyorAllProposal(final ProposalCollector propCollector, final int i) {
		final List<ISubReference> subrefs = propCollector.getReference().getSubreferences();
		if (i != 0 || subrefs.isEmpty() || Subreference_type.arraySubReference.equals(subrefs.get(0).getReferenceType())) {
			return;
		}

		final String fakeModuleName = propCollector.getReference().getModuleIdentifier().getDisplayName();

		if ("any port".equals(fakeModuleName)) {
			addReceiveProposals(propCollector, i);
			addTriggerProposals(propCollector, i);
			addGetcallProposals(propCollector, i);
			addGetreplyProposals(propCollector, i);
			addCatchProposals(propCollector, i);
			addCheckProposals(propCollector, i);
		} else if ("all port".equals(fakeModuleName)) {
			propCollector.addProposal("clear;", "clear", ImageCache.getImage("port.gif"), "");
			propCollector.addProposal("start;", "start", ImageCache.getImage("port.gif"), "");
			propCollector.addProposal("stop;", "stop", ImageCache.getImage("port.gif"), "");
			propCollector.addProposal("halt;", "halt", ImageCache.getImage("port.gif"), "");
		}
	}

	/**
	 * Adds the "receive" related operations of port types, to the completion
	 * list.
	 *
	 * @param propCollector the proposal collector
	 * @param i index, not used
	 * */
	private static void addReceiveProposals(final ProposalCollector propCollector, final int i) {
		propCollector.addProposal("receive", "receive", ImageCache.getImage("port.gif"), "");
		propCollector.addTemplateProposal("receive", new Template("receive -> value myVar", "value redirect", propCollector.getContextIdentifier(),
				"receive -> value ${myVar};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("receive", new Template("receive -> sender myPeer", "sender redirect",
				propCollector.getContextIdentifier(), "receive -> sender ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("receive", new Template("receive -> value myVar sender myPeer", "value and sender redirect", propCollector
				.getContextIdentifier(), "receive -> value ${myVar} sender ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("receive", new Template("receive from myPeer", "from clause", propCollector.getContextIdentifier(),
				"receive from ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("receive", new Template("receive from myPeer -> value myVar", "from clause with value redirect",
				propCollector.getContextIdentifier(), "receive from ${myPeer} -> value ${myVar};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("receive", new Template("receive( templateInstance )", "", propCollector.getContextIdentifier(),
				"receive( ${template} );", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("receive", new Template("receive( templateInstance ) -> value myVar", "value redirect", propCollector
				.getContextIdentifier(), "receive( ${templateInstance} ) -> value ${myVar};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("receive", new Template("receive( templateInstance ) -> sender myPeer", "sender redirect", propCollector
				.getContextIdentifier(), "receive( ${templateInstance} ) -> sender ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("receive", new Template("receive( templateInstance ) -> value myVar sender myPeer",
				"value and sender redirect", propCollector.getContextIdentifier(),
				"receive( ${templateInstance} ) -> value ${myVar} sender ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("receive", new Template("receive( templateInstance ) from myPeer", "from clause", propCollector
				.getContextIdentifier(), "receive( ${templateInstance} ) from ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("receive", new Template("receive( templateInstance ) from myPeer -> value myVar",
				"from clause with value redirect", propCollector.getContextIdentifier(),
				"receive( ${templateInstance} ) from ${myPeer} -> value ${myVar};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
	}

	/**
	 * Adds the "trigger" related operations of port types, to the completion
	 * list.
	 *
	 * @param propCollector the proposal collector
	 * @param i index, not used
	 * */
	public static void addTriggerProposals(final ProposalCollector propCollector, final int i) {
		propCollector.addProposal("trigger", "trigger", ImageCache.getImage("port.gif"), "");
		propCollector.addTemplateProposal("trigger", new Template("trigger -> value myVar", "value redirect", propCollector.getContextIdentifier(),
				"trigger -> value ${myVar};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("trigger", new Template("trigger -> sender myPeer", "sender redirect",
				propCollector.getContextIdentifier(), "trigger -> sender ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("trigger", new Template("trigger -> value myVar sender myPeer", "value and sender redirect", propCollector
				.getContextIdentifier(), "trigger -> value ${myVar} sender ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("trigger", new Template("trigger from myPeer", "from clause", propCollector.getContextIdentifier(),
				"trigger from ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("trigger", new Template("trigger from myPeer -> value myVar", "from clause with value redirect",
				propCollector.getContextIdentifier(), "trigger from ${myPeer} -> value ${myVar};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("trigger", new Template("trigger( templateInstance )", "", propCollector.getContextIdentifier(),
				"trigger( ${templateInstance} );", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("trigger", new Template("trigger( templateInstance ) -> value myVar", "value redirect", propCollector
				.getContextIdentifier(), "trigger( ${templateInstance} ) -> value ${myVar};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("trigger", new Template("trigger( templateInstance ) -> sender myPeer", "sender redirect", propCollector
				.getContextIdentifier(), "trigger( ${templateInstance} ) -> sender ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("trigger", new Template("trigger( templateInstance ) -> value myVar sender myPeer",
				"value and sender redirect", propCollector.getContextIdentifier(),
				"trigger( ${templateInstance} ) -> value ${myVar} sender ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("trigger", new Template("trigger( templateInstance ) from myPeer", "from clause", propCollector
				.getContextIdentifier(), "trigger( ${templateInstance} ) from ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("trigger", new Template("trigger( templateInstance ) from myPeer -> value myVar",
				"from clause with value redirect", propCollector.getContextIdentifier(),
				"trigger( ${templateInstance} ) from ${myPeer} -> value ${myVar};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);

	}

	/**
	 * Adds the "getcall" related operations of port types, to the completion
	 * list.
	 *
	 * @param propCollector the proposal collector
	 * @param i index, not used
	 * */
	public static void addGetcallProposals(final ProposalCollector propCollector, final int i) {
		propCollector.addProposal("getcall", "getcall", ImageCache.getImage("port.gif"), "");
		propCollector.addTemplateProposal("getcall", new Template("getcall from myPartner", "from clause", propCollector.getContextIdentifier(),
				"getcall from ${myPartner};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("getcall", new Template("getcall -> sender myPartnerVar", "sender redirect", propCollector
				.getContextIdentifier(), "getcall -> sender ${myPartnerVar};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("getcall", new Template("getcall( templateInstance )", "", propCollector.getContextIdentifier(),
				"getcall( ${templateInstance} );", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("getcall", new Template("getcall( templateInstance ) from myPartner", "from clause", propCollector
				.getContextIdentifier(), "getcall( ${templateInstance} ) from ${myPartner};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("getcall", new Template("getcall( templateInstance ) -> sender myPartnerVar", "sender redirect",
				propCollector.getContextIdentifier(), "getcall( ${templateInstance} ) -> sender ${myPartnerVar};", false),
				TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("getcall", new Template("getcall( templateInstance ) -> param(parameters)", "parameters", propCollector
				.getContextIdentifier(), "getcall( ${templateInstance} ) -> param( ${parameters} );", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("getcall", new Template("getcall( templateInstance ) from myPartner -> param(parameters)",
				"from clause and parameters", propCollector.getContextIdentifier(),
				"getcall( ${templateInstance} ) from ${myPartner} -> param( ${parameters} );", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("getcall", new Template("getcall( templateInstance ) -> param(parameters) sender mySenderVar",
				"parameters and sender clause", propCollector.getContextIdentifier(),
				"getcall( ${templateInstance} ) -> param( ${parameters} ) sender ${mySenderVar};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
	}

	/**
	 * Adds the "getreply" related operations of port types, to the completion
	 * list.
	 *
	 * @param propCollector the proposal collector
	 * @param i index, not used
	 * */
	public static void addGetreplyProposals(final ProposalCollector propCollector, final int i) {
		propCollector.addProposal("getreply", "getreply", ImageCache.getImage("port.gif"), "");
		propCollector.addTemplateProposal("getreply", new Template("getreply from myPartner", "from clause", propCollector.getContextIdentifier(),
				"getreply from ${myPartner};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("getreply", new Template(
				"getreply( templateInstance ) -> value myReturnValue param(parameters) sender mySenderVar", "value, parameters and sender clause",
				propCollector.getContextIdentifier(),
				"getreply( ${templateInstance} ) -> value ${myReturnValue} param( ${parameters} ) sender ${mySenderVar};", false),
				TTCN3CodeSkeletons.SKELETON_IMAGE);
	}

	/**
	 * Adds the "catch" related operations of port types, to the completion
	 * list.
	 *
	 * @param propCollector the proposal collector
	 * @param i index, not used
	 * */
	public static void addCatchProposals(final ProposalCollector propCollector, final int i) {
		propCollector.addProposal("catch", "catch", ImageCache.getImage("port.gif"), "");
		propCollector.addTemplateProposal("catch", new Template("catch -> value myVar", "value redirect", propCollector.getContextIdentifier(),
				"catch -> value ${myVar};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("catch", new Template("catch -> sender myPeer", "sender redirect", propCollector.getContextIdentifier(),
				"catch -> sender ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("catch", new Template("catch -> value myVar sender myPeer", "value and sender redirect", propCollector
				.getContextIdentifier(), "catch -> value ${myVar} sender ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("catch", new Template("catch from myPeer", "from clause", propCollector.getContextIdentifier(),
				"catch from ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("catch", new Template("catch from myPeer -> value myVar", "from clause with value redirect", propCollector
				.getContextIdentifier(), "catch from ${myPeer} -> value ${myVar};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("catch", new Template("catch( signature, templateInstance )", "", propCollector.getContextIdentifier(),
				"catch( ${signature}, ${templateInstance} );", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("catch", new Template("catch( signature, templateInstance ) -> value myVar", "value redirect",
				propCollector.getContextIdentifier(), "catch( ${signature}, ${templateInstance} ) -> value ${myVar};", false),
				TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("catch", new Template("catch( signature, templateInstance ) -> sender myPeer", "sender redirect",
				propCollector.getContextIdentifier(), "catch( ${signature}, ${templateInstance} ) -> sender ${myPeer};", false),
				TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("catch", new Template("catch( signature, templateInstance ) -> value myVar sender myPeer",
				"value and sender redirect", propCollector.getContextIdentifier(),
				"catch( ${signature}, ${templateInstance} ) -> value ${myVar} sender ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("catch", new Template("catch( signature, templateInstance ) from myPeer", "from clause", propCollector
				.getContextIdentifier(), "catch( ${signature}, ${templateInstance} ) from ${myPeer};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("catch", new Template("catch( signature, templateInstance ) from myPeer -> value myVar",
				"from clause with value redirect", propCollector.getContextIdentifier(),
				"catch( ${signature}, ${templateInstance} ) from ${myPeer} -> value ${myVar};", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("catch", new Template("catch( timeout )", "", propCollector.getContextIdentifier(), "catch( timeout );",
				false), TTCN3CodeSkeletons.SKELETON_IMAGE);
	}

	/**
	 * Adds the "check" related operations of port types, to the completion
	 * list.
	 *
	 * @param propCollector the proposal collector
	 * @param i index, not used
	 * */
	public static void addCheckProposals(final ProposalCollector propCollector, final int i) {
		propCollector.addProposal("check", "check", ImageCache.getImage("port.gif"), "");
		propCollector.addTemplateProposal("check", new Template("check( portOperation )", "", propCollector.getContextIdentifier(),
				"check( ${portOperation} );", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("check", new Template("check( from myPeer)", "form clause", propCollector.getContextIdentifier(),
				"check( from ${myPeer});", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("check", new Template("check( from myPeer -> value myVar)", "form and value clause", propCollector
				.getContextIdentifier(), "check( from ${myPeer} -> value ${myVar});", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("check", new Template("check( -> value myVar)", "value clause", propCollector.getContextIdentifier(),
				"check( -> value ${myVar} );", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
		propCollector.addTemplateProposal("check", new Template("check( -> value myVar sender myPeer)", "value and sender clause", propCollector
				.getContextIdentifier(), "check( -> value ${myVar} sender ${myPeer} );", false), TTCN3CodeSkeletons.SKELETON_IMAGE);
	}

	@Override
	/** {@inheritDoc} */
	public void updateSyntax(final TTCN3ReparseUpdater reparser, final boolean isDamaged) throws ReParseException {
		if (isDamaged) {
			throw new ReParseException();
		}

		if (inTypes != null) {
			for (final IType type : inTypes) {
				if (type instanceof IIncrementallyUpdateable) {
					((IIncrementallyUpdateable) type).updateSyntax(reparser, false);
					reparser.updateLocation(type.getLocation());
				} else {
					throw new ReParseException();
				}
			}
		}
		if (outTypes != null) {
			for (final IType type : outTypes) {
				if (type instanceof IIncrementallyUpdateable) {
					((IIncrementallyUpdateable) type).updateSyntax(reparser, false);
					reparser.updateLocation(type.getLocation());
				} else {
					throw new ReParseException();
				}
			}
		}
		if (inoutTypes != null) {
			for (final IType type : inoutTypes) {
				if (type instanceof IIncrementallyUpdateable) {
					((IIncrementallyUpdateable) type).updateSyntax(reparser, false);
					reparser.updateLocation(type.getLocation());
				} else {
					throw new ReParseException();
				}
			}
		}
		for (int i = 0; i < providerReferences.size(); i++) {
			final Reference reference = providerReferences.get(i);

			reference.updateSyntax(reparser, false);
			reparser.updateLocation(reference.getLocation());
		}
		if (inMappings != null) {
			inMappings.updateSyntax(reparser, false);
			reparser.updateLocation(inMappings.getLocation());
		}
		if (outMappings != null) {
			outMappings.updateSyntax(reparser, false);
			reparser.updateLocation(outMappings.getLocation());
		}
		if (vardefs != null) {
			for (int i = 0; i < vardefs.getNofAssignments(); i++) {
				final Definition definition = vardefs.getAssignmentByIndex(i);

				definition.updateSyntax(reparser, false);
				reparser.updateLocation(definition.getLocation());
			}

			reparser.updateLocation(vardefs.getLocation());
		}
		if (mapParams != null) {
			mapParams.updateSyntax(reparser, false);
			reparser.updateLocation(mapParams.getLocation());
		}
		if (unmapParams != null) {
			unmapParams.updateSyntax(reparser, false);
			reparser.updateLocation(unmapParams.getLocation());
		}
	}

	@Override
	/** {@inheritDoc} */
	public void findReferences(final ReferenceFinder referenceFinder, final List<Hit> foundIdentifiers) {
		if (inTypes != null) {
			for (final IType t : inTypes) {
				t.findReferences(referenceFinder, foundIdentifiers);
			}
		}
		if (outTypes != null) {
			for (final IType t : outTypes) {
				t.findReferences(referenceFinder, foundIdentifiers);
			}
		}
		if (inoutTypes != null) {
			for (final IType t : inoutTypes) {
				t.findReferences(referenceFinder, foundIdentifiers);
			}
		}
		for (int i = 0; i < providerReferences.size(); i++) {
			providerReferences.get(i).findReferences(referenceFinder, foundIdentifiers);
		}
		if (inMappings != null) {
			inMappings.findReferences(referenceFinder, foundIdentifiers);
		}
		if (outMappings != null) {
			outMappings.findReferences(referenceFinder, foundIdentifiers);
		}
		if (vardefs != null) {
			vardefs.findReferences(referenceFinder, foundIdentifiers);
		}
		if (mapParams != null) {
			mapParams.findReferences(referenceFinder, foundIdentifiers);
		}
		if (unmapParams != null) {
			unmapParams.findReferences(referenceFinder, foundIdentifiers);
		}
	}

	@Override
	/** {@inheritDoc} */
	protected boolean memberAccept(final ASTVisitor v) {
		if (inTypes!=null) {
			for (final IType t : inTypes) {
				if (!t.accept(v)) {
					return false;
				}
			}
		}
		if (outTypes!=null) {
			for (final IType t : outTypes) {
				if (!t.accept(v)) {
					return false;
				}
			}
		}
		if (inoutTypes!=null) {
			for (final IType t : inoutTypes) {
				if (!t.accept(v)) {
					return false;
				}
			}
		}
		for (int i = 0; i < providerReferences.size(); i++) {
			if (!providerReferences.get(i).accept(v)) {
				return false;
			}
		}
		if (inMappings!=null && !inMappings.accept(v)) {
			return false;
		}
		if (outMappings!=null && !outMappings.accept(v)) {
			return false;
		}
		if (vardefs!=null && !vardefs.accept(v)) {
			return false;
		}
		if (mapParams != null && !mapParams.accept(v)) {
			return false;
		}
		if (unmapParams != null && !unmapParams.accept(v)) {
			return false;
		}
		return true;
	}

	/**
	 * This function generates and returns the name of the class representing the port type.
	 * In some cases this is generated by us, in other cases the user has to provide it.
	 *
	 * @param aData used to access build settings.
	 * @param source where the source code is to be generated.
	 * */
	public String getClassName(final JavaGenData aData, final StringBuilder source) {
		final PortDefinition portDefinition = generateDefinitionForCodeGeneration(aData, source);

		return PortGenerator.getClassName(aData, source, portDefinition, myScope);
	}

	/**
	 * Add generated java code on this level.
	 *
	 * @param aData only used to update imports if needed
	 * @param source the source code generated
	 */
	public void generateCode(final JavaGenData aData, final StringBuilder source) {
		final PortDefinition portDefinition = generateDefinitionForCodeGeneration(aData, source);

		PortGenerator.generateClass(aData, source, myScope.getModuleScopeGen().getProject(), portDefinition);
	}

	/**
	 * Creates a PortDefinition data structure for code generation from the data of this port type body.
	 *
	 * @param aData only used to update imports if needed
	 * @param source the source code generated
	 *
	 * @return the data returned.
	 */
	public PortDefinition generateDefinitionForCodeGeneration(final JavaGenData aData, final StringBuilder source) {
		final String genName = myType.getGenNameOwn();

		final PortDefinition portDefinition = new PortDefinition(genName, getFullName());
		portDefinition.legacy = legacy;
		portDefinition.realtime = realtime;
		if (inMessages != null) {
			for (int i = 0 ; i < inMessages.getNofTypes(); i++) {
				final IType inType = inMessages.getTypeByIndex(i);

				final messageTypeInfo info = new messageTypeInfo(inType.getGenNameValue(aData, source), inType.getGenNameTemplate(aData, source), inType.getTypename());
				portDefinition.inMessages.add(info);
			}
		}

		if (outMessages != null) {
			for (int i = 0 ; i < outMessages.getNofTypes(); i++) {
				final IType outType = outMessages.getTypeByIndex(i);
				final MessageMappedTypeInfo mappedType = new MessageMappedTypeInfo(outType.getGenNameValue(aData, source), outType.getGenNameTemplate(aData, source), outType.getTypename());

				if (portType == PortType_type.PT_USER && (legacy || outMappings != null)) {
					if (legacy || outMappings.hasMappingForType(CompilationTimeStamp.getBaseTimestamp(), outType)) {
						final TypeMapping mapping = outMappings.getMappingForType(CompilationTimeStamp.getBaseTimestamp(), outType);
						final ArrayList<PortGenerator.MessageTypeMappingTarget> targets = new ArrayList<PortGenerator.MessageTypeMappingTarget>(mapping.getNofTargets());
						for (int j = 0; j < mapping.getNofTargets(); j++) {
							final AtomicBoolean hasSliding = new AtomicBoolean();
							final PortGenerator.MessageTypeMappingTarget tempTarget = mapping.getTargetByIndex(j).fillTypeMappingTarget(aData, source, outType, hasSliding);

							tempTarget.targetIndex = -1;
							targets.add(tempTarget);
						}

						mappedType.addTargets(targets);
					} else if (!legacy){
						final ArrayList<PortGenerator.MessageTypeMappingTarget> targets = new ArrayList<PortGenerator.MessageTypeMappingTarget>(1);

						final PortGenerator.MessageTypeMappingTarget tempTarget = new PortGenerator.MessageTypeMappingTarget(outType.getGenNameValue(aData, source), outType.getTypename());
						targets.add(tempTarget);
						mappedType.addTargets(targets);
					}
				}

				portDefinition.outMessages.add(mappedType);
			}
		}

		if (inSignatures != null) {
			for (int i = 0 ; i < inSignatures.getNofTypes(); i++) {
				final IType outType = inSignatures.getTypeByIndex(i);
				final Signature_Type signature = (Signature_Type) outType.getTypeRefdLast(CompilationTimeStamp.getBaseTimestamp());

				final procedureSignatureInfo info = new procedureSignatureInfo(outType.getGenNameValue(aData, source), outType.getTypename(), signature.isNonblocking(), signature.getSignatureExceptions() != null, false);
				portDefinition.inProcedures.add(info);
			}
		}

		if (outSignatures != null) {
			for (int i = 0 ; i < outSignatures.getNofTypes(); i++) {
				final IType outType = outSignatures.getTypeByIndex(i);
				final Signature_Type signature = (Signature_Type) outType.getTypeRefdLast(CompilationTimeStamp.getBaseTimestamp());

				final procedureSignatureInfo info = new procedureSignatureInfo(outType.getGenNameValue(aData, source), outType.getTypename(), signature.isNonblocking(), signature.getSignatureExceptions() != null, signature.getSignatureReturnType() != null);
				portDefinition.outProcedures.add(info);
			}
		}

		switch (testportType) {
		case TP_REGULAR:
			portDefinition.testportType = TestportType.NORMAL;
			break;
		case TP_INTERNAL:
			portDefinition.testportType = TestportType.INTERNAL;
			break;
		case TP_ADDRESS: {
			portDefinition.testportType = TestportType.ADDRESS;
			final IType address = getAddressType(CompilationTimeStamp.getBaseTimestamp());
			portDefinition.addressName = address.getGenNameValue(aData, source);
			break;
		}
		default:
			portDefinition.testportType = TestportType.NORMAL;
			break;

		}


		if (portType == PortType_type.PT_USER) {
			portDefinition.portType = PortType.USER;

			if (legacy) {
				final Port_Type providerType = providerTypes.get(0);
				final PortTypeBody providerBody = providerType.getPortBody();

				portDefinition.providerMessageOutList = new ArrayList<PortGenerator.portMessageProvider>();
				final String providerName = providerType.getGenNameOwn();
				final PortGenerator.portMessageProvider temp = new PortGenerator.portMessageProvider(providerName, null, providerBody.realtime);
				portDefinition.providerMessageOutList.add(temp);

				if (providerBody.inMessages != null) {
					portDefinition.providerInMessages = new ArrayList<PortGenerator.MessageMappedTypeInfo>(providerBody.inMessages.getNofTypes());
					for (int i = 0; i < providerBody.inMessages.getNofTypes(); i++) {
						final IType type = providerBody.inMessages.getTypeByIndex(i);
						final String typeName = type.getGenNameValue(aData, source);
						final String templateName = type.getGenNameTemplate(aData, source);
						final String displayName = type.getTypename();
						final MessageMappedTypeInfo mappedType = new MessageMappedTypeInfo(typeName, templateName, displayName);

						final TypeMapping mapping = inMappings.getMappingForType(CompilationTimeStamp.getBaseTimestamp(), type);
						final ArrayList<PortGenerator.MessageTypeMappingTarget> targets = new ArrayList<PortGenerator.MessageTypeMappingTarget>(mapping.getNofTargets());
						for (int j = 0; j < mapping.getNofTargets(); j++) {
							final TypeMappingTarget target = mapping.getTargetByIndex(j);
							final AtomicBoolean sliding = new AtomicBoolean();
							final MessageTypeMappingTarget mtmTarget = target.fillTypeMappingTarget(aData, source, type, sliding);
							targets.add(mtmTarget);
							portDefinition.has_sliding |= sliding.get();

							final Type targetType = target.getTargetType();
							if (targetType == null) {
								// the message will be discarded: fill in a dummy index
								mtmTarget.targetIndex = -1;
							} else {
								if (inMessages.hasType(CompilationTimeStamp.getBaseTimestamp(), targetType)) {
									mtmTarget.targetIndex = inMessages.getIndexByType(targetType);
								} else {
									mtmTarget.targetIndex = -1;
								}
							}
						}

						mappedType.addTargets(targets);
						portDefinition.providerInMessages.add(mappedType);
					}
				}
			} else {
				// non-legacy standard like behavior
				portDefinition.providerMessageOutList = new ArrayList<PortGenerator.portMessageProvider>(providerTypes.size());
				for (int i = 0; i < providerTypes.size(); i++) {
					final Port_Type providerType = providerTypes.get(i);
					final String name = providerType.getGenNameValue(aData, source);
					final PortTypeBody providerTypeBody = providerType.getPortBody();
					ArrayList<String> names = null;
					if (providerTypeBody.outMessages != null) {
						names = new ArrayList<String>(providerTypeBody.outMessages.getNofTypes());
						for (int j = 0; j < providerTypeBody.outMessages.getNofTypes(); j++) {
							names.add(providerTypeBody.outMessages.getTypeByIndex(j).getGenNameValue(aData, source));
						}
					}

					final PortGenerator.portMessageProvider temp = new PortGenerator.portMessageProvider(name, names, providerTypeBody.realtime);
					portDefinition.providerMessageOutList.add(temp);
				}

				if (inMessages != null) {
					portDefinition.providerInMessages = new ArrayList<PortGenerator.MessageMappedTypeInfo>(inMessages.getNofTypes());
					// First we insert the in messages with simple conversion (no conversion)
					// into a set called pdef.provider_msg_in.elements
					for (int i = 0; i < inMessages.getNofTypes(); i++) {
						final IType type = inMessages.getTypeByIndex(i);
						final String typeName = type.getGenNameValue(aData, source);
						final String templateName = type.getGenNameTemplate(aData, source);
						final String displayName = type.getTypename();
						final MessageMappedTypeInfo mappedType = new MessageMappedTypeInfo(typeName, templateName, displayName);

						final ArrayList<PortGenerator.MessageTypeMappingTarget> targets = new ArrayList<PortGenerator.MessageTypeMappingTarget>(1);
						final String targetType = type.getGenNameValue(aData, source);
						final String targetDisplayName = type.getTypename();
						final MessageTypeMappingTarget mtmTarget = new MessageTypeMappingTarget(targetType, targetDisplayName);
						mtmTarget.targetIndex = inMessages.getIndexByType(type);
						targets.add(mtmTarget);

						mappedType.addTargets(targets);
						portDefinition.providerInMessages.add(mappedType);
					}
				}

				if (inMappings != null) {
					// Secondly we insert the mappings into the pdef.
					// We collect the mapping sources for each distinct mapping targets.
					// Kind of reverse what we did in the legacy behaviour.
					for (int j = 0; j < inMappings.getNofMappings(); j++) {
						final TypeMapping mapping = inMappings.getMappingByIndex(j);
						for (int u = 0; u < mapping.getNofTargets(); u++) {
							final TypeMappingTarget mappingTarget = mapping.getTargetByIndex(u);
							final Type mappingTargetType = mappingTarget.getTargetType();

							MessageMappedTypeInfo mappedType = null;
							for (int k = 0; k < portDefinition.providerInMessages.size(); k++) {
								if (portDefinition.providerInMessages.get(k).mDisplayName.equals(mappingTargetType.getTypename())) {
									mappedType = portDefinition.providerInMessages.get(k);
									break;
								}
							}

							if (mappedType == null) {
								// Mapping target not found. Create new port_msg_mapped_type
								final String typeName = mappingTargetType.getGenNameValue(aData, source);
								final String templeName = mappingTargetType.getGenNameTemplate(aData, source);
								final String displayName = mappingTargetType.getTypename();
								mappedType = new MessageMappedTypeInfo(typeName, templeName, displayName);
								portDefinition.providerInMessages.add(mappedType);
							}

							// Insert the mapping source as the mapped target's target.
							final Def_Function targetFunction = ((FunctionTypeMappingTarget) mappingTarget).getFunction();
							final String functionName = targetFunction.getGenNameFromScope(aData, source, "");
							final String functionDisplayName = targetFunction.getFullName();
							final Type sourceType = mapping.getSourceType();
							final String targetType = sourceType.getGenNameValue(aData, source);
							final String targetDisplayName = sourceType.getTypename();

							final MessageTypeMappingTarget newTarget = new MessageTypeMappingTarget(targetType, targetDisplayName, functionName, functionDisplayName, FunctionPrototype_Type.FAST);
							mappedType.addTarget(newTarget);
							if (inMessages.hasType(CompilationTimeStamp.getBaseTimestamp(), sourceType)) {
								newTarget.targetIndex = inMessages.getIndexByType(sourceType);
							}
						}
					}
				}

				if (vardefs != null) {
					final StringBuilder tempVarDefs = new StringBuilder();
					final StringBuilder tempVarInit = new StringBuilder();
					for (int i = 0; i < vardefs.getNofAssignments(); i++) {
						final Definition def = vardefs.getAssignmentByIndex(i);
						String type = "";
						switch (def.getAssignmentType()) {
						case A_VAR:
							type = def.getType(CompilationTimeStamp.getBaseTimestamp()).getGenNameValue(aData, source);
							if(((Def_Var)def).getInitialValue() == null) {
								tempVarInit.append(MessageFormat.format("{0}.clean_up();\n", def.getGenName()));
							} else {
								def.generateCodeInitComp(aData, tempVarInit, def);
							}
							break;
						case A_CONST:
							type = def.getType(CompilationTimeStamp.getBaseTimestamp()).getGenNameValue(aData, source);
							def.generateCodeInitComp(aData, tempVarInit, def);
							break;
						case A_VAR_TEMPLATE:
							type = def.getType(CompilationTimeStamp.getBaseTimestamp()).getGenNameTemplate(aData, source);
							if(((Def_Var_Template)def).getInitialValue() == null) {
								tempVarInit.append(MessageFormat.format("{0}.clean_up();\n", def.getGenName()));
							} else {
								def.generateCodeInitComp(aData, tempVarInit, def);
							}
							break;
						default:
							//FATAL ERROR
							break;
						}

						tempVarDefs.append(MessageFormat.format("private {0} {1} = new {0}();\n", type, def.getGenName()));
					}

					portDefinition.varDefs = tempVarDefs.toString();
					portDefinition.varInit = tempVarInit.toString();
				}

				//collect and handle all of the functions with `port' clause belonging to this port type
				final HashSet<Def_Function> functions = new HashSet<Def_Function>();
				final StringBuilder tempTranslationFunctions = new StringBuilder();
				if (outMappings != null) {
					for (int i = 0; i < outMappings.getNofMappings(); i++) {
						final TypeMapping mapping = outMappings.getMappingByIndex(i);
						for (int j = 0; j < mapping.getNofTargets(); j++) {
							final TypeMappingTarget mappingTarget = mapping.getTargetByIndex(j);
							if (mappingTarget.getTypeMappingType() == TypeMapping_type.FUNCTION) {
								final Def_Function function = ((FunctionTypeMappingTarget)mappingTarget).getFunction();
								final IType functionPortType = function.getPortType(CompilationTimeStamp.getBaseTimestamp());
								if (functionPortType != null && functionPortType == myType && !functions.contains(function)) {
									function.generateCodePortBody(aData, tempTranslationFunctions);
									functions.add(function);
								}
							}
						}
					}
				}
				if (inMappings != null) {
					for (int i = 0; i < inMappings.getNofMappings(); i++) {
						final TypeMapping mapping = inMappings.getMappingByIndex(i);
						for (int j = 0; j < mapping.getNofTargets(); j++) {
							final TypeMappingTarget mappingTarget = mapping.getTargetByIndex(j);
							if (mappingTarget.getTypeMappingType() == TypeMapping_type.FUNCTION) {
								final Def_Function function = ((FunctionTypeMappingTarget)mappingTarget).getFunction();
								final IType functionPortType = function.getPortType(CompilationTimeStamp.getBaseTimestamp());
								if (functionPortType != null && functionPortType == myType && !functions.contains(function)) {
									function.generateCodePortBody(aData, tempTranslationFunctions);
									functions.add(function);
								}
							}
						}
					}
				}

				portDefinition.translationFunctions = tempTranslationFunctions.toString();
			}
		} else {
			// "internal provider" is the same as "internal"
			if (portType == PortType_type.PT_PROVIDER && testportType != TestPortAPI_type.TP_INTERNAL) {
				portDefinition.portType = PortType.PROVIDER;
			} else {
				portDefinition.portType = PortType.REGULAR;
			}
		}

		// TODO will we need to generate testport skeleton here, or are the feature supported by eclipse enough?

		return portDefinition;
	}
}
