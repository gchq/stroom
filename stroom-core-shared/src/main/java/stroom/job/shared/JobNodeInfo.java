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

@JsonInclude(Include.NON_DEFAULT)
public class JobNodeInfo {
    @JsonProperty
    private Integer currentTaskCount;
    @JsonProperty
    private Long scheduleReferenceTime;
    @JsonProperty
    private Long lastExecutedTime;

    public JobNodeInfo() {
        // Default constructor necessary for GWT serialisation.
    }

    @JsonCreator
    public JobNodeInfo(@JsonProperty("currentTaskCount") final Integer currentTaskCount,
                       @JsonProperty("scheduleReferenceTime") final Long scheduleReferenceTime,
                       @JsonProperty("lastExecutedTime") final Long lastExecutedTime) {
        this.currentTaskCount = currentTaskCount;
        this.scheduleReferenceTime = scheduleReferenceTime;
        this.lastExecutedTime = lastExecutedTime;
    }

    public Integer getCurrentTaskCount() {
        return currentTaskCount;
    }

    public void setCurrentTaskCount(final Integer currentTaskCount) {
        this.currentTaskCount = currentTaskCount;
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
}
