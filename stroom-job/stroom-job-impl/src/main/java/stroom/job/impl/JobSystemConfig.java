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

    public static final String PROP_NAME_ENABLE_PROCESSING = "enableDistributedJobsOnBootstrap";

    private static final int ONE_SECOND = 1000;
    private static final long DEFAULT_INTERVAL = 10 * ONE_SECOND;

    private DbConfig dbConfig = new DbConfig();
    private boolean enabled = true;
    private boolean enableDistributedJobsOnBootstrap = true;
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
    @JsonPropertyDescription("Enables/disables the job system that manages the execution of Stroom's enabled " +
            "scheduled jobs. " +
            "Set this to false for development and testing purposes otherwise the Stroom will " +
            "try and process files automatically outside of test cases.")
    public boolean isEnabled() {
        return enabled;
    }

    @SuppressWarnings("unused")
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @JsonPropertyDescription("On boot Stroom will ensure all distributed " +
            "jobs are created. If this property is set to true the distributed jobs will be set to enabled on creation, " +
            "else they will be disabled. Data Processing is one such job. " +
            "This only applies to a fresh install, an upgrade or the addition of a new node to the cluster." +
            "This property should be set to false " +
            "for production systems to avoid the risk of processing starting immediately after an upgrade.")
    @JsonProperty(PROP_NAME_ENABLE_PROCESSING)
    public boolean isEnableDistributedJobsOnBootstrap() {
        return enableDistributedJobsOnBootstrap;
    }

    @SuppressWarnings("unused")
    public void setEnableDistributedJobsOnBootstrap(final boolean enableDistributedJobsOnBootstrap) {
        this.enableDistributedJobsOnBootstrap = enableDistributedJobsOnBootstrap;
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
