/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.titan.common.logging.ErrorReporter;
import org.eclipse.titan.designer.parsers.GlobalParser;
import org.eclipse.titan.designer.preferences.PreferenceConstants;
import org.eclipse.titan.designer.productUtilities.ProductConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;

public class OutOfMemoryCheck {
	private static final long DecimalMegaByte = 1000 * 1000;
	private static boolean isOutOfMemory = false;

	public static final String OUTOFMEMORYERROR =
			"Free memory is running low. Syntactic and semantic check has been disabled. (Can be re-enabled in Window -> Preferences -> Titan preferences -> On-TheFly checker Preferences page)";

	private static class OutOfMemoryErrorDialog extends MessageDialog {
		private static volatile boolean isDialogOpen = false;

		public OutOfMemoryErrorDialog(final Shell parent, final String title, final String message) {
			super(parent, title, null, message, ERROR, new String[] {IDialogConstants.OK_LABEL}, 0);
		}

		@Override
		protected void buttonPressed(final int buttonId) {
			super.buttonPressed(buttonId);

			if (buttonId == Window.CANCEL) {
				PreferencesUtil.createPreferenceDialogOn(null,
						"org.eclipse.titan.designer.preferences.pages.TITANPreferencePage", null, null).open();
			}
		}

		@Override
		public int open() {
			isDialogOpen = true;

			int result =  super.open();
			isDialogOpen = false;

			return result;
		}
	}

	private OutOfMemoryCheck() {
		// Hide constructor
	}

	public static boolean isOutOfMemoryAlreadyReported() {
		return isOutOfMemory;
	}

	public static void outOfMemoryEvent() {
		isOutOfMemory = true;
		ErrorReporter.logError(OUTOFMEMORYERROR);

		if (PlatformUI.isWorkbenchRunning()) {
			Display.getDefault().asyncExec(new Runnable() {
				@Override
				public void run() {
					if (OutOfMemoryErrorDialog.isDialogOpen) {
						return;
					}

					Activator.getDefault().getPreferenceStore().setValue(PreferenceConstants.USEONTHEFLYPARSING, false);

					final OutOfMemoryErrorDialog dialog = new OutOfMemoryErrorDialog(null, "Low memory", OUTOFMEMORYERROR);
					dialog.open();
				}
			});
		}

		GlobalParser.clearAllInformation();
		System.gc();
	}

	public static void resetOutOfMemoryflag() {
		isOutOfMemory = false;
	}

	/**
	 * Check if remaining free memory is low
	 *
	 * @return true: if the remaining free memory is low
	 * */
	public static boolean isOutOfMemory() {
		final boolean checkForLowMemory = Platform.getPreferencesService().getBoolean(ProductConstants.PRODUCT_ID_DESIGNER, PreferenceConstants.CHECKFORLOWMEMORY, false, null);
		if (checkForLowMemory) {
			final Runtime Rt = Runtime.getRuntime();

			final long free = Rt.freeMemory();
			final long total = Rt.totalMemory();

			final long limit = Math.min(200 * DecimalMegaByte, Math.round(total * (double)0.1));

			if (free < limit) {
				ErrorReporter.logError("limit: "+String.valueOf(limit)+", free: " + String.valueOf(free));
				return true;
			}
		}

		return false;
	}
}
