/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.parsers;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.titan.common.logging.ErrorReporter;
import org.eclipse.titan.designer.commonFilters.ResourceExclusionHelper;

/**
 * General parsing related static class. Provides a common root for such
 * hierarchies
 *
 * @author Kristof Szabados
 */
// FIXME if ttcnpp is handled as ttcn file, this should be extended as well
public final class GlobalParser {
	public static final String[] SUPPORTED_TTCN3_EXTENSIONS = new String[] { "ttcn3", "ttcn" };
	public static final String TTCNPP_EXTENSION = "ttcnpp";
	public static final String TTCNIN_EXTENSION = "ttcnin";
	public static final String[] SUPPORTED_ASN1_EXTENSIONS = new String[] { "asn1", "asn" };
	public static final String[] SUPPORTED_CONFIG_FILE_EXTENSIONS = new String[] { "cfg" };

	public static final String TRUE = "true";
	public static final String DOT = ".";

	/** The Source Parsers whose project was already analyzed */
	private static final Map<IProject, ProjectSourceParser> TTCN3_PARSERS = new ConcurrentHashMap<IProject, ProjectSourceParser>();

	/** The Conifguration Parsers whose project was already analyzed */
	private static final Map<IProject, ProjectConfigurationParser> CFG_PARSERS = new ConcurrentHashMap<IProject, ProjectConfigurationParser>();

	/** private constructor to disable instantiation */
	private GlobalParser() {
	}

	/**
	 * Decides whether a string is a supported file extension for TTCN-3
	 * files or not.
	 *
	 * @param extension
	 *                the string to check
	 *
	 * @return true if it is a supported TTCN-3 file extension, false
	 *         otherwise
	 **/
	public static boolean isSupportedTTCN3Extension(final String extension) {
		for (int i = 0; i < SUPPORTED_TTCN3_EXTENSIONS.length; i++) {
			if (SUPPORTED_TTCN3_EXTENSIONS[i].equals(extension)) {
				return true;
			}
		}
		if (TTCNPP_EXTENSION.equals(extension)) {
			return true;
		}
		return false;
	}

	/**
	 * Decides whether a string is a supported file extension or not.
	 *
	 * @param extension
	 *                the string to check
	 *
	 * @return true if it is a supported file extension, false otherwise
	 **/
	public static boolean isSupportedExtension(final String extension) {
		for (int i = 0; i < SUPPORTED_TTCN3_EXTENSIONS.length; i++) {
			if (SUPPORTED_TTCN3_EXTENSIONS[i].equals(extension)) {
				return true;
			}
		}

		if (TTCNPP_EXTENSION.equals(extension) || TTCNIN_EXTENSION.equals(extension)) {
			return true;
		}

		for (int i = 0; i < SUPPORTED_ASN1_EXTENSIONS.length; i++) {
			if (SUPPORTED_ASN1_EXTENSIONS[i].equals(extension)) {
				return true;
			}
		}

		for (int i = 0; i < SUPPORTED_CONFIG_FILE_EXTENSIONS.length; i++) {
			if (SUPPORTED_CONFIG_FILE_EXTENSIONS[i].equals(extension)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates a parser that handles the parsing related jobs of the given
	 * project.
	 *
	 * @param project
	 *                the project to create a parser for
	 *
	 * @return the parser which handles the parsing of the provided project
	 * */
	public static ProjectSourceParser getProjectSourceParser(final IProject project) {
		ProjectSourceParser tempParser;
		if (TTCN3_PARSERS.containsKey(project)) {
			tempParser = TTCN3_PARSERS.get(project);
		} else {
			tempParser = new ProjectSourceParser(project);
			TTCN3_PARSERS.put(project, tempParser);
		}
		return tempParser;
	}

	/**
	 * Creates a parser that handles the parsing related jobs of the given
	 * project.
	 *
	 * @param project
	 *                the project to create a parser for
	 *
	 * @return the parser which handles the parsing of the provided project
	 * */
	public static ProjectConfigurationParser getConfigSourceParser(final IProject project) {
		ProjectConfigurationParser tempParser;
		if (CFG_PARSERS.containsKey(project)) {
			tempParser = CFG_PARSERS.get(project);
		} else {
			tempParser = new ProjectConfigurationParser(project);
			CFG_PARSERS.put(project, tempParser);
		}
		return tempParser;
	}

	/**
	 * Returns true if the resource (file, folder or project) contains ttcnpp file not excluded from the project.
	 * 
	 * @param resource to start from.
	 * @return {@code true} if there is a ttcnpp file in the project, {@code false} otherwise.
	 * @throws CoreException
	 */
	public static boolean hasTtcnppFiles(final IResource resource) throws CoreException {
		if(resource instanceof IFolder && ResourceExclusionHelper.isDirectlyExcluded((IFolder) resource)) {
			return false;
		} else if(resource instanceof IFile && ResourceExclusionHelper.isDirectlyExcluded((IFile) resource)) {
			return false;
		}

		if (resource instanceof IProject || resource instanceof IFolder) {
			final IResource[] children = resource instanceof IFolder ? ((IFolder) resource).members() : ((IProject) resource).members();
			for (final IResource res : children) {
				if (hasTtcnppFiles(res)) {
					return true;
				}
			}
		} else if (resource instanceof IFile) {
			final IFile file = (IFile) resource;
			return "ttcnpp".equals(file.getFileExtension());
		}

		return false;
	}

	/**
	 * Clears all on-the-fly information stored about the project.
	 *
	 * @param project
	 *                the project whose information is to be cleared.
	 * */
	public static void clearAllInformation(final IProject project) {
		if (TTCN3_PARSERS.containsKey(project)) {
			TTCN3_PARSERS.remove(project);
		}

		if (CFG_PARSERS.containsKey(project)) {
			CFG_PARSERS.remove(project);
		}
	}

	/**
	 * Clears all information about all of the known projects.
	 * */
	public static void clearAllInformation() {
		TTCN3_PARSERS.clear();
		CFG_PARSERS.clear();
	}

	/**
	 * Re-analyzes every already known projects, with all of their contents.
	 **/
	public static void reAnalyzeSemantically() {
		for (final ProjectSourceParser parser : TTCN3_PARSERS.values()) {
			final WorkspaceJob job = parser.analyzeAll(false);

			if (job != null) {
				try {
					job.join();
				} catch (final InterruptedException e) {
					ErrorReporter.logExceptionStackTrace(e);
				}
			}
		}

		for (final ProjectConfigurationParser parser : CFG_PARSERS.values()) {
			final WorkspaceJob job = parser.doSyntaticalAnalyze();

			if (job != null) {
				try {
					job.join();
				} catch (final InterruptedException e) {
					ErrorReporter.logExceptionStackTrace(e);
				}
			}
		}
	}

	/**
	 * Force the next semantic analyzation to reanalyze everything.
	 * */
	public static void clearSemanticInformation() {
		for (final ProjectSourceParser parser : TTCN3_PARSERS.values()) {
			parser.clearSemanticInformation();
		}
	}

	public static Set<IProject> getAllAnalyzedProjects() {
		return TTCN3_PARSERS.keySet();
	}
}
