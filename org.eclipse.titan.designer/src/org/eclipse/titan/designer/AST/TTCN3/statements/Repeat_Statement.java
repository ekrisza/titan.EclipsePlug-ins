/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.AST.TTCN3.statements;

import java.text.MessageFormat;

import org.eclipse.titan.designer.AST.ASTVisitor;
import org.eclipse.titan.designer.AST.GovernedSimple.CodeSectionType;
import org.eclipse.titan.designer.compiler.JavaGenData;
import org.eclipse.titan.designer.parsers.CompilationTimeStamp;
import org.eclipse.titan.designer.parsers.ttcn3parser.ReParseException;
import org.eclipse.titan.designer.parsers.ttcn3parser.TTCN3ReparseUpdater;

/**
 * @author Kristof Szabados
 * */
public final class Repeat_Statement extends Statement {
	private static final String INCORRECTUSAGE = "Repeat statement cannot be used outside alt statements,"
			+ " altsteps or response and exception handling part of call operations";

	private static final String STATEMENT_NAME = "repeat";

	private AltGuards myAltGuards;

	@Override
	/** {@inheritDoc} */
	public Statement_type getType() {
		return Statement_type.S_REPEAT;
	}

	@Override
	/** {@inheritDoc} */
	public String getStatementName() {
		return STATEMENT_NAME;
	}

	@Override
	/** {@inheritDoc} */
	public boolean isTerminating(final CompilationTimeStamp timestamp) {
		return true;
	}

	@Override
	/** {@inheritDoc} */
	public void setMyAltguards(final AltGuards altGuards) {
		this.myAltGuards = altGuards;
	}
	
	@Override
	/** {@inheritDoc} */
	public void setCodeSection(final CodeSectionType codeSection) {
		// Do nothing
	}

	@Override
	/** {@inheritDoc} */
	public void check(final CompilationTimeStamp timestamp) {
		if (lastTimeChecked != null && !lastTimeChecked.isLess(timestamp)) {
			return;
		}

		if (myAltGuards == null) {
			location.reportSemanticError(INCORRECTUSAGE);
		} else {
			myAltGuards.repeatFound();
		}

		lastTimeChecked = timestamp;
	}

	@Override
	/** {@inheritDoc} */
	public void checkAllowedInterleave() {
		location.reportSemanticError("Repeat statement is not allowed within an interleave statement");
	}

	@Override
	/** {@inheritDoc} */
	public void updateSyntax(final TTCN3ReparseUpdater reparser, final boolean isDamaged) throws ReParseException {
		if (isDamaged) {
			throw new ReParseException();
		}
	}

	@Override
	/** {@inheritDoc} */
	protected boolean memberAccept(final ASTVisitor v) {
		// no members
		return true;
	}

	@Override
	/** {@inheritDoc} */
	public void generateCode(final JavaGenData aData, final StringBuilder source) {
		final String label = myAltGuards.getLabel();

		if (label == null) {
			source.append("return TitanAlt_Status.ALT_REPEAT;\n");
		} else {
			source.append(MessageFormat.format("continue {0};\n", label));
		}
	}
}
