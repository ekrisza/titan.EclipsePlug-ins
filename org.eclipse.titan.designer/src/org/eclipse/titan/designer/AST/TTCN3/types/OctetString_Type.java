/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.AST.TTCN3.types;

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.titan.designer.AST.ArraySubReference;
import org.eclipse.titan.designer.AST.Assignment;
import org.eclipse.titan.designer.AST.FieldSubReference;
import org.eclipse.titan.designer.AST.IReferenceChain;
import org.eclipse.titan.designer.AST.ISubReference;
import org.eclipse.titan.designer.AST.ISubReference.Subreference_type;
import org.eclipse.titan.designer.AST.IType;
import org.eclipse.titan.designer.AST.IValue;
import org.eclipse.titan.designer.AST.IValue.Value_type;
import org.eclipse.titan.designer.AST.ParameterisedSubReference;
import org.eclipse.titan.designer.AST.Reference;
import org.eclipse.titan.designer.AST.Scope;
import org.eclipse.titan.designer.AST.TypeCompatibilityInfo;
import org.eclipse.titan.designer.AST.Value;
import org.eclipse.titan.designer.AST.ASN1.ASN1Type;
import org.eclipse.titan.designer.AST.ASN1.IASN1Type;
import org.eclipse.titan.designer.AST.TTCN3.Expected_Value_type;
import org.eclipse.titan.designer.AST.TTCN3.attributes.RawAST;
import org.eclipse.titan.designer.AST.TTCN3.templates.DecodeMatch_template;
import org.eclipse.titan.designer.AST.TTCN3.templates.ITTCN3Template;
import org.eclipse.titan.designer.AST.TTCN3.types.subtypes.SubType;
import org.eclipse.titan.designer.AST.TTCN3.values.Expression_Value.Operation_type;
import org.eclipse.titan.designer.AST.TTCN3.values.expressions.ExpressionStruct;
import org.eclipse.titan.designer.compiler.BuildTimestamp;
import org.eclipse.titan.designer.compiler.JavaGenData;
import org.eclipse.titan.designer.editors.actions.DeclarationCollector;
import org.eclipse.titan.designer.parsers.CompilationTimeStamp;

/**
 * @author Kristof Szabados
 * */
public final class OctetString_Type extends ASN1Type {
	private static final String OCTETSTRINGVALUEEXPECTED1 = "(reference to) OCTET STRING value was expected";
	private static final String OCTETSTRINGVALUEEXPECTED2 = "OCTET STRING value was expected";
	private static final String OCTETSTRINGVALUEEXPECTED3 = "octetstring value was expected";
	private static final String TEMPLATENOTALLOWED = "{0} cannot be used for type `octetstring''";

	@Override
	/** {@inheritDoc} */
	public Type_type getTypetype() {
		return Type_type.TYPE_OCTETSTRING;
	}

	@Override
	/** {@inheritDoc} */
	public IASN1Type newInstance() {
		return new OctetString_Type();
	}

	@Override
	/** {@inheritDoc} */
	public boolean isCompatible(final CompilationTimeStamp timestamp, final IType otherType, final TypeCompatibilityInfo info,
			final TypeCompatibilityInfo.Chain leftChain, final TypeCompatibilityInfo.Chain rightChain) {
		check(timestamp);
		otherType.check(timestamp);
		final IType temp = otherType.getTypeRefdLast(timestamp);
		if (getIsErroneous(timestamp) || temp.getIsErroneous(timestamp)) {
			return true;
		}

		return Type_type.TYPE_OCTETSTRING.equals(temp.getTypetype()) || (!isAsn() && Type_type.TYPE_ANY.equals(temp.getTypetype()));
	}

	@Override
	/** {@inheritDoc} */
	public Type_type getTypetypeTtcn3() {
		if (isErroneous) {
			return Type_type.TYPE_UNDEFINED;
		}

		return getTypetype();
	}

	@Override
	/** {@inheritDoc} */
	public String getTypename() {
		return "octetstring";
	}

	@Override
	/** {@inheritDoc} */
	public String getOutlineIcon() {
		return "octetstring.gif";
	}

