/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.AST.TTCN3.types;

import java.util.List;

import org.eclipse.titan.designer.AST.ASTNode;
import org.eclipse.titan.designer.AST.ASTVisitor;
import org.eclipse.titan.designer.AST.ILocateableNode;
import org.eclipse.titan.designer.AST.INamedNode;
import org.eclipse.titan.designer.AST.IType.TypeOwner_type;
import org.eclipse.titan.designer.AST.Identifier;
import org.eclipse.titan.designer.AST.Location;
import org.eclipse.titan.designer.AST.ReferenceFinder;
import org.eclipse.titan.designer.AST.ReferenceFinder.Hit;
import org.eclipse.titan.designer.AST.Scope;
import org.eclipse.titan.designer.AST.Type;
import org.eclipse.titan.designer.AST.TTCN3.IIncrementallyUpdateable;
import org.eclipse.titan.designer.parsers.ttcn3parser.ReParseException;
import org.eclipse.titan.designer.parsers.ttcn3parser.TTCN3ReparseUpdater;

/**
 * Signature parameter.
 *
 * @author Kristof Szabados
 */
public final class SignatureFormalParameter extends ASTNode implements ILocateableNode, IIncrementallyUpdateable {
	private static final String FULLNAMEPART = ".<type>";

	public enum ParamaterDirection {PARAM_IN, PARAM_OUT, PARAM_INOUT};

	private final ParamaterDirection parameterDirection;
	private final Identifier identifier;
	private final Type type;

	/**
	 * The location of the whole parameter. This location encloses the parameter
	 * fully, as it is used to report errors to.
	 **/
	private Location location;

	public SignatureFormalParameter(final ParamaterDirection parameterDirection, final Type type, final Identifier identifier) {
		this.parameterDirection = parameterDirection;
		this.type = type;
		this.identifier = identifier;

		if (type != null) {
			type.setOwnertype(TypeOwner_type.OT_SIG_PAR, this);
			type.setFullNameParent(this);
		}
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

	@Override
	/** {@inheritDoc} */
	public StringBuilder getFullName(final INamedNode child) {
		final StringBuilder builder = super.getFullName(child);

		if (type == child) {
			return builder.append(FULLNAMEPART);
		}

		return builder;
	}

	@Override
	/** {@inheritDoc} */
	public void setMyScope(final Scope scope) {
		if (type != null) {
			type.setMyScope(scope);
		}
	}

	public Identifier getIdentifier() {
		return identifier;
	}

	public Type getType() {
		return type;
	}

	public ParamaterDirection getDirection() {
		return parameterDirection;
	}

	@Override
	/** {@inheritDoc} */
	public void updateSyntax(final TTCN3ReparseUpdater reparser, final boolean isDamaged) throws ReParseException {
		if (isDamaged) {
			throw new ReParseException();
		}

		reparser.updateLocation(identifier.getLocation());
		if (type != null) {
			type.updateSyntax(reparser, false);
			reparser.updateLocation(type.getLocation());
		}
	}

	@Override
	/** {@inheritDoc} */
	public void findReferences(final ReferenceFinder referenceFinder, final List<Hit> foundIdentifiers) {
		if (type != null) {
			type.findReferences(referenceFinder, foundIdentifiers);
		}
	}

	@Override
	/** {@inheritDoc} */
	protected boolean memberAccept(final ASTVisitor v) {
		if (identifier!=null && !identifier.accept(v)) {
			return false;
		}
		if (type!=null && !type.accept(v)) {
			return false;
		}
		return true;
	}
}
