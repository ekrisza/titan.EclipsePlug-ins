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
import org.eclipse.titan.designer.AST.IType.Type_type;
import org.eclipse.titan.designer.AST.IValue;
import org.eclipse.titan.designer.AST.IValue.Value_type;
import org.eclipse.titan.designer.AST.ReferenceFinder;
import org.eclipse.titan.designer.AST.ReferenceFinder.Hit;
import org.eclipse.titan.designer.AST.Scope;
import org.eclipse.titan.designer.AST.Value;
import org.eclipse.titan.designer.AST.TTCN3.Expected_Value_type;
import org.eclipse.titan.designer.AST.TTCN3.values.Verdict_Value;
import org.eclipse.titan.designer.AST.TTCN3.values.Verdict_Value.Verdict_type;
import org.eclipse.titan.designer.AST.TTCN3.values.expressions.ExpressionStruct;
import org.eclipse.titan.designer.compiler.JavaGenData;
import org.eclipse.titan.designer.parsers.CompilationTimeStamp;
import org.eclipse.titan.designer.parsers.ttcn3parser.ReParseException;
import org.eclipse.titan.designer.parsers.ttcn3parser.TTCN3ReparseUpdater;
/**
 * @author Kristof Szabados
 * */
public final class Setverdict_Statement extends Statement {
	private static final String OPERANDERROR = "The operand of the `setverdict' operation should be a verdict value";
	private static final String INCONTROLPART = "Setverdict statement is not allowed in the control part";
	private static final String ERRORCANNOTBESET = "Error verdict cannot be set by the setverdict operation";

	private static final String FULLNAMEPART1 = ".verdictvalue";
	private static final String FULLNAMEPART2 = ".verdictreason";
	private static final String STATEMENT_NAME = "setverdict";

	private final Value verdictValue;
	private final LogArguments verdictReason;

	public Setverdict_Statement(final Value verdictValue, final LogArguments verdictReason) {
		this.verdictValue = verdictValue;
		this.verdictReason = verdictReason;

		if (verdictValue != null) {
			verdictValue.setFullNameParent(this);
		}
		if (verdictReason != null) {
			this.verdictReason.setFullNameParent(this);
		}
	}

	@Override
	/** {@inheritDoc} */
	public Statement_type getType() {
		return Statement_type.S_SETVERDICT;
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

		if (verdictValue == child) {
			return builder.append(FULLNAMEPART1);
		} else if (verdictReason == child) {
			return builder.append(FULLNAMEPART2);
		}

		return builder;
	}

	@Override
	/** {@inheritDoc} */
	public void setMyScope(final Scope scope) {
		super.setMyScope(scope);
		if (verdictValue != null) {
			verdictValue.setMyScope(scope);
		}
		if (verdictReason != null) {
			verdictReason.setMyScope(scope);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void setCodeSection(final CodeSectionType codeSection) {
		if (verdictValue != null) {
			verdictValue.setCodeSection(codeSection);
		}
		if (verdictReason != null) {
			verdictReason.setCodeSection(codeSection);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void check(final CompilationTimeStamp timestamp) {
		if (lastTimeChecked != null && !lastTimeChecked.isLess(timestamp)) {
			return;
		}

		if (myStatementBlock.getMyDefinition() == null) {
			location.reportSemanticError(INCONTROLPART);
		}

		if (verdictValue != null) {
			verdictValue.setLoweridToReference(timestamp);
			final IValue last = verdictValue.getValueRefdLast(timestamp, Expected_Value_type.EXPECTED_DYNAMIC_VALUE, null);//to force its checking
			final Type_type temp = verdictValue.getExpressionReturntype(timestamp, Expected_Value_type.EXPECTED_DYNAMIC_VALUE);
			switch (temp) {
			case TYPE_VERDICT:
				if (Value_type.VERDICT_VALUE.equals(last.getValuetype())
						&& Verdict_type.ERROR.equals(((Verdict_Value) last).getValue())) {
					verdictValue.getLocation().reportSemanticError(ERRORCANNOTBESET);
				}

				break;
			default:
				verdictValue.getLocation().reportSemanticError(OPERANDERROR);
				verdictValue.setIsErroneous(true);
				break;
			}

		}

		if (verdictReason != null) {
			verdictReason.check(timestamp);
		}

		lastTimeChecked = timestamp;
	}

	@Override
	/** {@inheritDoc} */
	public void updateSyntax(final TTCN3ReparseUpdater reparser, final boolean isDamaged) throws ReParseException {
		if (isDamaged) {
			throw new ReParseException();
		}

		if (verdictValue != null) {
			verdictValue.updateSyntax(reparser, false);
			reparser.updateLocation(verdictValue.getLocation());
		}

		if (verdictReason != null) {
			verdictReason.updateSyntax(reparser, false);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void findReferences(final ReferenceFinder referenceFinder, final List<Hit> foundIdentifiers) {
		if (verdictValue != null) {
			verdictValue.findReferences(referenceFinder, foundIdentifiers);
		}
		if (verdictReason != null) {
			verdictReason.findReferences(referenceFinder, foundIdentifiers);
		}
	}

	@Override
	/** {@inheritDoc} */
	protected boolean memberAccept(final ASTVisitor v) {
		if (verdictValue != null && !verdictValue.accept(v)) {
			return false;
		}
		if (verdictReason != null && !verdictReason.accept(v)) {
			return false;
		}
		return true;
	}

	public Value getVerdictValue() {
		return verdictValue;
	}

	public LogArguments getVerdictReason() {
		return verdictReason;
	}

	@Override
	/** {@inheritDoc} */
	public void generateCode(final JavaGenData aData, final StringBuilder source) {
		final ExpressionStruct expression = new ExpressionStruct();

		aData.addCommonLibraryImport("TTCN_Runtime");
		expression.expression.append("\t\t\tTTCN_Runtime.setverdict(");
		verdictValue.generateCodeExpression(aData, expression, false);
		if (verdictReason != null) {
			expression.expression.append(", ");
			final ExpressionStruct reason = new ExpressionStruct();
			verdictReason.generateCodeExpression(aData, reason);
			if (reason.preamble.length() > 0) {
				expression.preamble.append(reason.preamble);
			}
			if (reason.postamble.length() > 0) {
				expression.postamble.append(reason.postamble);
			}
			expression.expression.append(reason.expression);
			expression.expression.append(".get_value().toString()");
		}
		expression.expression.append(')');
		expression.mergeExpression(source);
	}
}
