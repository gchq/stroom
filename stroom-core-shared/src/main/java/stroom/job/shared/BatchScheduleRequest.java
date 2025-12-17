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
import stroom.util.shared.scheduler.Schedule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Set;

@SuppressWarnings("ClassCanBeRecord")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class BatchScheduleRequest {

    @JsonProperty
    private final Set<Integer> jobNodeIds;
    @JsonProperty
    private final JobType jobType;
    @JsonProperty
    private final Schedule schedule;

    @JsonCreator
    public BatchScheduleRequest(@JsonProperty("jobNodeIds") final Set<Integer> jobNodeIds,
                                @JsonProperty("jobType") final JobType jobType,
                                @JsonProperty("schedule") final Schedule schedule) {
        this.jobNodeIds = jobNodeIds;
        this.jobType = jobType;
        this.schedule = schedule;
    }

    public Set<Integer> getJobNodeIds() {
        return jobNodeIds;
    }

    public JobType getJobType() {
        return jobType;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    @Override
    public String toString() {
        return "BatchScheduleRequest{" +
                "jobNodeIds=" + jobNodeIds +
                ", jobType=" + jobType +
                ", schedule=" + schedule +
                '}';
    }
}
