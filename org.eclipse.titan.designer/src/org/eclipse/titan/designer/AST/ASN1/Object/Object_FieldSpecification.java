/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.AST.ASN1.Object;

import org.eclipse.titan.designer.AST.ASTVisitor;
import org.eclipse.titan.designer.AST.ISetting;
import org.eclipse.titan.designer.AST.Identifier;
import org.eclipse.titan.designer.AST.Scope;
import org.eclipse.titan.designer.AST.ASN1.ASN1Object;
import org.eclipse.titan.designer.AST.ASN1.ObjectClass;
import org.eclipse.titan.designer.compiler.JavaGenData;
import org.eclipse.titan.designer.editors.ProposalCollector;
import org.eclipse.titan.designer.editors.actions.DeclarationCollector;
import org.eclipse.titan.designer.parsers.CompilationTimeStamp;

/**
 * Class to represent an ObjectFieldSpec.
 *
 * @author Kristof Szabados
 */
public final class Object_FieldSpecification extends FieldSpecification {

	private final ObjectClass objectClass;
	private final ASN1Object defaultObject;

	public Object_FieldSpecification(final Identifier identifier, final ObjectClass objectClass, final boolean isOptional,
			final ASN1Object defaultObject) {
		super(identifier, isOptional);
		this.objectClass = objectClass;
		this.defaultObject = defaultObject;

		if (null != defaultObject) {
			defaultObject.setFullNameParent(this);
		}
	}

	@Override
	/** {@inheritDoc} */
	public Fieldspecification_types getFieldSpecificationType() {
		return Fieldspecification_types.FS_O;
	}

	@Override
	/** {@inheritDoc} */
	public void setMyObjectClass(final ObjectClass_Definition objectClass) {
		super.setMyObjectClass(objectClass);
		final Scope scope = myObjectClass.getMyScope();
		this.objectClass.setMyScope(scope);
		if (null != defaultObject) {
			defaultObject.setMyScope(scope);
		}
	}

	@Override
	/** {@inheritDoc} */
	public boolean hasDefault() {
		return null != defaultObject;
	}

	@Override
	/** {@inheritDoc} */
	public ISetting getDefault() {
		return defaultObject;
	}

	public ObjectClass getObjectClass() {
		return objectClass;
	}

	@Override
	/** {@inheritDoc} */
	public void check(final CompilationTimeStamp timestamp) {
		if (null != lastTimeChecked && !lastTimeChecked.isLess(timestamp)) {
			return;
		}

		objectClass.check(timestamp);

		if (null != defaultObject) {
			defaultObject.setMyGovernor(objectClass);
			defaultObject.check(timestamp);
		}

		lastTimeChecked = timestamp;
	}

	@Override
	/** {@inheritDoc} */
	public void addDeclaration(final DeclarationCollector declarationCollector, final int i) {
		if (null != objectClass) {
			objectClass.addDeclaration(declarationCollector, i);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void addProposal(final ProposalCollector propCollector, final int i) {
		if (null != objectClass) {
			objectClass.addProposal(propCollector, i);
		}
	}

	@Override
	/** {@inheritDoc} */
	protected boolean memberAccept(final ASTVisitor v) {
		if (identifier != null && !identifier.accept(v)) {
			return false;
		}
		if (objectClass != null && !objectClass.accept(v)) {
			return false;
		}
		if (defaultObject != null && !defaultObject.accept(v)) {
			return false;
		}
		return true;
	}

	@Override
	/** {@inheritDoc} */
	public void generateCode( final JavaGenData aData) {
		objectClass.generateCode(aData);
		if (defaultObject != null) {
			defaultObject.generateCode(aData);
		}
	}
}
