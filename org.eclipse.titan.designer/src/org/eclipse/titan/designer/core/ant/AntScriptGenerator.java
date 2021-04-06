/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.core.ant;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.jarpackagerfat.FatJarRsrcUrlBuilder;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.titan.common.logging.ErrorReporter;
import org.eclipse.titan.common.path.PathUtil;
import org.eclipse.titan.common.utils.CommentUtils;
import org.eclipse.titan.common.utils.FileUtils;
import org.eclipse.titan.common.utils.StringUtils;
import org.eclipse.titan.designer.GeneralConstants;
import org.eclipse.titan.designer.compiler.ProjectSourceCompiler;
import org.eclipse.titan.designer.core.JavaRuntimeHelper;
import org.eclipse.titan.designer.properties.data.MakefileCreationData;
import org.eclipse.titan.designer.properties.data.ProjectBuildPropertyData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Utility class for generating Ant script for building JAR.
 * @author Adam Knapp
 */
@SuppressWarnings("restriction")
public final class AntScriptGenerator {
	public static final String BUILD_XML_NAME = "jarbuild.xml";
	private static final String BUILD_TARGET = "jar";
	private static final String	MCTR_CLI_MAIN = "org.eclipse.titan.runtime.core.mctr.CliMain";
	private static final String REQUIRED_ANT_VERSION_TEXT = "ANT 1.7 is required";
	private static final String INDENT_SPACES = "    ";
	private static final String DOUBLE_INDENT_SPACES = INDENT_SPACES + INDENT_SPACES;
	/** @see org.eclipse.jdt.internal.ui.jarpackagerfat.JIJConstants */
	private static final String REDIRECTED_CLASS_PATH_MANIFEST_NAME = "Rsrc-Class-Path";
	/** @see org.eclipse.jdt.internal.ui.jarpackagerfat.JIJConstants */
	private static final String REDIRECTED_MAIN_CLASS_MANIFEST_NAME = "Rsrc-Main-Class";
	/** @see org.eclipse.jdt.internal.ui.jarpackagerfat.JIJConstants */
	private static final String CURRENT_DIR = "./";
	/** @see org.eclipse.jdt.internal.ui.jarpackagerfat.JIJConstants */
	private static final String LOADER_MAIN_CLASS = "org.eclipse.jdt.internal.jarinjarloader.JarRsrcLoader";
	
	public enum PackageType {
		EXTRACT, PACKAGE;
		
		public static PackageType getDefault() {
			return EXTRACT;
		}
	}

	/** @see org.eclipse.jdt.internal.ui.jarpackagerfat.FatJarAntExporter */
	private static class SourceInfo {
		public final boolean isJar;
		public final String absPath;

		public SourceInfo(boolean isJar, String absPath) {
			this.isJar = isJar;
			this.absPath = absPath;
		}
	}

	/**
	 * Converts the array of class paths into array of SourceInfo objects
	 * @param classpath Array of class paths
	 * @return Array of SourceInfo objects
	 */
	private static SourceInfo[] convert(IPath[] classpath) {
		SourceInfo[] result = new SourceInfo[classpath.length];
		for (int i = 0; i < classpath.length; i++) {
			IPath path = classpath[i];
			if (path != null) {
				if (path.toFile().isDirectory()) {
					result[i] = new SourceInfo(false, path.toString());
				} else if (path.toFile().isFile() && path.getFileExtension().equals("jar")) {
					result[i] = new SourceInfo(true, path.toString());
				}
			}
		}

		return result;
	}

	/**
	 * Copies the {@code jar-in-jar-loader.zip} file into the project's {@code java_bin} folder
	 * @param project Project where {@code jar-in-jar-loader.zip} is required
	 * @throws CoreException
	 * @throws IOException
	 */
	private static void copyJarInJarLoader(IProject project) throws CoreException, IOException {
		final String pathString = GeneralConstants.JAVA_TEMP_DIR + File.separator + FatJarRsrcUrlBuilder.JAR_RSRC_LOADER_ZIP;
		final IFile zipFile = project.getFile(new Path(pathString));
		if (zipFile.exists()) {
			return;
		}
		IFolder tempFolder = project.getFolder(new Path(GeneralConstants.JAVA_TEMP_DIR));
		FileUtils.createDir(tempFolder);
		final URI zipURI = URIUtil.toURI(zipFile.getLocation());
		if (zipURI == null) {
			throw new IOException("Path error: " + pathString);
		}
		InputStream is = JavaPlugin.getDefault().getBundle().getEntry(FatJarRsrcUrlBuilder.JAR_RSRC_LOADER_ZIP).openStream();
		OutputStream os = new FileOutputStream(new File(zipURI));
		byte[] buf = new byte[1024];
		while (true) {
			int cnt = is.read(buf);
			if (cnt <= 0)
				break;
			os.write(buf, 0, cnt);
		}
		os.close();
	}

