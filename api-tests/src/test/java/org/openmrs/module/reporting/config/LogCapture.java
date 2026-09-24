package org.openmrs.module.reporting.config;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.openmrs.logging.MemoryAppender;

/**
 * Collects what one logger emits, so a test can assert on the message rather than on the stack
 * trace log4j2 appends after it.
 */
class LogCapture {

    private final String loggerName;

    private final MemoryAppender appender;

    static LogCapture start(Class<?> loggerClass) {
        return new LogCapture(loggerClass.getName());
    }

    private LogCapture(String loggerName) {
        this.loggerName = loggerName;
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        appender = MemoryAppender.newBuilder().setName(loggerName + "_ERRORS")
                .setLayout(PatternLayout.newBuilder().withPattern("%p %m%n%ex").withConfiguration(configuration).build())
                .setConfiguration(configuration).build();
        appender.start();
        configuration.addAppender(appender);
        LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.ALL, loggerName, null,
            new AppenderRef[] { AppenderRef.createAppenderRef(appender.getName(), Level.ALL, null) }, null,
            configuration, null);
        loggerConfig.addAppender(appender, Level.ALL, null);
        configuration.addLogger(loggerName, loggerConfig);
        context.updateLoggers();
    }

    void stop() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.getConfiguration().removeLogger(loggerName);
        appender.stop();
        context.updateLoggers();
    }

    String errorEventNaming(String identifier) {
        for (String event : appender.getLogLines()) {
            if (messageOf(event).startsWith("ERROR ") && messageOf(event).contains(identifier)) {
                return event;
            }
        }
        return null;
    }

    String throwableOf(String event) {
        String[] messageAndThrowable = event.split("\\r?\\n", 2);
        return messageAndThrowable.length == 2 ? messageAndThrowable[1] : "";
    }

    private String messageOf(String event) {
        return event.split("\\r?\\n", 2)[0];
    }
}
