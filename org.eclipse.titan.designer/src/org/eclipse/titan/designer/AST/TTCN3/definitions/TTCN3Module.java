/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.AST.TTCN3.definitions;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.titan.common.product.ProductIdentity;
import org.eclipse.titan.designer.GeneralConstants;
import org.eclipse.titan.designer.AST.ASTVisitor;
import org.eclipse.titan.designer.AST.Assignment;
import org.eclipse.titan.designer.AST.Assignment.Assignment_type;
import org.eclipse.titan.designer.AST.Assignments;
import org.eclipse.titan.designer.AST.INamedNode;
import org.eclipse.titan.designer.AST.IReferenceChain;
import org.eclipse.titan.designer.AST.ISubReference;
import org.eclipse.titan.designer.AST.IType;
import org.eclipse.titan.designer.AST.Identifier;
import org.eclipse.titan.designer.AST.Identifier.Identifier_type;
import org.eclipse.titan.designer.AST.Location;
import org.eclipse.titan.designer.AST.MarkerHandler;
import org.eclipse.titan.designer.AST.Module;
import org.eclipse.titan.designer.AST.ModuleImportation;
import org.eclipse.titan.designer.AST.ModuleImportationChain;
import org.eclipse.titan.designer.AST.NamingConventionHelper;
import org.eclipse.titan.designer.AST.Reference;
import org.eclipse.titan.designer.AST.ReferenceFinder;
import org.eclipse.titan.designer.AST.ReferenceFinder.Hit;
import org.eclipse.titan.designer.AST.Scope;
import org.eclipse.titan.designer.AST.TTCN3.attributes.AttributeSpecification;
import org.eclipse.titan.designer.AST.TTCN3.attributes.ExtensionAttribute;
import org.eclipse.titan.designer.AST.TTCN3.attributes.ModuleVersionAttribute;
import org.eclipse.titan.designer.AST.TTCN3.attributes.MultipleWithAttributes;
import org.eclipse.titan.designer.AST.TTCN3.attributes.Qualifiers;
import org.eclipse.titan.designer.AST.TTCN3.attributes.SingleWithAttribute;
import org.eclipse.titan.designer.AST.TTCN3.attributes.SingleWithAttribute.Attribute_Type;
import org.eclipse.titan.designer.AST.TTCN3.attributes.TitanVersionAttribute;
import org.eclipse.titan.designer.AST.TTCN3.attributes.VersionRequirementAttribute;
import org.eclipse.titan.designer.AST.TTCN3.attributes.WithAttributesPath;
import org.eclipse.titan.designer.AST.TTCN3.types.Anytype_Type;
import org.eclipse.titan.designer.compiler.JavaGenData;
import org.eclipse.titan.designer.core.CompilerVersionInformationCollector;
import org.eclipse.titan.designer.core.LoadBalancingUtilities;
import org.eclipse.titan.designer.core.ProductIdentityHelper;
import org.eclipse.titan.designer.editors.ProposalCollector;
import org.eclipse.titan.designer.editors.SkeletonTemplateProposal;
import org.eclipse.titan.designer.editors.T3Doc;
import org.eclipse.titan.designer.editors.actions.DeclarationCollector;
import org.eclipse.titan.designer.editors.ttcn3editor.TTCN3CodeSkeletons;
import org.eclipse.titan.designer.editors.ttcn3editor.TTCN3Keywords;
import org.eclipse.titan.designer.parsers.CompilationTimeStamp;
import org.eclipse.titan.designer.parsers.ParserUtilities;
import org.eclipse.titan.designer.parsers.ProjectSourceParser;
import org.eclipse.titan.designer.parsers.ProjectStructureDataCollector;
import org.eclipse.titan.designer.parsers.extensionattributeparser.ExtensionAttributeAnalyzer;
import org.eclipse.titan.designer.parsers.ttcn3parser.IIdentifierReparser;
import org.eclipse.titan.designer.parsers.ttcn3parser.ITTCN3ReparseBase;
import org.eclipse.titan.designer.parsers.ttcn3parser.IdentifierReparser;
import org.eclipse.titan.designer.parsers.ttcn3parser.ReParseException;
import org.eclipse.titan.designer.parsers.ttcn3parser.TTCN3ReparseUpdater;
import org.eclipse.titan.designer.parsers.ttcn3parser.Ttcn3Reparser;
import org.eclipse.titan.designer.parsers.ttcn3parser.Ttcn3Reparser.Pr_reparser_optionalWithStatementContext;
import org.eclipse.titan.designer.preferences.PreferenceConstants;

/**
 * Represents a Module.
 *
 * @author Kristof Szabados
 * @author Arpad Lovassy
 * @author Jeno Attila Balasko
 */
public final class TTCN3Module extends Module {
	private static final String FULLNAMEPART = ".control";
	public static final String MODULE = "module";

	private static final String MISSINGREFERENCE = "There is no visible definition with name `{0}'' in module `{1}''";

	private List<String> languageSpecifications;
	private final List<ImportModule> importedModules;
	private final List<FriendModule> friendModules;
	private final Definitions definitions;
	private ControlPart controlpart;

	private Location commentLocation = null;

	// for TTCNPP modules
	private Set<IFile> includedFiles = null;
	// for TTCNPP modules
	private List<Location> inactiveCodeLocations = null;

	// The any type of the module
	private final Anytype_Type anytype;
	private final Def_Type anytypeDefinition;

	private WithAttributesPath withAttributesPath = null;

