/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reporting.config;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.GlobalProperty;
import org.openmrs.api.GlobalPropertyListener;
import org.openmrs.module.reporting.ReportingConstants;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads the report descriptors once the startup global property is known to be enabled, whether it
 * was already set when the module started or is set later in the same boot.
 */
public class StartupReportLoader implements GlobalPropertyListener {

	protected static final Log log = LogFactory.getLog(StartupReportLoader.class);

	private PlatformTransactionManager transactionManager;

	private final AtomicBoolean loaded = new AtomicBoolean();

	public void setTransactionManager(PlatformTransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

	@Override
	public boolean supportsPropertyName(String propertyName) {
		return ReportingConstants.GLOBAL_PROPERTY_LOAD_REPORTS_FROM_CONFIGURATION_AT_STARTUP.equals(propertyName);
	}

	/**
	 * The property is still uncommitted here, so the value has to come from the event rather than
	 * from a fresh read.
	 */
	@Override
	public void globalPropertyChanged(GlobalProperty globalProperty) {
		load(Boolean.parseBoolean(globalProperty.getPropertyValue()));
	}

	@Override
	public void globalPropertyDeleted(String propertyName) {
	}

	/**
	 * Loads the descriptors if the property is already enabled, for a boot where nothing sets it.
	 */
	public void loadIfEnabled() {
		load(ReportingConstants.GLOBAL_PROPERTY_LOAD_REPORTS_FROM_CONFIGURATION_AT_STARTUP());
	}

	private void load(boolean enabled) {
		if (!enabled || loaded.get()) {
			return;
		}
		try {
			// a save that triggered this has not committed, so the load needs its own transaction
			TransactionTemplate template = new TransactionTemplate(transactionManager);
			template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
			template.execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					ReportLoader.loadReportsFromConfig();
				}
			});
			loaded.set(true);
		}
		catch (Exception e) {
			// throwing here would roll back whatever save notified this listener
			log.error("Unable to load reports from configuration", e);
		}
	}
}
