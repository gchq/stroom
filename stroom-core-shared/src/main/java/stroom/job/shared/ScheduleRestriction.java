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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "allowSecond",
        "allowMinute",
        "allowHour"
})
@JsonInclude(Include.NON_NULL)
public class ScheduleRestriction {

    @JsonProperty
    private final boolean allowSecond;
    @JsonProperty
    private final boolean allowMinute;
    @JsonProperty
    private final boolean allowHour;

    @JsonCreator
    public ScheduleRestriction(@JsonProperty("allowSecond") final boolean allowSecond,
                               @JsonProperty("allowMinute") final boolean allowMinute,
                               @JsonProperty("allowHour") final boolean allowHour) {
        this.allowSecond = allowSecond;
        this.allowMinute = allowMinute;
        this.allowHour = allowHour;
    }

    public boolean isAllowSecond() {
        return allowSecond;
    }

    public boolean isAllowMinute() {
        return allowMinute;
    }

    public boolean isAllowHour() {
        return allowHour;
    }
}
