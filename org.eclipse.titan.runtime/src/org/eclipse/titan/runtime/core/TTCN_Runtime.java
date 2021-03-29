/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.runtime.core;

import java.lang.Thread.State;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.titan.runtime.core.TTCN_Logger.Severity;
import org.eclipse.titan.runtime.core.TitanLoggerApi.ExecutorComponent_reason;
import org.eclipse.titan.runtime.core.TitanLoggerApi.ParPort_operation;
import org.eclipse.titan.runtime.core.TitanLoggerApi.ParallelPTC_reason;
import org.eclipse.titan.runtime.core.TitanPort.Map_Params;
import org.eclipse.titan.runtime.core.TitanPort.translation_port_state;
import org.eclipse.titan.runtime.core.TitanVerdictType.VerdictTypeEnum;

/**
 * TTCN-3 runtime class
 *
 * TODO: lots to implement
 * TODO: reorganize according to .hh
 *
 * INFO: the current threads + threadlocals are temporary structure to get enough functionality to work.
 * Once the business logic is running stable, we will experiment with this a bit more.
 *
 * @author Kristof Szabados
 */
public final class TTCN_Runtime {
	public static final int TTCN3_MAJOR = 7;
	public static final int TTCN3_MINOR = 2;
	public static final int TTCN3_PATCHLEVEL = 2;
	public static final int TTCN3_BUILDNUMBER = 0;

	public static final String VERSION_STRING = "7.2.2";
	public static final String PRODUCT_NUMBER = VERSION_STRING;

	public enum executorStateEnum {
		UNDEFINED_STATE,

		SINGLE_CONTROLPART, SINGLE_TESTCASE,

		HC_INITIAL, HC_IDLE, HC_CONFIGURING, HC_ACTIVE, HC_OVERLOADED,  HC_OVERLOADED_TIMEOUT, HC_EXIT,

		MTC_INITIAL, MTC_IDLE, MTC_CONTROLPART, MTC_TESTCASE,
		MTC_TERMINATING_TESTCASE, MTC_TERMINATING_EXECUTION, MTC_PAUSED,
		MTC_CREATE, MTC_START, MTC_STOP, MTC_KILL, MTC_RUNNING, MTC_ALIVE,
		MTC_DONE, MTC_KILLED, MTC_CONNECT, MTC_DISCONNECT, MTC_MAP, MTC_UNMAP,
		MTC_CONFIGURING, MTC_EXIT,

		PTC_INITIAL, PTC_IDLE, PTC_FUNCTION, PTC_CREATE, PTC_START, PTC_STOP,
		PTC_KILL, PTC_RUNNING, PTC_ALIVE, PTC_DONE, PTC_KILLED, PTC_CONNECT,
		PTC_DISCONNECT, PTC_MAP, PTC_UNMAP, PTC_STOPPED, PTC_EXIT
	}
	private static ThreadLocal<executorStateEnum> executorState = new ThreadLocal<TTCN_Runtime.executorStateEnum>() {
		@Override
		protected executorStateEnum initialValue() {
			return executorStateEnum.UNDEFINED_STATE;
		}
	};

	private static ThreadLocal<String> component_type_module = new ThreadLocal<String>();
	private static ThreadLocal<String> component_type_name = new ThreadLocal<String>();
	private static ThreadLocal<String> system_type_module = new ThreadLocal<String>();
	private static ThreadLocal<String> system_type_name = new ThreadLocal<String>();
	private static ThreadLocal<String> component_name = new ThreadLocal<String>();
	private static ThreadLocal<Boolean> is_alive = new ThreadLocal<Boolean>() {
		@Override
		protected Boolean initialValue() {
			return Boolean.FALSE;
		}
	};

	private static String control_module_name = null;

	//originally testcase_name
	private static ThreadLocal<String> testcaseModuleName = new ThreadLocal<String>();
	private static ThreadLocal<String> testcaseDefinitionName = new ThreadLocal<String>();

	private static ThreadLocal<Double> startTime = new ThreadLocal<Double>() {
		@Override
		protected Double initialValue() {
			return Double.valueOf(0.0);
		}
	};

	private static ThreadLocal<VerdictTypeEnum> localVerdict = new ThreadLocal<TitanVerdictType.VerdictTypeEnum>() {
		@Override
		protected VerdictTypeEnum initialValue() {
			return VerdictTypeEnum.NONE;
		}
	};
	private static int verdictCount[] = new int[] {0,0,0,0,0};
	private static int controlErrorCount = 0;
	private static ThreadLocal<String> verdictReason = new ThreadLocal<String>() {
		@Override
		protected String initialValue() {
			return "";
		}
	};

	//in the compiler in_ttcn_try_block
	private static ThreadLocal<Integer> ttcn_try_block_counter = new ThreadLocal<Integer>() {
		@Override
		protected Integer initialValue() {
			return 0;
		}
	};

	private static ThreadLocal<Integer> create_done_killed_compref = new ThreadLocal<Integer>() {
		@Override
		protected Integer initialValue() {
			return TitanComponent.NULL_COMPREF;
		}
	};
	private static ThreadLocal<Boolean> running_alive_result = new ThreadLocal<Boolean>() {
		@Override
		protected Boolean initialValue() {
			return Boolean.FALSE;
		}
	};

	//MTC only static information
	private static TitanAlt_Status any_component_done_status = TitanAlt_Status.ALT_UNCHECKED;
	private static TitanAlt_Status all_component_done_status = TitanAlt_Status.ALT_UNCHECKED;
	private static TitanAlt_Status any_component_killed_status = TitanAlt_Status.ALT_UNCHECKED;
	private static TitanAlt_Status all_component_killed_status = TitanAlt_Status.ALT_UNCHECKED;

	private static class component_status_table_struct {
		TitanAlt_Status done_status;
		TitanAlt_Status killed_status;
		TitanVerdictType.VerdictTypeEnum local_verdict;
		String return_type;
		Text_Buf return_value;
	}

	private static ThreadLocal<Integer> component_status_table_offset = new ThreadLocal<Integer>() {
		@Override
		protected Integer initialValue() {
			return TitanComponent.FIRST_PTC_COMPREF;
		}
	};
	private static ThreadLocal<ArrayList<component_status_table_struct>> component_status_table = new ThreadLocal<ArrayList<TTCN_Runtime.component_status_table_struct>>() {
		@Override
		protected ArrayList<component_status_table_struct> initialValue() {
			return new ArrayList<TTCN_Runtime.component_status_table_struct>();
		}
	};

	// in the compiler the equivalent class is component_process_struct
	private static class component_thread_struct {
		int component_reference;
		Thread thread;
		boolean thread_killed;
	}

	private static HashMap<Integer, component_thread_struct> components_by_compref;
	private static HashMap<Thread, component_thread_struct> components_by_thread;
	private static final int HASHTABLE_SIZE = 97;
	// storing all started threads MTC and all PTC -s
	private static ArrayList<Thread> threads = new ArrayList<Thread>();

	private static ThreadLocal<Integer> translation_count = new ThreadLocal<Integer>() {
		@Override
		protected Integer initialValue() {
			return 0;
		}
	};
	private static ThreadLocal<TitanPort> port = new ThreadLocal<TitanPort>() {
		@Override
		protected TitanPort initialValue() {
			return null;
		}
	};

	private TTCN_Runtime() {
		// private constructor to disable accidental instantiation
	}

	//originally get_state
	public static executorStateEnum get_state() {
		return executorState.get();
	}

	// originally set_state
	public static void set_state(final executorStateEnum newState) {
		executorState.set(newState);
	}

	//originally is_hc
	public static boolean is_hc() {
		switch (executorState.get()) {
		case HC_INITIAL:
		case HC_IDLE:
		case HC_CONFIGURING:
		case HC_ACTIVE:
		case HC_OVERLOADED:
		case HC_OVERLOADED_TIMEOUT:
		case HC_EXIT:
			return true;
		default:
			return false;
		}
	}

	//originally is_mtc
	public static boolean is_mtc() {
		switch (executorState.get()) {
		case MTC_INITIAL:
		case MTC_IDLE:
		case MTC_CONTROLPART:
		case MTC_TESTCASE:
		case MTC_TERMINATING_TESTCASE:
		case MTC_TERMINATING_EXECUTION:
		case MTC_PAUSED:
		case MTC_CREATE:
		case MTC_START:
		case MTC_STOP:
		case MTC_KILL:
		case MTC_RUNNING:
		case MTC_ALIVE:
		case MTC_DONE:
		case MTC_KILLED:
		case MTC_CONNECT:
		case MTC_DISCONNECT:
		case MTC_MAP:
		case MTC_UNMAP:
		case MTC_CONFIGURING:
		case MTC_EXIT:
			return true;
		default:
			return false;
		}
	}

	//originally is_ptc
	public static boolean is_ptc() {
		switch (executorState.get()) {
		case PTC_INITIAL:
		case PTC_IDLE:
		case PTC_FUNCTION:
		case PTC_CREATE:
		case PTC_START:
		case PTC_STOP:
		case PTC_KILL:
		case PTC_RUNNING:
		case PTC_ALIVE:
		case PTC_DONE:
		case PTC_KILLED:
		case PTC_CONNECT:
		case PTC_DISCONNECT:
		case PTC_MAP:
		case PTC_UNMAP:
		case PTC_STOPPED:
		case PTC_EXIT:
			return true;
		default:
			return false;
		}
	}

	//originally is_tc
	public static boolean is_tc() {
		return is_mtc() || is_ptc();
	}

	//originally is_single
	public static boolean is_single() {
		switch (executorState.get()) {
		case SINGLE_CONTROLPART:
		case SINGLE_TESTCASE:
			return true;
		default:
			return false;
		}
	}

	//originally is_undefined
	public static boolean is_undefined() {
		return executorState.get() == executorStateEnum.UNDEFINED_STATE;
	}

	//originally is_idle
	public static boolean is_idle() {
		switch (executorState.get()) {
		case HC_IDLE:
		case HC_ACTIVE:
		case HC_OVERLOADED:
		case MTC_IDLE:
		case PTC_IDLE:
		case PTC_STOPPED:
			return true;
		default:
			return false;
		}
	}

	//originally is_overloaded
	public static boolean is_overloaded() {
		switch (executorState.get()) {
		case HC_OVERLOADED:
		case HC_OVERLOADED_TIMEOUT:
			return true;
		default:
			return false;
		}
	}

	public static void set_port_state(final int state, final String info, final boolean bySystem) {
		if (translation_count.get() > 0) {
			if (port == null) {
				throw new TtcnError("Internal error: TTCN_Runtime.set_port_state: The port is null.");
			} else {
				final int low_end = bySystem ? -1 : 0;
				if (state < low_end || state > 4) {
					translation_count.set(translation_count.get().intValue() - 1);
					throw new TtcnError("The value of the first parameter in the setstate operation must be 0, 1, 2, 3 or 4.");
				}

				final translation_port_state realState = translation_port_state.getByCode(state);
				port.get().change_port_state(realState);
				TTCN_Logger.log_setstate(port.get().get_name(), realState, new TitanCharString(info));
			}
		} else {
			throw new TtcnError("setstate operation was called without being in a translation procedure.");
		}
	}

	public static void set_port_state(final TitanInteger state, final TitanCharString info, final boolean bySystem) {
		set_port_state(state.get_int(), info.get_value().toString(), bySystem);
	}

	public static TitanPort get_translation_port() {
		if (port.get() == null) {
			throw new TtcnError("Operation 'port.getref' was called while not in a port translation procedure.");
		}

		return port.get();
	}

	public static void set_translation_mode(final boolean enabled, final TitanPort p_port) {
		if (enabled) {
			translation_count.set(translation_count.get().intValue() + 1 );
		} else {
			translation_count.set(translation_count.get().intValue() - 1 );
			if (translation_count.get() < 0) {
				translation_count.set(0);
			}
		}

		if (translation_count.get() == 0 || p_port != null) {
			port.set(p_port);
		}
	}

	public static void increase_try_catch_counter() {
		ttcn_try_block_counter.set(ttcn_try_block_counter.get() + 1);
	}

	public static void decrease_try_catch_counter() {
		ttcn_try_block_counter.set(ttcn_try_block_counter.get() - 1);
	}

	public static boolean is_in_ttcn_try_block() {
		return ttcn_try_block_counter.get() > 0;
	}

	//originally in_controlpart
	private static boolean in_controlPart() {
		return executorState.get() == executorStateEnum.SINGLE_CONTROLPART || executorState.get() == executorStateEnum.MTC_CONTROLPART;
	}

	//originally verdict_enabled
	private static boolean verdict_enabled() {
		return executorState.get() == executorStateEnum.SINGLE_TESTCASE || is_mtc() || is_ptc();
	}

	private static void wait_for_state_change() {
		final executorStateEnum oldState = executorState.get();

		do {
			TTCN_Snapshot.take_new(true);
		} while (oldState == executorState.get());
	}



	static void clean_up(){
		component_type_module.set(null);
		component_type_name.set(null);
		system_type_module.set(null);
		system_type_name.set(null);
		component_name.set(null);
		control_module_name = null;
		testcaseModuleName.set(null);
		testcaseDefinitionName.set(null);
	}

	//originally TTCN_Runtime::initialize_component_type
	private static void initialize_component_type() {
		TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.init__component__start, component_type_module.get(), component_type_name.get(), 0, null, TTCN_Runtime.get_testcase_name(), 0, 0);

		Module_List.initialize_component(component_type_module.get(), component_type_name.get(), true);

		TitanPort.set_parameters(TitanComponent.self.get().get_component(), component_name.get());
		TitanPort.all_start();

		TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.init__component__finish, component_type_module.get(), component_type_name.get(), 0, null, null, 0, 0);

		localVerdict.set(VerdictTypeEnum.NONE);
		verdictReason.set("");
	}

	//originally TTCN_Runtime::terminate_component_type
	private static void terminate_component_type() {
		if (component_type_module.get() != null && component_type_name.get() != null) {
			TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.terminating__component, component_type_module.get(), component_type_name.get(), 0, null, null, 0, 0);

			TTCN_Default.deactivate_all();
			TitanTimer.all_stop();
			TitanPort.deactivate_all();

			TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.component__shut__down, component_type_module.get(), component_type_name.get(), 0, null, TTCN_Runtime.get_testcase_name(), 0, 0);

			component_type_module.set(null);
			component_type_name.set(null);
		}
	}

	//originally TTCN_Runtime::set_component_type
	private static void set_component_type(final String par_component_type_module, final String par_component_type_name) {
		if (par_component_type_module == null || par_component_type_module.length() == 0 ||
				par_component_type_name == null || par_component_type_name.length() == 0) {
			throw new TtcnError("Internal error: TTCN_Runtime.set_component_type: Trying to set an invalid component type.");
		}

		if (component_type_module.get() != null && component_type_name.get() != null) {
			throw new TtcnError(MessageFormat.format("Internal error: TTCN_Runtime.set_component_type: Trying to set component type {0}.{1} while another one is active.", par_component_type_module, par_component_type_name));
		}

		component_type_module.set(par_component_type_module);
		component_type_name.set(par_component_type_name);
	}

	//originally TTCN_Runtime::set_system_type
	private static void set_system_type(final String par_system_type_module, final String par_system_type_name) {
		if (par_system_type_module == null || par_system_type_module.length() == 0 ||
				par_system_type_name == null || par_system_type_name.length() == 0) {
			throw new TtcnError("Internal error: TTCN_Runtime.set_system_type: Trying to set an invalid system component type.");
		}

		system_type_module.set(par_system_type_module);
		system_type_name.set(par_system_type_name);
	}

	// originally TTCN_Runtime::set_component_name
	public static void set_component_name(final String new_component_name) {
		if (new_component_name != null && new_component_name.length() > 0) {
			component_name.set(new_component_name);
		} else {
			component_name.set(null);
		}
	}

	//originally set_testcase_name
	private static void set_testcase_name(final String parModuleName, final String parTestcaseName) {
		if (parModuleName == null || parModuleName.length() == 0 ||
				parTestcaseName == null || parTestcaseName.length() == 0) {
			throw new TtcnError("Internal error: TTCN_Runtime.set_testcase_name: Trying to set an invalid testcase name.");
		}

		if (testcaseModuleName.get() != null || testcaseDefinitionName.get() != null) {
			throw new TtcnError(MessageFormat.format("Internal error: TTCN_Runtime.set_testcase_name: Trying to set testcase name {0}.{1} while another one is active.", parModuleName, parTestcaseName));
		}

		testcaseModuleName.set(parModuleName);
		testcaseDefinitionName.set(parTestcaseName);
	}

	public static String get_host_name() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			throw new TtcnError(e);
		}
	}

	//originally get_component_type
	public static String get_component_type() {
		return component_type_name.get();
	}

	public static String get_component_name() {
		return component_name.get();
	}

	public static String get_testcase_name() {
		return testcaseDefinitionName.get();
	}

	public static void set_logger_parameters() {
		TTCN_Logger.set_plugin_parameters(TitanComponent.self.get(), component_name.get());
	}

	//originally get_testcase_id_macro
	public static TitanCharString get_testcase_id_macro() {
		if (in_controlPart()) {
			throw new TtcnError("Macro %%testcaseId cannot be used from the control part outside test cases.");
		}

		if (testcaseDefinitionName.get() == null || testcaseDefinitionName.get().length() == 0) {
			throw new TtcnError("Internal error: Evaluating macro %%testcaseId, but the name of the current testcase is not set.");
		}

		return new TitanCharString(testcaseDefinitionName.get());
	}

	//originally get_testcasename
	public static TitanCharString get_testcasename() {
		if (in_controlPart() || is_hc()) {
			return new TitanCharString("");
		}

		if (testcaseDefinitionName.get() == null || testcaseDefinitionName.get().length() == 0) {
			throw new TtcnError("Internal error: Evaluating predefined function testcasename(), but the name of the current testcase is not set.");
		}

		return new TitanCharString(testcaseDefinitionName.get());
	}

	/**
	 * Calculates and returns the number of seconds elapsed since the testcase began.
	 *
	 * @return the time passed since the testcase was started.
	 * */
	public static TitanFloat now() {
		if (startTime.get() == 0.0) {
			throw new TtcnError("Accessing the test system time while no test case is running.");
		}

		final double current_Time = System.currentTimeMillis() / 1000.0;

		return new TitanFloat(current_Time - startTime.get());
	}

	public static int hc_main(final String local_addr, final String MC_host, final int MC_port) {
		int returnValue = 0;
		TTCN_Runtime.set_state(executorStateEnum.HC_INITIAL);
		TTCN_Logger.log_hc_start(get_host_name());
		TTCN_Logger.write_logger_settings();

		try {
			// FIXME implement
			TTCN_Communication.set_mc_address(MC_host, MC_port);
			TTCN_Communication.connect_mc();

			if (Usage_Stats.USAGE_STAT_SENDING) {
				Module_List.send_usage_stats();
			}
			executorState.set(executorStateEnum.HC_IDLE);
			TTCN_Communication.send_version();
			initialize_component_process_tables();

			do {
				TTCN_Snapshot.take_new(true);
				TTCN_Communication.process_all_messages_hc();
			} while (executorState.get().ordinal() >= executorStateEnum.HC_IDLE.ordinal() && executorState.get().ordinal() < executorStateEnum.HC_EXIT.ordinal());

			if (executorState.get() == executorStateEnum.HC_EXIT) {
				TTCN_Communication.disconnect_mc();
				clean_up();
			}
		} catch (final TtcnError error) {
			returnValue = -1;
			clean_up();
		}
		//FIXME implement

		clear_component_process_tables();

		if (is_hc()) {
			TTCN_Logger.log_executor_runtime(TitanLoggerApi.ExecutorRuntime_reason.enum_type.host__controller__finished);
		}

		if (Usage_Stats.USAGE_STAT_SENDING) {
			Module_List.clean_up_usage_stats();
		}

		TTCN_Logger.close_file();

		return returnValue;
	}

	public static int mtc_main() {
		//FIXME implement rest
		int returnValue = 0;

		TTCN_Logger.open_file();
		TTCN_Logger.log_executor_component(TitanLoggerApi.ExecutorComponent_reason.enum_type.mtc__started);

		try {
			TTCN_Communication.connect_mc();
			executorState.set(executorStateEnum.MTC_IDLE);
			TTCN_Communication.send_mtc_created();

			do {
				TTCN_Snapshot.take_new(true);
				TTCN_Communication.process_all_messages_tc();
			} while (executorState.get() != executorStateEnum.MTC_EXIT);

			TTCN_Communication.disconnect_mc();
			clean_up();
		} catch (final TtcnError error) {
			returnValue = -1;
		}

		TTCN_Logger.log_executor_component(TitanLoggerApi.ExecutorComponent_reason.enum_type.mtc__finished);
		TTCN_Logger.close_file();

		return returnValue;
	}

	public static int ptc_main() {
		//FIXME implement logger plugin loading
		int returnValue = 0;

		TTCN_Logger.open_file();
		TTCN_Logger.begin_event(Severity.EXECUTOR_COMPONENT);
		TTCN_Logger.log_event_str(MessageFormat.format("TTCN-3 Parallel Test Component started on {0}. Component reference: ", get_host_name()));
		TitanComponent.self.get().log();
		TTCN_Logger.log_event_str(MessageFormat.format(", component type: {0}.{1}", component_type_module.get(), component_type_name.get()));
		if (component_name != null) {
			TTCN_Logger.log_event_str(MessageFormat.format(", component name: {0}", component_name.get()));
		}
		TTCN_Logger.log_event_str(". Version: " + PRODUCT_NUMBER + '.');
		TTCN_Logger.end_event();
		TTCN_Logger.write_logger_settings();

		//FIXME implement missing parts
		try {
			TTCN_Communication.connect_mc();
			executorState.set(executorStateEnum.PTC_IDLE);
			TTCN_Communication.send_ptc_created(TitanComponent.self.get().componentValue);
			try {
				initialize_component_type();
			} catch (final TtcnError error) {
				TTCN_Logger.log_executor_component(ExecutorComponent_reason.enum_type.component__init__fail);
				returnValue = -1;
			}

			if (returnValue == 0) {
				try {
					do {
						TTCN_Snapshot.take_new(true);
						TTCN_Communication.process_all_messages_tc();
					} while (executorState.get() != executorStateEnum.PTC_EXIT);
				} catch (final TtcnError error) {
					TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.error__idle__ptc, null, null, 0, null, null, 0, 0);
					returnValue = -1;
				}
			}
			if (returnValue != 0) {
				// ignore errors in subsequent operations
				try {
					terminate_component_type();
				} catch (final TtcnError error) {
					//intentionally empty
				}
				try {
					TTCN_Communication.send_killed(localVerdict.get(), verdictReason.get());
				} catch (final TtcnError error) {
					//intentionally empty
				}

				TTCN_Logger.log_final_verdict(true, localVerdict.get(), localVerdict.get(), localVerdict.get(), verdictReason.get(), -1, TitanComponent.UNBOUND_COMPREF, null);
				executorState.set(executorStateEnum.PTC_EXIT);
			}

			TTCN_Communication.disconnect_mc();
			clear_component_status_table();
			clean_up();
		} catch (final TtcnError error) {
			returnValue = -1;
		}

		TTCN_Logger.log_executor_component(ExecutorComponent_reason.enum_type.ptc__finished);
		TTCN_Logger.close_file();

		return returnValue;
	}

	//originally initialize_system_port
	public static void initialize_system_port(final String port_name)
	{
		Module_List.initialize_system_port(system_type_module.get(),
				system_type_name.get(), port_name);
	}

	//originally create_component
	public static int create_component(final String createdComponentTypeModule, final String createdComponentTypeName,
			String createdComponentName, String createdComponentLocation, final boolean createdComponentAlive) {
		if (in_controlPart()) {
			throw new TtcnError("Create operation cannot be performed in the control part.");
		} else if (is_single()) {
			throw new TtcnError("Create operation cannot be performed in single mode.");
		}

		if (createdComponentName != null && createdComponentName.length() == 0) {
			TtcnError.TtcnWarning("Empty charstring value was ignored as component name in create operation.");
			createdComponentName = null;
		}

		if (createdComponentLocation != null && createdComponentLocation.length() == 0) {
			TtcnError.TtcnWarning("Empty charstring value was ignored as component location in create operation.");
			createdComponentLocation = null;
		}

		TTCN_Logger.begin_event(Severity.PARALLEL_UNQUALIFIED);
		TTCN_Logger.log_event_str(MessageFormat.format("Creating new {0}PTC with component type {1}.{2}", createdComponentAlive ? "alive " : "", createdComponentTypeModule, createdComponentTypeName));
		if (createdComponentName != null) {
			TTCN_Logger.log_event_str(MessageFormat.format(", component name: {0}", createdComponentName));
		}
		if (createdComponentLocation != null) {
			TTCN_Logger.log_event_str(MessageFormat.format(", location: {0}", createdComponentName));
		}
		TTCN_Logger.log_char('.');
		TTCN_Logger.end_event();

		switch (executorState.get()) {
		case MTC_TESTCASE:
			executorState.set(executorStateEnum.MTC_CREATE);
			break;
		case PTC_FUNCTION:
			executorState.set(executorStateEnum.PTC_CREATE);
			break;
		default:
			throw new TtcnError("Internal error: Executing create operation in invalid state.");
		}

		TTCN_Communication.send_create_req(createdComponentTypeModule, createdComponentTypeName, createdComponentName, createdComponentLocation, createdComponentAlive, startTime.get());
		if (is_mtc()) {
			// updating the component status flags
			// 'any component.done' and 'any component.killed' might be successful
			// from now since the PTC can terminate by itself
			if (any_component_done_status == TitanAlt_Status.ALT_NO) {
				any_component_done_status = TitanAlt_Status.ALT_UNCHECKED;
			}
			if (any_component_killed_status == TitanAlt_Status.ALT_NO) {
				any_component_killed_status = TitanAlt_Status.ALT_UNCHECKED;
			}
			all_component_killed_status = TitanAlt_Status.ALT_UNCHECKED;
		}

		wait_for_state_change();

		TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.ptc__created, createdComponentTypeModule, createdComponentTypeName, create_done_killed_compref.get().intValue(), createdComponentName, createdComponentLocation, createdComponentAlive ? 1: 0, 0);

		TitanComponent.register_component_name(create_done_killed_compref.get().intValue(), createdComponentName);

		return create_done_killed_compref.get();
	}

	public static void prepare_start_component(final TitanComponent component_reference, final String module_name, final String function_name, final Text_Buf text_buf) {
		if (in_controlPart()) {
			throw new TtcnError("Start test component operation cannot be performed in the control part.");
		} else if (is_single()) {
			throw new TtcnError("Start test component operation cannot be performed in single mode.");
		}
		component_reference.must_bound("Performing a start operation on an unbound component reference.");

		final int compref = component_reference.get_component();
		switch (compref) {
		case TitanComponent.NULL_COMPREF:
			throw new TtcnError("Start operation cannot be performed on the null component reference.");
		case TitanComponent.MTC_COMPREF:
			throw new TtcnError("Start operation cannot be performed on the component reference of MTC.");
		case TitanComponent.SYSTEM_COMPREF:
			throw new TtcnError("Start operation cannot be performed on the component reference of system.");
		case TitanComponent.ANY_COMPREF:
			throw new TtcnError("Internal error: 'any component' cannot be started.");
		case TitanComponent.ALL_COMPREF:
			throw new TtcnError("Internal error: 'all component' cannot be started.");
		default:
			break;
		}

		if (TitanComponent.self.get().get_component() == compref) {
			throw new TtcnError("Start operation cannot be performed on the own component reference of the initiating component (i.e. 'self.start' is not allowed).");
		}

		if (in_component_status_table(compref)) {
			if (get_killed_status(compref) == TitanAlt_Status.ALT_YES) {
				throw new TtcnError(MessageFormat.format("PTC with component reference {0} is not alive anymore. Start operation cannot be performed on it.", compref));
			}

			cancel_component_done(compref);
		}

		TTCN_Communication.prepare_start_req(text_buf, compref, module_name, function_name);
	}

	public static void send_start_component(final Text_Buf text_buf) {
		switch (executorState.get()) {
		case MTC_TESTCASE:
			executorState.set(executorStateEnum.MTC_START);
			break;
		case PTC_FUNCTION:
			executorState.set(executorStateEnum.PTC_START);
			break;
		default:
			throw new TtcnError("Internal error: Executing component start operation in invalid state.");
		}

		TTCN_Communication.send_message(text_buf);
		if (is_mtc()) {
			all_component_done_status = TitanAlt_Status.ALT_UNCHECKED;
		}

		wait_for_state_change();

		TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.function__started, null, null, 0, null, null, 0, 0);
	}

	public static void start_function(final String module_name, final String function_name, final Text_Buf text_buf) {
		switch (executorState.get()) {
		case PTC_IDLE:
		case PTC_STOPPED:
			break;
		default:
			text_buf.cut_message();

			throw new TtcnError("Internal error: Message START arrived in invalid state.");
		}

		try {
			Module_List.start_function(module_name, function_name, text_buf);

			// do nothing: the function terminated normally
			return;
		} catch (TC_End e) {
			// executor_state is already set by stop_execution or kill_execution
			switch (executorState.get()) {
			case PTC_STOPPED:
				TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, MessageFormat.format("Function {0} was stopped. PTC remains alive and is waiting for next start.", function_name));
				// send a STOPPED message without return value
				TTCN_Communication.send_stopped(localVerdict.get(), verdictReason.get());

				// return and do nothing else
				return;
			case PTC_EXIT:
				TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.function__stopped, null, function_name, 0, null, null, 0, 0);
				break;
			default:
				throw new TtcnError("Internal error: PTC was stopped in invalid state.");
			}
		} catch (TtcnError e) {
			TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.function__error, null, function_name, 0, null, null, 0, 0);
			executorState.set(executorStateEnum.PTC_EXIT);
		}

		// the control reaches this code if the PTC has to be terminated
		terminate_component_type();
		// send a STOPPED_KILLED message without return value
		TTCN_Communication.send_stopped_killed(localVerdict.get(), verdictReason.get());
		TTCN_Logger.log_final_verdict(true, localVerdict.get(), localVerdict.get(), localVerdict.get(), verdictReason.get(), -1, TitanComponent.UNBOUND_COMPREF, null);
	}

	public static void function_started(final Text_Buf text_buf) {
		// The buffer still contains the incoming START message.
		text_buf.cut_message();

		executorState.set(executorStateEnum.PTC_FUNCTION);
		// The remaining messages must be processed now.
		TTCN_Communication.process_all_messages_tc();
	}

	public static void prepare_function_finished(final String return_type, final Text_Buf text_buf) {
		if (executorState.get() != executorStateEnum.PTC_FUNCTION) {
			throw new TtcnError("Internal error: PTC behaviour function finished in invalid state.");
		}

		if (is_alive.get()) {
			// Prepare a STOPPED message with the current verdict and possible return value.
			TTCN_Communication.prepare_stopped(text_buf, localVerdict.get(), return_type, verdictReason.get());
		} else {
			terminate_component_type();
			TTCN_Communication.prepare_stopped_killed(text_buf, localVerdict.get(), return_type, verdictReason.get());
		}
	}

	public static void send_function_finished(final Text_Buf text_buf) {
		// send out the STOPPED or STOPPED_KILLED message, which is already
		// complete and contains the return value
		TTCN_Communication.send_message(text_buf);
		if (is_alive.get()) {
			executorState.set(executorStateEnum.PTC_STOPPED);
		} else {
			TTCN_Logger.log_final_verdict(true, localVerdict.get(), localVerdict.get(), localVerdict.get(), verdictReason.get(), -1, TitanComponent.UNBOUND_COMPREF, null);
			executorState.set(executorStateEnum.PTC_EXIT);
		}
	}

	public static void function_finished(final String function_name) {
		TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.function__finished, null, function_name, 0, null, null, is_alive.get() ? 1 : 0, 0);

		final Text_Buf text_buf = new Text_Buf();
		prepare_function_finished(null, text_buf);
		send_function_finished(text_buf);
	}

	//originally component_done, with component parameter
	public static TitanAlt_Status component_done(final int component_reference, final AtomicReference<VerdictTypeEnum> ptc_verdict) {
		if (in_controlPart()) {
			throw new TtcnError("Done operation cannot be performed in the control part.");
		}

		switch (component_reference) {
		case TitanComponent.NULL_COMPREF:
			throw new TtcnError("Done operation cannot be performed on the null component reference.");
		case TitanComponent.MTC_COMPREF:
			throw new TtcnError("Done operation cannot be performed on the component reference of MTC.");
		case TitanComponent.SYSTEM_COMPREF:
			throw new TtcnError("Done operation cannot be performed on the component reference of system.");
		case TitanComponent.ANY_COMPREF:
			return any_component_done();
		case TitanComponent.ALL_COMPREF:
			return all_component_done();
		default:
			return ptc_done(component_reference, ptc_verdict);
		}
	}

	//originally component_done, with component parameter
	public static TitanAlt_Status component_done(final int component_reference, final String return_type, final AtomicReference<Text_Buf> text_buf) {
		if (in_controlPart()) {
			throw new TtcnError("Done operation cannot be performed in the control part.");
		}

		switch (component_reference) {
		case TitanComponent.NULL_COMPREF:
			throw new TtcnError("Done operation cannot be performed on the null component reference.");
		case TitanComponent.MTC_COMPREF:
			throw new TtcnError("Done operation cannot be performed on the component reference of MTC.");
		case TitanComponent.SYSTEM_COMPREF:
			throw new TtcnError("Done operation cannot be performed on the component reference of system.");
		case TitanComponent.ANY_COMPREF:
			throw new TtcnError("Done operation with return value cannot be performed on 'any component'.");
		case TitanComponent.ALL_COMPREF:
			throw new TtcnError("Done operation with return value cannot be performed on 'all component'.");
		default:
			break;
		}

		if (is_single()) {
			throw new TtcnError("Done operation on a component reference cannot be performed in single mode.");
		}

		if (TitanComponent.self.get().componentValue == component_reference) {
			TtcnError.TtcnWarning("Done operation on the component reference of self will never succeed.");
			return TitanAlt_Status.ALT_NO;
		} else {
			final int index = get_component_status_table_index(component_reference);
			final ArrayList<component_status_table_struct> local_status_table = component_status_table.get();
			final component_status_table_struct indexed_status_table_element = local_status_table.get(index);
			switch (indexed_status_table_element.done_status) {
			case ALT_UNCHECKED:
				switch (executorState.get()) {
				case MTC_TESTCASE:
					executorState.set(executorStateEnum.MTC_DONE);
					break;
				case PTC_FUNCTION:
					executorState.set(executorStateEnum.PTC_DONE);
					break;
				default:
					throw new TtcnError("Internal error: Executing done operation in invalid state.");
				}

				TTCN_Communication.send_done_req(component_reference);
				indexed_status_table_element.done_status = TitanAlt_Status.ALT_MAYBE;
				create_done_killed_compref.set(component_reference);
				// wait for DONE_ACK
				wait_for_state_change();

				return TitanAlt_Status.ALT_REPEAT;
			case ALT_YES:
				if (indexed_status_table_element.return_type == null) {
					TTCN_Logger.log_matching_done(return_type, component_reference, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.done__failed__no__return);
					return TitanAlt_Status.ALT_NO;
				}

				if (indexed_status_table_element.return_type.equals(return_type)) {
					indexed_status_table_element.return_value.rewind();
					text_buf.set(indexed_status_table_element.return_value);

					return TitanAlt_Status.ALT_YES;
				} else {
					TTCN_Logger.log_matching_done(return_type, component_reference, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.done__failed__wrong__return__type);

					return TitanAlt_Status.ALT_NO;
				}
			default:
				return TitanAlt_Status.ALT_MAYBE;
			}
		}
	}

	//originally component_killed, with component parameter
	public static TitanAlt_Status component_killed(final TitanComponent component_reference) {
		return component_killed(component_reference.get_component());
	}

	//originally component_killed, with component parameter
	public static TitanAlt_Status component_killed(final int component_reference) {
		if (in_controlPart()) {
			throw new TtcnError("Killed operation cannot be performed in the control part.");
		}

		switch (component_reference) {
		case TitanComponent.NULL_COMPREF:
			throw new TtcnError("Killed operation cannot be performed on the null component reference.");
		case TitanComponent.MTC_COMPREF:
			throw new TtcnError("Killed operation cannot be performed on the component reference of MTC.");
		case TitanComponent.SYSTEM_COMPREF:
			throw new TtcnError("Killed operation cannot be performed on the component reference of system.");
		case TitanComponent.ANY_COMPREF:
			return any_component_killed();
		case TitanComponent.ALL_COMPREF:
			return all_component_killed();
		default:
			return ptc_killed(component_reference);
		}
	}

	//originally component_running, with component parameter
	public static boolean component_running(final TitanComponent component_reference) {
		return component_running(component_reference.get_component());
	}

	//originally component_running, with component parameter
	public static boolean component_running(final int component_reference) {
		if (in_controlPart()) {
			throw new TtcnError("Component running operation cannot be performed in the control part.");
		}

		switch (component_reference) {
		case TitanComponent.NULL_COMPREF:
			throw new TtcnError("Running operation cannot be performed on the null component reference.");
		case TitanComponent.MTC_COMPREF:
			throw new TtcnError("Running operation cannot be performed on the component reference of MTC.");
		case TitanComponent.SYSTEM_COMPREF:
			throw new TtcnError("Running operation cannot be performed on the component reference of system.");
		case TitanComponent.ANY_COMPREF:
			return any_component_running();
		case TitanComponent.ALL_COMPREF:
			return all_component_running();
		default:
			return ptc_running(component_reference);
		}
	}

	//originally component_alive, with component parameter
	public static boolean component_alive(final TitanComponent component_reference) {
		return component_alive(component_reference.get_component());
	}

	//originally component_alive, with component parameter
	public static boolean component_alive(final int component_reference) {
		if (in_controlPart()) {
			throw new TtcnError("Alive operation cannot be performed in the control part.");
		}

		switch (component_reference) {
		case TitanComponent.NULL_COMPREF:
			throw new TtcnError("Alive operation cannot be performed on the null component reference.");
		case TitanComponent.MTC_COMPREF:
			throw new TtcnError("Alive operation cannot be performed on the component reference of MTC.");
		case TitanComponent.SYSTEM_COMPREF:
			throw new TtcnError("Alive operation cannot be performed on the component reference of system.");
		case TitanComponent.ANY_COMPREF:
			return any_component_alive();
		case TitanComponent.ALL_COMPREF:
			return all_component_alive();
		default:
			return ptc_alive(component_reference);
		}
	}

	//originally stop_component
	public static void stop_component(final TitanComponent component_reference) {
		stop_component(component_reference.get_component());
	}

	//originally stop_component
	public static void stop_component(final int component_reference) {
		if (in_controlPart()) {
			throw new TtcnError("Component stop operation cannot be performed in the control part.");
		}

		if (TitanComponent.self.get().componentValue == component_reference) {
			stop_execution();
		}

		switch (component_reference) {
		case TitanComponent.NULL_COMPREF:
			throw new TtcnError("Stop operation cannot be performed on the null component reference.");
		case TitanComponent.MTC_COMPREF:
			stop_mtc();
			break;
		case TitanComponent.SYSTEM_COMPREF:
			throw new TtcnError("Stop operation cannot be performed on the component reference of system.");
		case TitanComponent.ANY_COMPREF:
			throw new TtcnError("Internal error: 'any component' cannot be stopped.");
		case TitanComponent.ALL_COMPREF:
			stop_all_component();
			break;
		default:
			stop_ptc(component_reference);
			break;
		}
	}

	//originally stop_execution
	public static void stop_execution() {
		if (in_controlPart()) {
			TTCN_Logger.log_executor_runtime(TitanLoggerApi.ExecutorRuntime_reason.enum_type.stopping__control__part__execution);
		} else {
			TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, "Stopping test component execution.");

			if (is_ptc()) {
				// the state variable indicates whether the component remains alive
				// after termination or not
				if (is_alive.get()) {
					executorState.set(executorStateEnum.PTC_STOPPED);
				} else {
					executorState.set(executorStateEnum.PTC_EXIT);
				}
			}
		}

		throw new TC_End();
	}

	//originally kill_component
	public static void kill_component(final TitanComponent component_reference) {
		kill_component(component_reference.get_component());
	}

	//originally kill_component
	public static void kill_component(final int component_reference) {
		if (in_controlPart()) {
			throw new TtcnError("Kill operation cannot be performed in the control part.");
		}

		if (TitanComponent.self.get().componentValue == component_reference) {
			kill_execution();
		}

		switch (component_reference) {
		case TitanComponent.NULL_COMPREF:
			throw new TtcnError("Kill operation cannot be performed on the null component reference.");
		case TitanComponent.MTC_COMPREF:
			stop_mtc();
			break;
		case TitanComponent.SYSTEM_COMPREF:
			throw new TtcnError("Kill operation cannot be performed on the component reference of system.");
		case TitanComponent.ANY_COMPREF:
			throw new TtcnError("Internal error: 'any component' cannot be killed.");
		case TitanComponent.ALL_COMPREF:
			kill_all_component();
			break;
		default:
			kill_ptc(component_reference);
			break;
		}
	}

	//originally kill_execution
	public static void kill_execution() {
		TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, "Terminating test component execution.");

		if (is_ptc()) {
			executorState.set(executorStateEnum.PTC_EXIT);
		}

		throw new TC_End();
	}

	public static TitanAlt_Status ptc_done(final int component_reference, final AtomicReference<VerdictTypeEnum> ptc_verdict) {
		if (is_single()) {
			throw new TtcnError("Done operation on a component reference cannot be performed in single mode.");
		}

		if (TitanComponent.self.get().componentValue == component_reference) {
			TtcnError.TtcnWarning("Done operation on the component reference of self will never succeed.");

			return TitanAlt_Status.ALT_NO;
		}

		final int index = get_component_status_table_index(component_reference);
		final ArrayList<component_status_table_struct> local_status_table = component_status_table.get();
		final component_status_table_struct indexed_status_table_element = local_status_table.get(index);
		// a successful killed operation on the component reference implies done
		if (indexed_status_table_element.killed_status == TitanAlt_Status.ALT_YES) {
			TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.ptc__done, null, null, component_reference, null, null, 0, 0);
			if (ptc_verdict != null) {
				ptc_verdict.set(indexed_status_table_element.local_verdict);
			}

			return TitanAlt_Status.ALT_YES;
		}

		switch (indexed_status_table_element.done_status) {
		case ALT_UNCHECKED:
			switch (executorState.get()) {
			case MTC_TESTCASE:
				executorState.set(executorStateEnum.MTC_DONE);
				break;
			case PTC_FUNCTION:
				executorState.set(executorStateEnum.PTC_DONE);
				break;
			default:
				throw new TtcnError("Internal error: Executing done operation in invalid state.");
			}

			TTCN_Communication.send_done_req(component_reference);
			indexed_status_table_element.done_status = TitanAlt_Status.ALT_MAYBE;
			create_done_killed_compref.set(component_reference);
			// wait for DONE_ACK
			wait_for_state_change();
			// always re-evaluate the current alternative using a new snapshot
			return TitanAlt_Status.ALT_REPEAT;
		case ALT_YES:
			TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.ptc__done, null, null, component_reference, null, null, 0, 0);
			if (ptc_verdict != null) {
				ptc_verdict.set(indexed_status_table_element.local_verdict);
			}

			return TitanAlt_Status.ALT_YES;
		default:
			return TitanAlt_Status.ALT_MAYBE;
		}
	}

	public static TitanAlt_Status any_component_done() {
		// the operation is never successful in single mode
		if (is_single()) {
			TTCN_Logger.log_matching_done(null, 0, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.any__component__done__failed);

			return TitanAlt_Status.ALT_NO;
		}

		if (!is_mtc()) {
			throw new TtcnError("Operation 'any component.done' can only be performed on the MTC.");
		}

		// the operation is successful if there is a component reference with a successful done or killed operation
		final ArrayList<component_status_table_struct> local_status_table = component_status_table.get();
		for ( int i = 0; i < local_status_table.size(); i++) {
			final component_status_table_struct componentStatus = local_status_table.get(i);
			if (componentStatus.done_status == TitanAlt_Status.ALT_YES ||
					componentStatus.killed_status == TitanAlt_Status.ALT_YES) {
				TTCN_Logger.log_matching_done(null, 0, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.any__component__done__successful);

				return TitanAlt_Status.ALT_YES;
			}
		}

		// a successful 'any component.killed' implies 'any component.done'
		if (any_component_killed_status == TitanAlt_Status.ALT_YES) {
			TTCN_Logger.log_matching_done(null, 0, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.any__component__done__successful);

			return TitanAlt_Status.ALT_YES;
		}

		switch (any_component_done_status) {
		case ALT_UNCHECKED:
			if (executorState.get() != executorStateEnum.MTC_TESTCASE) {
				throw new TtcnError("Internal error: Executing 'any component.done' in invalid state.");
			}

			executorState.set(executorStateEnum.MTC_DONE);
			TTCN_Communication.send_done_req(TitanComponent.ANY_COMPREF);
			any_component_done_status = TitanAlt_Status.ALT_MAYBE;
			create_done_killed_compref.set(TitanComponent.ANY_COMPREF);
			// wait for DONE_ACK
			wait_for_state_change();
			// always re-evaluate the current alternative using a new snapshot
			return TitanAlt_Status.ALT_REPEAT;
		case ALT_YES:
			TTCN_Logger.log_matching_done(null, 0, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.any__component__done__successful);

			return TitanAlt_Status.ALT_YES;
		case ALT_NO:
			TTCN_Logger.log_matching_done(null, 0, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.any__component__done__failed);

			return TitanAlt_Status.ALT_NO;
		default:
			return TitanAlt_Status.ALT_MAYBE;
		}
	}

	public static TitanAlt_Status all_component_done() {
		// the operation is always successful in single mode
		if (is_single()) {
			TTCN_Logger.log_matching_done(null, 0, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.all__component__done__successful);

			return TitanAlt_Status.ALT_YES;
		}

		if (!is_mtc()) {
			throw new TtcnError("Operation 'all component.done' can only be performed on the MTC.");
		}

		// a successful 'all component.killed' implies 'all component.done'
		if (all_component_killed_status == TitanAlt_Status.ALT_YES) {
			TTCN_Logger.log_matching_done(null, 0, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.all__component__done__successful);

			return TitanAlt_Status.ALT_YES;
		}

		switch (all_component_done_status) {
		case ALT_UNCHECKED:
			if (executorState.get() != executorStateEnum.MTC_TESTCASE) {
				throw new TtcnError("Internal error: Executing 'all component.done' in invalid state.");
			}

			executorState.set(executorStateEnum.MTC_DONE);
			TTCN_Communication.send_done_req(TitanComponent.ALL_COMPREF);
			all_component_done_status = TitanAlt_Status.ALT_MAYBE;
			create_done_killed_compref.set(TitanComponent.ALL_COMPREF);
			// wait for DONE_ACK
			wait_for_state_change();
			// always re-evaluate the current alternative using a new snapshot
			return TitanAlt_Status.ALT_REPEAT;
		case ALT_YES:
			TTCN_Logger.log_matching_done(null, 0, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.all__component__done__successful);

			return TitanAlt_Status.ALT_YES;
		default:
			return TitanAlt_Status.ALT_MAYBE;
		}
	}

	public static TitanAlt_Status ptc_killed(final int component_reference) {
		if (is_single()) {
			throw new TtcnError("Killed operation on a component reference cannot be performed in single mode.");
		}

		// the answer is always true if the operation refers to self
		if (TitanComponent.self.get().componentValue == component_reference) {
			TtcnError.TtcnWarning("Killed operation on the component reference of self will never succeed.");

			return TitanAlt_Status.ALT_NO;
		}


		final int index = get_component_status_table_index(component_reference);
		switch (component_status_table.get().get(index).killed_status) {
		case ALT_UNCHECKED:
			switch (executorState.get()) {
			case MTC_TESTCASE:
				executorState.set(executorStateEnum.MTC_KILLED);
				break;
			case PTC_FUNCTION:
				executorState.set(executorStateEnum.PTC_KILLED);
				break;
			default:
				throw new TtcnError("Internal error: Executing killed operation in invalid state.");
			}

			TTCN_Communication.send_killed_req(component_reference);
			component_status_table.get().get(index).killed_status = TitanAlt_Status.ALT_MAYBE;
			create_done_killed_compref.set(component_reference);
			wait_for_state_change();

			// always re-evaluate the current alternative using a new snapshot
			return TitanAlt_Status.ALT_REPEAT;
		case ALT_YES:
			TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.ptc__killed, null, null, component_reference, null, null, 0, 0);
			return TitanAlt_Status.ALT_YES;
		default:
			return TitanAlt_Status.ALT_MAYBE;
		}
	}

	public static TitanAlt_Status any_component_killed() {
		if (is_single()) {
			TTCN_Logger.log_matching_done(null, 0, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.any__component__killed__failed);

			return TitanAlt_Status.ALT_NO;
		}

		if (!is_mtc()) {
			throw new TtcnError("Operation 'any component.killed' can only be performed on the MTC.");
		}

		// the operation is successful if there is a component reference with a successful killed operation
		final ArrayList<component_status_table_struct> local_status_table = component_status_table.get();
		for (int i = 0; i < local_status_table.size(); i++) {
			if (local_status_table.get(i).killed_status == TitanAlt_Status.ALT_YES) {
				TTCN_Logger.log_matching_done(null, 0, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.any__component__killed__successful);

				return TitanAlt_Status.ALT_YES;
			}
		}

		switch (any_component_killed_status) {
		case ALT_UNCHECKED:
			if (executorState.get() != executorStateEnum.MTC_TESTCASE) {
				throw new TtcnError("Internal error: Executing 'any component.killed' in invalid state.");
			}

			executorState.set(executorStateEnum.MTC_KILLED);
			TTCN_Communication.send_killed_req(TitanComponent.ANY_COMPREF);
			any_component_killed_status = TitanAlt_Status.ALT_MAYBE;
			create_done_killed_compref.set(TitanComponent.ANY_COMPREF);
			wait_for_state_change();

			return TitanAlt_Status.ALT_REPEAT;
		case ALT_YES:
			TTCN_Logger.log_matching_done(null, 0, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.any__component__killed__successful);

			return TitanAlt_Status.ALT_YES;
		case ALT_NO:
			TTCN_Logger.log_matching_done(null, 0, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.any__component__killed__failed);

			return TitanAlt_Status.ALT_NO;
		default:
			return TitanAlt_Status.ALT_MAYBE;
		}
	}

	public static TitanAlt_Status all_component_killed() {
		if (is_single()) {
			TTCN_Logger.log_matching_done(null, 0, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.all__component__killed__successful);

			return TitanAlt_Status.ALT_YES;
		}

		if (!is_mtc()) {
			throw new TtcnError("Operation 'all component.killed' can only be performed on the MTC.");
		}

		switch (all_component_killed_status) {
		case ALT_UNCHECKED:
			if (executorState.get() != executorStateEnum.MTC_TESTCASE) {
				throw new TtcnError("Internal error: Executing 'all component.killed' in invalid state.");
			}

			executorState.set(executorStateEnum.MTC_KILLED);
			TTCN_Communication.send_killed_req(TitanComponent.ALL_COMPREF);
			all_component_killed_status = TitanAlt_Status.ALT_MAYBE;
			create_done_killed_compref.set(TitanComponent.ALL_COMPREF);
			wait_for_state_change();

			return TitanAlt_Status.ALT_REPEAT;
		case ALT_YES:
			TTCN_Logger.log_matching_done(null, 0, null, TitanLoggerApi.MatchingDoneType_reason.enum_type.all__component__killed__successful);

			return TitanAlt_Status.ALT_YES;
		default:
			return TitanAlt_Status.ALT_MAYBE;
		}
	}

	public static boolean ptc_running(final int component_reference) {
		if (is_single()) {
			throw new TtcnError("Running operation on a component reference cannot be performed in single mode.");
		}

		// the answer is always true if the operation refers to self
		if (TitanComponent.self.get().componentValue == component_reference) {
			return true;
		}

		// look into the component status tables
		if (in_component_status_table(component_reference)) {
			final int index = get_component_status_table_index(component_reference);
			final ArrayList<component_status_table_struct> local_status_table = component_status_table.get();
			final component_status_table_struct tempComponent = local_status_table.get(index);
			if (tempComponent.done_status == TitanAlt_Status.ALT_YES ||
					tempComponent.killed_status == TitanAlt_Status.ALT_YES) {
				return false;
			}
		}

		// the decision cannot be made locally, MC must be asked
		switch (executorState.get()) {
		case MTC_TESTCASE:
			executorState.set(executorStateEnum.MTC_RUNNING);
			break;
		case PTC_FUNCTION:
			executorState.set(executorStateEnum.PTC_RUNNING);
			break;
		default:
			throw new TtcnError("Internal error: Executing component running operation in invalid state.");
		}

		TTCN_Communication.send_is_running(component_reference);
		wait_for_state_change();

		return running_alive_result.get();
	}

	public static boolean any_component_running() {
		if (is_single()) {
			return false;
		}

		if (!is_mtc()) {
			throw new TtcnError("Operation 'any component.running' can only be performed on the MTC.");
		}

		// the answer is false if 'all component.done' or 'all component.killed' operation was successful
		if (all_component_done_status == TitanAlt_Status.ALT_YES ||
				all_component_killed_status == TitanAlt_Status.ALT_YES) {
			return false;
		}

		// the decision cannot be made locally, MC must be asked
		if (executorState.get() != executorStateEnum.MTC_TESTCASE) {
			throw new TtcnError("Internal error: Executing 'any component.running' in invalid state.");
		}

		TTCN_Communication.send_is_running(TitanComponent.ANY_COMPREF);
		executorState.set(executorStateEnum.MTC_RUNNING);
		wait_for_state_change();

		if (!running_alive_result.get()) {
			all_component_done_status = TitanAlt_Status.ALT_YES;
		}

		return running_alive_result.get();
	}

	public static boolean all_component_running() {
		if (is_single()) {
			return true;
		}

		if (!is_mtc()) {
			throw new TtcnError("Operation 'all component.running' can only be performed on the MTC.");
		}

		// return true if no PTCs exist
		if (all_component_done_status == TitanAlt_Status.ALT_NO) {
			return true;
		}

		// the decision cannot be made locally, MC must be asked
		if (executorState.get() != executorStateEnum.MTC_TESTCASE) {
			throw new TtcnError("Internal error: Executing 'all component.running' in invalid state.");
		}

		TTCN_Communication.send_is_running(TitanComponent.ALL_COMPREF);
		executorState.set(executorStateEnum.MTC_RUNNING);
		wait_for_state_change();

		if (!running_alive_result.get()) {
			all_component_done_status = TitanAlt_Status.ALT_YES;
		}

		return running_alive_result.get();
	}

	public static boolean ptc_alive(final int component_reference) {
		if (is_single()) {
			throw new TtcnError("Alive operation on a component reference cannot be performed in single mode.");
		}

		// the answer is always true if the operation refers to self
		if (TitanComponent.self.get().componentValue == component_reference) {
			TtcnError.TtcnWarning("Alive operation on the component reference of self always returns true.");
			return true;
		}

		// the answer is false if a successful killed operation was performed on the component reference
		if (in_component_status_table(component_reference) && get_killed_status(component_reference) == TitanAlt_Status.ALT_YES) {
			return false;
		}

		// the decision cannot be made locally, MC must be asked
		switch (executorState.get()) {
		case MTC_TESTCASE:
			executorState.set(executorStateEnum.MTC_ALIVE);
			break;
		case PTC_FUNCTION:
			executorState.set(executorStateEnum.PTC_ALIVE);
			break;
		default:
			throw new TtcnError("Internal error: Executing component alive operation in invalid state.");
		}

		TTCN_Communication.send_is_alive(component_reference);
		wait_for_state_change();

		return running_alive_result.get();
	}

	public static boolean any_component_alive() {
		if (is_single()) {
			return false;
		}

		if (!is_mtc()) {
			throw new TtcnError("Operation 'any component.alive' can only be performed on the MTC.");
		}

		// the answer is false if 'all component.killed' operation was successful
		if (all_component_killed_status == TitanAlt_Status.ALT_YES) {
			return false;
		}

		// the decision cannot be made locally, MC must be asked
		if (executorState.get() != executorStateEnum.MTC_TESTCASE) {
			throw new TtcnError("Internal error: Executing 'any component.alive' in invalid state.");
		}

		TTCN_Communication.send_is_alive(TitanComponent.ANY_COMPREF);
		executorState.set(executorStateEnum.MTC_ALIVE);
		wait_for_state_change();

		if (!running_alive_result.get()) {
			all_component_killed_status = TitanAlt_Status.ALT_YES;
		}

		return running_alive_result.get();
	}

	public static boolean all_component_alive() {
		if (is_single()) {
			return true;
		}

		if (!is_mtc()) {
			throw new TtcnError("Operation 'all component.alive' can only be performed on the MTC.");
		}

		// return true if no PTCs exist
		if (all_component_killed_status == TitanAlt_Status.ALT_NO) {
			return true;
		}

		// return false if at least one PTC has been created and
		// 'all component.killed' was successful after the create operation
		if (all_component_killed_status == TitanAlt_Status.ALT_YES) {
			return false;
		}

		// the operation is successful if there is a component reference with a
		// successful killed operation
		final ArrayList<component_status_table_struct> local_status_table = component_status_table.get();
		for (int i = 0; i < local_status_table.size(); i++) {
			if (local_status_table.get(i).killed_status == TitanAlt_Status.ALT_YES) {
				return false;
			}
		}

		// the decision cannot be made locally, MC must be asked
		if (executorState.get() != executorStateEnum.MTC_TESTCASE) {
			throw new TtcnError("Internal error: Executing 'all component.alive' in invalid state.");
		}

		TTCN_Communication.send_is_alive(TitanComponent.ALL_COMPREF);
		executorState.set(executorStateEnum.MTC_ALIVE);
		wait_for_state_change();

		return running_alive_result.get();
	}

	public static void stop_mtc() {
		TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.stopping__mtc, null, null, 0, null, null, 0, 0);
		TTCN_Communication.send_stop_req(TitanComponent.MTC_COMPREF);
		stop_execution();
	}

	public static void stop_ptc(final int component_reference) {
		if (is_single()) {
			throw new TtcnError("Stop operation on a component reference cannot be performed in single mode.");
		}

		// do nothing if a successful done or killed operation was performed on the component reference
		if (in_component_status_table(component_reference)) {
			final int index = get_component_status_table_index(component_reference);
			final ArrayList<component_status_table_struct> local_status_table = component_status_table.get();
			final component_status_table_struct tempComponent = local_status_table.get(index);
			if (tempComponent.done_status == TitanAlt_Status.ALT_YES ||
					tempComponent.killed_status == TitanAlt_Status.ALT_YES) {
				TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, MessageFormat.format("PTC with component reference {0} is not running. Stop operation had no effect.", component_reference));

				return;
			}
		}

		switch (executorState.get()) {
		case MTC_TESTCASE:
			executorState.set(executorStateEnum.MTC_STOP);
			break;
		case PTC_FUNCTION:
			executorState.set(executorStateEnum.PTC_STOP);
			break;
		default:
			throw new TtcnError("Internal error: Executing component stop operation in invalid state.");
		}

		TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, MessageFormat.format("Stopping PTC with component reference {0}.", component_reference));
		TTCN_Communication.send_stop_req(component_reference);
		//wait for STOP_ACK;
		wait_for_state_change();

		TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.ptc__stopped, null, null, component_reference, null, null, 0, 0);
	}

	public static void stop_all_component() {
		if (is_single()) {
			TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, "No PTCs are running. Operation 'all component.stop' had no effect.");

			return;
		}

		if (!is_mtc()) {
			throw new TtcnError("Operation 'all component.stop' can only be performed on the MTC.");
		}

		// do nothing if 'all component.done' or 'all component.killed' was successful
		if (all_component_done_status == TitanAlt_Status.ALT_YES ||
				all_component_killed_status == TitanAlt_Status.ALT_YES) {
			TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, "No PTCs are running. Operation 'all component.stop' had no effect.");

			return;
		}

		// a request must be sent to MC
		if (executorState.get() != executorStateEnum.MTC_TESTCASE) {
			throw new TtcnError("Internal error: Executing 'all component.stop' in invalid state.");
		}

		executorState.set(executorStateEnum.MTC_STOP);
		TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, "Stopping all components.");
		TTCN_Communication.send_stop_req(TitanComponent.ALL_COMPREF);

		//wait for STOP_ACK
		wait_for_state_change();
		// 'all component.done' will be successful later
		all_component_done_status = TitanAlt_Status.ALT_YES;
		TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.all__comps__stopped, null, null, 0, null, null, 0, 0);
	}

	public static void kill_ptc(final int component_reference) {
		if (is_single()) {
			throw new TtcnError("Kill operation on a component reference cannot be performed in single mode.");
		}

		// do nothing if a successful  killed operation was performed on the component reference
		if (in_component_status_table(component_reference) && get_killed_status(component_reference) == TitanAlt_Status.ALT_YES) {
			TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, MessageFormat.format("PTC with component reference {0} is not alive anyomre. Kill operation had no effect.", component_reference));

			return;
		}

		// MC must be asked to kill the PTC
		switch (executorState.get()) {
		case MTC_TESTCASE:
			executorState.set(executorStateEnum.MTC_KILL);
			break;
		case PTC_FUNCTION:
			executorState.set(executorStateEnum.PTC_KILL);
			break;
		default:
			throw new TtcnError("Internal error: Executing kill operation in invalid state.");
		}

		TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, MessageFormat.format("Killing PTC with component reference {0}.", component_reference));
		TTCN_Communication.send_kill_req(component_reference);
		//wait for KILL_ACK;
		wait_for_state_change();

		// updating the killed status of the PTC
		final int index = get_component_status_table_index(component_reference);
		component_status_table.get().get(index).killed_status = TitanAlt_Status.ALT_YES;

		TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.ptc__killed, null, null, component_reference, null, null, 0, 0);
	}

	public static void kill_all_component() {
		if (is_single()) {
			TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, "There are no alive PTCs. Operation 'all component.kill' had no effect.");

			return;
		}

		if (!is_mtc()) {
			throw new TtcnError("Operation 'all component.kill' can only be performed on the MTC.");
		}

		// do nothing if 'all component.killed' was successful
		if (all_component_killed_status == TitanAlt_Status.ALT_YES) {
			TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, "There are no alive PTCs. Operation 'all component.kill' had no effect.");

			return;
		}

		// a request must be sent to MC
		if (executorState.get() != executorStateEnum.MTC_TESTCASE) {
			throw new TtcnError("Internal error: Executing 'all component.kill' in invalid state.");
		}

		executorState.set(executorStateEnum.MTC_KILL);
		TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, "Killing all components.");
		TTCN_Communication.send_kill_req(TitanComponent.ALL_COMPREF);

		//wait for KILL_ACK
		wait_for_state_change();
		// 'all component.done' and 'all component.killed' will be successful later
		all_component_done_status = TitanAlt_Status.ALT_YES;
		all_component_killed_status = TitanAlt_Status.ALT_YES;
		TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.all__comps__killed, null, null, 0, null, null, 0, 0);
	}

	private static void check_port_name(final String portName, final String operationName, final String whichArgument) {
		if (portName == null) {
			throw new TtcnError(MessageFormat.format("Internal error: The port name in the {0} argument of {1} operation is a NULL pointer.", whichArgument, operationName));
		}
		if (portName.length() == 0) {
			throw new TtcnError(MessageFormat.format("Internal error: The {0} argument of {1} operation contains an empty string as port name.", whichArgument, operationName));
		}
	}

	public static void connect_port(final TitanComponent sourceComponent, final String sourePort, final TitanComponent destinationComponent, final String destinationPort) {
		check_port_name(sourePort, "connect", "first");
		check_port_name(destinationPort, "connect", "second");

		TTCN_Logger.begin_event(Severity.PARALLEL_UNQUALIFIED);
		TTCN_Logger.log_event_str("Connecting ports ");
		sourceComponent.log();
		TTCN_Logger.log_event_str(MessageFormat.format(":{0} and ", sourePort));
		destinationComponent.log();
		TTCN_Logger.log_event_str(MessageFormat.format(":{0}.", destinationPort));
		TTCN_Logger.end_event();

		sourceComponent.must_bound("The first argument of connect operation contains an unbound component reference.");
		switch (sourceComponent.get_component()) {
		case TitanComponent.NULL_COMPREF:
			throw new TtcnError("The first argument of connect operation contains the null component reference.");
		case TitanComponent.SYSTEM_COMPREF:
			throw new TtcnError("The first argument of connect operation refers to a system port.");
		default:
			break;
		}

		destinationComponent.must_bound("The second argument of connect operation contains an unbound component reference.");
		switch (destinationComponent.get_component()) {
		case TitanComponent.NULL_COMPREF:
			throw new TtcnError("The second argument of connect operation contains the null component reference.");
		case TitanComponent.SYSTEM_COMPREF:
			throw new TtcnError("The second argument of connect operation refers to a system port.");
		default:
			break;
		}

		switch (executorState.get()) {
		case SINGLE_TESTCASE:
			if (sourceComponent.get_component() != TitanComponent.MTC_COMPREF || destinationComponent.get_component() != TitanComponent.MTC_COMPREF) {
				throw new TtcnError("Both endpoints of connect operation must refer to ports of mtc in single mode.");
			}

			TitanPort.make_local_connection(sourePort, destinationPort);
			break;
		case MTC_TESTCASE:
			TTCN_Communication.send_connect_req(sourceComponent.get_component(), sourePort, destinationComponent.get_component(), destinationPort);
			executorState.set(executorStateEnum.MTC_CONNECT);
			wait_for_state_change();
			break;
		case PTC_FUNCTION:
			TTCN_Communication.send_connect_req(sourceComponent.get_component(), sourePort, destinationComponent.get_component(), destinationPort);
			executorState.set(executorStateEnum.PTC_CONNECT);
			wait_for_state_change();
			break;
		default:
			if (in_controlPart()) {
				throw new TtcnError("Connect operation cannot be performed in the control part.");
			} else {
				throw new TtcnError("Internal error: Executing connect operation in invalid state.");
			}
		}

		TTCN_Logger.log_portconnmap(ParPort_operation.enum_type.connect__, sourceComponent.get_component(), sourePort, destinationComponent.get_component(), destinationPort);
	}

	public static void disconnect_port(final TitanComponent sourceComponent, final String sourePort, final TitanComponent destinationComponent, final String destinationPort) {
		check_port_name(sourePort, "disconnect", "first");
		check_port_name(destinationPort, "disconnect", "second");

		TTCN_Logger.begin_event(Severity.PARALLEL_UNQUALIFIED);
		TTCN_Logger.log_event_str("Disconnecting ports ");
		sourceComponent.log();
		TTCN_Logger.log_event_str(MessageFormat.format(":{0} and ", sourePort));
		destinationComponent.log();
		TTCN_Logger.log_event_str(MessageFormat.format(":{0}.", destinationPort));
		TTCN_Logger.end_event();

		sourceComponent.must_bound("The first argument of disconnect operation contains an unbound component reference.");
		switch (sourceComponent.get_component()) {
		case TitanComponent.NULL_COMPREF:
			throw new TtcnError("The first argument of disconnect operation contains the null component reference.");
		case TitanComponent.SYSTEM_COMPREF:
			throw new TtcnError("The first argument of disconnect operation refers to a system port.");
		default:
			break;
		}

		destinationComponent.must_bound("The second argument of disconnect operation contains an unbound component reference.");
		switch (destinationComponent.get_component()) {
		case TitanComponent.NULL_COMPREF:
			throw new TtcnError("The second argument of disconnect operation contains the null component reference.");
		case TitanComponent.SYSTEM_COMPREF:
			throw new TtcnError("The second argument of disconnect operation refers to a system port.");
		default:
			break;
		}

		switch (executorState.get()) {
		case SINGLE_TESTCASE:
			if (sourceComponent.get_component() != TitanComponent.MTC_COMPREF || destinationComponent.get_component() != TitanComponent.MTC_COMPREF) {
				throw new TtcnError("Both endpoints of disconnect operation must refer to ports of mtc in single mode.");
			}

			TitanPort.terminate_local_connection(sourePort, destinationPort);
			break;
		case MTC_TESTCASE:
			TTCN_Communication.send_disconnect_req(sourceComponent.get_component(), sourePort, destinationComponent.get_component(), destinationPort);
			executorState.set(executorStateEnum.MTC_DISCONNECT);
			wait_for_state_change();
			break;
		case PTC_FUNCTION:
			TTCN_Communication.send_disconnect_req(sourceComponent.get_component(), sourePort, destinationComponent.get_component(), destinationPort);
			executorState.set(executorStateEnum.PTC_DISCONNECT);
			wait_for_state_change();
			break;
		default:
			if (in_controlPart()) {
				throw new TtcnError("Disconnect operation cannot be performed in the control part.");
			} else {
				throw new TtcnError("Internal error: Executing disconnect operation in invalid state.");
			}
		}

		TTCN_Logger.log_portconnmap(ParPort_operation.enum_type.disconnect__, sourceComponent.get_component(), sourePort, destinationComponent.get_component(), destinationPort);
	}


	//originally map_port
	public static void map_port(final TitanComponent sourceComponentRef, final String sourePort, final TitanComponent destinationComponentRef, final String destinationPort, final Map_Params params, final boolean translation) {
		check_port_name(sourePort, "map", "first");
		check_port_name(destinationPort, "map", "second");

		TTCN_Logger.begin_event(Severity.PARALLEL_UNQUALIFIED);
		TTCN_Logger.log_event_str("Mapping ports ");
		sourceComponentRef.log();
		TTCN_Logger.log_event_str(MessageFormat.format(":{0} to ", sourePort));
		destinationComponentRef.log();
		TTCN_Logger.log_event_str(MessageFormat.format(":{0}.", destinationPort));
		TTCN_Logger.end_event();

		sourceComponentRef.must_bound("The first argument of map operation contains an unbound component reference.");

		final TitanComponent sourceComponent = sourceComponentRef;
		if (sourceComponent.get_component() == TitanComponent.NULL_COMPREF) {
			throw new TtcnError("The first argument of map operation contains the null component reference.");
		}

		destinationComponentRef.must_bound("The second argument of map operation contains an unbound component reference.");

		final TitanComponent destinationComponent = destinationComponentRef;
		if (destinationComponent.get_component() == TitanComponent.NULL_COMPREF) {
			throw new TtcnError("The second argument of map operation contains the null component reference.");
		}

		TitanComponent componentReference;
		String componentPort;
		String systemPort;
		if (sourceComponent.get_component() == TitanComponent.SYSTEM_COMPREF) {
			if (destinationComponent.get_component() == TitanComponent.SYSTEM_COMPREF) {
				throw new TtcnError("Both arguments of map operation refer to system ports.");
			}
			componentReference = destinationComponent;
			componentPort = destinationPort;
			systemPort = sourePort;
		} else if (destinationComponent.get_component() == TitanComponent.SYSTEM_COMPREF) {
			componentReference = sourceComponent;
			componentPort = sourePort;
			systemPort = destinationPort;
		} else {
			throw new TtcnError("Both arguments of map operation refer to test component ports.");
		}

		switch (executorState.get()) {
		case SINGLE_TESTCASE:
			if (componentReference.componentValue != TitanComponent.MTC_COMPREF) {
				throw new TtcnError("Only the ports of mtc can be mapped in single mode.");
			}

			TitanPort.map_port(componentPort, systemPort, params, false);
			if (translation) {
				TitanPort.map_port(componentPort, systemPort, params, true);
			}
			break;
		case MTC_TESTCASE:
			TTCN_Communication.send_map_req(componentReference.componentValue, componentPort, systemPort, params, translation);
			executorState.set(executorStateEnum.MTC_MAP);
			wait_for_state_change();
			params.operator_assign(TitanPort.map_params_cache.get());
			break;
		case PTC_FUNCTION:
			TTCN_Communication.send_map_req(componentReference.componentValue, componentPort, systemPort, params, translation);
			executorState.set(executorStateEnum.PTC_MAP);
			wait_for_state_change();
			params.operator_assign(TitanPort.map_params_cache.get());
			break;
		default:
			if (in_controlPart()) {
				throw new TtcnError("Map operation cannot be performed in the control part.");
			} else {
				throw new TtcnError("Internal error: Executing map operation in invalid state.");
			}
		}

		TTCN_Logger.log_portconnmap(ParPort_operation.enum_type.map__, sourceComponentRef.componentValue, sourePort, destinationComponentRef.componentValue, destinationPort);
	}

	//originally unmap_port
	public static void unmap_port(final TitanComponent sourceComponentRef, final String sourePort, final TitanComponent destinationComponentRef, final String destinationPort, final Map_Params params, final boolean translation) {
		check_port_name(sourePort, "unmap", "first");
		check_port_name(destinationPort, "unmap", "second");

		TTCN_Logger.begin_event(Severity.PARALLEL_UNQUALIFIED);
		TTCN_Logger.log_event_str("Unmapping ports ");
		sourceComponentRef.log();
		TTCN_Logger.log_event_str(MessageFormat.format(":{0} from ", sourePort));
		destinationComponentRef.log();
		TTCN_Logger.log_event_str(MessageFormat.format(":{0}.", destinationPort));
		TTCN_Logger.end_event();

		sourceComponentRef.must_bound("The first argument of unmap operation contains an unbound component reference.");

		final TitanComponent sourceComponent = sourceComponentRef;
		if (sourceComponent.get_component() == TitanComponent.NULL_COMPREF) {
			throw new TtcnError("The first argument of unmap operation contains the null component reference.");
		}

		destinationComponentRef.must_bound("The second argument of unmap operation contains an unbound component reference.");

		final TitanComponent destinationComponent = destinationComponentRef;
		if (destinationComponent.get_component() == TitanComponent.NULL_COMPREF) {
			throw new TtcnError("The second argument of unmap operation contains the null component reference.");
		}

		TitanComponent componentReference;
		String componentPort;
		String systemPort;
		if (sourceComponent.get_component() == TitanComponent.SYSTEM_COMPREF) {
			if (destinationComponent.get_component() == TitanComponent.SYSTEM_COMPREF) {
				throw new TtcnError("Both arguments of unmap operation refer to system ports.");
			}
			componentReference = destinationComponent;
			componentPort = destinationPort;
			systemPort = sourePort;
		} else if (destinationComponent.get_component() == TitanComponent.SYSTEM_COMPREF) {
			componentReference = sourceComponent;
			componentPort = sourePort;
			systemPort = destinationPort;
		} else {
			throw new TtcnError("Both arguments of unmap operation refer to test component ports.");
		}

		switch (executorState.get()) {
		case SINGLE_TESTCASE:
			if (componentReference.componentValue != TitanComponent.MTC_COMPREF) {
				throw new TtcnError("Only the ports of mtc can be unmapped in single mode.");
			}

			TitanPort.unmap_port(componentPort, systemPort, params, false);
			if (translation) {
				TitanPort.unmap_port(componentPort, systemPort, params, true);
			}
			break;
		case MTC_TESTCASE:
			TTCN_Communication.send_unmap_req(componentReference.componentValue, componentPort, systemPort, params, translation);
			executorState.set(executorStateEnum.MTC_UNMAP);
			wait_for_state_change();
			params.operator_assign(TitanPort.map_params_cache.get());
			break;
		case PTC_FUNCTION:
			TTCN_Communication.send_unmap_req(componentReference.componentValue, componentPort, systemPort, params, translation);
			executorState.set(executorStateEnum.PTC_UNMAP);
			wait_for_state_change();
			params.operator_assign(TitanPort.map_params_cache.get());
			break;
		default:
			if (in_controlPart()) {
				throw new TtcnError("Unmap operation cannot be performed in the control part.");
			} else {
				throw new TtcnError("Internal error: Executing unmap operation in invalid state.");
			}
		}

		TTCN_Logger.log_portconnmap(ParPort_operation.enum_type.unmap__, sourceComponentRef.componentValue, sourePort, destinationComponentRef.componentValue, destinationPort);
	}

	public static void begin_controlpart(final String moduleName) {
		control_module_name = moduleName;
		//FIXME implement execute_command
		TTCN_Logger.log_controlpart_start_stop(moduleName, false);
	}

	public static void end_controlpart() {
		TTCN_Default.deactivate_all();
		TTCN_Default.reset_counter();
		TitanTimer.all_stop();
		TTCN_Logger.log_controlpart_start_stop(control_module_name, true);
		//FIXME implement execute_command
		control_module_name = null;
	}

	//originally TTCN_Runtime::check_begin_testcase
	public static void check_begin_testcase(final boolean hasTimer, final TitanFloat timerValue) {
		if (!in_controlPart()) {
			if (is_single() || is_mtc()) {
				throw new TtcnError(MessageFormat.format("Test case cannot be executed while another one ({0}.{1}) is running.", testcaseModuleName, testcaseDefinitionName));
			} else if (is_ptc()) {
				throw new TtcnError("Test case cannot be executed on a PTC.");
			} else {
				throw new TtcnError("Internal error: Executing a test case in an invalid state.");
			}
		}

		if (hasTimer && timerValue.is_less_than(0.0)) {
			throw new TtcnError(MessageFormat.format("The test case supervisor timer has negative duration ({0} s).", timerValue.get_value()));
		}
	}

	// originally TTCN_Runtime::begin_testcase
	public static void begin_testcase(final String moduleName, final String testcaseName, final String mtc_comptype_module, final String mtc_comptype_name, final String system_comptype_module, final String system_comptype_name, final boolean hasTimer, final TitanFloat timerValue) {
		switch (executorState.get()) {
		case SINGLE_CONTROLPART:
			executorState.set(executorStateEnum.SINGLE_TESTCASE);
			break;
		case MTC_CONTROLPART:
			TTCN_Communication.send_testcase_started(moduleName, testcaseName, mtc_comptype_module, mtc_comptype_name, system_comptype_module, system_comptype_name);
			executorState.set(executorStateEnum.MTC_TESTCASE);
			break;
		default:
			throw new TtcnError("Internal error: Executing a test case in an invalid state.");
		}
		TitanTimer.save_control_timers();
		TTCN_Default.save_control_defaults();
		set_testcase_name(moduleName, testcaseName);
		set_system_type(system_comptype_module, system_comptype_name);
		//FIXME implement command execution

		TTCN_Logger.log_testcase_started(moduleName, testcaseName);
		if (hasTimer) {
			TitanTimer.testcaseTimer.start(timerValue.get_value());
		}

		set_component_type(mtc_comptype_module, mtc_comptype_name);
		initialize_component_type();

		any_component_done_status = TitanAlt_Status.ALT_NO;
		all_component_done_status = TitanAlt_Status.ALT_YES;
		any_component_killed_status = TitanAlt_Status.ALT_NO;
		all_component_killed_status = TitanAlt_Status.ALT_YES;
		startTime.set(System.currentTimeMillis() / 1000.0);
	}

	//originally TTCN_Runtime::end_testcase
	public static VerdictTypeEnum end_testcase() {
		switch (executorState.get()) {
		case MTC_CREATE:
		case MTC_START:
		case MTC_STOP:
		case MTC_KILL:
		case MTC_RUNNING:
		case MTC_ALIVE:
		case MTC_DONE:
		case MTC_KILLED:
		case MTC_CONNECT:
		case MTC_DISCONNECT:
		case MTC_MAP:
		case MTC_UNMAP:
			executorState.set(executorStateEnum.MTC_TESTCASE);
			break;
		case MTC_TESTCASE:
			break;
		case SINGLE_TESTCASE:
			// FIXME implement disable_interrupt_handler();
			break;
		default:
			throw new TtcnError("Internal error: Ending a testcase in an invalid state.");
		}

		TitanTimer.testcaseTimer.stop();
		terminate_component_type();

		if (executorState.get() == executorStateEnum.MTC_TESTCASE) {
			TTCN_Logger.log_executor_runtime(TitanLoggerApi.ExecutorRuntime_reason.enum_type.waiting__for__ptcs__to__finish);
			TTCN_Communication.send_testcase_finished(localVerdict.get(), verdictReason.get());
			executorState.set(executorStateEnum.MTC_TERMINATING_TESTCASE);
			wait_for_state_change();
		} else if (executorState.get() == executorStateEnum.SINGLE_TESTCASE) {
			executorState.set(executorStateEnum.SINGLE_CONTROLPART);
			// FIXME implement enable_interrupt_handler
		}

		TTCN_Logger.log_testcase_finished(testcaseModuleName.get(), testcaseDefinitionName.get(), localVerdict.get(), verdictReason.get());

		verdictCount[localVerdict.get().getValue()]++;

		testcaseModuleName.set(null);
		testcaseDefinitionName.set(null);
		clear_component_status_table();
		any_component_done_status = TitanAlt_Status.ALT_UNCHECKED;
		all_component_done_status = TitanAlt_Status.ALT_UNCHECKED;
		any_component_killed_status = TitanAlt_Status.ALT_UNCHECKED;
		all_component_killed_status = TitanAlt_Status.ALT_UNCHECKED;

		TTCN_Default.restore_control_defaults();
		TitanTimer.restore_control_timers();
		TTCN_EncDec_ErrorContext.reset_all_contexts();
		startTime.set(0.0);

		if (executorState.get() == executorStateEnum.MTC_PAUSED) {
			TTCN_Logger.log_executor_runtime(TitanLoggerApi.ExecutorRuntime_reason.enum_type.user__paused__waiting__to__resume);
			wait_for_state_change();
			if (executorState.get() != executorStateEnum.MTC_TERMINATING_EXECUTION) {
				TTCN_Logger.log_executor_runtime(TitanLoggerApi.ExecutorRuntime_reason.enum_type.resuming__execution);
			}
		}
		if (executorState.get() == executorStateEnum.MTC_TERMINATING_EXECUTION) {
			executorState.set(executorStateEnum.MTC_CONTROLPART);
			TTCN_Logger.log_executor_runtime(TitanLoggerApi.ExecutorRuntime_reason.enum_type.terminating__execution);

			throw new TC_End();
		}

		return localVerdict.get();
	}

	//originally log_verdict_statistics
	public static void log_verdict_statistics() {
		final int totalTestcases = verdictCount[VerdictTypeEnum.NONE.getValue()] + verdictCount[VerdictTypeEnum.PASS.getValue()]
				+ verdictCount[VerdictTypeEnum.INCONC.getValue()] + verdictCount[VerdictTypeEnum.FAIL.getValue()]
						+ verdictCount[VerdictTypeEnum.ERROR.getValue()];

		VerdictTypeEnum overallVerdict;
		if (controlErrorCount > 0 || verdictCount[VerdictTypeEnum.ERROR.getValue()] > 0) {
			overallVerdict = VerdictTypeEnum.ERROR;
		} else if (verdictCount[VerdictTypeEnum.FAIL.getValue()] > 0) {
			overallVerdict = VerdictTypeEnum.FAIL;
		} else if (verdictCount[VerdictTypeEnum.INCONC.getValue()] > 0) {
			overallVerdict = VerdictTypeEnum.INCONC;
		} else if (verdictCount[VerdictTypeEnum.PASS.getValue()] > 0) {
			overallVerdict = VerdictTypeEnum.PASS;
		} else {
			overallVerdict = VerdictTypeEnum.NONE;
		}

		if (totalTestcases > 0) {
			TTCN_Logger.log_verdict_statistics(verdictCount[VerdictTypeEnum.NONE.getValue()], (100.0 * verdictCount[VerdictTypeEnum.NONE.getValue()]) / totalTestcases,
					verdictCount[VerdictTypeEnum.PASS.getValue()], (100.0 * verdictCount[VerdictTypeEnum.PASS.getValue()]) / totalTestcases,
					verdictCount[VerdictTypeEnum.INCONC.getValue()], (100.0 * verdictCount[VerdictTypeEnum.INCONC.getValue()]) / totalTestcases,
					verdictCount[VerdictTypeEnum.FAIL.getValue()], (100.0 * verdictCount[VerdictTypeEnum.FAIL.getValue()]) / totalTestcases,
					verdictCount[VerdictTypeEnum.ERROR.getValue()], (100.0 * verdictCount[VerdictTypeEnum.ERROR.getValue()]) / totalTestcases);
		} else {
			TTCN_Logger.log_verdict_statistics(0, 0.0, 0, 0.0, 0, 0.0, 0, 0.0, 0, 0.0);
		}

		if (controlErrorCount > 0) {
			TTCN_Logger.log_controlpart_errors(controlErrorCount);
		}

		TTCN_Logger.log_str(Severity.STATISTICS_VERDICT, MessageFormat.format("Test execution summary: {0} test case{1} executed. Overall verdict: {2}", totalTestcases, totalTestcases > 1 ? "s were" : " was", overallVerdict.getName()));

		verdictCount[VerdictTypeEnum.NONE.getValue()] = 0;
		verdictCount[VerdictTypeEnum.PASS.getValue()] = 0;
		verdictCount[VerdictTypeEnum.INCONC.getValue()] = 0;
		verdictCount[VerdictTypeEnum.FAIL.getValue()] = 0;
		verdictCount[VerdictTypeEnum.ERROR.getValue()] = 0;
		controlErrorCount = 0;
	}

	public static void begin_action() {
		TTCN_Logger.begin_event(Severity.ACTION_UNQUALIFIED);
		TTCN_Logger.log_event_str("Action: ");
	}

	public static void end_action() {
		TTCN_Logger.end_event();
	}

	public static void setverdict(final TitanVerdictType.VerdictTypeEnum newValue) {
		setverdict(newValue, "");
	}

	public static void setverdict(final TitanVerdictType.VerdictTypeEnum newValue, final String reason) {
		if (verdict_enabled()) {
			if (VerdictTypeEnum.ERROR.equals(newValue)) {
				throw new TtcnError("Error verdict cannot be set explicitly.");
			}

			setverdict_internal(newValue, reason);
		} else if (in_controlPart()) {
			throw new TtcnError("Verdict cannot be set in the control part.");
		} else {
			throw new TtcnError("Internal error: Setting the verdict in invalid state.");
		}
	}

	public static void setverdict(final TitanVerdictType newValue) {
		setverdict(newValue, "");
	}

	public static void setverdict(final TitanVerdictType newValue, final String reason) {
		newValue.must_bound("The argument of setverdict operation is an unbound verdict value.");

		setverdict(newValue.get_value(), reason);
	}

	//originally set_error_verdict
	public static void set_error_verdict() {
		if (verdict_enabled()) {
			setverdict_internal(VerdictTypeEnum.ERROR, "");
		} else if (is_single() || is_mtc()) {
			controlErrorCount++;
		}
	}

	//originally getverdict
	public static TitanVerdictType get_verdict() {
		if (verdict_enabled()) {
			TTCN_Logger.log_getverdict(localVerdict.get());
		} else if (in_controlPart()) {
			throw new TtcnError("Getverdict operation cannot be performed in the control part.");
		} else {
			throw new TtcnError("Internal error: Performing getverdict operation in invalid state.");
		}

		return new TitanVerdictType(localVerdict.get());
	}

	//originally setverdict_internal
	private static void setverdict_internal(final TitanVerdictType.VerdictTypeEnum newValue, final String reason) {
		if (newValue.getValue() < VerdictTypeEnum.NONE.getValue() || newValue.getValue() > VerdictTypeEnum.ERROR.getValue()) {
			throw new TtcnError(MessageFormat.format("Internal error: setting an invalid verdict value ({0}).", newValue.getValue()));
		}

		final VerdictTypeEnum oldVerdict = localVerdict.get();
		if (localVerdict.get().getValue() < newValue.getValue()) {
			verdictReason.set(reason);
			localVerdict.set(newValue);
			if (reason == null || reason.length() == 0) {
				TTCN_Logger.log_setverdict(newValue, oldVerdict, localVerdict.get(), null, null);
			} else {
				TTCN_Logger.log_setverdict(newValue, oldVerdict, localVerdict.get(), reason, reason);
			}
		} else if (localVerdict.get().getValue() == newValue.getValue()) {
			if (reason == null || reason.length() == 0) {
				TTCN_Logger.log_setverdict(newValue, oldVerdict, localVerdict.get(), null, null);
			} else {
				TTCN_Logger.log_setverdict(newValue, oldVerdict, localVerdict.get(), reason, reason);
			}
		}

		//FIXME handle debugger breakpoints
	}

	public static void process_create_mtc(){
		switch (executorState.get()) {
		case HC_ACTIVE:
		case HC_OVERLOADED:
			break;
		default:
			TTCN_Communication.send_error("Message CREATE_MTC arrived in invalid state.");
			return;
		}

		// clean the emergency buffer
		TTCN_Logger.ring_buffer_dump(false);

		final Thread MTC = new Thread() {

			@Override
			public void run() {
				//runs in the MTC
				TTCN_Snapshot.re_open();
				//TTCN_Communication.close_mc_connection();not needed on the Java side

				TitanComponent.self.set(new TitanComponent(TitanComponent.MTC_COMPREF));
				executorState.set(executorStateEnum.MTC_INITIAL);

				//stuff from Parallel_main::main after hc_main call
				//FIXME clear stuff before mtc_main
				TitanComponent.clear_component_names();
				TTCN_Logger.close_file();
				TTCN_Logger.set_start_time();
				mtc_main();
				//FIXME close down stuff after mtc_main
			}

		};

		threads.add(MTC);
		MTC.start();

		TTCN_Logger.log_mtc_created(0);
		add_component(TitanComponent.MTC_COMPREF, MTC);
		successful_process_creation();

		//FIXME implement
	}

	public static void process_create_ptc(final int component_reference, final String component_type_module, final String component_type_name,
			final String system_type_module, final String system_type_name, final String par_component_name, final boolean par_is_alive,
			final String current_testcase_module, final String current_testcase_name, final double testcase_start_time) {
		switch (executorState.get()) {
		case HC_ACTIVE:
		case HC_OVERLOADED:
			break;
		default:
			TTCN_Communication.send_error("Message CREATE_PTC arrived in invalid state.");
			return;
		}

		// clean the emergency buffer
		TTCN_Logger.ring_buffer_dump(false);

		final Thread PTC = new Thread() {

			@Override
			public void run() {
				//runs in the PTC
				TTCN_Snapshot.re_open();
				//TTCN_Communication.close_mc_connection();not needed on the Java side

				TitanComponent.self.set(new TitanComponent(component_reference));
				set_component_type(component_type_module, component_type_name);
				set_system_type(system_type_module, system_type_name);
				set_component_name(par_component_name);
				TTCN_Runtime.is_alive.set(par_is_alive);
				set_testcase_name(current_testcase_module, current_testcase_name);
				startTime.set(testcase_start_time);
				executorState.set(executorStateEnum.PTC_INITIAL);

				//What now???

				//stuff from Parallel_main::main after hc_main call
				TitanComponent.clear_component_names();
				TTCN_Logger.close_file();
				TTCN_Logger.set_start_time();
				ptc_main();
				//FIXME close down stuff after ptc_main
			}

		};

		threads.add(PTC);
		PTC.start();

		TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.ptc__created__pid, component_type_module, component_type_name, component_reference, par_component_name, current_testcase_name, 0, 0);
		add_component(component_reference, PTC);
		TitanComponent.register_component_name(component_reference, par_component_name);
		successful_process_creation();
		//FIXME implement add_child_process
	}

	public static void process_create_ack(final int new_component) {
		switch (executorState.get()) {
		case MTC_CREATE:
			executorState.set(executorStateEnum.MTC_TESTCASE);
			break;
		case MTC_TERMINATING_TESTCASE:
			break;
		case PTC_CREATE:
			executorState.set(executorStateEnum.PTC_FUNCTION);
			break;
		default:
			throw new TtcnError("Internal error: Message CREATE_ACK arrived in invalid state.");
		}

		create_done_killed_compref.set(new_component);
	}

	public static void process_running(final boolean result_value) {
		switch (executorState.get()) {
		case MTC_RUNNING:
			executorState.set(executorStateEnum.MTC_TESTCASE);
			break;
		case MTC_TERMINATING_TESTCASE:
			break;
		case PTC_RUNNING:
			executorState.set(executorStateEnum.PTC_FUNCTION);
			break;
		default:
			throw new TtcnError("Internal error: Message RUNNING arrived in invalid state.");
		}

		running_alive_result.set(result_value);
	}

	public static void process_alive(final boolean result_value) {
		switch (executorState.get()) {
		case MTC_ALIVE:
			executorState.set(executorStateEnum.MTC_TESTCASE);
			break;
		case MTC_TERMINATING_TESTCASE:
			break;
		case PTC_ALIVE:
			executorState.set(executorStateEnum.PTC_FUNCTION);
			break;
		default:
			throw new TtcnError("Internal error: Message ALIVE arrived in invalid state.");
		}

		running_alive_result.set(result_value);
	}

	public static void process_done_ack(final boolean done_status, final VerdictTypeEnum ptc_verdict, final String return_type, final byte[] data, final int return_value_len, final int buffer_begin, final int return_value_begin) {
		switch (executorState.get()) {
		case MTC_DONE:
			executorState.set(executorStateEnum.MTC_TESTCASE);
			break;
		case MTC_TERMINATING_TESTCASE:
			break;
		case PTC_DONE:
			executorState.set(executorStateEnum.PTC_FUNCTION);
			break;
		default:
			throw new TtcnError("Internal error: Message DONE_ACK arrived in invalid state.");
		}

		if (done_status) {
			set_component_done(create_done_killed_compref.get(), ptc_verdict, return_type, data, return_value_len, buffer_begin, return_value_begin);
		}

		create_done_killed_compref.set(TitanComponent.NULL_COMPREF);
	}

	public static void process_killed_ack(final boolean killed_status) {
		switch (executorState.get()) {
		case MTC_KILLED:
			executorState.set(executorStateEnum.MTC_TESTCASE);
			break;
		case MTC_TERMINATING_TESTCASE:
			break;
		case PTC_KILLED:
			executorState.set(executorStateEnum.PTC_FUNCTION);
			break;
		default:
			throw new TtcnError("Internal error: Message KILLED_ACK arrived in invalid state.");
		}

		if (killed_status) {
			set_component_killed(create_done_killed_compref.get());
		}

		create_done_killed_compref.set(TitanComponent.NULL_COMPREF);
	}

	public static void process_ptc_verdict(final Text_Buf text_buf) {
		if (executorState.get() != executorStateEnum.MTC_TERMINATING_TESTCASE) {
			throw new TtcnError("Internal error: Message PTC_VERDICT arrived in invalid state.");
		}

		VerdictTypeEnum localVerdictEnum = localVerdict.get();
		TTCN_Logger.log_final_verdict(false, localVerdictEnum, localVerdictEnum, localVerdictEnum, verdictReason.get(), TitanLoggerApi.FinalVerdictType_choice_notification.enum_type.setting__final__verdict__of__the__test__case.ordinal(), TitanComponent.UNBOUND_COMPREF, null);
		TTCN_Logger.log_final_verdict(false, localVerdictEnum, localVerdictEnum, localVerdictEnum, verdictReason.get(), -1, TitanComponent.UNBOUND_COMPREF, null);

		final int n_PTCS = text_buf.pull_int().get_int();
		if (n_PTCS > 0) {
			for (int i = 0; i < n_PTCS; i++) {
				final int ptc_compref = text_buf.pull_int().get_int();
				final String ptc_name = text_buf.pull_string();
				final int verdictInt = text_buf.pull_int().get_int();
				final String ptc_verdict_reason = text_buf.pull_string();
				if (verdictInt < VerdictTypeEnum.NONE.ordinal() || verdictInt > VerdictTypeEnum.ERROR.ordinal()) {
					throw new TtcnError(MessageFormat.format("Internal error: Invalid PTC verdict was received from MC: {0}.", verdictInt));
				}

				final VerdictTypeEnum ptc_verdict = VerdictTypeEnum.values()[verdictInt];
				VerdictTypeEnum newVerdict = localVerdictEnum;
				if (ptc_verdict.ordinal() > localVerdictEnum.ordinal()) {
					newVerdict = ptc_verdict;
					verdictReason.set(ptc_verdict_reason);
				}

				TTCN_Logger.log_final_verdict(true, ptc_verdict, localVerdictEnum, newVerdict, ptc_verdict_reason, -1, ptc_compref, ptc_name);
				localVerdict.set(newVerdict);
				localVerdictEnum = newVerdict;
			}
		} else {
			TTCN_Logger.log_final_verdict(false, localVerdictEnum, localVerdictEnum, localVerdictEnum, verdictReason.get(), TitanLoggerApi.FinalVerdictType_choice_notification.enum_type.no__ptcs__were__created.ordinal(), TitanComponent.UNBOUND_COMPREF, null);
		}

		final boolean continueExecution = text_buf.pull_int().get_int() == 0 ? false : true;
		if (continueExecution) {
			executorState.set(executorStateEnum.MTC_CONTROLPART);
		} else {
			executorState.set(executorStateEnum.MTC_PAUSED);
		}
	}

	public static void process_kill() {
		if (!is_ptc()) {
			throw new TtcnError("Internal error: Message KILL arrived in invalid state.");
		}

		switch (executorState.get()) {
		case PTC_IDLE:
		case PTC_STOPPED:
			TTCN_Logger.log_par_ptc(ParallelPTC_reason.enum_type.kill__request__frm__mc, null, null, 0, null, null, 0, 0);

			// This may affect the final verdict.
			terminate_component_type();

			TTCN_Communication.send_killed(localVerdict.get(), verdictReason.get());
			TTCN_Logger.log_final_verdict(true, localVerdict.get(), localVerdict.get(), localVerdict.get(), verdictReason.get(), -1, TitanComponent.UNBOUND_COMPREF, null);
			executorState.set(executorStateEnum.PTC_EXIT);
			break;
		case PTC_EXIT:
			break;
		default:
			TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, "Kill was requested from MC.");

			kill_execution();
			break;
		}
	}

	public static void process_kill_process(final int component_reference) {
		if (!is_hc()) {
			throw new TtcnError("Internal error: Message KILL_PROCESS arrived in invalid state.");
		}

		final component_thread_struct comp = get_component_by_compref(component_reference);
		if (comp == null) {
			TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, MessageFormat.format("Component with component reference {0} does not exist. Request for killing was ignored.", component_reference));
		} else {
			TTCN_Logger.log_str(Severity.PARALLEL_UNQUALIFIED, MessageFormat.format("Killing component with component reference {0}, thread id: {1}.", component_reference, comp.thread.getId()));

			if (comp.thread_killed) {
				TtcnError.TtcnWarning(MessageFormat.format("Process with process id {0} has been already killed. Killing it again.", comp.thread.getId()));
			}

			comp.thread.stop();
			//TODO check how Java reacts in different situations
			comp.thread_killed = true;
		}
	}

	public static void set_component_done(final int component_reference, final VerdictTypeEnum ptc_verdict, final String return_type, final byte[] data, final int return_value_len, final int buffer_begin, final int return_value_begin) {
		switch (component_reference) {
		case TitanComponent.ANY_COMPREF:
			if (is_mtc()) {
				any_component_done_status = TitanAlt_Status.ALT_YES;
			} else {
				throw new TtcnError("Internal error: TTCN_Runtime.set_component_done(ANY_COMPREF): can be used only on MTC.");
			}
			break;
		case TitanComponent.ALL_COMPREF:
			if (is_mtc()) {
				all_component_done_status = TitanAlt_Status.ALT_YES;
			} else {
				throw new TtcnError("Internal error: TTCN_Runtime.set_component_done(ALL_COMPREF): can be used only on MTC.");
			}
			break;
		case TitanComponent.NULL_COMPREF:
		case TitanComponent.MTC_COMPREF:
		case TitanComponent.SYSTEM_COMPREF:
			throw new TtcnError(MessageFormat.format("Internal error: TTCN_Runtime.set_component_done: invalid component reference: {0}.", component_reference));
		default: {
			final int index = get_component_status_table_index(component_reference);
			final component_status_table_struct temp_struct = component_status_table.get().get(index);
			temp_struct.done_status = TitanAlt_Status.ALT_YES;
			temp_struct.local_verdict = ptc_verdict;
			if (return_type != null && return_type.length() > 0) {
				temp_struct.return_type = return_type;
				temp_struct.return_value = new Text_Buf();
				//TODO can this be done faster?
				final byte[] temp = new byte[return_value_len];
				System.arraycopy(data, buffer_begin + return_value_begin, temp, 0, return_value_len);
				temp_struct.return_value.push_raw(temp);
			} else {
				temp_struct.return_type = null;
				temp_struct.return_value = null;
			}
		}
		}
	}

	public static void set_component_killed(final int component_reference) {
		switch (component_reference) {
		case TitanComponent.ANY_COMPREF:
			if (is_mtc()) {
				any_component_killed_status = TitanAlt_Status.ALT_YES;
			} else {
				throw new TtcnError("Internal error: TTCN_Runtime.set_component_killed(ANY_COMPREF): can be used only on MTC.");
			}
			break;
		case TitanComponent.ALL_COMPREF:
			if (is_mtc()) {
				all_component_killed_status = TitanAlt_Status.ALT_YES;
			} else {
				throw new TtcnError("Internal error: TTCN_Runtime.set_component_killed(ALL_COMPREF): can be used only on MTC.");
			}
			break;
		case TitanComponent.NULL_COMPREF:
		case TitanComponent.MTC_COMPREF:
		case TitanComponent.SYSTEM_COMPREF:
			throw new TtcnError(MessageFormat.format("Internal error: TTCN_Runtime.set_component_killed: invalid component reference: {0}.", component_reference));
		default:{
			final int index = get_component_status_table_index(component_reference);
			component_status_table.get().get(index).killed_status = TitanAlt_Status.ALT_YES;
		}
		}
	}

	public static void cancel_component_done(final int component_reference) {
		switch (component_reference) {
		case TitanComponent.ANY_COMPREF:
			if (is_mtc()) {
				any_component_done_status = TitanAlt_Status.ALT_UNCHECKED;
			} else {
				throw new TtcnError("Internal error: TTCN_Runtime.cancel_component_done(ANY_COMPREF): can be used only on MTC.");
			}
			break;
		case TitanComponent.ALL_COMPREF:
		case TitanComponent.NULL_COMPREF:
		case TitanComponent.MTC_COMPREF:
		case TitanComponent.SYSTEM_COMPREF:
			throw new TtcnError(MessageFormat.format("Internal error: TTCN_Runtime.cancel_component_done: invalid component reference: {0}.", component_reference));
		default:
			if (in_component_status_table(component_reference)) {
				final int index = get_component_status_table_index(component_reference);
				final component_status_table_struct temp = component_status_table.get().get(index);
				temp.done_status = TitanAlt_Status.ALT_UNCHECKED;
				temp.return_type = null;
				temp.return_value = null;
			}
		}
	}

	private static int get_component_status_table_index(final int component_reference) {
		if (component_reference < TitanComponent.FIRST_PTC_COMPREF) {
			throw new TtcnError(MessageFormat.format("Internal error: TTCN_Runtime.get_component_status_table_index: invalid component reference: {0}.", component_reference));
		}

		final ArrayList<component_status_table_struct> localTables = component_status_table.get();
		if (localTables.isEmpty()) {
			//the table is empty, this will be the first entry
			final component_status_table_struct temp = new component_status_table_struct();
			temp.done_status = TitanAlt_Status.ALT_UNCHECKED;
			temp.killed_status = TitanAlt_Status.ALT_UNCHECKED;
			temp.local_verdict = TitanVerdictType.VerdictTypeEnum.NONE;
			temp.return_type = null;
			temp.return_value = null;

			localTables.add(temp);
			component_status_table_offset.set(component_reference);

			return 0;
		} else if (component_reference >= component_status_table_offset.get().intValue()) {
			// the table contains at least one entry that is smaller than component_reference
			final int component_index = component_reference - component_status_table_offset.get().intValue();
			if (component_index >= localTables.size()) {
				// component_reference is still not in the table
				// the table has to be extended at the end
				localTables.ensureCapacity(component_index);
				for (int i = localTables.size(); i <= component_index; i++) {
					final component_status_table_struct temp = new component_status_table_struct();
					temp.done_status = TitanAlt_Status.ALT_UNCHECKED;
					temp.killed_status = TitanAlt_Status.ALT_UNCHECKED;
					temp.local_verdict = TitanVerdictType.VerdictTypeEnum.NONE;
					temp.return_type = null;
					temp.return_value = null;

					localTables.add(i, temp);
				}
			}

			return component_index;
		} else {
			// component_reference has to be inserted before the existing table
			final int offset_diff = component_status_table_offset.get().intValue() - component_reference;
			final ArrayList<component_status_table_struct> temp_table = new ArrayList<TTCN_Runtime.component_status_table_struct>(offset_diff);
			for (int i = 0; i < offset_diff; i++) {
				final component_status_table_struct temp = new component_status_table_struct();
				temp.done_status = TitanAlt_Status.ALT_UNCHECKED;
				temp.killed_status = TitanAlt_Status.ALT_UNCHECKED;
				temp.local_verdict = TitanVerdictType.VerdictTypeEnum.NONE;
				temp.return_type = null;
				temp.return_value = null;

				temp_table.add(i, temp);
			}
			localTables.addAll(0, temp_table);
			component_status_table_offset.set(component_reference);

			return 0;
		}
	}

	private static TitanAlt_Status get_killed_status(final int component_reference) {
		final int index = get_component_status_table_index(component_reference);
		return component_status_table.get().get(index).killed_status;
	}

	private static boolean in_component_status_table(final int component_reference) {
		return component_reference >= component_status_table_offset.get().intValue() && component_reference < component_status_table.get().size() + component_status_table_offset.get().intValue();
	}

	private static void clear_component_status_table() {
		component_status_table.get().clear();
		component_status_table_offset.set(TitanComponent.FIRST_PTC_COMPREF);
	}

	private static void initialize_component_process_tables() {
		components_by_compref = new HashMap<Integer, TTCN_Runtime.component_thread_struct>(HASHTABLE_SIZE);
		components_by_thread = new HashMap<Thread, TTCN_Runtime.component_thread_struct>(HASHTABLE_SIZE);
	}

	private static void add_component(final int component_reference, final Thread thread) {
		if (component_reference != TitanComponent.MTC_COMPREF && get_component_by_compref(component_reference) != null) {
			throw new TtcnError(MessageFormat.format("Internal error: TTCN_Runtime.add_component: duplicated component reference ({0})", component_reference));
		}

		if (get_component_by_thread(thread) != null) {
			throw new TtcnError(MessageFormat.format("Internal error: TTCN_Runtime.add_component: duplicated thread ({0})", thread));
		}

		final component_thread_struct newComp = new component_thread_struct();
		newComp.component_reference = component_reference;
		newComp.thread = thread;
		newComp.thread_killed = false;

		components_by_compref.put(component_reference, newComp);
		components_by_thread.put(thread, newComp);
	}

	private static void remove_component(final component_thread_struct comp) {
		components_by_compref.remove(comp.component_reference);
		components_by_thread.remove(comp.thread);
	}

	private static component_thread_struct get_component_by_compref(final int component_reference) {
		return components_by_compref.get(component_reference);
	}

	private static component_thread_struct get_component_by_thread(final Thread thread) {
		return components_by_thread.get(thread);
	}

	private static void clear_component_process_tables() {
		if (components_by_compref == null) {
			return;
		}

		components_by_compref.clear();
		components_by_thread.clear();
	}

	private static void successful_process_creation() {
		if (is_overloaded()) {
			TTCN_Communication.send_hc_ready();
			TTCN_Communication.disable_periodic_call();
			executorState.set(executorStateEnum.HC_ACTIVE);
		}
	}

	private static void failed_process_creation() {
		if (executorState.get() == executorStateEnum.HC_ACTIVE) {
			TTCN_Communication.enable_periodic_call();
			executorState.set(executorStateEnum.HC_OVERLOADED);
		}
	}

	public static void wait_terminated_processes() {
		if (!is_hc()) {
			return;
		}

		for (int i = 0; i < threads.size(); ) {
			if (threads.get(i).getState() == State.TERMINATED) {
				final Thread thread = threads.get(i);
				threads.remove(i);

				final component_thread_struct comp = get_component_by_thread(thread);
				if (comp == null) {
					TtcnError.TtcnWarning(MessageFormat.format("wait_terminated_processes found unknown thread {0}.", thread));
				} else {
					ParallelPTC_reason.enum_type reason;
					String componentName = null;
					if (comp.component_reference == TitanComponent.MTC_COMPREF) {
						reason = ParallelPTC_reason.enum_type.mtc__finished;
					} else {
						reason = ParallelPTC_reason.enum_type.ptc__finished;
						componentName = TitanComponent.get_component_name(comp.component_reference);
					}

					//TODO add rusage info if possible
					TTCN_Logger.log_par_ptc(reason, null, null, comp.component_reference, componentName, null, 0, 0);
					remove_component(comp);
				}
			} else {
				i++;
			}
		}

	}
}
