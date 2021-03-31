/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.AST;

import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.eclipse.titan.common.utils.Joiner;

/**
 * This class represents a chain of references, used to detect circular references.
 * <p>
 * This class is able to mark a state, and later on return back to this state.
 * @see #markState()
 * @see #previousState()
 *
 * @author Kristof Szabados
 * */
public final class ReferenceChain implements IReferenceChain {
	/**
	 * Static cache to decrease the memory usage.
	 * */
	private static final Queue<ReferenceChain> CHAIN_CACHE = new ConcurrentLinkedQueue<ReferenceChain>();

	/**
	 * The list of references contained in the chain.
	 * */
	private final List<IReferenceChainElement> chainLinks = new ArrayList<IReferenceChainElement>();

	/**
	 * The list of marked states.
	 * */
	private final Deque<Integer> markedStates = new ArrayDeque<Integer>();

	/**
	 * The message used to report if a circular chain is actually found.
	 * <p>
	 * This String object must have exactly one location for inserting a text into.
	 * See the {@link MessageFormat#format(String, Object...)} method for more information.
	 * */
	private String message;

	/**
	 * Should we report the problem as an error or as a warning.
	 * */
	private boolean isError;


	/**
	 * Private constructor.
	 *
	 * @param message the message or the problem to be reported when a cycle is found
	 * @param isError whether the problem would be reported as error, or warning
	 * */
	private ReferenceChain(final String message, final boolean isError) {
		this.message = message;
		this.isError = isError;
	}

	/**
	 * The factory method of this class, used to create an instance.
	 *
	 * @param message the message or the problem to be reported when a cycle is found
	 * @param isError whether the problem would be reported as error, or warning
	 *
	 *  @return the created reference chain instance.
	 * */
	public static ReferenceChain getInstance(final String message, final boolean isError) {
		final ReferenceChain result = CHAIN_CACHE.poll();
		if (result != null) {
			result.message = message;
			result.isError = isError;
			return result;
		}

		return new ReferenceChain(message, isError);
	}

	@Override
	/** {@inheritDoc} */
	public void release() {
		if (!chainLinks.isEmpty()) {
			chainLinks.clear();
			markedStates.clear();
		}

		CHAIN_CACHE.offer(this);
	}

	@Override
	/** {@inheritDoc} */
	public boolean contains(final IReferenceChainElement chainLink) {
		return chainLinks.indexOf(chainLink) != -1;
	}

	@Override
	/** {@inheritDoc} */
	public boolean add(final IReferenceChainElement chainLink) {
		final int index = chainLinks.indexOf(chainLink);
		if (index >= 0) {
			reportError(index);
			return false;
		}

		chainLinks.add(chainLink);
		return true;
	}

	private void reportError(final int firstIndex) {
		//for every element of the circle
		for (int i = firstIndex; i < chainLinks.size(); i++) {
			final Joiner joiner = new Joiner(" -> ");

			//add the elements till the end of the chain
			for (int i2 = i; i2 < chainLinks.size(); i2++) {
				joiner.join(chainLinks.get(i2).chainedDescription());
			}

			//and from the first repeated element
			for (int i2 = firstIndex; i2 < i; i2++) {
				joiner.join(chainLinks.get(i2).chainedDescription());
			}

			//add the element in question to the end, where it should appear again to provide the "circular" feeling
			joiner.join(chainLinks.get(i).chainedDescription());

			final Location location = chainLinks.get(i).getChainLocation();
			if (location != null) {
				if (isError) {
					location.reportSingularSemanticError(MessageFormat.format(message, joiner.toString()));
				} else {
					location.reportSingularSemanticWarning(MessageFormat.format(message, joiner.toString()));
				}
			}
		}
	}

	@Override
	/** {@inheritDoc} */
	public void markState() {
		markedStates.add(chainLinks.size());
	}

	@Override
	/** {@inheritDoc} */
	public void previousState() {
		if (markedStates.isEmpty()) {
			return;
		}

		final Integer polled = markedStates.pollLast();
		if (polled == null) {
			return;
		}
		final int markedLimit = polled.intValue();
		for (int i = chainLinks.size() - 1; i >= markedLimit; i--) {
			chainLinks.remove(i);
		}
	}

	/**
	 * Creates a {@link org.eclipse.titan.designer.AST.CachedReferenceChain CachedReferenceChain} from the current chain.
	 * @return the created chain
	 */
	public CachedReferenceChain toCachedReferenceChain() {
		final CachedReferenceChain result = new CachedReferenceChain(message, isError);

		int idx1 = 0;
		for (final int idx2 : markedStates) {
			for (int i = idx1; i < idx2; ++i) {
				result.add(chainLinks.get(i));
			}
			result.markState();
			idx1 = idx2;
		}

		for (int i = markedStates.isEmpty() ? 0 : markedStates.peek();
		i < chainLinks.size(); ++i) {
			result.add(chainLinks.get(i));
		}

		return result;
	}
}