	/**
	 * Checks whether the jarbuild.xml is already exist
	 * @param project Project where jarbuild.xml is required
	 * @return Returns whether the jarbuild.xml is already exist
	 */
	public static boolean existsBuildXML(final IProject project) {
		if (project == null) {
			return false;
		}
		final IFile buildFile = project.getFile(BUILD_XML_NAME);
		return buildFile.exists();
	}

	/**
	 * Creates and then stores the {@code jarbuild.xml} ANT script for the specified project's root 
	 * @param project Project where the ANT script is required
	 * @param isUpdated Whether the file path was modified on the UI
	 * @return {@code true} if the generation was completed or {@code false} if error occurred during the process
	 */
	public static boolean generateAndStoreBuildXML(final IProject project, final boolean isUpdated) {
		return generateAndStoreBuildXML(project, isUpdated, PackageType.getDefault());
	}

	/**
	 * Creates and then stores the {@code jarbuild.xml} ANT script for the specified project's root 
	 * @param project Project where the ANT script is required
	 * @param isUpdated Whether the file path was modified on the UI
	 * @param pt Whether the class files are extracted and re-packed from JAR dependencies or 
	 * 			 JAR dependencies included in the generated JAR as they are
	 * @return {@code true} if the generation was completed or {@code false} if error occurred during the process
	 */
	public static boolean generateAndStoreBuildXML(final IProject project, final boolean isUpdated, final PackageType pt) {
		if (project == null) {
			return false;
		}
		if (existsBuildXML(project) && !isUpdated) {
			return true;
		}
		try {
			final Document content = generateBuildXML(project, pt);
			if (content == null) {
				return false;
			}
			storeBuildXML(project, content);
			switch (pt) {
			case PACKAGE:
				copyJarInJarLoader(project);
				break;
			default:
				break;
			}
		} catch (CoreException e) {
			ErrorReporter.logExceptionStackTrace(e);
			return false;
		} catch (IOException e) {
			ErrorReporter.logExceptionStackTrace(e);
			return false;
		}
		return true;
	}

