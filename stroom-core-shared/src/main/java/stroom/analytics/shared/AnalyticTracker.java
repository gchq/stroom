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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticTracker {

    @JsonProperty
    private String analyticUuid;
    @JsonProperty
    private AnalyticTrackerData analyticTrackerData;

    @JsonCreator
    public AnalyticTracker(@JsonProperty("analyticUuid") final String analyticUuid,
                           @JsonProperty("analyticTrackerData") final AnalyticTrackerData analyticTrackerData) {
        this.analyticUuid = analyticUuid;
        this.analyticTrackerData = analyticTrackerData;
    }

    public String getAnalyticUuid() {
        return analyticUuid;
    }

    public void setAnalyticUuid(final String analyticUuid) {
        this.analyticUuid = analyticUuid;
    }

    public AnalyticTrackerData getAnalyticTrackerData() {
        return analyticTrackerData;
    }

    public void setAnalyticTrackerData(final AnalyticTrackerData analyticTrackerData) {
        this.analyticTrackerData = analyticTrackerData;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnalyticTracker that = (AnalyticTracker) o;
        return Objects.equals(analyticUuid, that.analyticUuid) &&
                Objects.equals(analyticTrackerData, that.analyticTrackerData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(analyticUuid, analyticTrackerData);
    }
}
