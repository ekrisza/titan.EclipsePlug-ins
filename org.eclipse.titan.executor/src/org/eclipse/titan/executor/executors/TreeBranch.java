/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.executor.executors;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a branch of a tree.
 *
 * @author Kristof Szabados
 * */
public class TreeBranch extends TreeLeaf implements ITreeBranch {
	private final List<ITreeLeaf> children;

	public TreeBranch(final String name) {
		super(name);
		children = new ArrayList<ITreeLeaf>();
	}

	@Override
	public void dispose() {
		for (final ITreeLeaf element : children) {
			element.dispose();
		}

		children.clear();
		super.dispose();
	}

	@Override
	public final List<ITreeLeaf> children() {
		return children;
	}

	public final void addChildToEnd(final TreeLeaf element) {
		element.parent(this);
		children.add(element);
	}

	public final void addChildBefore(final TreeLeaf child, final TreeLeaf reference) {
		final int index = children.indexOf(reference);
		if (index == -1) {
			children.add(child);
		} else {
			children.add(index, child);
		}
		child.parent(this);
	}

	public final void remove(final TreeLeaf element) {
		children.remove(element);
		element.parent(null);
	}

	/**
	 * Moves all of the parameter's children to the end of his own children.
	 *
	 * @param element the element whose children are to be transported.
	 * */
	public final void transferChildren(final TreeBranch element) {
		for (final ITreeLeaf tempElement : element.children) {
			children.add(tempElement);
			tempElement.parent(this);
		}
		element.children.clear();
	}
}
