/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.AST.ASN1.definitions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.titan.designer.AST.ASTNode;
import org.eclipse.titan.designer.AST.ASTVisitor;
import org.eclipse.titan.designer.AST.FieldSubReference;
import org.eclipse.titan.designer.AST.ILocateableNode;
import org.eclipse.titan.designer.AST.ISubReference;
import org.eclipse.titan.designer.AST.Identifier;
import org.eclipse.titan.designer.AST.Location;
import org.eclipse.titan.designer.AST.NULL_Location;
import org.eclipse.titan.designer.AST.ASN1.Defined_Reference;
import org.eclipse.titan.designer.parsers.CompilationTimeStamp;

/**
 * Represents the exported symbols of a module.
 *
 * @author Kristof Szabados
 */
public final class Exports extends ASTNode implements ILocateableNode {
	/** my module. */
	private ASN1Module module;
	/** exported symbols. */
	private final Symbols symbols;
	/**
	 * exports all (true if the module of this export list exports all of
	 * its assignments).
	 */
	private final boolean exportAll;

	/**
	 * The location of the whole export list. This location encloses the
	 * export list fully, as it is used to report errors to.
	 **/
	private Location location = NULL_Location.INSTANCE;

	/**
	 * Holds the last time when these exports were checked, or null if
	 * never.
	 */
	private CompilationTimeStamp lastCompilationTimeStamp;

	public Exports(final boolean exportAll) {
		this.exportAll = exportAll;
		if (exportAll) {
			symbols = null;
		} else {
			symbols = new Symbols();
		}
	}

	public Exports(final Symbols symbols) {
		exportAll = false;
		this.symbols = symbols;
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

	/**
	 * Sets the module of this export list to be the provided module.
	 *
	 * @param module
	 *                the module of this export list.
	 * */
	public void setMyModule(final ASN1Module module) {
		this.module = module;
	}

	/**
	 * Checks if there is a symbol exported with the provided identifier.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle.
	 * @param identifier
	 *                the identifier used to search for a symbol.
	 *
	 * @return true if a symbol with the provided name is exported, false
	 *         otherwise.
	 * */
	public boolean exportsSymbol(final CompilationTimeStamp timestamp, final Identifier id) {
		check(timestamp);

		if (exportAll) {
			return true;
		}

		return symbols.hasSymbol(id.getName());

	}

	/**
	 * Checks this export list.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle.
	 * */
	public void check(final CompilationTimeStamp timestamp) {
		if (null != lastCompilationTimeStamp && !lastCompilationTimeStamp.isLess(timestamp)) {
			return;
		}

		if (exportAll) {
			lastCompilationTimeStamp = timestamp;
			return;
		}

		symbols.checkUniqueness(timestamp);

		for (int i = 0; i < symbols.size(); i++) {
			final List<ISubReference> list = new ArrayList<ISubReference>();
			list.add(new FieldSubReference(symbols.getNthElement(i)));
			final Defined_Reference reference = new Defined_Reference(null, list);

			/* check whether exists or not */
			module.getAssBySRef(timestamp, reference);
		}

		lastCompilationTimeStamp = timestamp;
	}

	@Override
	/** {@inheritDoc} */
	protected boolean memberAccept(final ASTVisitor v) {
		// TODO
		return true;
	}
}