	// The "supplied" version
	private ProductIdentity versionNumber = null;

	//TODO detecting missing references should be done in Titanium.
	private final List<Reference> missingReferences;

	//The MD5 hash of the module
	private byte[] digest;

	private boolean needsTobeBuilt = true;

	public TTCN3Module(final Identifier identifier, final IProject project) {
		super(identifier, project);

		importedModules = new CopyOnWriteArrayList<ImportModule>();
		friendModules = new CopyOnWriteArrayList<FriendModule>();

		definitions = new Definitions();
		definitions.setParentScope(this);
		definitions.setFullNameParent(this);

		anytype = new Anytype_Type();
		anytypeDefinition = new Def_Type(new Identifier(Identifier_type.ID_TTCN, "anytype"), anytype);
		anytypeDefinition.setMyScope(this);
		anytypeDefinition.setFullNameParent(this);

		missingReferences = new ArrayList<Reference>();
	}

	@Override
	/** {@inheritDoc} */
	public module_type getModuletype() {
		return module_type.TTCN3_MODULE;
	}

	@Override
	/** {@inheritDoc} */
	public StringBuilder getFullName(final INamedNode child) {
		final StringBuilder builder = new StringBuilder();
		builder.append(INamedNode.MODULENAMEPREFIX).append(getIdentifier().getDisplayName());

		if (controlpart == child) {
			return builder.append(FULLNAMEPART);
		} else if (anytypeDefinition == child) {
			final Identifier identifier = anytypeDefinition.getIdentifier();

			return builder.append(INamedNode.DOT).append(identifier.getDisplayName());
		}

		return builder;
	}

	/**
	 * Sets the MD5 digest of this module.
	 *
	 * @param digest the digest.
	 * */
	public void addMD5Digest(final byte[] digest) {
		this.digest = new byte[digest.length];
		System.arraycopy(digest, 0, this.digest, 0, digest.length);
	}

	public void setLanguageSpecifications(final List<String> languageSpecifications) {
		this.languageSpecifications = languageSpecifications;
	}

	public List<String> getLanguageSpecifictions() {
		return languageSpecifications;
	}

	/**
	 * Sets the location of this definition list.
	 *
	 * @param location
	 *                the location to set.
	 * */
	public void setDefinitionsLocation(final Location location) {
		definitions.setLocation(location);
	}

	/**
	 * Sets the module's version information
	 *
	 * @param versionNumber
	 *                the version number.
	 */
	private void setVersion(final ProductIdentity versionNumber) {
		this.versionNumber = versionNumber;
	}

	/**
	 * @return The location of the comment assigned to this definition. Or
	 *         null if none.
	 * */
	@Override
	public Location getCommentLocation() {
		return commentLocation;
	}

	/**
	 * Sets the location of the comment that belongs to this definition.
	 *
	 * @param commentLocation
	 *                the location of the comment
	 * */
	public void setCommentLocation(final Location commentLocation) {
		this.commentLocation = commentLocation;
	}

	public void addDefinitions(final List<Definition> definitionList) {
		definitions.addDefinitions(definitionList);
	}

	public void addImportedModule(final ImportModule impmod) {
		if (impmod == null || impmod.getIdentifier() == null || impmod.getLocation() == null) {
			return;
		}

		importedModules.add(impmod);
		impmod.setMyModule(identifier);
		impmod.setMyModule(this);
		impmod.setProject(project);
	}

	public void addGroup(final Group group) {
		if (group != null && group.getIdentifier() != null) {
			definitions.addGroup(group);
		}
	}

	public void addFriendModule(final FriendModule friendModule) {
		if (friendModule != null) {
			friendModules.add(friendModule);
			friendModule.setProject(project);
		}
	}

	/**
	 * Adds a list of friend module declarations to the list of friend modules.
	 * <p>
	 * The project of the newly added declarations are set to this project
	 * here.
	 *
	 * @param friendModuleList
	 *                the friend modules to be added
	 * */
	public void addFriendModules(final List<FriendModule> friendModuleList) {
		if (friendModuleList != null) {
			final ArrayList<FriendModule> safeToAdd = new ArrayList<FriendModule>(friendModuleList.size());
			for (final FriendModule friendModule : friendModuleList) {
				if (friendModule != null) {
					safeToAdd.add(friendModule);
					friendModule.setProject(project);
				}
			}
			friendModules.addAll(safeToAdd);
		}
	}

	@Override
	/** {@inheritDoc} */
	public Definitions getAssignmentsScope() {
		return definitions;
	}

	@Override
	/** {@inheritDoc} */
	public Assignments getAssignments() {
		return definitions;
	}

	public Definitions getDefinitions(){
		return definitions;
	}

	@Override
	/** {@inheritDoc} */
	public Def_Type getAnytype() {
		return anytypeDefinition;
	}

	/**
	 * Checks if there is an address type defined in the module.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle
	 *
	 * @return a pointer to the TTCN-3 special address type that is defined
	 *         in the TTCN-3 module. null is returned if the address type is
	 *         not defined in this module.
	 * */
	public IType getAddressType(final CompilationTimeStamp timestamp) {
		final Identifier addressIdentifier = new Identifier(Identifier_type.ID_TTCN, "address");

		if (!definitions.hasLocalAssignmentWithID(timestamp, addressIdentifier)) {
			return null;
		}

		final Definition definition = definitions.getLocalAssignmentByID(timestamp, addressIdentifier);

		if (!Assignment_type.A_TYPE.semanticallyEquals(definition.getAssignmentType())) {
			return null;
		}

		return definition.getType(timestamp);
	}

