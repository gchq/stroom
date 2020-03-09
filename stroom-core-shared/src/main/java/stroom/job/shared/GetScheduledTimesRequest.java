/*
 * Copyright 2016 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.job.shared.JobNode.JobType;

@JsonInclude(Include.NON_NULL)
public class GetScheduledTimesRequest {
    @JsonProperty
    private JobType jobType;
    @JsonProperty
    private Long scheduleReferenceTime;
    @JsonProperty
    private Long lastExecutedTime;
    @JsonProperty
    private String schedule;

    public GetScheduledTimesRequest() {
    }

    @JsonCreator
    public GetScheduledTimesRequest(@JsonProperty("jobType") final JobType jobType,
                                    @JsonProperty("scheduleReferenceTime") final Long scheduleReferenceTime,
                                    @JsonProperty("lastExecutedTime") final Long lastExecutedTime,
                                    @JsonProperty("schedule") final String schedule) {
        this.jobType = jobType;
        this.scheduleReferenceTime = scheduleReferenceTime;
        this.lastExecutedTime = lastExecutedTime;
        this.schedule = schedule;
    }

    public JobType getJobType() {
        return jobType;
    }

    public void setJobType(final JobType jobType) {
        this.jobType = jobType;
    }

    public Long getScheduleReferenceTime() {
        return scheduleReferenceTime;
    }

    public void setScheduleReferenceTime(final Long scheduleReferenceTime) {
        this.scheduleReferenceTime = scheduleReferenceTime;
    }

    public Long getLastExecutedTime() {
        return lastExecutedTime;
    }

    public void setLastExecutedTime(final Long lastExecutedTime) {
        this.lastExecutedTime = lastExecutedTime;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(final String schedule) {
        this.schedule = schedule;
    }
}
