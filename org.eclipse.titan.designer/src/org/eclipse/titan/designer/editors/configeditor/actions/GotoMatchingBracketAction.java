/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.editors.configeditor.actions;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.titan.designer.editors.configeditor.ConfigEditor;
import org.eclipse.titan.designer.editors.configeditor.ConfigTextEditor;
import org.eclipse.titan.designer.editors.configeditor.PairMatcher;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;

/**
 * @author Kristof Szabados
 * */
public class GotoMatchingBracketAction extends AbstractHandler implements IEditorActionDelegate {
	private ConfigEditor targetEditor = null;
	private ISelection selection = TextSelection.emptySelection();

	@Override
	public final void run(final IAction action) {
		if (targetEditor == null) {
			return;
		}

		if (targetEditor.getActiveEditor() != targetEditor.getEditor()) {
			return;
		}

		if (!selection.isEmpty()) {
			if (selection instanceof TextSelection) {
				final TextSelection tSelection = (TextSelection) selection;
				if (tSelection.getLength() != 0) {
					return;
				}
			}
		}

		final ConfigTextEditor textEditor = targetEditor.getEditor();
		final IDocument document = textEditor.getDocument();
		final int carretOffset = textEditor.getCarretOffset();
		final PairMatcher pairMatcher = new PairMatcher();

		final IRegion region = pairMatcher.match(document, carretOffset);
		if (region == null) {
			return;
		}

		int targetOffset;
		if (region.getOffset() + 1 == carretOffset) {
			targetOffset = region.getOffset() + region.getLength();
		} else {
			targetOffset = region.getOffset() + 1;
		}

		textEditor.setCarretOffset(targetOffset);
		textEditor.selectAndReveal(targetOffset, 0);
	}

	@Override
	public final void selectionChanged(final IAction action, final ISelection selection) {
		this.selection = selection;
	}

	@Override
	public final void setActiveEditor(final IAction action, final IEditorPart targetEditor) {
		if (targetEditor instanceof ConfigEditor) {
			this.targetEditor = (ConfigEditor) targetEditor;
		} else {
			this.targetEditor = null;
		}
	}

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final IEditorPart activeEditor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		if (activeEditor instanceof ConfigEditor) {
			this.targetEditor = (ConfigEditor) activeEditor;
		} else {
			this.targetEditor = null;
		}

		if (activeEditor == null) {
			return null;
		}

		if (this.targetEditor.getActiveEditor() != this.targetEditor.getEditor()) {
			return null;
		}

		if (!selection.isEmpty()) {
			if (selection instanceof TextSelection) {
				final TextSelection tSelection = (TextSelection) selection;
				if (tSelection.getLength() != 0) {
					return null;
				}
			}
		}

		final ConfigTextEditor textEditor = this.targetEditor.getEditor();
		final IDocument document = textEditor.getDocument();
		final int carretOffset = textEditor.getCarretOffset();
		final PairMatcher pairMatcher = new PairMatcher();

		final IRegion region = pairMatcher.match(document, carretOffset);
		if (region == null) {
			return null;
		}

		int targetOffset;
		if (region.getOffset() + 1 == carretOffset) {
			targetOffset = region.getOffset() + region.getLength();
		} else {
			targetOffset = region.getOffset() + 1;
		}

		textEditor.setCarretOffset(targetOffset);
		textEditor.selectAndReveal(targetOffset, 0);
		return null;
	}
}
