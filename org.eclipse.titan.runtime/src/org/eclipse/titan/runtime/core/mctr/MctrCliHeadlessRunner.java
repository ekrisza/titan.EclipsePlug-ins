/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.runtime.core.mctr;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import java.util.Map;

public final class MctrCliHeadlessRunner implements IApplication {
	
	private MainController mainController;
	
	@Override
	public Object start(IApplicationContext context) throws Exception {
		final String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		final Cli userInterface = new Cli();
		final int max_ptcs = -1;

		mainController = new MainController();
		userInterface.setMainController(mainController);
		mainController.initialize(userInterface, max_ptcs);

		userInterface.enterLoop(args);
		
		return EXIT_OK;
	}

	@Override
	public void stop() {
		mainController.terminate();
	}

}
