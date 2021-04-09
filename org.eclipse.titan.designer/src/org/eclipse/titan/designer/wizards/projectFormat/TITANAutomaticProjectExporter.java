/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.wizards.projectFormat;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.titan.common.logging.ErrorReporter;
import org.eclipse.titan.designer.core.TITANJavaBuilder;
import org.eclipse.titan.designer.preferences.PreferenceConstants;
import org.eclipse.titan.designer.preferences.pages.ExportOptionsPage;
import org.eclipse.titan.designer.productUtilities.ProductConstants;
import org.eclipse.titan.designer.properties.data.ProjectBuildPropertyData;

/**
 * Automatically exports the project information into a tpd file, if it is
 * ordered on workspace level
 * 
 * @see ExportOptionsPage#automaticExport
 * @see ExportOptionsPage#requestLocation
 * @see TITANProjectExporter#saveAll()
 * 
 * @author Jeno Balasko
 * 
 */
public final class TITANAutomaticProjectExporter {

	private static IPreferencesService preferenceService = Platform.getPreferencesService();

	private static String projectFileName;

	private static boolean automaticExportRequested() {
		return preferenceService.getBoolean(ProductConstants.PRODUCT_ID_DESIGNER, PreferenceConstants.EXPORT_AUTOMATIC_EXPORT, false, null);
	}

	private static boolean isRequestedLocation() {
		return preferenceService.getBoolean(ProductConstants.PRODUCT_ID_DESIGNER, PreferenceConstants.EXPORT_REQUEST_LOCATION, false, null);
	}

	/**
	 * Checks if the project has been already exported
	 * @param project reference for the project to be checked
	 * @return true if the project is already exported, otherwise false
	 */
	private static boolean isAlreadyExported(final IProject project) {
		if (project == null) {
			return false;
		}

		String exported = ProjectBuildPropertyData.TRUE_STRING;
		try {
			exported = project.getPersistentProperty(new QualifiedName(ProjectBuildPropertyData.QUALIFIER,
					ProjectBuildPropertyData.ALREADY_EXPORTED));
		} catch (CoreException e) {
			ErrorReporter.logError("Project property 'alreadyExported` cannot be loaded");
		}
		return (ProjectBuildPropertyData.TRUE_STRING).equals(exported) ? true : false;
	}

	/**
	 * Save all project information of "project" into the tpd file given output
	 * file "projectFile" if the flag automaticallyExport is true
	 * 
	 * @return true if the save was ordered and successful
	 */
	public static boolean saveAllAutomatically(final IProject project) {

		if (project == null) {
			ErrorReporter.logError("Invalid project");
			return false;
		}

		if (!automaticExportRequested()) {
			return false;
		}
		
		if (TITANJavaBuilder.isBuilderEnabled(project)) {
			return false;
		}

		final TITANProjectExporter exporter = new TITANProjectExporter(project);
		exporter.setProjectFileFromLoadLocation();
		exporter.setExportPreferences();

		if (isRequestedLocation() && !isAlreadyExported(project)) {

			// Request location in a dialog

			// set project file name with full path
			projectFileName = exporter.getProjectFile();
			if (projectFileName == null) {
				// the project hasn't been saved yet:
				if (project.getLocation() != null) {
					projectFileName = project.getLocation().append(project.getName() + ".tpd").toOSString();
				} else {
					projectFileName = project.getName() + ".tpd";
				}
			}

			// Dialog to fetch a tpd:
			Display.getDefault().syncExec(new Runnable() {
				@Override
				public void run() {
					final FileDialog dialog = new FileDialog(new Shell(Display.getDefault(), SWT.ON_TOP));
					dialog.setText("Location for the TITAN Project Descriptor (tpd) file");
					dialog.setFileName(projectFileName);
					dialog.setFilterExtensions(new String[] { "*.tpd" });
					projectFileName = dialog.open();
				}
			});

			// Process the fetched tpd file name:
			if (projectFileName == null) {
				return false; //cancel pressed
			}

			// convert the file name for common shape ( \ -> / )
			final IPath temp = new Path(projectFileName);
			projectFileName = temp.toString();
			ProjectBuildPropertyData.setLoadLocation(project, projectFileName);
			exporter.setProjectFileFromLoadLocation();

			return exporter.saveAll();

		} else {

			return exporter.saveAll();
		}
	}

}
