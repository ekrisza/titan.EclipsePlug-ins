/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.AST.ASN1.types;

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.titan.common.logging.ErrorReporter;
import org.eclipse.titan.designer.AST.ASTVisitor;
import org.eclipse.titan.designer.AST.Assignment;
import org.eclipse.titan.designer.AST.IReferenceChain;
import org.eclipse.titan.designer.AST.IReferencingType;
import org.eclipse.titan.designer.AST.IType;
import org.eclipse.titan.designer.AST.IValue;
import org.eclipse.titan.designer.AST.IValue.Value_type;
import org.eclipse.titan.designer.AST.Identifier;
import org.eclipse.titan.designer.AST.Location;
import org.eclipse.titan.designer.AST.Reference;
import org.eclipse.titan.designer.AST.ReferenceChain;
import org.eclipse.titan.designer.AST.ReferenceFinder;
import org.eclipse.titan.designer.AST.ReferenceFinder.Hit;
import org.eclipse.titan.designer.AST.Scope;
import org.eclipse.titan.designer.AST.Type;
import org.eclipse.titan.designer.AST.TypeCompatibilityInfo;
import org.eclipse.titan.designer.AST.ASN1.ASN1Type;
import org.eclipse.titan.designer.AST.ASN1.IASN1Type;
import org.eclipse.titan.designer.AST.TTCN3.Expected_Value_type;
import org.eclipse.titan.designer.AST.TTCN3.templates.ITTCN3Template;
import org.eclipse.titan.designer.AST.TTCN3.types.Signature_Type;
import org.eclipse.titan.designer.compiler.JavaGenData;
import org.eclipse.titan.designer.parsers.CompilationTimeStamp;

/**
 * @author Kristof Szabados
 * */
public final class Selection_Type extends ASN1Type implements IReferencingType {
	private static final String CHOICEFERENCEEXPECTED = "(Reference to) a CHOICE type was expected in selection type";
	private static final String MISSINGALTERNATIVE = "No alternative with name `{0}'' in the given type `{1}''";

	private final Identifier identifier;
	private final IASN1Type selectionType;
	private IType referencedLast;

	public Selection_Type(final Identifier identifier, final IASN1Type selectionType) {
		this.identifier = identifier;
		this.selectionType = selectionType;

		if (null != selectionType) {
			selectionType.setOwnertype(TypeOwner_type.OT_SELTYPE, this);
			selectionType.setFullNameParent(this);
		}
	}

	@Override
	/** {@inheritDoc} */
	public Type_type getTypetype() {
		return Type_type.TYPE_SELECTION;
	}

	@Override
	/** {@inheritDoc} */
	public IASN1Type newInstance() {
		return new Selection_Type(identifier, selectionType.newInstance());
	}

	@Override
	/** {@inheritDoc} */
	public void setMyScope(final Scope scope) {
		super.setMyScope(scope);
		if (null != selectionType) {
			selectionType.setMyScope(scope);
		}
	}

	@Override
	/** {@inheritDoc} */
	public String chainedDescription() {
		return "selection type with name: " + identifier.getDisplayName();
	}

	@Override
	/** {@inheritDoc} */
	public boolean isCompatible(final CompilationTimeStamp timestamp, final IType otherType, final TypeCompatibilityInfo info,
			final TypeCompatibilityInfo.Chain leftChain, final TypeCompatibilityInfo.Chain rightChain) {
		check(timestamp);
		otherType.check(timestamp);

		if (null == selectionType) {
			return false;
		}

		final IType t1 = selectionType.getTypeRefdLast(timestamp);
		final IType t2 = otherType.getTypeRefdLast(timestamp);

		if (t1.getIsErroneous(timestamp) || t2.getIsErroneous(timestamp)) {
			return true;
		}

		return t1.isCompatible(timestamp, t2, null, null, null);
	}

	@Override
	/** {@inheritDoc} */
	public Type_type getTypetypeTtcn3() {
		return Type_type.TYPE_UNDEFINED;
	}

	@Override
	/** {@inheritDoc} */
	public String getTypename() {
		if (isErroneous || null == selectionType || this == selectionType) {
			return "Selection type";
		}

		return selectionType.getTypename();
	}

	@Override
	/** {@inheritDoc} */
	public String getOutlineIcon() {
		return "asn1_selection.gif";
	}

	@Override
	/** {@inheritDoc} */
	public StringBuilder getProposalDescription(final StringBuilder builder) {
		builder.append("selection of ");
		if (null != selectionType) {
			selectionType.getProposalDescription(builder);
		}
		return builder;
	}

