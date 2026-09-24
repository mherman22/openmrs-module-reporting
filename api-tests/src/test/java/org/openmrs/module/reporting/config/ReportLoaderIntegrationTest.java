package org.openmrs.module.reporting.config;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.openmrs.logging.MemoryAppender;
import org.hibernate.cfg.Environment;
import org.junit.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.reporting.cohort.definition.library.BuiltInCohortDefinitionLibrary;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.dataset.definition.DataSetDefinition;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.parameter.Mapped;
import org.openmrs.module.reporting.evaluation.querybuilder.SqlQueryBuilder;
import org.openmrs.module.reporting.evaluation.service.EvaluationService;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.ReportDesignResource;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.openmrs.module.reporting.config.ReportLoader.getReportingDescriptorsConfigurationDir;

public class ReportLoaderIntegrationTest extends BaseModuleContextSensitiveTest {

    public static final String appDataTestDir = "testAppDataDir";

    private static final int FAILING_DESCRIPTOR_COUNT = 6;
    
    @Autowired @Qualifier("reportingReportDefinitionService")
    ReportDefinitionService reportDefinitionService;

    @Autowired @Qualifier("reportingEvaluationService")
    EvaluationService evaluationService;

    @Autowired
    BuiltInCohortDefinitionLibrary cohorts;

    @Override
    public Properties getRuntimeProperties() {
        Properties p = super.getRuntimeProperties();
        String path = getClass().getClassLoader().getResource(appDataTestDir).getPath() + File.separator;
        p.put("connection.url", p.getProperty(Environment.URL));
        p.put(Environment.URL, p.getProperty(Environment.URL) + ";MVCC=TRUE");
        p.put("connection.driver_class", p.getProperty(Environment.DRIVER));
        p.setProperty(OpenmrsConstants.APPLICATION_DATA_DIRECTORY_RUNTIME_PROPERTY, path);
        System.setProperty("OPENMRS_APPLICATION_DATA_DIRECTORY", path);
        return p;
    }

    private List<File> descriptorFilesOnDisk() {
        List<File> files = new ArrayList<File>(FileUtils.listFiles(new File(ReportLoader.getReportingDescriptorsConfigurationDir()),
            FileFilterUtils.suffixFileFilter("yml"), TrueFileFilter.INSTANCE));
        Collections.sort(files);
        return files;
    }

    private boolean descriptorFileExists(String name) {
        return new File(ReportLoader.getReportingDescriptorsConfigurationDir(), name).exists();
    }

    /**
     * The skip tests only pin "the rest still load" while the failing descriptors are the ones the loader
     * reaches first, so anything that reorders them has to fail here rather than pass silently.
     */
    private void assertFailingDescriptorsLoadFirst() {
        for (File file : descriptorFilesOnDisk().subList(0, FAILING_DESCRIPTOR_COUNT)) {
            assertThat(file.getName(), startsWith("failing"));
        }
        // failingParseReport.yml never parses, so it has no position in this list
        List<ReportDescriptor> reportDescriptors = ReportLoader.loadReportDescriptors();
        assertThat(reportDescriptors.get(0).getKey(), is("unsupporteddatasettypeexport"));
        assertThat(reportDescriptors.get(1).getKey(), is("missingtemplateexport"));
    }

    @Test
    public void shouldLoadAllReportDescriptorsInReportDescriptorsDirectory() {
        List<ReportDescriptor> reportDescriptors = ReportLoader.loadReportDescriptors();
        assertThat(reportDescriptors.size(), is(9));

        List<String> names = new ArrayList<String>();
        for (ReportDescriptor reportDescriptor : reportDescriptors) {
            names.add(reportDescriptor.getName());
        }

        assertThat(names, hasItems("sample.export.encounters.name","sample.export.orders.name", "sample.export.nested.name"));
    }

    @Test
    public void shouldSkipMalformedDescriptorsAndLoadTheRest() {
        assertFailingDescriptorsLoadFirst();
        // failingParseReport.yml gives datasets[].config a mapping where DataSetDescriptor declares a String
        List<ReportDescriptor> reportDescriptors = ReportLoader.loadReportDescriptors();

        List<String> names = new ArrayList<String>();
        for (ReportDescriptor reportDescriptor : reportDescriptors) {
            names.add(reportDescriptor.getName());
        }
        assertThat(names.contains("sample.export.malformed.name"), is(false));
        assertThat(reportDescriptors.size(), is(descriptorFilesOnDisk().size() - 1));
        assertThat(names, hasItems("sample.export.encounters.name", "sample.export.orders.name", "sample.export.nested.name"));
    }

