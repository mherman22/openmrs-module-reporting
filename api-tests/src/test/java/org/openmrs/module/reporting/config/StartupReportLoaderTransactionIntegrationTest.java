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

import java.io.File;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class StartupReportLoaderTransactionIntegrationTest extends BaseModuleContextSensitiveTest {

    @Autowired @Qualifier("reportingReportDefinitionService")
    ReportDefinitionService reportDefinitionService;

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
     * The only descriptor here fails while saving, which leaves the session it was saved in unusable.
     * Whoever set the property has to be able to go on using theirs.
     */
    @Test
    public void shouldLeaveTheSessionOfWhoeverSetThePropertyUsable() {
        Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(
                ReportingConstants.GLOBAL_PROPERTY_LOAD_REPORTS_FROM_CONFIGURATION_AT_STARTUP, "true"));

        assertThat(reportDefinitionService.getAllDefinitions(false), notNullValue());
        assertThat(Context.getAdministrationService().getGlobalProperty(
                ReportingConstants.GLOBAL_PROPERTY_LOAD_REPORTS_FROM_CONFIGURATION_AT_STARTUP), is("true"));
    }
}
