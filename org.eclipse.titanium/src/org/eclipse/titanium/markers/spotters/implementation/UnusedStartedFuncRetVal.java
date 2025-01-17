/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titanium.markers.spotters.implementation;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.titan.designer.AST.Assignment;
import org.eclipse.titan.designer.AST.IReferenceChain;
import org.eclipse.titan.designer.AST.IReferencingType;
import org.eclipse.titan.designer.AST.IType;
import org.eclipse.titan.designer.AST.IVisitableNode;
import org.eclipse.titan.designer.AST.ReferenceChain;
import org.eclipse.titan.designer.AST.Value;
import org.eclipse.titan.designer.AST.TTCN3.Expected_Value_type;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Def_Function;
import org.eclipse.titan.designer.AST.TTCN3.statements.Port_Utility;
import org.eclipse.titan.designer.AST.TTCN3.statements.Start_Component_Statement;
import org.eclipse.titan.designer.AST.TTCN3.statements.Start_Referenced_Component_Statement;
import org.eclipse.titan.designer.AST.TTCN3.types.Function_Type;
import org.eclipse.titan.designer.AST.TTCN3.values.Expression_Value;
import org.eclipse.titan.designer.AST.TTCN3.values.Expression_Value.Operation_type;
import org.eclipse.titan.designer.parsers.CompilationTimeStamp;
import org.eclipse.titanium.markers.spotters.BaseModuleCodeSmellSpotter;
import org.eclipse.titanium.markers.types.CodeSmellType;

public class UnusedStartedFuncRetVal extends BaseModuleCodeSmellSpotter {
	private static final String PROBLEM = "Return type of function type `{0}'' is `{1}'', which does not have the `done'' extension attibute."
			+ "When the test component terminates the returned value cannot be retrived with a `done'' operation";

	public UnusedStartedFuncRetVal() {
		super(CodeSmellType.UNUSED_STARTED_FUNCTION_RETURN_VALUES);
	}

	@Override
	public void process(final IVisitableNode node, final Problems problems) {
		if (node instanceof Start_Component_Statement) {
			final CompilationTimeStamp timestamp = CompilationTimeStamp.getBaseTimestamp();
			final Start_Component_Statement s = (Start_Component_Statement) node;
			final Assignment assignment = s.getFunctionInstanceReference().getRefdAssignment(timestamp, false);
			if (assignment == null) {
				return;
			}

			switch (assignment.getAssignmentType()) {
			case A_FUNCTION:
			case A_FUNCTION_RTEMP:
			case A_FUNCTION_RVAL:
				break;
			default:
				return;
			}

			final Def_Function function = (Def_Function) assignment;
			final IType runsOnType = function.getRunsOnType(timestamp);
			final IType compType = Port_Utility.checkComponentReference(timestamp, s, s.getComponent(), false, false, false);
			if (compType == null || runsOnType == null || !function.isStartable()) {
				return;
			}

			switch (function.getAssignmentType()) {
			case A_FUNCTION_RTEMP:
				break;
			case A_FUNCTION_RVAL:
				IType type = function.getType(timestamp);
				boolean returnTypeCorrect = false;
				while (!returnTypeCorrect) {
					if (type.hasDoneAttribute()) {
						returnTypeCorrect = true;
						break;
					}
					if (type instanceof IReferencingType) {
						final IReferenceChain refChain = ReferenceChain.getInstance(IReferenceChain.CIRCULARREFERENCE, true);
						final IType refd = ((IReferencingType) type).getTypeRefd(timestamp, refChain);
						refChain.release();
						if (type != refd) {
							type = refd;
						} else {
							break;
						}
					} else {
						break;
					}
				}

				if (!returnTypeCorrect) {
					final String msg = MessageFormat.format(PROBLEM, function.getDescription(), function.getType(timestamp)
							.getTypename());
					problems.report(s.getFunctionInstanceReference().getLocation(), msg);
				}
				break;
			default:
				break;
			}
		} else if (node instanceof Start_Referenced_Component_Statement) {
			final Start_Referenced_Component_Statement s = (Start_Referenced_Component_Statement) node;

			final Value dereferredValue = s.getDereferredValue();
			if (dereferredValue == null) {
				return;
			}

			switch (dereferredValue.getValuetype()) {
			case EXPRESSION_VALUE:
				if (Operation_type.REFERS_OPERATION.equals(((Expression_Value) dereferredValue).getOperationType())) {
					return;
				}
				break;
			case TTCN3_NULL_VALUE:
			case FAT_NULL_VALUE:
				return;
			default:
				break;
			}

			final CompilationTimeStamp timestamp = CompilationTimeStamp.getBaseTimestamp();
			IType type = dereferredValue.getExpressionGovernor(timestamp, Expected_Value_type.EXPECTED_DYNAMIC_VALUE);
			if (type != null) {
				type = type.getTypeRefdLast(timestamp);
			}
			if (type == null || type.getIsErroneous(timestamp)) {
				return;
			}

			if (!(type instanceof Function_Type)) {
				return;
			}

			final Function_Type functionType = (Function_Type) type;
			if (functionType.isRunsOnSelf()) {
				return;
			}
			if (!functionType.isStartable(timestamp)) {
				return;
			}

			final IType returnType = functionType.getReturnType();
			if (returnType == null) {
				return;
			}

			if (functionType.returnsTemplate()) {
				return;
			}

			IType lastType = returnType;
			boolean returnTypeCorrect = false;
			while (!returnTypeCorrect) {
				if (lastType.hasDoneAttribute()) {
					returnTypeCorrect = true;
					break;
				}
				if (lastType instanceof IReferencingType) {
					final IReferenceChain refChain = ReferenceChain.getInstance(IReferenceChain.CIRCULARREFERENCE, true);
					final IType refd = ((IReferencingType) lastType).getTypeRefd(timestamp, refChain);
					refChain.release();
					if (lastType != refd) {
						lastType = refd;
					} else {
						break;
					}
				} else {
					break;
				}
			}

			if (!returnTypeCorrect) {
				final String msg = MessageFormat.format(PROBLEM, functionType.getTypename(), returnType.getTypename());
				problems.report(dereferredValue.getLocation(), msg);
			}
		}
	}

	@Override
	public List<Class<? extends IVisitableNode>> getStartNode() {
		final List<Class<? extends IVisitableNode>> ret = new ArrayList<Class<? extends IVisitableNode>>(1);
		ret.add(Start_Component_Statement.class);
		ret.add(Start_Referenced_Component_Statement.class);
		return ret;
	}
}