    @Test
    public void shouldContinuePastADescriptorThatParsesButFailsToLoad() {
        assertFailingDescriptorsLoadFirst();
        ReportLoader.loadReportsFromConfig();

        assertThat(descriptorFileExists("failingDataSetTypeReport.yml"), is(true));
        assertThat(reportDefinitionService.getDefinitionByUuid("8c4a71e2-9a4d-11ee-b9d1-0242ac120002"), nullValue());
        assertThat(reportDefinitionService.getDefinitionByUuid("9e7dc296-2aad-11e3-a840-5b9e0b589afb"), notNullValue());
        assertThat(reportDefinitionService.getDefinitionByUuid("752e386d-da67-4e3d-bddc-95157c58c54c"), notNullValue());
        assertThat(reportDefinitionService.getDefinitionByUuid("c2fb2082-9b36-4398-96af-d20570bacd07"), notNullValue());
    }

    @Test
    public void shouldNotSaveADefinitionWhoseDesignsFailToBuild() {
        assertFailingDescriptorsLoadFirst();
        ReportLoader.loadReportsFromConfig();

        assertThat(descriptorFileExists("failingTemplateReport.yml"), is(true));
        assertThat(reportDefinitionService.getDefinitionByUuid("a1d5f3c4-9a4d-11ee-b9d1-0242ac120002"), nullValue());
        assertThat(reportDefinitionService.getDefinitionByUuid("9e7dc296-2aad-11e3-a840-5b9e0b589afb"), notNullValue());
    }

    @Test
    public void shouldLoadReportsFromConfigAndSave() {
        ReportLoader.loadReportsFromConfig();

        ReportDefinition ordersReportDefinition = reportDefinitionService.getDefinitionByUuid("9e7dc296-2aad-11e3-a840-5b9e0b589afb");
        ReportDefinition encountersReportDefinition = reportDefinitionService.getDefinitionByUuid("752e386d-da67-4e3d-bddc-95157c58c54c");
        ReportDefinition nestedReportDefinition = reportDefinitionService.getDefinitionByUuid("c2fb2082-9b36-4398-96af-d20570bacd07");

        assertThat(ordersReportDefinition, notNullValue());
        assertThat(encountersReportDefinition, notNullValue());
        assertThat(nestedReportDefinition, notNullValue());

        assertThat(ordersReportDefinition.getName(), is("sample.export.orders.name"));
        assertThat(encountersReportDefinition.getName(), is("sample.export.encounters.name"));
        assertThat(nestedReportDefinition.getName(), is("sample.export.nested.name"));

        List<ReportDesign> existingOrderReportDesigns = Context.getService(ReportService.class).getReportDesigns(ordersReportDefinition, null, true);
        assertThat(existingOrderReportDesigns.size(), is(2));
    }

    @Test
    public void shouldSupportFixedParametersInDataSetDefinitions() throws Exception {
        ReportLoader.loadReportsFromConfig();
        ReportDefinition rd = reportDefinitionService.getDefinitionByUuid("0c32f660-c2de-11eb-b5a4-0242ac110002");
        assertThat(rd.getParameters().size(), is(2));
        Mapped<? extends DataSetDefinition> maleMapped = rd.getDataSetDefinitions().get("males");
        Mapped<? extends DataSetDefinition> femaleMapped = rd.getDataSetDefinitions().get("females");
        assertThat(maleMapped.getParameterizable().getParameters().size(), is(3));
        assertThat(femaleMapped.getParameterizable().getParameters().size(), is(3));
        assertThat(maleMapped.getParameterMappings().get("gender").toString(), is("M"));
        assertThat(femaleMapped.getParameterMappings().get("gender").toString(), is("F"));
        ReportData data = reportDefinitionService.evaluate(rd, new EvaluationContext());
        List<Integer> rptMales = new ArrayList<Integer>();
        List<Integer> rptFemales = new ArrayList<Integer>();
        for (DataSetRow row : data.getDataSets().get("males")) {
            rptMales.add((Integer)row.getColumnValue("person_id"));
        }
        for (DataSetRow row : data.getDataSets().get("females")) {
            rptFemales.add((Integer)row.getColumnValue("person_id"));
        }

        SqlQueryBuilder maleQuery = new SqlQueryBuilder("select person_id from person where gender = 'M'");
        List<Integer> males = evaluationService.evaluateToList(maleQuery, Integer.class, new EvaluationContext());

        SqlQueryBuilder femaleQuery = new SqlQueryBuilder("select person_id from person where gender = 'F'");
        List<Integer> females = evaluationService.evaluateToList(femaleQuery, Integer.class, new EvaluationContext());

        assertThat(males.size(), is(rptMales.size()));
        assertThat(females.size(), is(rptFemales.size()));
        assertTrue(males.containsAll(rptMales));
        assertTrue(females.containsAll(rptFemales));
    }

