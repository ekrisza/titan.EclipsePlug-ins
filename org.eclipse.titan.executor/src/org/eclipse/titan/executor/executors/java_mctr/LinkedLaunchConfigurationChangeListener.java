/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.executor.executors.java_mctr;

import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationListener;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.internal.core.groups.GroupLaunchConfigurationDelegate;
import org.eclipse.debug.internal.core.groups.GroupLaunchElement;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.titan.common.logging.ErrorReporter;
import org.eclipse.titan.executor.executors.LaunchConfigurationUtil;

/**
 * Follows the modifications of the Java app and launch group configurations that are linked to
 * the Titan native Java main controller by implementing the {@code ILaunchConfigurationListener}
 * @see org.eclipse.debug.core.ILaunchConfigurationListener
 * @author Adam Knapp
 * */
@SuppressWarnings("restriction")
public final class LinkedLaunchConfigurationChangeListener implements ILaunchConfigurationListener {

	/**
	 * Default constructor that also registers this listener
	 * @see org.eclipse.debug.core.ILaunchManager#addLaunchConfigurationListener
	 */
	public LinkedLaunchConfigurationChangeListener() {
		LaunchConfigurationUtil.getLaunchManager().addLaunchConfigurationListener(this);
	}

	/**
	 * Deregisters this listener
	 * @see org.eclipse.debug.core.ILaunchManager#removeLaunchConfigurationListener
	 */
	public void deregister() {
		LaunchConfigurationUtil.getLaunchManager().removeLaunchConfigurationListener(this);
	}

	@Override
	public void launchConfigurationAdded(ILaunchConfiguration configuration) {
		final ILaunchConfiguration original = LaunchConfigurationUtil.getLaunchManager().getMovedFrom(configuration);
		ILaunchConfigurationType type = null;
		try {
			type = configuration.getType();
			if (!checkType(type)) {
				return;
			}
		} catch (CoreException e) {
			ErrorReporter.logExceptionStackTrace(e);
			return;
		}
		final ILaunchConfigurationType javaMtcrType = LaunchConfigurationUtil.getJavaMctrLaunchConfigurationType();
		if (javaMtcrType == null) {
			ErrorReporter.logError("Cannot find Titan Native Java launch configuration type");
			return;
		}
		// If the renamed configuration is not available from the Launch Manager, try to look for it
		if (original == null) {
			if (type != LaunchConfigurationUtil.getJavaAppLaunchConfigurationType()) {
				return;
			}
			try {
				for (ILaunchConfiguration config : LaunchConfigurationUtil.getLaunchManager().getLaunchConfigurations(javaMtcrType)) {
					if (!equalsProject(config, configuration)) {
						continue;
					}
					final String linkedJavaAppConfigName = LaunchConfigurationUtil.getLinkedJavaAppLaunchConfigurationName(config);
					final String linkedGroupConfigName = LaunchConfigurationUtil.getLinkedGroupLaunchConfigurationName(config);
					if (linkedJavaAppConfigName.isEmpty() || linkedGroupConfigName.isEmpty()) {
						return;
					}
					final ILaunchConfiguration groupConfig = LaunchConfigurationUtil.findGroupLaunchConfigurationByName(linkedGroupConfigName);
					if (groupConfig == null) {
						return;
					}
					List<GroupLaunchElement> configList = GroupLaunchConfigurationDelegate.createLaunchElements(groupConfig);
					if (configList.size() < 2 && !configList.get(0).name.equals(config.getName())) {
						ErrorReporter.logError("Erroneous linking from: " + config.getName() + " to " + groupConfig.getName());
						return;
					}
					if (!configList.get(1).name.equals(linkedJavaAppConfigName)) {
						List<String> linkedList = LaunchConfigurationUtil.getLinkedLaunchConfigurations(config);
						linkedList.set(0, configuration.getName());
						ILaunchConfigurationWorkingCopy wc = config.getWorkingCopy();
						LaunchConfigurationUtil.setLinkedLaunchConfigurations(wc, linkedList);
						wc.doSave();
					}
				}
			} catch (CoreException e) {
				ErrorReporter.logExceptionStackTrace(e);
			}
		// Simpler way: the renamed configuration is received
		} else {
			try {
				for (ILaunchConfiguration config : LaunchConfigurationUtil.getLaunchManager().getLaunchConfigurations(javaMtcrType)) {
					boolean updated = false;
					List<String> linkedList = LaunchConfigurationUtil.getLinkedLaunchConfigurations(config);
					for (int i = 0; i < linkedList.size(); i++) {
						if (linkedList.get(i).equals(original.getName())) {
							updated = true;
							linkedList.set(i, configuration.getName());
						}
					}
					if (updated) {
						ILaunchConfigurationWorkingCopy wc = config.getWorkingCopy();
						LaunchConfigurationUtil.setLinkedLaunchConfigurations(wc, linkedList);
						wc.doSave();
					}
				}
			} catch (CoreException e) {
				ErrorReporter.logExceptionStackTrace(e);
			}
		}
	}