	@Override
	/** {@inheritDoc} */
	public Object[] getOutlineChildren() {
		if (!importedModules.isEmpty()) {
			return new Object[] { importedModules, definitions };
		}
		return new Object[] { definitions };
	}

	@Override
	/** {@inheritDoc} */
	public String getOutlineIcon() {
		return "ttcn.gif";
	}

	/**
	 * Adds the control part to this module.
	 *
	 * @param controlpart
	 *                the controlpart to be added.
	 * */
	public void addControlpart(final ControlPart controlpart) {
		if (controlpart != null) {
			this.controlpart = controlpart;
			controlpart.setMyScope(definitions);
			controlpart.setFullNameParent(this);
		}
	}

	@Override
	/** {@inheritDoc} */
	public boolean isValidModuleId(final Identifier identifier) {
		if (identifier == null) {
			return false;
		}

		final String originalName = identifier.getName();
		// The identifier represents the current module
		if (this.identifier != null && originalName.equals(this.identifier.getName())) {
			return true;
		}
		// The identifier represents a module imported in the current
		// module
		for (final ImportModule impMod : importedModules) {
			if (originalName.equals(impMod.getName())) {
				return true;
			}
		}
		return false;
	}

	@Override
	/** {@inheritDoc} */
	public void checkImports(final CompilationTimeStamp timestamp, final ModuleImportationChain referenceChain, final List<Module> moduleStack) {

		if (lastImportCheckTimeStamp != null && !lastImportCheckTimeStamp.isLess(timestamp)) {
			return;
		}

		for (final ImportModule impmod : importedModules) {
			impmod.setUsedForImportation(false);
		}

		for (final ImportModule impmod : importedModules) {
			referenceChain.markState();
			impmod.checkImports(timestamp, referenceChain, moduleStack);//This checks only existence, not "used or not used"
			//timestamp is set!!!, markers are refreshed
			referenceChain.previousState();
			LoadBalancingUtilities.astNodeChecked();
		}
		lastImportCheckTimeStamp = timestamp; //timestamp can be set only here to handle circular import chains!
	}

	/**
	 * Checks that each friend module of this module is provided only once.
	 * */
	private void checkFriendModuleUniqueness() {
		if (!friendModules.isEmpty()) {
			final Map<String, FriendModule> map = new HashMap<String, FriendModule>(friendModules.size());

			for (final FriendModule friendModule : friendModules) {
				final Identifier identifier = friendModule.getIdentifier();
				final String name = identifier.getName();
				if (map.containsKey(name)) {
					final Location otherLocation = map.get(name).getIdentifier().getLocation();
					otherLocation.reportSingularSemanticError(MessageFormat.format(
							"Duplicate friend module `{0}'' was first declared here", identifier.getDisplayName()));

					final Location friendLocation = friendModule.getIdentifier().getLocation();
					friendLocation.reportSingularSemanticError(MessageFormat.format(
							"Duplicate friend module `{0}'' was declared here again", identifier.getDisplayName()));
				} else {
					map.put(name, friendModule);
				}
			}
		}
	}

	/**
	 * Collects the module importations into a list. The list shall always
	 * exist even if being empty.
	 *
	 * @return the list of modules imported.
	 * */
	public List<ImportModule> getImports() {
		final List<ImportModule> result = new ArrayList<ImportModule>(importedModules.size());
		result.addAll(importedModules);

		return result;
	}

	@Override
	/** {@inheritDoc} */
	public List<Module> getImportedModules() {
		final List<Module> result = new ArrayList<Module>();

		for (final ImportModule impmod : importedModules) {
			final Module module = impmod.getReferredModule();
			if (module != null) {
				result.add(module);
			}
		}

		return result;
	}

	@Override
	/** {@inheritDoc} */
	public boolean hasUnhandledImportChanges() {
		for (final ImportModule impmod : importedModules) {
			if (impmod.hasUnhandledChange()) {
				return true;
			}
		}

		return false;
	}

	@Override
	/** {@inheritDoc} */
	public void check(final CompilationTimeStamp timestamp) {
		if (lastCompilationTimeStamp != null && !lastCompilationTimeStamp.isLess(timestamp)) {
			return;
		}

		T3Doc.check(this.getCommentLocation(), MODULE);

		lastCompilationTimeStamp = timestamp;
		needsTobeBuilt = true;

		if (getSkippedFromSemanticChecking()) {
			return;
		}

		NamingConventionHelper.checkConvention(PreferenceConstants.REPORTNAMINGCONVENTION_TTCN3MODULE, identifier, "TTCN-3 module");

		// re-initialize at the beginning of the cycle.
		versionNumber = null;
		missingReferences.clear();

		definitions.setGenName();
		anytypeDefinition.setGenName(anytypeDefinition.getIdentifier().getName());

		if (withAttributesPath != null) {
			MarkerHandler.markAllSemanticMarkersForRemoval(withAttributesPath);
			withAttributesPath.checkGlobalAttributes(timestamp, false);
			withAttributesPath.checkAttributes(timestamp);
		}

		for (final ImportModule impMod : importedModules) {
			impMod.check(timestamp);
		}

		checkFriendModuleUniqueness();
		for (final FriendModule friendModule : friendModules) {
			friendModule.check(timestamp);
		}

		analyzeExtensionAttributes(timestamp);

		anytypeDefinition.check(timestamp);
		definitions.check(timestamp);

		if (controlpart != null) {
			controlpart.check(timestamp);
		}
	}