	@Override
	/** {@inheritDoc} */
	public SubType.SubType_type getSubtypeType() {
		return SubType.SubType_type.ST_OCTETSTRING;
	}

	@Override
	/** {@inheritDoc} */
	public void check(final CompilationTimeStamp timestamp) {
		if (lastTimeChecked != null && !lastTimeChecked.isLess(timestamp)) {
			return;
		}

		lastTimeChecked = timestamp;

		initAttributes(timestamp);

		if (constraints != null) {
			constraints.check(timestamp);
		}

		checkSubtypeRestrictions(timestamp);

		if (myScope != null) {
			checkEncode(timestamp);
			checkVariants(timestamp);
		}
	}

	@Override
	/** {@inheritDoc} */
	public boolean checkThisValue(final CompilationTimeStamp timestamp, final IValue value, final Assignment lhs, final ValueCheckingOptions valueCheckingOptions) {
		final boolean selfReference = super.checkThisValue(timestamp, value, lhs, valueCheckingOptions);

		IValue last = value.getValueRefdLast(timestamp, valueCheckingOptions.expected_value, null);
		if (last == null || last.getIsErroneous(timestamp)) {
			return selfReference;
		}

		// already handled ones
		switch (value.getValuetype()) {
		case OMIT_VALUE:
		case REFERENCED_VALUE:
			return selfReference;
		case UNDEFINED_LOWERIDENTIFIER_VALUE:
			if (Value_type.REFERENCED_VALUE.equals(last.getValuetype())) {
				return selfReference;
			}
			break;
		default:
			break;
		}

		if (last.isAsn()) {
			if (Value_type.REFERENCED_VALUE.equals(value.getValuetype())) {
				final IType lastType = last.getMyGovernor().getTypeRefdLast(timestamp);
				if (!lastType.getIsErroneous(timestamp) && !Type_type.TYPE_OCTETSTRING.equals(lastType.getTypetype())) {
					value.getLocation().reportSemanticError(OCTETSTRINGVALUEEXPECTED1);
					value.setIsErroneous(true);
					return selfReference;
				}
			}
			switch (last.getValuetype()) {
			case OCTETSTRING_VALUE:
				break;
			case BITSTRING_VALUE:
			case HEXSTRING_VALUE:
				if (last == value) {
					last = last.setValuetype(timestamp, Value_type.OCTETSTRING_VALUE);
				}
				break;
			case EXPRESSION_VALUE:
			case MACRO_VALUE:
				// already checked
				break;
			default:
				value.getLocation().reportSemanticError(OCTETSTRINGVALUEEXPECTED2);
				value.setIsErroneous(true);
				break;
			}
		} else {
			switch (last.getValuetype()) {
			case OCTETSTRING_VALUE:
				break;
			case EXPRESSION_VALUE:
			case MACRO_VALUE:
				// already checked
				break;
			default:
				value.getLocation().reportSemanticError(OCTETSTRINGVALUEEXPECTED3);
				value.setIsErroneous(true);
				break;
			}
		}

		if (valueCheckingOptions.sub_check) {
			//there is no parent type to check
			if (subType != null) {
				subType.checkThisValue(timestamp, last);
			}
		}

		value.setLastTimeChecked(timestamp);

		return selfReference;
	}

	@Override
	/** {@inheritDoc} */
	public boolean checkThisTemplate(final CompilationTimeStamp timestamp, final ITTCN3Template template,
			final boolean isModified, final boolean implicitOmit, final Assignment lhs) {
		registerUsage(template);
		template.setMyGovernor(this);

		boolean selfReference = false;
		switch (template.getTemplatetype()) {
		case OSTR_PATTERN:
			break;
		case DECODE_MATCH:
			selfReference = ((DecodeMatch_template)template).checkThisTemplateString(timestamp, this, implicitOmit, lhs);
			break;
		default:
			template.getLocation().reportSemanticError(MessageFormat.format(TEMPLATENOTALLOWED, template.getTemplateTypeName()));
			break;
		}

		return selfReference;
	}