	@Override
	/** {@inheritDoc} */
	public void check(final CompilationTimeStamp timestamp) {
		if (null != lastTimeChecked && !lastTimeChecked.isLess(timestamp)) {
			return;
		}

		lastTimeChecked = timestamp;
		isErroneous = false;

		referencedLast = getTypeRefdLast(timestamp);

		if (!referencedLast.getIsErroneous(timestamp)) {
			referencedLast.check(timestamp);
		}

		if (null != selectionType) {
			selectionType.check(timestamp);
		}

		if (null != constraints) {
			constraints.check(timestamp);
		}
	}

	@Override
	/** {@inheritDoc} */
	public IValue checkThisValueRef(final CompilationTimeStamp timestamp, final IValue value) {
		if (Value_type.UNDEFINED_LOWERIDENTIFIER_VALUE.equals(value.getValuetype())) {
			final IReferenceChain tempReferenceChain = ReferenceChain.getInstance(IReferenceChain.CIRCULARREFERENCE, true);
			final IType refd = getTypeRefd(timestamp, tempReferenceChain);
			tempReferenceChain.release();

			if (null == refd) {
				return value;
			}

			return refd.checkThisValueRef(timestamp, value);
		}

		return value;
	}

	@Override
	/** {@inheritDoc} */
	public boolean checkThisValue(final CompilationTimeStamp timestamp, final IValue value, final Assignment lhs, final ValueCheckingOptions valueCheckingOptions) {
		final IReferenceChain refChain = ReferenceChain.getInstance(IReferenceChain.CIRCULARREFERENCE, true);
		final IType last = getTypeRefd(timestamp, refChain);
		refChain.release();

		boolean selfReference = false;
		if (null != last && last != this) {
			selfReference = last.checkThisValue(timestamp, value, lhs, valueCheckingOptions);
		}

		value.setLastTimeChecked(timestamp);

		return selfReference;
	}

	@Override
	/** {@inheritDoc} */
	public boolean checkThisTemplate(final CompilationTimeStamp timestamp, final ITTCN3Template template, final boolean isModified,
			final boolean implicitOmit, final Assignment lhs) {
		registerUsage(template);

		if (getIsErroneous(timestamp)) {
			return false;
		}

		boolean selfReference = false;
		final IType tempType = getTypeRefdLast(timestamp);
		if (tempType != this) {
			selfReference = tempType.checkThisTemplate(timestamp, template, isModified, implicitOmit, lhs);
		}

		return selfReference;
	}

	@Override
	/** {@inheritDoc} */
	public Type getTypeRefd(final CompilationTimeStamp timestamp, final IReferenceChain refChain) {
		if (refChain.add(this) && !getIsErroneous(timestamp)) {
			final IType type = selectionType.getTypeRefdLast(timestamp);
			if (type.getIsErroneous(timestamp)) {
				isErroneous = true;
				lastTimeChecked = timestamp;
				return this;
			}

			if (Type_type.TYPE_ASN1_CHOICE.equals(type.getTypetype())) {
				if (((ASN1_Choice_Type) type).hasComponentWithName(identifier)) {
					return ((ASN1_Choice_Type) type).getComponentByName(identifier).getType();
				}

				final String message = MessageFormat.format(MISSINGALTERNATIVE, identifier.getDisplayName(), type.getFullName());
				location.reportSemanticError(message);
			} else {
				selectionType.getLocation().reportSemanticError(CHOICEFERENCEEXPECTED);
			}
		}

		isErroneous = true;
		lastTimeChecked = timestamp;
		return this;
	}

	@Override
	/** {@inheritDoc} */
	public IType getTypeRefdLast(final CompilationTimeStamp timestamp, final IReferenceChain referenceChain) {
		final boolean newChain = null == referenceChain;
		IReferenceChain tempReferenceChain;
		if (newChain) {
			tempReferenceChain = ReferenceChain.getInstance(IReferenceChain.CIRCULARREFERENCE, true);
		} else {
			tempReferenceChain = referenceChain;
		}

		IType type = this;
		while (null != type && type instanceof IReferencingType && !type.getIsErroneous(timestamp)) {
			type = ((IReferencingType) type).getTypeRefd(timestamp, tempReferenceChain);
		}

		if (newChain) {
			tempReferenceChain.release();
		}
		return type;
	}

	@Override
	/** {@inheritDoc} */
	public void checkRecursions(final CompilationTimeStamp timestamp, final IReferenceChain referenceChain) {
		if (referenceChain.add(this)) {
			final IReferenceChain tempReferenceChain = ReferenceChain.getInstance(IReferenceChain.CIRCULARREFERENCE, true);
			final Type type = getTypeRefd(timestamp, tempReferenceChain);
			tempReferenceChain.release();

			if (null != type && !type.getIsErroneous(timestamp) && !this.equals(type)) {
				type.checkRecursions(timestamp, referenceChain);
			}
		}
	}