    @Test
    public void shouldConstructExcelReportDesign() {
        ReportDefinition reportDefinition = new ReportDefinition();
        reportDefinition.setName("My Test Report");

        DesignDescriptor designDescriptor = new DesignDescriptor();
        designDescriptor.setType("excel");
        designDescriptor.setTemplate("templates/SampleReportTemplate.xls");

        ReportDescriptor reportDescriptor = new ReportDescriptor();
        reportDescriptor.setPath(new File(getReportingDescriptorsConfigurationDir()));
        reportDescriptor.setDesigns(new ArrayList<DesignDescriptor>());
        reportDescriptor.getDesigns().add(designDescriptor);

        List<ReportDesign> reportDesigns = ReportLoader.constructReportDesigns(reportDefinition, reportDescriptor);
        assertThat(reportDesigns.size(), is(1));
        assertThat(reportDesigns.get(0).getName(), is("reporting.excel"));
        assertThat(reportDesigns.get(0).getRendererType().getName(), endsWith("XlsReportRenderer"));
        assertThat(reportDesigns.get(0).getReportDefinition(), is(reportDefinition));

        assertThat(reportDesigns.get(0).getResources().size(), is(1));
        ReportDesignResource reportDesignResource = reportDesigns.get(0).getResources().iterator().next();
        assertThat(reportDesignResource.getName(), is("template"));
        assertThat(reportDesignResource.getExtension(), is("xls"));
        assertThat(reportDesignResource.getContentType(), is("application/vnd.ms-excel"));
        assertThat(reportDesignResource.getContents(), is(notNullValue()));
    }

    @Test
    public void shouldLogAtErrorWithItsCauseTheFileOfADescriptorThatFailsToParse() {
        MemoryAppender appender = startCapturingReportLoaderLogs();
        try {
            ReportLoader.loadReportsFromConfig();

            String event = errorEventNaming(appender, "failingParseReport.yml");
            assertThat(event, notNullValue());
            // the logged message names the file, not the reason; Jackson's reference chain has it
            assertThat(throwableOf(event), containsString("DataSetDescriptor[\"config\"]"));
        }
        finally {
            stopCapturingReportLoaderLogs(appender);
        }
    }

    @Test
    public void shouldLogAtErrorWithItsCauseAReportThatFailsToBuild() {
        MemoryAppender appender = startCapturingReportLoaderLogs();
        try {
            ReportLoader.loadReportsFromConfig();

            String event = errorEventNaming(appender, "8c4a71e2-9a4d-11ee-b9d1-0242ac120002");
            assertThat(event, notNullValue());
            assertThat(throwableOf(event), containsString("notaregistereddatasetfactory"));
        }
        finally {
            stopCapturingReportLoaderLogs(appender);
        }
    }

    @Test
    public void shouldLogAtErrorWhateverIdentifierAReportThatFailsToBuildDeclares() {
        MemoryAppender appender = startCapturingReportLoaderLogs();
        try {
            ReportLoader.loadReportsFromConfig();

            assertThat(errorEventNaming(appender, "Unable to load report uuidlessexport"), notNullValue());
            assertThat(errorEventNaming(appender, "Unable to load report sample.export.keyless.name"), notNullValue());
            assertThat(errorEventNaming(appender, "Unable to load report with no uuid, key or name"), notNullValue());
        }
        finally {
            stopCapturingReportLoaderLogs(appender);
        }
    }

    private String errorEventNaming(MemoryAppender appender, String identifier) {
        for (String event : appender.getLogLines()) {
            if (messageOf(event).startsWith("ERROR ") && messageOf(event).contains(identifier)) {
                return event;
            }
        }
        return null;
    }

    /**
     * Returns the message without the stack trace log4j2 appends after it, so an assertion
     * about what was logged cannot be satisfied by the trace.
     */
    private String messageOf(String event) {
        return event.split("\\r?\\n", 2)[0];
    }

    private String throwableOf(String event) {
        String[] messageAndThrowable = event.split("\\r?\\n", 2);
        return messageAndThrowable.length == 2 ? messageAndThrowable[1] : "";
    }

    private MemoryAppender startCapturingReportLoaderLogs() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        MemoryAppender appender = MemoryAppender.newBuilder().setName("REPORT_LOADER_ERRORS")
                .setLayout(PatternLayout.newBuilder().withPattern("%p %m%n%ex").withConfiguration(configuration).build())
                .setConfiguration(configuration).build();
        appender.start();
        configuration.addAppender(appender);
        LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.ALL, ReportLoader.class.getName(), null,
            new AppenderRef[] { AppenderRef.createAppenderRef(appender.getName(), Level.ALL, null) }, null,
            configuration, null);
        loggerConfig.addAppender(appender, Level.ALL, null);
        configuration.addLogger(ReportLoader.class.getName(), loggerConfig);
        context.updateLoggers();
        return appender;
    }

    private void stopCapturingReportLoaderLogs(MemoryAppender appender) {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.getConfiguration().removeLogger(ReportLoader.class.getName());
        appender.stop();
        context.updateLoggers();
    }
}