	@Override
	/** {@inheritDoc} */
	public void checkCodingAttributes(final CompilationTimeStamp timestamp, final IReferenceChain refChain) {
		//check raw attributes
		if (subType != null) {
			final int restrictionLength = subType.get_length_restriction();
			if (restrictionLength != -1) {
				if (rawAttribute == null) {
					rawAttribute = new RawAST(getDefaultRawFieldLength());
				}
				if (rawAttribute.fieldlength == 0) {
					rawAttribute.fieldlength = restrictionLength * 8;
					rawAttribute.length_restriction = -1;
				} else {
					rawAttribute.length_restriction = restrictionLength;
				}
			}
		}

		checkJson(timestamp);
		//TODO add checks for other encodings.
	}

	@Override
	/** {@inheritDoc} */
	public boolean canHaveCoding(final CompilationTimeStamp timestamp, final MessageEncoding_type coding) {
		if (coding == MessageEncoding_type.BER) {
			return hasEncoding(timestamp, MessageEncoding_type.BER, null);
		}

		switch (coding) {
		case RAW:
		case TEXT:
		case JSON:
		case XER:
			return true;
		default:
			return false;
		}
	}

	@Override
	/** {@inheritDoc} */
	public IType getFieldType(final CompilationTimeStamp timestamp, final Reference reference, final int actualSubReference,
			final Expected_Value_type expectedIndex, final IReferenceChain refChain, final boolean interruptIfOptional) {
		final List<ISubReference> subreferences = reference.getSubreferences();
		if (subreferences.size() <= actualSubReference) {
			return this;
		}

		final ISubReference subreference = subreferences.get(actualSubReference);
		switch (subreference.getReferenceType()) {
		case arraySubReference:
			if (subreferences.size() > actualSubReference + 1) {
				subreference.getLocation().reportSemanticError(ArraySubReference.INVALIDSTRINGELEMENTINDEX);
				return null;
			} else if (subreferences.size() == actualSubReference + 1) {
				reference.setStringElementReferencing();
			}

			final Value indexValue = ((ArraySubReference) subreference).getValue();
			checkStringIndex(timestamp, indexValue, expectedIndex, refChain);

			return this;
		case fieldSubReference:
			subreference.getLocation().reportSemanticError(
					MessageFormat.format(FieldSubReference.INVALIDSUBREFERENCE, ((FieldSubReference) subreference).getId().getDisplayName(),
							getTypename()));
			return null;
		case parameterisedSubReference:
			subreference.getLocation().reportSemanticError(
					MessageFormat.format(FieldSubReference.INVALIDSUBREFERENCE, ((ParameterisedSubReference) subreference).getId().getDisplayName(),
							getTypename()));
			return null;
		default:
			subreference.getLocation().reportSemanticError(ISubReference.INVALIDSUBREFERENCE);
			return null;
		}
	}

	@Override
	/** {@inheritDoc} */
	public int getLengthMultiplier() {
		return 8;
	}

	@Override
	/** {@inheritDoc} */
	public int getRawLength(final BuildTimestamp timestamp) {
		if (rawAttribute != null && rawAttribute.fieldlength > 0) {
			return rawAttribute.fieldlength;
		}

		return -1;
	}

	@Override
	/** {@inheritDoc} */
	public StringBuilder getProposalDescription(final StringBuilder builder) {
		return builder.append("octetstring");
	}

