/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.common.product;

import org.eclipse.titan.common.utils.ObjectUtils;
import org.eclipse.titan.common.utils.StringUtils;

/**
 * For information on the product identity system please refer to 0012-073 Uen or to 1092-212 Uen
 * */
public final class ProductIdentity implements Comparable<ProductIdentity> {
	public static final String TITAN_PRODUCT_NUMBER = "";
	public static final String TITAN_PRODUCT_NUMBER_CAX = "CAX 105 7730";
	public static final String TITAN_PRODUCT_NUMBER_CRL = "CRL 113 200";
	public static final String TITAN_PRODUCT_NUMBER_CNL = "CNL 113 200";
	public static final int TITAN_VERSION_MAJOR = 7;
	public static final int TITAN_VERSION_MINOR = 2;
	public static final int TITAN_VERSION_PATCHLEVEL = 2;

	private String productNumber = null;
	private int productNumberSuffix = 1;
	private int revisionDigit;
	private int revisionLetter;
	private int verificationStep;

	private final boolean magicString;

	private ProductIdentity(final String productNumber, final int productNumberSuffix, final int revisionDigit, final int revisionLetter,
			final int verificationStep) {
		this.productNumber = productNumber;
		this.productNumberSuffix = productNumberSuffix;
		this.revisionDigit = revisionDigit;
		this.revisionLetter = revisionLetter;
		this.verificationStep = verificationStep;
		this.magicString = false;
	}

	/**
	 * Creates the product identity that represents the magic string RnXnn.
	 * */
	public ProductIdentity() {
		this.magicString = true;
	}

	private boolean isMagicString() {
		return magicString;
	}

	/**
	 * Creates and returns a version number representing the "magic version number" RnXnn.
	 *
	 * @return the magic version number.
	 * */
	public static ProductIdentity getMagicVersionNumber() {
		return new ProductIdentity();
	}

	/**
	 * Validates the input and if found correctly returns a version number representing that version.
	 *
	 * @param versionNumber the version number to decode;
	 *
	 * @return the new version number, or null if the input is not correct.
	 * */
	public static ProductIdentity getProductIdentity(final String productNumber, final long versionNumber) {
		int version = (int) versionNumber;
		if (version > 1000000) {
			// pre-release builds
			final int productNumberSuffix = version / 1000000;
			version -= productNumberSuffix * 1000000;
			final int revisionDigit = version / 10000;
			version -= revisionDigit * 10000;
			final int revisionLetter = version / 100;
			version -= revisionLetter * 100;
			final int verificationStep = version;

			return new ProductIdentity(productNumber, productNumberSuffix, revisionDigit, revisionLetter, verificationStep);
		}

		// official releases
		final int productNumberSuffix = version / 10000;
		version -= productNumberSuffix * 10000;
		final int revisionDigit = version / 100;
		version -= revisionDigit * 100;
		final int revisionLetter = version;

		return new ProductIdentity(productNumber, productNumberSuffix, revisionDigit, revisionLetter, 0);

	}

	/**
	 * Validates the input and if found correctly returns a version number representing that version.
	 *
	 * @param productNumber the product number part of the version number.
	 * @param productNumberSuffix the major part of the version number.
	 * @param revisionDigit the minor part of the version number.
	 * @param revisionLetter the patch level part of the version number.
	 * @param verificationStep the build number part of the version number.
	 * @return the new version number, or null if the input is not correct.
	 * */
	public static ProductIdentity getProductIdentity(final String productNumber, final int productNumberSuffix, final int revisionDigit,
			final int revisionLetter, final int verificationStep) {
		return new ProductIdentity(productNumber, productNumberSuffix, revisionDigit, revisionLetter, verificationStep);
	}

	@Override
	public int compareTo(final ProductIdentity other) {
		if (magicString || other == null || other.isMagicString()) {
			return 0;
		}

		if (productNumberSuffix != other.productNumberSuffix) {
			return productNumberSuffix - other.productNumberSuffix;
		}

		if (revisionDigit != other.revisionDigit) {
			return revisionDigit - other.revisionDigit;
		}

		if (revisionLetter != other.revisionLetter) {
			return revisionLetter - other.revisionLetter;
		}

		if (verificationStep == 0) {
			if (other.verificationStep == 0) {
				return 0;
			}

			return 1;
		}

		if (other.verificationStep == 0) {
			return -1;
		}

		return verificationStep - other.verificationStep;
	}

	@Override
	public String toString() {
		if (magicString) {
			return "RnXnn";
		}

		final StringBuilder builder = new StringBuilder(20);
		if (!StringUtils.isNullOrEmpty(productNumber)) {
			if (productNumber.contains("CRL") || productNumber.contains("crl")) {
				builder.append(productNumber);
				if (productNumberSuffix == 0 || (productNumberSuffix == 1 && revisionDigit < 9)) {
					builder.append(' ');
				} else {
					builder.append('/').append(productNumberSuffix);
				}
				builder.append(' ');
			} else if (productNumber.contains("CAX") || productNumber.contains("cax")) {
				if (productNumberSuffix != 0) {
					builder.append(productNumberSuffix);
					builder.append('/');
				}
				builder.append(productNumber);
				builder.append(' ');
			}
			builder.append('R').append(revisionDigit).append((char) (revisionLetter + 'A'));
			if (verificationStep != 0) {
				if (verificationStep < 10) {
					builder.append('0');
				}
				builder.append(verificationStep);
			}
		}
		if (productNumber != null && productNumber.isEmpty()) {
			builder.append(productNumberSuffix).append('.').append(revisionDigit).append('.')
				.append(revisionLetter);
		}

		return builder.toString();
	}

	@Override
	public int hashCode() {
		return ObjectUtils.hash(magicString, productNumber, productNumberSuffix,
				revisionDigit, revisionLetter, verificationStep);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}

		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}

		final ProductIdentity other = (ProductIdentity) obj;
		return ObjectUtils.equals(magicString, other.magicString)
				&& ObjectUtils.equals(productNumber, other.productNumber)
				&& ObjectUtils.equals(productNumberSuffix, other.productNumberSuffix)
				&& ObjectUtils.equals(revisionDigit, other.revisionDigit)
				&& ObjectUtils.equals(revisionLetter, other.revisionLetter)
				&& ObjectUtils.equals(verificationStep, other.verificationStep);
	}
}