	/**
	 * Generates the {@code jarbuild.xml} ANT script for the specified project
	 * @param project Project where the ANT script is required
	 * @param pt Whether the class files are extracted and re-packed from JAR dependencies or 
	 * 			 JAR dependencies included in the generated JAR as they are
	 * @return DOM document containing the ANT script 
	 * @throws CoreException
	 * @see org.eclipse.jdt.internal.ui.jarpackagerfat.FatJarRsrcUrlAntExporter#buildANTScript
	 */
	public static Document generateBuildXML(final IProject project, final PackageType pt) throws CoreException {
		if (project == null) {
			return null;
		}
		final String jarPathString = project.getPersistentProperty(new QualifiedName(ProjectBuildPropertyData.QUALIFIER,
				MakefileCreationData.TARGET_EXECUTABLE_PROPERTY));
		if (StringUtils.isNullOrEmpty(jarPathString)) {
			ErrorReporter.INTERNAL_ERROR("Jar file is null or empty");
			return null;
		}
		final File jarFile = new File(jarPathString);
		final String jarFolder = jarFile.getParent();
		final String jarFileName = jarFile.getName();
		if (StringUtils.isNullOrEmpty(jarFolder) || StringUtils.isNullOrEmpty(jarFileName)) {
			ErrorReporter.INTERNAL_ERROR("Jar file is null or empty");
			return null;
		}
		final ILaunchConfiguration config = JavaAppLaunchConfigGenerator.findLaunchConfiguration(project);
		if (config == null) {
			ErrorReporter.parallelErrorDisplayInMessageDialog(
					"Error while generating the ANT script 'jarbuild.xml' for project",
					"No suitable launch configuration is found! Create launch configuration by " +
					"selecting 'Run As' in the pop up menu");
			return null;
		}
		final SourceInfo[] sourceInfos = convert(getClasspath(config));
		JavaAppLaunchConfigGenerator.deleteTemporaryJavaAppLaunchConfiguration(project);
		DocumentBuilder docBuilder = null;
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(false);
		try {
			docBuilder = factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			ErrorReporter.INTERNAL_ERROR("Could not get XML builder");
			return null;
		}

		Document document = docBuilder.newDocument();

		final String headerComment = CommentUtils.getHeaderComments(" ", GeneralConstants.VERSION_STRING).replace("for", DOUBLE_INDENT_SPACES + "for")
				+ "\n " + DOUBLE_INDENT_SPACES + REQUIRED_ANT_VERSION_TEXT + "\n " + DOUBLE_INDENT_SPACES + CommentUtils.DO_NOT_EDIT_TEXT + " ";
		Node comment = document.createComment(headerComment);
		document.appendChild(comment);

		Element projectElement = document.createElement("project");
		projectElement.setAttribute("name", project.getName());
		projectElement.setAttribute("default", BUILD_TARGET);
		projectElement.setAttribute("basedir", ".");
		projectElement.appendChild(comment);
		document.appendChild(projectElement);

		Element property = document.createElement("property");
		property.setAttribute("name", "project");
		property.setAttribute("value", project.getName());
		projectElement.appendChild(property);

		property = document.createElement("property");
		property.setAttribute("name", "dir.build");
		property.setAttribute("value", GeneralConstants.JAVA_BUILD_DIR);
		projectElement.appendChild(property);

		property = document.createElement("property");
		property.setAttribute("name", "dir.jar");
		property.setAttribute("value", jarFolder);
		projectElement.appendChild(property);

		property = document.createElement("property");
		property.setAttribute("name", "version");
		property.setAttribute("value", "1.0");
		projectElement.appendChild(property);

		property = document.createElement("property");
		property.setAttribute("name", "main-class");
		property.setAttribute("value", ProjectSourceCompiler.getPackageGeneratedRoot(project) + ".Parallel_main");
		projectElement.appendChild(property);

		Element target = document.createElement("target");
		target.setAttribute("name", BUILD_TARGET);
		target.setAttribute("description", "generate the JAR file");
		projectElement.appendChild(target);

		Element buildNumber = document.createElement("buildnumber");
		target.appendChild(buildNumber);

		comment = document.createComment(" Create the directory for JAR ");
		target.appendChild(comment);

		Element makeDir = document.createElement("mkdir");
		makeDir.setAttribute("dir", "${dir.jar}");
		target.appendChild(makeDir);

		final String parametrizedJarString = "${dir.jar}/" + jarFileName;
		Element jar = document.createElement("jar");
		jar.setAttribute("destfile", parametrizedJarString);
		switch (pt) {
		case EXTRACT:
			jar.setAttribute("filesetmanifest", "mergewithoutmain");
			break;
		default:
			break;
		}
		target.appendChild(jar);

		Element manifest = document.createElement("manifest");
		jar.appendChild(manifest);

		Element attribute = document.createElement("attribute");
		attribute.setAttribute("name", "Main-Class");
		switch (pt) {
		case EXTRACT:
			attribute.setAttribute("value", "${main-class}");
			break;
		case PACKAGE:
			attribute.setAttribute("value", LOADER_MAIN_CLASS);
			break;
		default:
			break;
		}
		manifest.appendChild(attribute);

		switch (pt) {
		case PACKAGE:
			attribute = document.createElement("attribute");
			attribute.setAttribute("name", REDIRECTED_MAIN_CLASS_MANIFEST_NAME);
			attribute.setAttribute("value", "${main-class}");
			manifest.appendChild(attribute);
			attribute = document.createElement("attribute");
			attribute.setAttribute("name", REDIRECTED_CLASS_PATH_MANIFEST_NAME);
			StringBuilder rsrcClassPath= new StringBuilder();
			rsrcClassPath.append(CURRENT_DIR);
			for (SourceInfo sourceInfo : sourceInfos) {
				if (sourceInfo.isJar) {
					rsrcClassPath.append(" ").append(new File(sourceInfo.absPath).getName());
				}
			}
			attribute.setAttribute("value", rsrcClassPath.toString());
			manifest.appendChild(attribute);
			break;
		default:
			break;
		}

		attribute = document.createElement("attribute");
		attribute.setAttribute("name", "Class-Path");
		attribute.setAttribute("value", ".");
		manifest.appendChild(attribute);

		switch (pt) {
		case PACKAGE:
			Element zipfileset = document.createElement("zipfileset");
			zipfileset.setAttribute("src", GeneralConstants.JAVA_TEMP_DIR + "/" + FatJarRsrcUrlBuilder.JAR_RSRC_LOADER_ZIP);
			jar.appendChild(zipfileset);
			break;
		default:
			break;
		}

		for (SourceInfo sourceInfo : sourceInfos) {
			if (sourceInfo.isJar) {
				final File sourceJarFile = new File(sourceInfo.absPath);
				final String relPath = PathUtil.getRelativePath(project.getLocation().toOSString(), sourceJarFile.getParent()).replace("\\", "/");
				Element fileset = document.createElement("zipfileset");
				switch (pt) {
				case EXTRACT:
					fileset.setAttribute("src", relPath + "/" +sourceJarFile.getName());
					fileset.setAttribute("excludes", "META-INF/*.SF");
					break;
				case PACKAGE:
					fileset.setAttribute("dir", relPath);
					fileset.setAttribute("includes", sourceJarFile.getName());
					break;
				default:
					break;
				}
				jar.appendChild(fileset);
			} else {
				Element fileset = document.createElement("fileset");
				final String relPath = PathUtil.getRelativePath(project.getLocation().toOSString(), sourceInfo.absPath).replace("\\", "/");
				fileset.setAttribute("dir", relPath);
				jar.appendChild(fileset);
			}
		}

		return document;
	}

