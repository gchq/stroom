/*
 * Copyright 2024 Crown Copyright
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


import stroom.util.shared.HasAuditInfo;
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
public class JobNode implements HasAuditInfo, HasIntegerId {

    @JsonProperty
    private Integer id;
    @JsonProperty
    private Integer version;
    @JsonProperty
    private Long createTimeMs;
    @JsonProperty
    private String createUser;
    @JsonProperty
    private Long updateTimeMs;
    @JsonProperty
    private String updateUser;
    @JsonProperty
    private Job job;
    @JsonProperty
    private JobType jobType;
    @JsonProperty
    private String nodeName;
    @JsonProperty
    private Integer taskLimit;
    @JsonProperty
    private String schedule;
    @JsonProperty
    private boolean enabled;

    public JobNode() {
        taskLimit = 20;
    }

    public JobNode(final Integer id,
                   final String nodeName,
                   final Job job,
                   final int taskLimit,
                   final JobType jobType,
                   final String schedule,
                   final boolean enabled) {
        this.id = id;
        this.nodeName = nodeName;
        this.job = job;
        this.taskLimit = taskLimit;
        this.jobType = jobType;
        this.schedule = schedule;
        this.enabled = enabled;
    }

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

    public void setId(final Integer id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public void setCreateTimeMs(final Long createTimeMs) {
        this.createTimeMs = createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public void setUpdateTimeMs(final Long updateTimeMs) {
        this.updateTimeMs = updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }

    public Job getJob() {
        return job;
    }

    public void setJob(final Job job) {
        this.job = job;
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

    public void setJobType(final JobType jobType) {
        this.jobType = jobType;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
    }

    public int getTaskLimit() {
        return taskLimit;
    }

    public void setTaskLimit(final int taskLimit) {
        this.taskLimit = taskLimit;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(final String schedule) {
        this.schedule = schedule;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
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
