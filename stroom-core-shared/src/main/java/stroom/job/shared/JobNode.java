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

package stroom.job.shared;


import stroom.util.shared.AuditInfoBuilder;
import stroom.util.shared.HasAuditInfoGetters;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PrimitiveValueConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;
import java.util.function.Function;

@JsonInclude(Include.NON_NULL)
public class JobNode implements HasAuditInfoGetters, HasIntegerId {

    @JsonProperty
    private final Integer id;
    @JsonProperty
    private final Integer version;
    @JsonProperty
    private final Long createTimeMs;
    @JsonProperty
    private final String createUser;
    @JsonProperty
    private final Long updateTimeMs;
    @JsonProperty
    private final String updateUser;
    @JsonProperty
    private final Job job;
    @JsonProperty
    private final JobType jobType;
    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final Integer taskLimit;
    @JsonProperty
    private final String schedule;
    @JsonProperty
    private final boolean enabled;

    @JsonCreator
    public JobNode(@JsonProperty("id") final Integer id,
                   @JsonProperty("version") final Integer version,
                   @JsonProperty("createTimeMs") final Long createTimeMs,
                   @JsonProperty("createUser") final String createUser,
                   @JsonProperty("updateTimeMs") final Long updateTimeMs,
                   @JsonProperty("updateUser") final String updateUser,
                   @JsonProperty("job") final Job job,
                   @JsonProperty("jobType") final JobType jobType,
                   @JsonProperty("nodeName") final String nodeName,
                   @JsonProperty("taskLimit") final Integer taskLimit,
                   @JsonProperty("schedule") final String schedule,
                   @JsonProperty("enabled") final boolean enabled) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.job = job;
        this.jobType = jobType;
        this.nodeName = nodeName;
        this.taskLimit = taskLimit;
        this.schedule = schedule;
        this.enabled = enabled;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public Integer getVersion() {
        return version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    public Job getJob() {
        return job;
    }

    /**
     * Convenience method for {@link Job#getName()}.
     *
     * @return The job name
     */
    @JsonIgnore
    public String getJobName() {
        return NullSafe.get(job, Job::getName);
    }

    public JobType getJobType() {
        return jobType;
    }

    public String getNodeName() {
        return nodeName;
    }

    public int getTaskLimit() {
        return taskLimit;
    }

    public String getSchedule() {
        return schedule;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return The state of the {@link Job} and {@link JobNode},
     * e.g. ENABLED:DISABLED
     */
    @JsonIgnore
    public String getCombinedStateAsString() {
        final Function<Boolean, String> enableStateFunc = isEnabled -> isEnabled
                ? "ENABLED"
                : "DISABLED";
        return enableStateFunc.apply(job.isEnabled())
               + ":"
               + enableStateFunc.apply(enabled);
    }

    @Override
    public String toString() {
        return "JobNode{" +
               "id=" + id +
               ", version=" + version +
               ", createTimeMs=" + createTimeMs +
               ", createUser='" + createUser + '\'' +
               ", updateTimeMs=" + updateTimeMs +
               ", updateUser='" + updateUser + '\'' +
               ", job=" + job +
               ", jobType=" + jobType +
               ", nodeName='" + nodeName + '\'' +
               ", taskLimit=" + taskLimit +
               ", schedule='" + schedule + '\'' +
               ", enabled=" + enabled +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JobNode jobNode = (JobNode) o;
        return Objects.equals(id, jobNode.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static final class Builder extends AuditInfoBuilder<JobNode, Builder> {

        private Integer id;
        private Integer version;
        private Job job;
        private JobType jobType;
        private String nodeName;
        private Integer taskLimit = 20;
        private String schedule;
        private boolean enabled;

        private Builder() {
        }

        private Builder(final JobNode jobNode) {
            this.id = jobNode.id;
            this.version = jobNode.version;
            this.createTimeMs = jobNode.createTimeMs;
            this.createUser = jobNode.createUser;
            this.updateTimeMs = jobNode.updateTimeMs;
            this.updateUser = jobNode.updateUser;
            this.job = jobNode.job;
            this.jobType = jobNode.jobType;
            this.nodeName = jobNode.nodeName;
            this.taskLimit = jobNode.taskLimit;
            this.schedule = jobNode.schedule;
            this.enabled = jobNode.enabled;
        }

        public Builder id(final Integer id) {
            this.id = id;
            return self();
        }

        public Builder version(final Integer version) {
            this.version = version;
            return self();
        }

        public Builder job(final Job job) {
            this.job = job;
            return self();
        }

        public Builder jobType(final JobType jobType) {
            this.jobType = jobType;
            return self();
        }

        public Builder nodeName(final String nodeName) {
            this.nodeName = nodeName;
            return self();
        }

        public Builder taskLimit(final Integer taskLimit) {
            this.taskLimit = taskLimit;
            return self();
        }

        public Builder schedule(final String schedule) {
            this.schedule = schedule;
            return self();
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public JobNode build() {
            return new JobNode(
                    id,
                    version,
                    createTimeMs,
                    createUser,
                    updateTimeMs,
                    updateUser,
                    job,
                    jobType,
                    nodeName,
                    taskLimit,
                    schedule,
                    enabled);
        }
    }


    // --------------------------------------------------------------------------------


    @Schema
    public enum JobType implements HasPrimitiveValue {
        UNKNOWN("UNKNOWN", 0),
        CRON("Cron", 1),
        FREQUENCY("Frequency", 2),
        DISTRIBUTED("Distributed", 3),
        ;

        public static final PrimitiveValueConverter<JobType> PRIMITIVE_VALUE_CONVERTER =
                PrimitiveValueConverter.create(JobType.class, JobType.values());

        private final String displayValue;
        private final byte primitiveValue;

        JobType(final String displayValue, final int primitiveValue) {
            this.displayValue = displayValue;
            this.primitiveValue = (byte) primitiveValue;
        }

        public String getDisplayValue() {
            return displayValue;
        }

        @Override
        public byte getPrimitiveValue() {
            return primitiveValue;
        }
    }
}