	@Override
	/** {@inheritDoc} */
	public void addDeclaration(final DeclarationCollector declarationCollector, final int i) {
		final List<ISubReference> subreferences = declarationCollector.getReference().getSubreferences();
		if (subreferences.size() <= i) {
			return;
		}

		final ISubReference subreference = subreferences.get(i);
		if (Subreference_type.arraySubReference.equals(subreference.getReferenceType())) {
			if (subreferences.size() == i + 1) {
				declarationCollector.addDeclaration("octetstring", location, this);
			}
		}
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

		if(needsAlias()) {
			final String ownName = getGenNameOwn();
			
			source.append(MessageFormat.format("\tpublic static class {0} extends {1} '{'\n", ownName, getGenNameValue(aData, source)));

			final StringBuilder descriptor = new StringBuilder();
			generateCodeTypedescriptor(aData, source, descriptor, null);
			generateCodeDefaultCoding(aData, source, descriptor);
			generateCodeForCodingHandlers(aData, source, descriptor);
			source.append(descriptor);

			source.append("\t}\n");
			
			source.append(MessageFormat.format("\tpublic static class {0}_template extends {1} '{' '}'\n", ownName, getGenNameTemplate(aData, source)));
		} else if (getParentType() == null || !getParentType().generatesOwnClass(aData, source)) {
			generateCodeTypedescriptor(aData, source, null, aData.attibute_registry);
			generateCodeDefaultCoding(aData, source, null);
			generateCodeForCodingHandlers(aData, source, null);
		}

		if (hasDoneAttribute()) {
			generateCodeDone(aData, source);
		}
		if (subType != null) {
			subType.generateCode(aData, source);
		}
	}

	@Override
	/** {@inheritDoc} */
	public String createStringRep_for_OpenType_AltName(final CompilationTimeStamp timestamp) {
		if(isTagged() /*|| hasRawAttrs()*/) {
			return getGenNameOwn();
		}

		return "OCTET__STRING";
	}

	@Override
	/** {@inheritDoc} */
	public String getGenNameValue(final JavaGenData aData, final StringBuilder source) {
		aData.addBuiltinTypeImport( "TitanOctetString" );

		return "TitanOctetString";
	}

	@Override
	/** {@inheritDoc} */
	public String getGenNameTemplate(final JavaGenData aData, final StringBuilder source) {
		aData.addBuiltinTypeImport( "TitanOctetString_template" );

		return "TitanOctetString_template";
	}

	@Override
	/** {@inheritDoc} */
	public void generateCodeIsPresentBoundChosen(final JavaGenData aData, final ExpressionStruct expression, final List<ISubReference> subreferences,
			final int subReferenceIndex, final String globalId, final String externalId, final boolean isTemplate, final Operation_type optype, final String field, final Scope targetScope) {
		generateCodeIspresentBound_forStrings(aData, expression, subreferences, subReferenceIndex, globalId, externalId, isTemplate, optype, field, targetScope);
	}

	@Override
	/** {@inheritDoc} */
	public String getGenNameTypeDescriptor(final JavaGenData aData, final StringBuilder source) {
		if (rawAttribute != null || jsonAttribute != null ||
				hasVariantAttributes(CompilationTimeStamp.getBaseTimestamp())
				|| (!isAsn() && hasEncodeAttribute("JSON"))) {
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

		aData.addBuiltinTypeImport( "TitanOctetString" );
		return "TitanOctetString.TitanOctetString";
	}

	@Override
	/** {@inheritDoc} */
	public boolean needsOwnRawDescriptor(final JavaGenData aData) {
		return rawAttribute != null;
	}

	@Override
	/** {@inheritDoc} */
	public String getGenNameRawDescriptor(final JavaGenData aData, final StringBuilder source) {
		if (rawAttribute == null) {
			aData.addBuiltinTypeImport( "TitanOctetString" );

			return "TitanOctetString.TitanOctetString_raw_";
		} else if (needsAlias()) {
			return getGenNameOwn(aData) + "." + getGenNameOwn() + "_raw_";
		} else {
			return getGenNameOwn(aData) + "_raw_";
		}
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
		if (needsOwnJsonDescriptor(aData)) {
			return getGenNameOwn(aData) + "_json_";
		}

		aData.addBuiltinTypeImport( "TitanOctetString" );

		return "TitanOctetString.TitanOctetString_json_";
	}

	@Override
	/** {@inheritDoc} */
	public String generateConversion(final JavaGenData aData, final IType fromType, final String fromName, final boolean forValue, final ExpressionStruct expression) {
		aData.addBuiltinTypeImport( "TitanOctetString" );

		return MessageFormat.format("TitanOctetString.convert_to_OctetString({0})", fromName);
	}
}
