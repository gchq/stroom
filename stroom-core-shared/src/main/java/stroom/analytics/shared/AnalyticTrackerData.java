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

package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StreamingAnalyticTrackerData.class, name = "streaming"),
        @JsonSubTypes.Type(value = TableBuilderAnalyticTrackerData.class, name = "table_builder"),
        @JsonSubTypes.Type(value = ScheduledQueryAnalyticTrackerData.class, name = "scheduled_query"),
})
public abstract sealed class AnalyticTrackerData permits
        StreamingAnalyticTrackerData,
        TableBuilderAnalyticTrackerData,
        ScheduledQueryAnalyticTrackerData {

    @JsonProperty
    private String message;

    public AnalyticTrackerData() {
    }

    public AnalyticTrackerData(final String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnalyticTrackerData that = (AnalyticTrackerData) o;
        return Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message);
    }

    @Override
    public String toString() {
        return "AnalyticTrackerData{" +
               "message='" + message + '\'' +
               '}';
    }
}
