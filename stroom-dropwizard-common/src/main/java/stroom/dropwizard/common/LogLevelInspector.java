package stroom.dropwizard.common;

import stroom.util.HasHealthCheck;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.codahale.metrics.health.HealthCheck;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Class to present all the defined log levels as either a {@link SystemInfoResult} or a {@link HealthCheck}
 * so you can view all log levels as they are at run time. Pick an interface to bind with Guice.
 * Log levels can be changed using 'httpie' like this:
 * http -f POST http://127.0.0.1:8080/admin/tasks/log-level logger=stroom.statistics.internal.MultiServiceInternalStatisticsReceiver level=TRACE
 */
public class LogLevelInspector implements HasSystemInfo, HasHealthCheck {

    @Override
    public SystemInfoResult getSystemInfo() {
        LoggerContext loggerContext = getLoggerContext();

        if (loggerContext != null) {
            Map<String, String> levels = getLogLevels(loggerContext);

            return SystemInfoResult.builder(getSystemInfoName())
                    .withDetail("levels", levels)
                    .build();
        } else {
            return SystemInfoResult.builder(getSystemInfoName())
                    .withError("Unable to obtain levels, LoggerContext is null")
                    .build();
        }
    }

    @Override
    public HealthCheck.Result getHealth() {
        LoggerContext loggerContext = getLoggerContext();

        if (loggerContext != null) {
            Map<String, String> levels = getLogLevels(loggerContext);

            return HealthCheck.Result.builder()
                    .healthy()
                    .withDetail("levels", levels)
                    .build();
        } else {
            return HealthCheck.Result.unhealthy("Unable to obtain levels, LoggerContext is null");
        }
    }

    private LoggerContext getLoggerContext() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }


    private Map<String, String> getLogLevels(final LoggerContext loggerContext) {
        // use a treemap so get the levels nicely sorted
        return loggerContext.getLoggerList().stream()
                .filter(logger -> logger.getLevel() != null)
                .collect(Collectors.toMap(
                        Logger::getName,
                        logger -> logger.getLevel().levelStr,
                        (v1, v2) -> v1 + ", " + v2, //don't think we will ever get dups, but just in case concat them
                        TreeMap::new));
    }
}