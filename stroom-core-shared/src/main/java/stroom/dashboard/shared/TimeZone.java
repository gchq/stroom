/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.HasDisplayValue;

@JsonPropertyOrder({"use", "id", "offsetHours", "offsetMinutes"})
@JsonInclude(Include.NON_NULL)
public class TimeZone {
    @JsonProperty
    private final Use use;
    @JsonProperty
    private final String id;
    @JsonProperty
    private final Integer offsetHours;
    @JsonProperty
    private final Integer offsetMinutes;

    @JsonCreator
    public TimeZone(@JsonProperty("use") final Use use,
                    @JsonProperty("id") final String id,
                    @JsonProperty("offsetHours") final Integer offsetHours,
                    @JsonProperty("offsetMinutes") final Integer offsetMinutes) {
        this.use = use;
        this.id = id;
        this.offsetHours = offsetHours;
        this.offsetMinutes = offsetMinutes;
    }

    public static TimeZone local() {
        return new TimeZone(Use.LOCAL, null, null, null);
    }

    public static TimeZone utc() {
        return new TimeZone(Use.UTC, null, null, null);
    }

    public static TimeZone fromId(final String id) {
        return new TimeZone(Use.ID, id, null, null);
    }

    public static TimeZone fromOffset(final int offsetHours, final int offsetMinutes) {
        return new TimeZone(Use.OFFSET, null, offsetHours, offsetMinutes);
    }

    public Use getUse() {
        return use;
    }

    public String getId() {
        return id;
    }

    public Integer getOffsetHours() {
        return offsetHours;
    }

    public Integer getOffsetMinutes() {
        return offsetMinutes;
    }

    public enum Use implements HasDisplayValue {
        LOCAL("Local"), UTC("UTC"), ID("Id"), OFFSET("Offset");

        private final String displayValue;

        Use(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        @Override
        public String toString() {
            return getDisplayValue();
        }
    }
}
