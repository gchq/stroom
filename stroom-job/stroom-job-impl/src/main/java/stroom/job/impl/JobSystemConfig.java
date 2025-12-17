/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.job.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.ModelStringUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@JsonPropertyOrder(alphabetic = true)
public class JobSystemConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobSystemConfig.class);

    public static final String PROP_NAME_ENABLE_PROCESSING = "enableJobsOnBootstrap";

    private static final int ONE_SECOND = 1000;
    private static final long DEFAULT_INTERVAL = 10 * ONE_SECOND;

    private final JobSystemDbConfig dbConfig;
    private final boolean enabled;
    private final boolean enableJobsOnBootstrap;
    private final String executionInterval;

    public JobSystemConfig() {
        dbConfig = new JobSystemDbConfig();
        enabled = true;
        enableJobsOnBootstrap = false;
        executionInterval = "10s";
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public JobSystemConfig(@JsonProperty("db") final JobSystemDbConfig dbConfig,
                           @JsonProperty("enabled") final boolean enabled,
                           @JsonProperty(PROP_NAME_ENABLE_PROCESSING) final boolean enableJobsOnBootstrap,
                           @JsonProperty("executionInterval") final String executionInterval) {
        this.dbConfig = dbConfig;
        this.enabled = enabled;
        this.enableJobsOnBootstrap = enableJobsOnBootstrap;
        this.executionInterval = executionInterval;
    }

    @Override
    @JsonProperty("db")
    public JobSystemDbConfig getDbConfig() {
        return dbConfig;
    }

    @SuppressWarnings("unused")

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Enables/disables the job system that manages the execution of Stroom's enabled " +
            "scheduled jobs. " +
            "Set this to false for development and testing purposes otherwise the Stroom will " +
            "try and process files automatically outside of test cases.")
    public boolean isEnabled() {
        return enabled;
    }

    @JsonPropertyDescription("On boot Stroom will ensure all jobs are created. " +
            "If this property is set to true the jobs will also be set to enabled upon creation, else they will be " +
            "disabled. " +
            "This should only be set to true in a demo environment and never in production therefore default is false.")
    @JsonProperty(PROP_NAME_ENABLE_PROCESSING)
    public boolean isEnableJobsOnBootstrap() {
        return enableJobsOnBootstrap;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("How frequently should the lifecycle service attempt to execute any jobs that " +
            "are ready (by their frequency or schedule) to be executed.")
    public String getExecutionInterval() {
        return executionInterval;
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

    @BootStrapConfig
    public static class JobSystemDbConfig extends AbstractDbConfig {

        public JobSystemDbConfig() {
            super();
        }

        @SuppressWarnings("unused")
        @JsonCreator
        public JobSystemDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