	/**
	 * Gets the class paths based on the specified launch configuration
	 * @param configuration Launch configuration of Java application type
	 * @return The array of class paths
	 * @throws CoreException
	 * @see org.eclipse.jdt.internal.ui.jarpackagerfat.FatJarAntExporter#getClasspath
	 */
	private static IPath[] getClasspath(ILaunchConfiguration configuration) throws CoreException {
		IRuntimeClasspathEntry[] entries = JavaRuntime.computeUnresolvedRuntimeClasspath(configuration);
		entries = JavaRuntime.resolveRuntimeClasspath(entries, configuration);

		final ArrayList<IPath> userEntries = new ArrayList<IPath>(entries.length);
		final boolean isModularConfig = JavaRuntimeHelper.isModularConfiguration(configuration);
		for (final IRuntimeClasspathEntry cpentry : entries) {
			final int classPathProperty= cpentry.getClasspathProperty();
			if ((!isModularConfig && classPathProperty == IRuntimeClasspathEntry.USER_CLASSES)
					|| (isModularConfig && (classPathProperty == JavaRuntimeHelper.CLASS_PATH || classPathProperty == JavaRuntimeHelper.MODULE_PATH))) {
				final String location = cpentry.getLocation();
				if (location != null) {
					final IPath entry = Path.fromOSString(location);
					if (!userEntries.contains(entry)) {
						userEntries.add(entry);
					}
				}
			}
		}
		return userEntries.toArray(new IPath[userEntries.size()]);
	}

	/**
	 * Stores the {@code jarbuild.xml} ANT script in the specified project's root
	 * @param project Project where the ANT script is required
	 * @param document DOM document containing the ANT script
	 */
	public static void storeBuildXML(final IProject project, final Document document) throws IOException {
		if (project == null || document == null) {
			return;
		}
		try {
			final String buildXMLPath = project.getLocation().toOSString() + File.separator + BUILD_XML_NAME;
			FileOutputStream outputStream = new FileOutputStream(buildXMLPath);
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.METHOD, "xml");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			DOMSource source = new DOMSource(document);
			StreamResult result = new StreamResult(outputStream);
			transformer.transform(source, result);
		} catch (TransformerException e) {
			throw new IOException("Could not transform to XML");
		}
	}
}
