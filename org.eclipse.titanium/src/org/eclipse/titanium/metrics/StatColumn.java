/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titanium.metrics;

/**
 * Columns of the metric statistics.
 * <p>
 * These enum values represents a part of the whole statistical data calculated
 * on <code>IMetric</code>s.
 *
 * @see Statistics
 *
 * @author poroszd
 */
public enum StatColumn {
	/** Sum of the values */
	TOTAL("Total"),
	/** Maximum of the values */
	MAX("Max"),
	/** Mean of the values */
	MEAN("Mean"),
	/** Standard deviation of the values */
	DEV("Std. dev");

	private String name;

	StatColumn(final String name) {
		this.name = name;
	}

	/**
	 * @return The displayable name of the stat.
	 */
	public String getName() {
		return name;
	}
}
