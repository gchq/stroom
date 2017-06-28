package stroom.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.codahale.metrics.health.HealthCheck;
import org.slf4j.LoggerFactory;
import stroom.resources.HasHealthCheck;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public enum LogLevelInspector implements HasHealthCheck{

    INSTANCE;

    public HealthCheck.Result getHealth() {

        LoggerContext loggerContext = ((LoggerContext) LoggerFactory.getILoggerFactory());

        if (loggerContext != null) {
            //use a treemap so se get the levels nicely sorted
            Map<String, String> levels = loggerContext.getLoggerList().stream()
                    .filter(logger -> logger.getLevel() != null)
                    .collect(Collectors.toMap(
                            Logger::getName,
                            logger -> logger.getLevel().levelStr,
                            (v1, v2) -> v1 + ", " + v2, //don't think we will ever get dups, but just in case concat them
                            TreeMap::new));

            return HealthCheck.Result.builder()
                    .healthy()
                    .withDetail("levels", levels)
                    .build();
        } else {
            return HealthCheck.Result.unhealthy("Unable to obtain levels, LoggerContext is null");
        }
    }
}