	@Override
	public void launchConfigurationChanged(ILaunchConfiguration configuration) {
	}

	@Override
	public void launchConfigurationRemoved(ILaunchConfiguration configuration) {
		final ILaunchConfigurationType javaMtcrType = LaunchConfigurationUtil.getJavaMctrLaunchConfigurationType();
		if (javaMtcrType == null) {
			ErrorReporter.logError("Cannot find Titan Native Java launch configuration type");
			return;
		}
		try {
			for (ILaunchConfiguration config : LaunchConfigurationUtil.getLaunchManager().getLaunchConfigurations(javaMtcrType)) {
				boolean removed = false;
				List<String> linkedList = LaunchConfigurationUtil.getLinkedLaunchConfigurations(config);
				for (int i = 0; i < linkedList.size(); i++) {
					if (linkedList.get(i).equals(configuration.getName())) {
						linkedList.set(i, "");
						removed = true;
						break;
					}
				}
				if (removed) {
					ILaunchConfigurationWorkingCopy wc = config.getWorkingCopy();
					LaunchConfigurationUtil.setLinkedLaunchConfigurations(wc, linkedList);
					wc.doSave();
				}
			}
		} catch (CoreException e) {
			ErrorReporter.logExceptionStackTrace(e);
		}
	}

	/**
	 * Checks whether the specific type is Java app. launch configuration or launch group configuration type
	 * @param configuration Type to check
	 * @return true, if the type is Java app. launch configuration or launch group configuration, false otherwise
	 * @throws CoreException
	 */
	private boolean checkType(final ILaunchConfigurationType type) {
		return type != null && (type == LaunchConfigurationUtil.getJavaAppLaunchConfigurationType() ||
				type == LaunchConfigurationUtil.getGroupLaunchConfigurationType());
	}
	
	/**
	 * Check whether the two specific configuration are related to the same project 
	 * @param config1 1st configuration
	 * @param config2 2nd configuration
	 * @return true, if the two specific configuration are related to the same project, false otherwise
	 * @throws CoreException
	 */
	private boolean equalsProject(final ILaunchConfiguration config1, final ILaunchConfiguration config2) throws CoreException {
		if (config1 == null || config2 == null) {
			return false;
		}
		final IResource[] resources1 = config1.getMappedResources();
		if (resources1 == null || resources1.length == 0) {
			return false;
		}
		final IResource[] resources2 = config2.getMappedResources();
		if (resources2 == null || resources2.length == 0) {
			return false;
		}
		final String projectName1 = config1.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "");
		final String projectName2 = config2.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "");
		for (final IResource resource1 : resources1) {
			for (final IResource resource2 : resources2) {
				if (resource1.getType() == IResource.PROJECT && resource2.getType() == IResource.PROJECT) {
						return resource1.equals(resource2);
				}
				if (resource1.getType() == IResource.PROJECT && !projectName2.isEmpty()) {
					return resource1.getName().equals(projectName2);
				}
				if (resource2.getType() == IResource.PROJECT && !projectName1.isEmpty()) {
					return resource2.getName().equals(projectName1);
				}
				if (!projectName1.isEmpty() && !projectName2.isEmpty()) {
					return projectName1.equals(projectName2);
				}
			}
		}
		return false;
	}
}
