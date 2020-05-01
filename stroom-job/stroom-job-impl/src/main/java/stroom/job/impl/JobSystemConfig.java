package stroom.job.impl;

import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.ModelStringUtil;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

@Singleton
public class JobSystemConfig extends AbstractConfig implements HasDbConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobSystemConfig.class);

    private static final int ONE_SECOND = 1000;
    private static final long DEFAULT_INTERVAL = 10 * ONE_SECOND;

    private DbConfig dbConfig = new DbConfig();
    private boolean enabled = true;
    private String executionInterval = "10s";

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    @SuppressWarnings("unused")
    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Enables/disables the job system that executes Stroom's enabled scheduled jobs. " +
            "Set this to false for development and testing purposes otherwise the Stroom will " +
            "try and process files automatically outside of test cases.")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("How frequently should the lifecycle service attempt to execute any jobs that " +
            "are ready (by their frequency or schedule) to be executed.")
    public String getExecutionInterval() {
        return executionInterval;
    }

    @SuppressWarnings("unused")
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
            LOGGER.error("Unable to parse property 'stroom.job.executionInterval' value '" + executionInterval
                    + "', using default of '10s' instead", e);
            ms = DEFAULT_INTERVAL;
        }
        return ms;
    }

    @Override
    public String toString() {
        return "JobSystemConfig{" +
                "enabled=" + enabled +
                ", executionInterval='" + executionInterval + '\'' +
                '}';
    }
}
