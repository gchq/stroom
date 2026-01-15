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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class JobNodeInfo {

    @JsonProperty
    private final Integer currentTaskCount;
    @JsonProperty
    private final Long scheduleReferenceTime;
    @JsonProperty
    private final Long lastExecutedTime;
    @JsonProperty
    private final Long nextScheduledTime;

    @JsonCreator
    public JobNodeInfo(@JsonProperty("currentTaskCount") final Integer currentTaskCount,
                       @JsonProperty("scheduleReferenceTime") final Long scheduleReferenceTime,
                       @JsonProperty("lastExecutedTime") final Long lastExecutedTime,
                       @JsonProperty("nextScheduledTime") final Long nextScheduledTime) {
        this.currentTaskCount = currentTaskCount;
        this.scheduleReferenceTime = scheduleReferenceTime;
        this.lastExecutedTime = lastExecutedTime;
        this.nextScheduledTime = nextScheduledTime;
    }

    public static JobNodeInfo empty() {
        return new JobNodeInfo(
                null, null, null, null);
    }

    public Integer getCurrentTaskCount() {
        return currentTaskCount;
    }

    public Long getScheduleReferenceTime() {
        return scheduleReferenceTime;
    }

    public Long getLastExecutedTime() {
        return lastExecutedTime;
    }

    public Long getNextScheduledTime() {
        return nextScheduledTime;
    }

    @Override
    public String toString() {
        return "JobNodeInfo{" +
                "currentTaskCount=" + currentTaskCount +
                ", scheduleReferenceTime=" + scheduleReferenceTime +
                ", lastExecutedTime=" + lastExecutedTime +
                ", nextScheduledTime=" + nextScheduledTime +
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
        final JobNodeInfo that = (JobNodeInfo) o;
        return Objects.equals(currentTaskCount, that.currentTaskCount) &&
                Objects.equals(scheduleReferenceTime, that.scheduleReferenceTime) &&
                Objects.equals(lastExecutedTime, that.lastExecutedTime) &&
                Objects.equals(nextScheduledTime, that.nextScheduledTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentTaskCount, scheduleReferenceTime, lastExecutedTime, nextScheduledTime);
    }
}
