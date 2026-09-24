package org.openmrs.module.reporting.config;

import org.hibernate.cfg.Environment;
import org.junit.Test;
import org.openmrs.GlobalProperty;
import org.openmrs.api.context.Context;
import org.openmrs.module.reporting.ReportingConstants;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class StartupReportLoaderTransactionIntegrationTest extends BaseModuleContextSensitiveTest {

    private static final String OVERSIZED_DESCRIPTOR_UUID = "3f1b6a02-1111-4c2a-9f0e-000000000002";

    private static final String VALID_DESCRIPTOR_UUID = "3f1b6a02-1111-4c2a-9f0e-000000000003";

    @Autowired @Qualifier("reportingReportDefinitionService")
    ReportDefinitionService reportDefinitionService;

    @Autowired @Qualifier("transactionManager")
    PlatformTransactionManager transactionManager;

    @Override
    public Properties getRuntimeProperties() {
        Properties p = super.getRuntimeProperties();
        String path = getClass().getClassLoader().getResource("testStartupSaveFailureAppDataDir").getPath()
                + File.separator;
        p.put("connection.url", p.getProperty(Environment.URL));
        p.put(Environment.URL, p.getProperty(Environment.URL) + ";MVCC=TRUE");
        p.put("connection.driver_class", p.getProperty(Environment.DRIVER));
        p.setProperty(OpenmrsConstants.APPLICATION_DATA_DIRECTORY_RUNTIME_PROPERTY, path);
        System.setProperty("OPENMRS_APPLICATION_DATA_DIRECTORY", path);
        return p;
    }

    /**
     * One descriptor here fails while saving, which leaves the session it was saved in unusable.
     * Whoever set the property has to be able to go on using theirs.
     */
    @Test
    public void shouldLeaveTheSessionOfWhoeverSetThePropertyUsable() {
        LogCapture startupLogs = LogCapture.start(StartupReportLoader.class);
        try {
            Context.getAdministrationService().saveGlobalProperty(enabledStartupProperty());

            unlatchedLoader().globalPropertyChanged(enabledStartupProperty());

            assertThat(startupLogs.errorEventNaming(OVERSIZED_DESCRIPTOR_UUID), notNullValue());

            assertThat(reportDefinitionService.getDefinitionByUuid(OVERSIZED_DESCRIPTOR_UUID), nullValue());
            assertThat(Context.getAdministrationService().getGlobalProperty(
                    ReportingConstants.GLOBAL_PROPERTY_LOAD_REPORTS_FROM_CONFIGURATION_AT_STARTUP), is("true"));
        }
        finally {
            startupLogs.stop();
        }
    }

    /**
     * The valid descriptor sorts before the failing one, so it is saved first. The failure that
     * follows it must not take it back out.
     */
    @Test
    public void shouldKeepADescriptorThatSavedBeforeOneThatFailed() {
        LogCapture startupLogs = LogCapture.start(StartupReportLoader.class);
        try {
            unlatchedLoader().globalPropertyChanged(enabledStartupProperty());

            assertThat(startupLogs.errorEventNaming(OVERSIZED_DESCRIPTOR_UUID), notNullValue());
            assertThat(reportDefinitionService.getDefinitionByUuid(VALID_DESCRIPTOR_UUID), notNullValue());
        }
        finally {
            startupLogs.stop();
        }
    }

    /**
     * A load that failed has to be retriable, so a later trigger in the same boot still gets one.
     */
    @Test
    public void shouldLoadAgainAfterALoadThatFailed() {
        StartupReportLoader loader = unlatchedLoader();
        loader.globalPropertyChanged(enabledStartupProperty());

        LogCapture startupLogs = LogCapture.start(StartupReportLoader.class);
        try {
            loader.globalPropertyChanged(enabledStartupProperty());

            assertThat(startupLogs.errorEventNaming(OVERSIZED_DESCRIPTOR_UUID), notNullValue());
        }
        finally {
            startupLogs.stop();
        }
    }

    /**
     * The registered listener latches once it has loaded, which an earlier test in this fork may
     * already have done.
     */
    private StartupReportLoader unlatchedLoader() {
        StartupReportLoader loader = new StartupReportLoader();
        loader.setTransactionManager(transactionManager);
        return loader;
    }

    private GlobalProperty enabledStartupProperty() {
        return new GlobalProperty(
                ReportingConstants.GLOBAL_PROPERTY_LOAD_REPORTS_FROM_CONFIGURATION_AT_STARTUP, "true");
    }
}
