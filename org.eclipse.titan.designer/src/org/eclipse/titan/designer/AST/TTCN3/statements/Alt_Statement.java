/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.AST.TTCN3.statements;

import java.util.List;

import org.eclipse.titan.designer.AST.ASTVisitor;
import org.eclipse.titan.designer.AST.GovernedSimple.CodeSectionType;
import org.eclipse.titan.designer.AST.INamedNode;
import org.eclipse.titan.designer.AST.ReferenceFinder;
import org.eclipse.titan.designer.AST.ReferenceFinder.Hit;
import org.eclipse.titan.designer.AST.Scope;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Definition;
import org.eclipse.titan.designer.compiler.JavaGenData;
import org.eclipse.titan.designer.parsers.CompilationTimeStamp;
import org.eclipse.titan.designer.parsers.ttcn3parser.ReParseException;
import org.eclipse.titan.designer.parsers.ttcn3parser.TTCN3ReparseUpdater;

/**
 * The Alt_Statement class represents TTCN3 alt statements.
 *
 * @see AltGuards
 *
 * @author Kristof Szabados
 * */
public final class Alt_Statement extends Statement {
	private static final String FULLNAMEPART = ".ags";
	private static final String STATEMENT_NAME = "alt";

	/**
	 * the list of guard statements that belong to this alt statement.
	 * <p>
	 * Can not be null.
	 * */
	private final AltGuards altGuards;

	public Alt_Statement(final AltGuards altGuards) {
		this.altGuards = altGuards;

		altGuards.setFullNameParent(this);
	}

	@Override
	/** {@inheritDoc} */
	public Statement_type getType() {
		return Statement_type.S_ALT;
	}

	@Override
	/** {@inheritDoc} */
	public String getStatementName() {
		return STATEMENT_NAME;
	}

	@Override
	/** {@inheritDoc} */
	public StringBuilder getFullName(final INamedNode child) {
		final StringBuilder builder = super.getFullName(child);

		if (altGuards == child) {
			return builder.append(FULLNAMEPART);
		}

		return builder;
	}

	@Override
	/** {@inheritDoc} */
	public void setMyScope(final Scope scope) {
		super.setMyScope(scope);
		altGuards.setMyScope(scope);
	}

	@Override
	/** {@inheritDoc} */
	public void setCodeSection(final CodeSectionType codeSection) {
		if (altGuards != null) {
			altGuards.setCodeSection(codeSection);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void setMyStatementBlock(final StatementBlock statementBlock, final int index) {
		super.setMyStatementBlock(statementBlock, index);
		altGuards.setMyStatementBlock(statementBlock, index);
	}

	@Override
	/** {@inheritDoc} */
	public void setMyDefinition(final Definition definition) {
		altGuards.setMyDefinition(definition);
	}

	@Override
	/** {@inheritDoc} */
	public StatementBlock.ReturnStatus_type hasReturn(final CompilationTimeStamp timestamp) {
		StatementBlock.ReturnStatus_type result = altGuards.hasReturn(timestamp);

		if (result == StatementBlock.ReturnStatus_type.RS_YES && !altGuards.hasElse()) {
			// the invoked defaults may skip the entire statement
			result = StatementBlock.ReturnStatus_type.RS_MAYBE;
		}

		return result;
	}

	@Override
	/** {@inheritDoc} */
	public boolean hasReceivingStatement() {
		return true;
	}

	@Override
	/** {@inheritDoc} */
	protected void setMyLaicStmt(final AltGuards pAltGuards, final Statement pLoopStmt) {
		if (pLoopStmt != null) {
			altGuards.setMyLaicStmt(null,pLoopStmt);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void check(final CompilationTimeStamp timestamp) {
		if (lastTimeChecked != null && !lastTimeChecked.isLess(timestamp)) {
			return;
		}

		if (altGuards != null) {
			altGuards.setMyAltguards(altGuards);
			altGuards.setMyLaicStmt(altGuards, null);
			altGuards.check(timestamp);
		}

		lastTimeChecked = timestamp;
	}

	@Override
	/** {@inheritDoc} */
	public void checkAllowedInterleave() {
		if (altGuards != null) {
			altGuards.checkAllowedInterleave();
		}
	}

	@Override
	/** {@inheritDoc} */
	public void postCheck() {
		if (altGuards != null) {
			altGuards.postCheck();
		}
	}

	@Override
	/** {@inheritDoc} */
	public void updateSyntax(final TTCN3ReparseUpdater reparser, final boolean isDamaged) throws ReParseException {
		if (isDamaged) {
			lastTimeChecked = null;
			if (altGuards != null) {
				if (reparser.envelopsDamage(altGuards.getLocation())) {
					altGuards.updateSyntax(reparser, true);
					reparser.updateLocation(altGuards.getLocation());
					return;
				}
			}

			throw new ReParseException();
		}

		if (altGuards != null) {
			altGuards.updateSyntax(reparser, false);
			reparser.updateLocation(altGuards.getLocation());
		}
	}

	@Override
	/** {@inheritDoc} */
	public void findReferences(final ReferenceFinder referenceFinder, final List<Hit> foundIdentifiers) {
		if (altGuards == null) {
			return;
		}

		altGuards.findReferences(referenceFinder, foundIdentifiers);
	}

	@Override
	/** {@inheritDoc} */
	protected boolean memberAccept(final ASTVisitor v) {
		if (altGuards != null && !altGuards.accept(v)) {
			return false;
		}
		return true;
	}

	public AltGuards getAltGuards() {
		return altGuards;
	}

	@Override
	/** {@inheritDoc} */
	public void generateCode(final JavaGenData aData, final StringBuilder source) {
		if (altGuards != null) {
			altGuards.generateCodeAlt(aData, source);
		}
	}
}