	@Override
	/** {@inheritDoc} */
	public IType getFieldType(final CompilationTimeStamp timestamp, final Reference reference, final int actualSubReference,
			final Expected_Value_type expectedIndex, final IReferenceChain refChain, final boolean interruptIfOptional) {
		if (null == lastTimeChecked) {
			check(CompilationTimeStamp.getBaseTimestamp());
		}

		if (null != referencedLast && this != referencedLast) {
			final Expected_Value_type internalExpectation =
					(expectedIndex == Expected_Value_type.EXPECTED_TEMPLATE) ? Expected_Value_type.EXPECTED_DYNAMIC_VALUE
							: expectedIndex;

			return referencedLast.getFieldType(timestamp, reference, actualSubReference, internalExpectation, refChain,
					interruptIfOptional);
		}

		return null;
	}

	@Override
	/** {@inheritDoc} */
	public void checkMapParameter(final CompilationTimeStamp timestamp, final IReferenceChain refChain, final Location errorLocation) {
		if (refChain.contains(this)) {
			return;
		}

		refChain.add(this);
		if (referencedLast != null) {
			referencedLast.checkMapParameter(timestamp, refChain, errorLocation);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void findReferences(final ReferenceFinder referenceFinder, final List<Hit> foundIdentifiers) {
		super.findReferences(referenceFinder, foundIdentifiers);
		if (selectionType != null) {
			selectionType.findReferences(referenceFinder, foundIdentifiers);
		}
	}

	@Override
	/** {@inheritDoc} */
	protected boolean memberAccept(final ASTVisitor v) {
		if (!super.memberAccept(v)) {
			return false;
		}
		if (identifier != null && !identifier.accept(v)) {
			return false;
		}
		if (selectionType != null && !selectionType.accept(v)) {
			return false;
		}
		return true;
	}

	@Override
	/** {@inheritDoc} */
	public String createStringRep_for_OpenType_AltName(final CompilationTimeStamp timestamp) {
		if (isTagged() || constraints != null /*||
			(withAttributesPath != null && withAttributesPath.has_attribs())*/) {
			return getGenNameOwn();
		} else {
			return getTypeRefdLast(timestamp).createStringRep_for_OpenType_AltName(timestamp);
		}
	}

	@Override
	/** {@inheritDoc} */
	public String getGenNameValue(final JavaGenData aData, final StringBuilder source) {
		if (this == referencedLast || referencedLast == null) {
			ErrorReporter.INTERNAL_ERROR("Code generator reached erroneous type reference `" + getFullName() + "''");
			return "FATAL_ERROR encountered while processing `" + getFullName() + "''\n";
		}

		return referencedLast.getGenNameValue(aData, source);
	}

	@Override
	/** {@inheritDoc} */
	public String getGenNameTemplate(final JavaGenData aData, final StringBuilder source) {
		if (this == referencedLast || referencedLast == null) {
			ErrorReporter.INTERNAL_ERROR("Code generator reached erroneous type reference `" + getFullName() + "''");
			return "FATAL_ERROR encountered while processing `" + getFullName() + "''\n";
		}

		return referencedLast.getGenNameTemplate(aData, source);
	}

	@Override
	/** {@inheritDoc} */
	public String getGenNameTypeDescriptor(final JavaGenData aData, final StringBuilder source) {
		if (rawAttribute != null || jsonAttribute != null ||
				hasVariantAttributes(CompilationTimeStamp.getBaseTimestamp())) {
			if (needsAlias()) {
				final String baseName = getGenNameOwn(aData);
				return baseName + "." + getGenNameOwn();
			} else if (getParentType() != null) {
				final IType parentType = getParentType();
				if (parentType.generatesOwnClass(aData, source)) {
					return parentType.getGenNameOwn(aData) + "." + getGenNameOwn();
				}

				return getGenNameOwn(aData);
			}

			return getGenNameOwn(aData);
		}

		if (needsAlias()) {
			final String baseName = getGenNameOwn(aData);
			return baseName + "." + getGenNameOwn();
		}

		return referencedLast.getGenNameTypeDescriptor(aData, source);
	}

	@Override
	/** {@inheritDoc} */
	public boolean needsOwnRawDescriptor(final JavaGenData aData) {
		return rawAttribute != null;
	}

	@Override
	/** {@inheritDoc} */
	public String getGenNameRawDescriptor(final JavaGenData aData, final StringBuilder source) {
		if (this == referencedLast|| referencedLast == null) {
			ErrorReporter.INTERNAL_ERROR("Code generator reached erroneous type reference `" + getFullName() + "''");

			return "FATAL_ERROR encountered while processing `" + getFullName() + "''\n";
		}

		if (rawAttribute != null) {
			return getGenNameOwn(aData) + "_raw_";
		}

		return referencedLast.getGenNameRawDescriptor(aData, source);
	}

	@Override
	/** {@inheritDoc} */
	public boolean needsOwnJsonDescriptor(final JavaGenData aData) {
		return !((jsonAttribute == null || jsonAttribute.empty()) && (getOwnertype() != TypeOwner_type.OT_RECORD_OF || getParentType().getJsonAttribute() == null
				|| !getParentType().getJsonAttribute().as_map));
	}

	@Override
	/** {@inheritDoc} */
	public String getGenNameJsonDescriptor(final JavaGenData aData, final StringBuilder source) {
		if (this == referencedLast || referencedLast == null) {
			ErrorReporter.INTERNAL_ERROR("Code generator reached erroneous type reference `" + getFullName() + "''");

			return "FATAL_ERROR encountered while processing `" + getFullName() + "''\n";
		}

		if (needsOwnJsonDescriptor(aData)) {
			return getGenNameOwn(aData) + "_json_";
		}

		return referencedLast.getGenNameJsonDescriptor(aData, source);
	}

	@Override
	/** {@inheritDoc} */
	public boolean generatesOwnClass(final JavaGenData aData, final StringBuilder source) {
		return needsAlias();
	}

	@Override
	/** {@inheritDoc} */
	public void generateCode( final JavaGenData aData, final StringBuilder source ) {
		if (lastTimeGenerated != null && !lastTimeGenerated.isLess(aData.getBuildTimstamp())) {
			return;
		}

		lastTimeGenerated = aData.getBuildTimstamp();

		final IType last = getTypeRefdLast(CompilationTimeStamp.getBaseTimestamp());
		if(myScope.getModuleScopeGen() == last.getMyScope().getModuleScopeGen()) {
			final StringBuilder tempSource = aData.getCodeForType(last.getGenNameOwn());
			if (tempSource.length() == 0) {
				last.generateCode(aData, tempSource);
			}
		}

		generateCodeTypedescriptor(aData, source, null, aData.attibute_registry);
		generateCodeDefaultCoding(aData, source, null);
		generateCodeForCodingHandlers(aData, source, null);
		if(needsAlias()) {
			final String ownName = getGenNameOwn();
			switch (last.getTypetype()) {
			case TYPE_PORT:
				source.append(MessageFormat.format("\tpublic static class {0} extends {1} '{' '}'\n", ownName, referencedLast.getGenNameValue(aData, source)));
				break;
			case TYPE_SIGNATURE:
				source.append(MessageFormat.format("\tpublic static class {0}_call extends {1}_call '{' '}'\n", ownName, referencedLast.getGenNameValue(aData, source)));
				source.append(MessageFormat.format("\tpublic static class {0}_call_redirect extends {1}_call_redirect '{' '}'\n", ownName, referencedLast.getGenNameValue(aData, source)));
				if (!((Signature_Type) last).isNonblocking()) {
					source.append(MessageFormat.format("\tpublic static class {0}_reply extends {1}_reply '{' '}'\n", ownName, referencedLast.getGenNameValue(aData, source)));
					source.append(MessageFormat.format("\tpublic static class {0}_reply_redirect extends {1}_reply_redirect '{' '}'\n", ownName, referencedLast.getGenNameValue(aData, source)));
				}
				if (((Signature_Type) last).getSignatureExceptions() != null) {
					source.append(MessageFormat.format("\tpublic static class {0}_template extends {1} '{' '}'\n", ownName, referencedLast.getGenNameTemplate(aData, source)));
				}
				break;
			default:
				source.append(MessageFormat.format("\tpublic static class {0} extends {1} '{'\n", ownName, referencedLast.getGenNameValue(aData, source)));

				final StringBuilder descriptor = new StringBuilder();
				generateCodeTypedescriptor(aData, source, descriptor, null);
				generateCodeDefaultCoding(aData, source, descriptor);
				generateCodeForCodingHandlers(aData, source, descriptor);
				source.append(descriptor);

				source.append("\t}\n");

				source.append(MessageFormat.format("\tpublic static class {0}_template extends {1} '{' '}'\n", ownName, referencedLast.getGenNameTemplate(aData, source)));
				break;
			}
		}
	}
}
