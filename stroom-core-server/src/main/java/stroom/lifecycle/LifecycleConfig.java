package stroom.lifecycle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Singleton;

@Singleton
public class LifecycleConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(LifecycleConfig.class);

    private static final int ONE_SECOND = 1000;
    private static final long DEFAULT_INTERVAL = 10 * ONE_SECOND;

    private boolean enabled = true;
    private String executionInterval = "10s";

    @JsonPropertyDescription("Set this to false for development and testing purposes otherwise the Stroom will try and process files automatically outside of test cases.")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @JsonPropertyDescription("How frequently should the lifecycle service attempt execution.")
    public String getExecutionInterval() {
        return executionInterval;
    }

    public void setExecutionInterval(final String executionInterval) {
        this.executionInterval = executionInterval;
    }

    @JsonIgnore
    long getExecutionIntervalMs() {
        Long ms;
        try {
            ms = ModelStringUtil.parseDurationString(executionInterval);
            if (ms == null) {
                ms = DEFAULT_INTERVAL;
            }
        } catch (final NumberFormatException e) {
            LOGGER.error("Unable to parse property 'stroom.lifecycle.executionInterval' value '" + executionInterval
                    + "', using default of '10s' instead", e);
            ms = DEFAULT_INTERVAL;
        }
        return ms;
    }
}
