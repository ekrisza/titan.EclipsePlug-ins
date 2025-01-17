/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.runtime.core;

import org.eclipse.titan.runtime.core.BER.ASN_BERdescriptor;
import org.eclipse.titan.runtime.core.JSON.TTCN_JSONdescriptor;
import org.eclipse.titan.runtime.core.JSON.json_string_escaping;

/**
 * ASN.1 any type
 *
 * @author Kristof Szabados
 */
public class TitanAsn_Any extends TitanOctetString {
	public static final ASN_BERdescriptor TitanASN_Any_Ber_ = new ASN_BERdescriptor(0, null);
	public static final TTCN_JSONdescriptor TitanAsn_Any_json_ = new TTCN_JSONdescriptor(false, null, false, null, false, false, false, 0, null, false, json_string_escaping.ESCAPE_AS_SHORT);
	public static final TTCN_Typedescriptor TitanAsn_Any_descr_ = new TTCN_Typedescriptor("ANY", TitanASN_Any_Ber_, null, TitanAsn_Any_json_, null);

	/**
	 * Initializes to unbound value.
	 * */
	public TitanAsn_Any() {
		//intentionally empty
	}

	/**
	 * Initializes to a given value.
	 *
	 * @param otherValue
	 *                the value to initialize to.
	 * */
	public TitanAsn_Any(final TitanOctetString otherValue) {
		super(otherValue);
	}

	/**
	 * Initializes to a given value.
	 *
	 * @param otherValue
	 *                the value to initialize to.
	 * */
	public TitanAsn_Any(final TitanAsn_Any otherValue) {
		super(otherValue);
	}

  // TODO encoding/decoding support needed later
}
