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

import stroom.job.shared.JobNode.JobType;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobNodeAndInfo {

    @JsonProperty
    private final JobNode jobNode;

    @JsonProperty
    // Can be set lazily
    private JobNodeInfo jobNodeInfo;

    @JsonCreator
    public JobNodeAndInfo(@JsonProperty("jobNode") final JobNode jobNode,
                          @JsonProperty("jobNodeInfo") final JobNodeInfo jobNodeInfo) {
        this.jobNode = jobNode;
        this.jobNodeInfo = jobNodeInfo;
    }

    public static JobNodeAndInfo withoutInfo(final JobNode jobNode) {
        return new JobNodeAndInfo(jobNode, null);
    }

    public static JobNodeAndInfo empty() {
        return new JobNodeAndInfo(null, null);
    }

    public JobNode getJobNode() {
        return jobNode;
    }

    /**
     * May be null. Intended to be lazily populated as the info must come from the node itself
     */
    public JobNodeInfo getJobNodeInfo() {
        return jobNodeInfo;
    }

    public void setJobNodeInfo(final JobNodeInfo jobNodeInfo) {
        this.jobNodeInfo = jobNodeInfo;
    }

    public void clearJobNodeInfo() {
        this.jobNodeInfo = null;
    }

    @Override
    public String toString() {
        return "JobNodeAndInfo{" +
                "jobNode=" + jobNode +
                ", jobNodeInfo=" + jobNodeInfo +
                '}';
    }

    // -----------------------------
    // Delegate methods from jobNode
    // -----------------------------

    /**
     * @return {@link JobNode#getId()}
     */
    @JsonIgnore
    public Integer getId() {
        return NullSafe.get(jobNode, JobNode::getId);
    }

    @JsonIgnore
    public Job getJob() {
        return NullSafe.get(jobNode, JobNode::getJob);
    }

    @JsonIgnore
    public String getJobName() {
        return NullSafe.get(jobNode, JobNode::getJobName);
    }

    @JsonIgnore
    public JobType getJobType() {
        return NullSafe.get(jobNode, JobNode::getJobType);
    }

    @JsonIgnore
    public String getNodeName() {
        return NullSafe.get(jobNode, JobNode::getNodeName);
    }

    @JsonIgnore
    public int getTaskLimit() {
        return NullSafe.get(jobNode, JobNode::getTaskLimit);
    }

    @JsonIgnore
    public String getSchedule() {
        return NullSafe.get(jobNode, JobNode::getSchedule);
    }

    @JsonIgnore
    public boolean isEnabled() {
        return NullSafe.get(jobNode, JobNode::isEnabled);
    }

    // ---------------------------------
    // Delegate methods from jobNodeInfo
    // ---------------------------------

    @JsonIgnore
    public Integer getCurrentTaskCount() {
        return NullSafe.get(jobNodeInfo, JobNodeInfo::getCurrentTaskCount);
    }

    @JsonIgnore
    public Long getScheduleReferenceTime() {
        return NullSafe.get(jobNodeInfo, JobNodeInfo::getScheduleReferenceTime);
    }

    @JsonIgnore
    public Long getLastExecutedTime() {
        return NullSafe.get(jobNodeInfo, JobNodeInfo::getLastExecutedTime);
    }

    @JsonIgnore
    public Long getNextScheduledTime() {
        return NullSafe.get(jobNodeInfo, JobNodeInfo::getNextScheduledTime);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final JobNodeAndInfo that = (JobNodeAndInfo) object;
        return Objects.equals(jobNode, that.jobNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobNode);
    }
}