	/**
	 * Experimental method for BrokenPartsViaInvertedImports.
	 */
	public void checkWithDefinitions(final CompilationTimeStamp timestamp, final List<Assignment> assignments) {
		if (lastCompilationTimeStamp != null && !lastCompilationTimeStamp.isLess(timestamp)) {
			return;
		}

		T3Doc.check(this.getCommentLocation(), MODULE);

		lastCompilationTimeStamp = timestamp;
		needsTobeBuilt = true;

		NamingConventionHelper.checkConvention(PreferenceConstants.REPORTNAMINGCONVENTION_TTCN3MODULE, identifier, "TTCN-3 module");

		// re-initialize at the beginning of the cycle.
		versionNumber = null;
		missingReferences.clear();

		if (withAttributesPath != null) {
			MarkerHandler.markAllSemanticMarkersForRemoval(withAttributesPath);
			withAttributesPath.checkGlobalAttributes(timestamp, false);
			withAttributesPath.checkAttributes(timestamp);
		}

		//for (ImportModule impMod : importedModules) {
		//	impMod.check(timestamp);
		//}

		checkFriendModuleUniqueness();
		for (final FriendModule friendModule : friendModules) {
			friendModule.check(timestamp);
		}

		analyzeExtensionAttributes(timestamp);

		//anytypeDefinition.check(timestamp);
		//definitions.check(timestamp);

		definitions.checkWithDefinitions(timestamp, assignments);

		if (controlpart != null) {
			controlpart.check(timestamp);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void postCheck() {
		definitions.postCheck();

		if (controlpart != null) {
			controlpart.postCheck();
		}
	}

	/**
	 * Convert and check the version, requires and titan version extension attributes.
	 *
	 * @param timestamp
	 *                the timestamp of the actual build cycle.
	 * */
	private void analyzeExtensionAttributes(final CompilationTimeStamp timestamp) {
		if (withAttributesPath == null) {
			return;
		}

		final List<SingleWithAttribute> realAttributes = withAttributesPath.getRealAttributes(timestamp);

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
					specifications.add(attribute.getAttributeSpecification());
				}
			}
		}

		if (specifications == null) {
			return;
		}

		final List<ExtensionAttribute> attributes = new ArrayList<ExtensionAttribute>();
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

