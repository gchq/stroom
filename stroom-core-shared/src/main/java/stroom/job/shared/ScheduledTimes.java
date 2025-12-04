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

import stroom.util.shared.scheduler.Schedule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema
@JsonInclude(Include.NON_NULL)
public class ScheduledTimes {

    @JsonProperty
    private final Schedule schedule;
    @JsonProperty
    private final Long nextScheduledTimeMs;
    @JsonProperty
    private final String error;

    @JsonCreator
    public ScheduledTimes(@JsonProperty("schedule") final Schedule schedule,
                          @JsonProperty("nextScheduledTimeMs") final Long nextScheduledTimeMs,
                          @JsonProperty("error") final String error) {
        this.schedule = schedule;
        this.nextScheduledTimeMs = nextScheduledTimeMs;
        this.error = error;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public Long getNextScheduledTimeMs() {
        return nextScheduledTimeMs;
    }

    public String getError() {
        return error;
    }

    @JsonIgnore
    public boolean isError() {
        return error != null && error.trim().length() > 0;
    }
}
