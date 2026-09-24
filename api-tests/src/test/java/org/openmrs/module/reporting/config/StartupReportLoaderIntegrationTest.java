package org.openmrs.module.reporting.config;

import org.hibernate.cfg.Environment;
import org.junit.Test;
import org.openmrs.GlobalProperty;
import org.openmrs.api.context.Context;
import org.openmrs.module.reporting.ReportingConstants;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class StartupReportLoaderIntegrationTest extends BaseModuleContextSensitiveTest {

    @Autowired @Qualifier("reportingReportDefinitionService")
    ReportDefinitionService reportDefinitionService;

    @Override
    public Properties getRuntimeProperties() {
        Properties p = super.getRuntimeProperties();
        String path = getClass().getClassLoader().getResource("testStartupAppDataDir").getPath() + File.separator;
        p.put("connection.url", p.getProperty(Environment.URL));
        p.put(Environment.URL, p.getProperty(Environment.URL) + ";MVCC=TRUE");
        p.put("connection.driver_class", p.getProperty(Environment.DRIVER));
        p.setProperty(OpenmrsConstants.APPLICATION_DATA_DIRECTORY_RUNTIME_PROPERTY, path);
        System.setProperty("OPENMRS_APPLICATION_DATA_DIRECTORY", path);
        return p;
    }

    private List<String> savedDefinitionNames() {
        List<String> names = new ArrayList<String>();
        for (ReportDefinition reportDefinition : reportDefinitionService.getAllDefinitions(false)) {
            names.add(reportDefinition.getName());
        }
        return names;
    }

    @Test
    public void shouldLoadReportsWhenTheStartupPropertyIsSetAfterTheModuleHasStarted() {
        assertThat(savedDefinitionNames().isEmpty(), is(true));

        Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(
                ReportingConstants.GLOBAL_PROPERTY_LOAD_REPORTS_FROM_CONFIGURATION_AT_STARTUP, "true"));

        assertThat(savedDefinitionNames(), hasItem("startup.valid.name"));
    }

    /**
     * ReportingModuleActivator.started() looks this component up by name, so an inner bean or a
     * renamed one would stop the module rather than fail a build.
     */
    @Test
    public void shouldRegisterTheLoaderUnderTheNameTheActivatorLooksUp() {
        assertThat(Context.getRegisteredComponent("reportingStartupReportLoader", StartupReportLoader.class),
                notNullValue());
    }
}