		ExtensionAttribute extensionAttribute;
		for (int i = 0; i < attributes.size(); i++) {
			extensionAttribute = attributes.get(i);

			switch (extensionAttribute.getAttributeType()) {
			case VERSION: {
				final ModuleVersionAttribute moduleVersion = (ModuleVersionAttribute) extensionAttribute;
				moduleVersion.parse();
				if (versionNumber != null) {
					moduleVersion.getLocation().reportSemanticError("Duplicate version attribute");
				} else {
					setVersion(moduleVersion.getVersionNumber());
				}
				break;
			}
			case REQUIRES: {
				final VersionRequirementAttribute versionReq = (VersionRequirementAttribute) extensionAttribute;
				versionReq.parse();

				ImportModule theImport = null;
				final String requiredModuleName = versionReq.getRequiredModule().getName();
				for (final ImportModule impMod : importedModules) {
					if (requiredModuleName.equals(impMod.getIdentifier().getName())) {
						theImport = impMod;
						break;
					}
				}
				if (theImport == null) {
					final String message = MessageFormat.format(ImportModule.MISSINGMODULE, versionReq.getRequiredModule()
							.getDisplayName());
					versionReq.getRequiredModule().getLocation().reportSemanticError(message);
				} else {
					final TTCN3Module theImportedModule = (TTCN3Module) theImport.getReferredModule();
					if (theImportedModule == null) {
						break;
					}
					// make sure the version attribute is parsed (if any)
					theImportedModule.check(timestamp);
					final ProductIdentity requiredVersion = versionReq.getVersionNumber();
					if (requiredVersion != null && theImportedModule.versionNumber != null
							&& theImportedModule.versionNumber.compareTo(requiredVersion) < 0) {
						final String message = MessageFormat
								.format("Module `{0}'' requires version {1} of module `{2}'', but only version {3} is available",
										identifier.getDisplayName(), requiredVersion.toString(),
										theImportedModule.getIdentifier().getDisplayName(),
										theImportedModule.versionNumber.toString());
						versionReq.getLocation().reportSemanticError(message);
					}
				}
				break;
			}
			case TITANVERSION: {
				final TitanVersionAttribute titanReq = (TitanVersionAttribute) extensionAttribute;
				titanReq.parse();
				final ProductIdentity requiredTITANVersion = titanReq.getVersionNumber();
				final String temp = CompilerVersionInformationCollector.getCompilerProductNumber();
				final ProductIdentity compilerVersion = ProductIdentityHelper.getProductIdentity(temp, null);
				if (requiredTITANVersion != null && compilerVersion != null && compilerVersion.compareTo(requiredTITANVersion) < 0) {
					final String message = MessageFormat.format(
							"Module `{0}'' requires TITAN version {1}, but version {2} is used right now",
							identifier.getDisplayName(), requiredTITANVersion.toString(), compilerVersion.toString());
					titanReq.getLocation().reportSemanticError(message);
				}
				if (requiredTITANVersion != null && GeneralConstants.ON_THE_FLY_ANALYZER_VERSION != null
						&& GeneralConstants.ON_THE_FLY_ANALYZER_VERSION.compareTo(requiredTITANVersion) < 0) {
					final String message = MessageFormat.format(
							"Module `{0}'' requires TITAN version {1}, but the on-the-fly analyzer is of version {2}",
							identifier.getDisplayName(), requiredTITANVersion.toString(),
							GeneralConstants.ON_THE_FLY_ANALYZER_VERSION.toString());
					titanReq.getLocation().reportSemanticError(message);
				}

				break;
			}
			default:
				// we don't care
				break;
			}
		}
	}

	@Override
	/** {@inheritDoc} */
	public Scope getSmallestEnclosingScope(final int offset) {
		if (location == null || offset < location.getOffset() || offset > location.getEndOffset()) {
			return null;
		}
		if (controlpart != null && controlpart.getLocation() != null && controlpart.getLocation().getOffset() < offset
				&& offset < controlpart.getLocation().getEndOffset()) {
			return controlpart.getSmallestEnclosingScope(offset);
		}
		return definitions.getSmallestEnclosingScope(offset);
	}

	@Override
	/** {@inheritDoc} */
	public boolean hasImportedAssignmentWithID(final CompilationTimeStamp timestamp, final Identifier identifier) {
		for (final ImportModule impMod : importedModules) {
			if (impMod.hasImportedAssignmentWithID(timestamp, identifier)) {
				return true;
			}
		}

		return false;
	}

	@Override
	/** {@inheritDoc} */
	public Definition importAssignment(final CompilationTimeStamp timestamp, final Identifier moduleId, final Reference reference) {
		final Definition result = definitions.getLocalAssignmentByID(timestamp, reference.getId());
		if (result == null) {
			return null;
		}

		final VisibilityModifier modifier = result.getVisibilityModifier();

		switch (modifier) {
		case Public:
			return result;
		case Friend:
			for (final FriendModule friend : friendModules) {
				if (friend.getIdentifier().getName().equals(moduleId.getName())) {
					return result;
				}
			}

			return null;
		case Private:
			return null;
		default:
			return result;
		}
	}

	/**
	 * Checks whether the module importation in this module is visible in
	 * the provided module or not.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle.
	 * @param moduleId
	 *                the identifier of the module, against which the
	 *                visibility of the assignment is checked.
	 * @param impmod
	 *                the module importation to check.
	 *
	 * @return true if it is visible, false otherwise.
	 * */
	public boolean isVisible(final CompilationTimeStamp timestamp, final Identifier moduleId, final ImportModule impmod) {
		final VisibilityModifier modifier = impmod.getVisibilityModifier();
		switch (modifier) {
		case Public:
			return true;
		case Friend:
			for (final FriendModule friend : friendModules) {
				if (friend.getIdentifier().getName().equals(moduleId.getName())) {
					return true;
				}
			}
			return false;
		case Private:
			return false;
		default:
			return false;
		}
	}

	@Override
	/** {@inheritDoc} */
	public boolean isVisible(final CompilationTimeStamp timestamp, final Identifier moduleId, final Assignment assignment) {
		if (assignment == null || !(assignment instanceof Definition)) {
			return false;
		}

		if (definitions.getLocalAssignmentByID(timestamp, assignment.getIdentifier()) != assignment) {
			return false;
		}

		final VisibilityModifier modifier = ((Definition) assignment).getVisibilityModifier();
		switch (modifier) {
		case Public:
			return true;
		case Friend:
			for (final FriendModule friend : friendModules) {
				if (friend.getIdentifier().getName().equals(moduleId.getName())) {
					return true;
				}
			}
			return false;
		case Private:
			return false;
		default:
			return false;
		}
	}

	@Override
	/** {@inheritDoc} */
	public Assignment getAssBySRef(final CompilationTimeStamp timestamp, final Reference reference) {
		return getAssBySRef(timestamp, reference, null);
	}

	@Override
	/** {@inheritDoc} */
	public Assignment getAssBySRef(final CompilationTimeStamp timestamp, final Reference reference, final IReferenceChain refChain) {
		// if a moduleId is present, that import (or the actual module)
		// must be searched
		final Identifier moduleId = reference.getModuleIdentifier();
		final Location referenceLocation = reference.getLocation();
		final Identifier id = reference.getId();

		if (id == null) {
			return null;
		}

		Assignment temporalAssignment = null;

		if (moduleId == null) {
			// no module name is given in the reference
			if ("anytype".equals(id.getTtcnName())) {
				return anytypeDefinition;
			}

			Assignment tempResult = null;

			for (final ImportModule impMod : importedModules) {
				if (impMod.getReferredModule() != null) {
					final ModuleImportationChain referenceChain = new ModuleImportationChain(ModuleImportationChain.CIRCULARREFERENCE,
							false);
					tempResult = impMod.importAssignment(timestamp, referenceChain, identifier, reference,
							new ArrayList<ModuleImportation>());
					if (tempResult != null
							&& !tempResult.getMyScope().getModuleScope()
							.isVisible(timestamp, this.getIdentifier(), tempResult)) {
						tempResult = null;
					}
					if (tempResult != null) {
						if (temporalAssignment == null) {
							temporalAssignment = tempResult;
						} else if (temporalAssignment != tempResult) {
							reference.getLocation().reportSemanticError(
									"It is not possible to resolve this reference unambigously, as  it can be resolved to `"
											+ temporalAssignment.getFullName() + "' and to `"
											+ tempResult.getFullName() + "'");
							return null;
						}
					}
				}
			}

			if (temporalAssignment != null) {
				return temporalAssignment;
			}

			referenceLocation
			.reportSemanticError(MessageFormat.format(MISSINGREFERENCE, id.getDisplayName(), identifier.getDisplayName()));

			missingReferences.add(reference);
		} else if (moduleId.getName().equals(name)) {
			// the reference points to the own module
			if ("anytype".equals(id.getTtcnName())) {
				return anytypeDefinition;
			}

			temporalAssignment = definitions.getLocalAssignmentByID(timestamp, id);
			if (temporalAssignment == null) {
				referenceLocation.reportSemanticError(MessageFormat.format(MISSINGREFERENCE, id.getDisplayName(),
						identifier.getDisplayName()));
			}
		} else {
			// the reference points to another module
			for (final ImportModule impMod : importedModules) {
				if (moduleId.getName().equals(impMod.getName())) {
					if (impMod.getReferredModule() == null) {
						return temporalAssignment;
					}

					final ModuleImportationChain referenceChain = new ModuleImportationChain(ModuleImportationChain.CIRCULARREFERENCE,
							false);
					temporalAssignment = impMod.importAssignment(timestamp, referenceChain, identifier, reference,
							new ArrayList<ModuleImportation>());
					if (!impMod.getReferredModule().isVisible(timestamp, this.getIdentifier(), temporalAssignment)) {
						temporalAssignment = null;
					}
					if (temporalAssignment == null) {
						referenceLocation.reportSemanticError(MessageFormat.format(MISSINGREFERENCE, id.getDisplayName(),
								impMod.getIdentifier().getDisplayName()));
					}

					return temporalAssignment;
				}
			}

			missingReferences.add(reference);
		}

		return temporalAssignment;
	}

	/**
	 * Collects those references whose referred assignment could not be
	 * found.
	 *
	 * @return the list of missing references.
	 * */
	public List<Reference> getMissingReferences() {
		return missingReferences;
	}

	@Override
	/** {@inheritDoc} */
	public String toString() {
		final StringBuilder builder = new StringBuilder("module: ").append(name);
		return builder.toString();
	}

	@Override
	/** {@inheritDoc} */
	public void addProposal(final ProposalCollector propCollector) {
		final Identifier moduleId = propCollector.getReference().getModuleIdentifier();
		if (moduleId == null) {
			for (final ImportModule importedModule : importedModules) {
				if (importedModule != null) {
					importedModule.addProposal(propCollector, identifier);
				}
			}
		} else if (this.identifier != null && moduleId.getName().equals(this.identifier.getName())) {
			for (int i = 0; i < definitions.getNofAssignments(); i++) {
				definitions.getAssignmentByIndex(i).addProposal(propCollector, 0);
			}
		} else {
			for (final ImportModule importedModule : importedModules) {
				if (importedModule != null && importedModule.getName().equals(moduleId.getName())) {
					importedModule.addProposal(propCollector, identifier);
				}
			}
		}

		super.addProposal(propCollector);
	}

	@Override
	/** {@inheritDoc} */
	public void addSkeletonProposal(final ProposalCollector propCollector) {
		for (final SkeletonTemplateProposal templateProposal : TTCN3CodeSkeletons.MODULE_LEVEL_SKELETON_PROPOSALS) {
			propCollector.addTemplateProposal(templateProposal.getPrefix(), templateProposal.getProposal(),
					TTCN3CodeSkeletons.SKELETON_IMAGE);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void addKeywordProposal(final ProposalCollector propCollector) {
		propCollector.addProposal(TTCN3Keywords.MODULE_SCOPE, null, TTCN3Keywords.KEYWORD);
		propCollector.addProposal(TTCN3Keywords.GENERALLY_USABLE, null, TTCN3Keywords.KEYWORD);
		super.addKeywordProposal(propCollector);
	}

	@Override
	/** {@inheritDoc} */
	public void addDeclaration(final DeclarationCollector declarationCollector) {
		final Identifier moduleId = declarationCollector.getReference().getModuleIdentifier();
		if (moduleId == null) {
			final List<ISubReference> subrefs = declarationCollector.getReference().getSubreferences();
			if (subrefs.size() == 1 && this.identifier != null && this.identifier.getName().equals(subrefs.get(0).getId().getName())) {
				declarationCollector.addDeclaration(name, identifier.getLocation(), this);
			}
			for (final ImportModule importedModule : importedModules) {
				if (importedModule != null) {
					importedModule.addDeclaration(declarationCollector, identifier);
				}
			}
		} else if (this.identifier != null && moduleId.getName().equals(this.identifier.getName())) {
			for (int i = 0; i < definitions.getNofAssignments(); i++) {
				definitions.getAssignmentByIndex(i).addDeclaration(declarationCollector, 0);
			}
		} else {
			for (final ImportModule importedModule : importedModules) {
				if (importedModule != null && importedModule.getName().equals(moduleId.getName())) {
					importedModule.addDeclaration(declarationCollector, identifier);
				}
			}
		}

		super.addDeclaration(declarationCollector);
	}

	/**
	 * Sets the with attributes for this module if it has any. Also creates
	 * the with attribute path, to store the attributes in.
	 *
	 * @param attributes
	 *                the attribute to be added.
	 * */
	public void setWithAttributes(final MultipleWithAttributes attributes) {
		if (withAttributesPath == null) {
			withAttributesPath = new WithAttributesPath();
		}
		if (attributes != null) {
			withAttributesPath.setWithAttributes(attributes);
		}
	}

	/**
	 * @return the with attribute path element of this module. If it did not
	 *         exist it will be created.
	 * */
	public WithAttributesPath getAttributePath() {
		if (withAttributesPath == null) {
			withAttributesPath = new WithAttributesPath();
		}

		return withAttributesPath;
	}

	public void addDeclarationWithoutImportLookup(final DeclarationCollector declarationCollector) {
		final Identifier moduleId = declarationCollector.getReference().getModuleIdentifier();
		if (moduleId == null) {
			for (int i = 0; i < definitions.getNofAssignments(); i++) {
				definitions.getAssignmentByIndex(i).addDeclaration(declarationCollector, 0);
			}

			final List<ISubReference> subrefs = declarationCollector.getReference().getSubreferences();
			if (subrefs.size() == 1 && this.identifier != null && this.identifier.getName().equals(subrefs.get(0).getId().getName())) {
				declarationCollector.addDeclaration(name, identifier.getLocation(), this);
			}
		}
	}

	@Override
	/** {@inheritDoc} */
	public void extractStructuralInformation(final ProjectStructureDataCollector collector) {
		for (final ImportModule imported : importedModules) {
			collector.addImportation(identifier, imported.getIdentifier());
		}
	}

	private int reparseAfterModule(final TTCN3ReparseUpdater aReparser) {
		return aReparser.parse(new ITTCN3ReparseBase() {
			@Override
			public void reparse(final Ttcn3Reparser parser) {
				final Pr_reparser_optionalWithStatementContext root = parser.pr_reparser_optionalWithStatement();
				ParserUtilities.logParseTree( root, parser );
				final MultipleWithAttributes attributes = root.attributes;

				final ParseTree rootEof = parser.pr_EndOfFile();
				ParserUtilities.logParseTree( rootEof, parser );
				if ( parser.isErrorListEmpty() ) {
					withAttributesPath = new WithAttributesPath();
					withAttributesPath.setWithAttributes(attributes);
					if (attributes != null) {
						getLocation().setEndOffset(attributes.getLocation().getEndOffset());
					}
				}
			}
		});
	}

	private int reparseInsideAttributelist(final TTCN3ReparseUpdater aReparser) {
		return aReparser.parse(new ITTCN3ReparseBase() {
			@Override
			public void reparse(final Ttcn3Reparser parser) {
				final Pr_reparser_optionalWithStatementContext root = parser.pr_reparser_optionalWithStatement();
				ParserUtilities.logParseTree( root, parser );
				final MultipleWithAttributes attributes = root.attributes;

				final ParseTree rootEof = parser.pr_EndOfFile();
				ParserUtilities.logParseTree( rootEof, parser );
				if ( parser.isErrorListEmpty() ) {
					withAttributesPath.setWithAttributes(attributes);
					getLocation().setEndOffset(attributes.getLocation().getEndOffset());
				}
			}
		});
	}

	/**
	 * Handles the incremental parsing of this definition.
	 *
	 * @param reparser
	 *                the parser doing the incremental parsing.
	 * @param sourceParser
	 *                the general utility handling the parse of TTCN-3 and
	 *                ASN.1 files, to efficiently handle module renaming.
	 * */
	public void updateSyntax(final TTCN3ReparseUpdater reparser, final ProjectSourceParser sourceParser) throws ReParseException {
		needsTobeBuilt = true;

		if (reparser.getShift() < 0) {
			throw new ReParseException();
		}
		// edited outside the module
		if (reparser.getDamageEnd() < location.getOffset()) {
			// before the module
			reparser.updateLocation(identifier.getLocation());
			if (definitions != null) {
				definitions.updateSyntax(reparser, importedModules, friendModules, controlpart);
			}
			if (controlpart != null) {
				controlpart.updateSyntax(reparser);
				reparser.updateLocation(controlpart.getLocation());
			}
			if (withAttributesPath != null) {
				withAttributesPath.updateSyntax(reparser, false);
				reparser.updateLocation(withAttributesPath.getLocation());
			}
			return;
		} else if (reparser.getDamageStart() > location.getEndOffset()) {
			// after the module
			if (withAttributesPath == null || withAttributesPath.getAttributes() == null) {
				// new attribute might have appeared
				reparser.extendDamagedRegionTillFileEnd();
				final int result = reparseAfterModule( reparser );

				if (result != 0) {
					throw new ReParseException();
				}
			}
			return;
		}

		// edited the module identifier
		final Location temporalIdentifier = identifier.getLocation();
		if (reparser.envelopsDamage(temporalIdentifier) || reparser.isExtending(temporalIdentifier)) {
			reparser.extendDamagedRegion(temporalIdentifier);
			final IIdentifierReparser r = new IdentifierReparser(reparser);
			final int result = r.parse();
			identifier = r.getIdentifier();
			// damage handled
			if (result != 0) {
				throw new ReParseException(result);
			}

			if (definitions != null) {
				definitions.updateSyntax(reparser, importedModules, friendModules, controlpart);
			}
			if (controlpart != null) {
				controlpart.updateSyntax(reparser);
				reparser.updateLocation(controlpart.getLocation());
			}
			if (withAttributesPath != null) {
				withAttributesPath.updateSyntax(reparser, false);
				reparser.updateLocation(withAttributesPath.getLocation());
			}
			return;
		} else if (reparser.isDamaged(temporalIdentifier)) {
			throw new ReParseException();
		}

		// the module has structurally changed
		boolean enveloped = false;
		if ((definitions != null && reparser.envelopsDamage(definitions.getLocation()))
				|| (controlpart != null && reparser.envelopsDamage(controlpart.getLocation()))) {
			if (definitions != null && reparser.isAffected(definitions.getLocation())) {
				try {
					definitions.updateSyntax(reparser, importedModules, friendModules, controlpart);
				} catch (ReParseException e) {
					throw e;
				}

				reparser.updateLocation(definitions.getLocation());
			}
			if (controlpart != null && reparser.isAffected(controlpart.getLocation())) {
				try {
					controlpart.updateSyntax(reparser);
				} catch (ReParseException e) {
					throw e;
				}

				reparser.updateLocation(controlpart.getLocation());
			}

			enveloped = true;
		}

		if (withAttributesPath != null && reparser.isAffected(withAttributesPath.getLocation())) {
			// The modification happened inside the attribute list
			if (reparser.envelopsDamage(withAttributesPath.getLocation())) {
				reparser.extendDamagedRegion(withAttributesPath.getLocation());
				final int result = reparseInsideAttributelist( reparser );

				if (result != 0) {
					throw new ReParseException();
				}

				return;
			} else if (enveloped) {
				// The modification happened inside the module
				withAttributesPath.updateSyntax(reparser, reparser.envelopsDamage(withAttributesPath.getLocation()));

				reparser.updateLocation(withAttributesPath.getLocation());
			} else {
				// Something happened that we can not handle,
				// for example the with attribute were commented
				// out
				throw new ReParseException();
			}
		}

		if (!enveloped) {
			throw new ReParseException();
		}
	}

	@Override
	/** {@inheritDoc} */
	public Assignment getEnclosingAssignment(final int offset) {
		if (definitions == null) {
			return null;
		}
		return definitions.getEnclosingAssignment(offset);
	}

	@Override
	/** {@inheritDoc} */
	public void findReferences(final ReferenceFinder referenceFinder, final List<Hit> foundIdentifiers) {
		if (definitions != null) {
			definitions.findReferences(referenceFinder, foundIdentifiers);
		}
		if (controlpart != null) {
			controlpart.findReferences(referenceFinder, foundIdentifiers);
		}
		if (anytypeDefinition != null) {
			anytypeDefinition.findReferences(referenceFinder, foundIdentifiers);
		}
	}

	@Override
	/** {@inheritDoc} */
	protected boolean memberAccept(final ASTVisitor v) {
		if (!super.memberAccept(v)) {
			return false;
		}
		if (importedModules != null) {
			for (final ImportModule im : importedModules) {
				if (!im.accept(v)) {
					return false;
				}
			}
		}
		if (friendModules != null) {
			for (final FriendModule fm : friendModules) {
				if (!fm.accept(v)) {
					return false;
				}
			}
		}
		if (definitions != null) {
			if (!definitions.accept(v)) {
				return false;
			}
		}
		if (controlpart != null) {
			if (!controlpart.accept(v)) {
				return false;
			}
		}
		if (anytypeDefinition != null) {
			if (!anytypeDefinition.accept(v)) {
				return false;
			}
		}
		if (withAttributesPath != null) {
			if (!withAttributesPath.accept(v)) {
				return false;
			}
		}

		return true;
	}

	public void setIncludedFiles(final Set<IFile> includedFiles) {
		this.includedFiles = includedFiles;
	}

	public Set<IFile> getIncludedFiles() {
		return includedFiles;
	}

	public void setInactiveCodeLocations(final List<Location> inactiveCodeLocations) {
		this.inactiveCodeLocations = inactiveCodeLocations;
	}

	public List<Location> getInactiveCodeLocations() {
		return inactiveCodeLocations;
	}

	@Override
	/** {@inheritDoc} */
	public boolean shouldBeGenerated() {
		return needsTobeBuilt;
	}

	@Override
	/** {@inheritDoc} */
	public void generateCode( final JavaGenData aData ) {
		needsTobeBuilt = false;

		aData.addBuiltinTypeImport("TTCN_Module");

		for (final ImportModule importModule : importedModules) {
			importModule.generateCode(aData);
		}

		aData.getClassHeader().append(MessageFormat.format("public final class {0} extends TTCN_Module '{'\n", name));
		if ( definitions != null ) {
			definitions.generateCode( aData );
		}

		final StringBuilder constructor = aData.getConstructor();
		constructor.append(MessageFormat.format("\tpublic {0}() '{'\n", identifier.getName()));
		constructor.append(MessageFormat.format("\t\tsuper(\"{0}\", module_type_enum.TTCN3_MODULE", identifier.getDisplayName()));
		if (digest == null) {
			constructor.append(", null");
		} else {
			constructor.append(", new byte[] {");
			for (int i = 0; i < digest.length; i++) {
				if (i > 0) {
					constructor.append(", ");
				}
				constructor.append("(byte)").append(String.format("0x%02X", digest[i] & 0xff));
			}
			constructor.append('}');
		}
		constructor.append(");\n");
		constructor.append("\t}\n\n");

		if (anytypeDefinition != null) {
			anytypeDefinition.generateCode(aData, false);
		}

		if ( controlpart != null ) {
			controlpart.generateCode( aData );
		}
	}
}
