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

import stroom.query.api.DateTimeSettings;
import stroom.query.api.OffsetRange;
import stroom.query.api.TimeRange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Criteria class.
 */
@JsonInclude(Include.NON_NULL)
public class GetAnalyticShardDataRequest {

    @JsonProperty
    private final OffsetRange requestedRange;
    @JsonProperty
    private final TimeRange timeRange;
    @JsonProperty
    private final String analyticDocUuid;
    @JsonProperty
    private final String path;
    @JsonProperty
    private final DateTimeSettings dateTimeSettings;

    @JsonCreator
    public GetAnalyticShardDataRequest(@JsonProperty("requestedRange") final OffsetRange requestedRange,
                                       @JsonProperty("timeRange") final TimeRange timeRange,
                                       @JsonProperty("analyticDocUuid") final String analyticDocUuid,
                                       @JsonProperty("path") final String path,
                                       @JsonProperty("dateTimeSettings") final DateTimeSettings dateTimeSettings) {
        this.requestedRange = requestedRange;
        this.timeRange = timeRange;
        this.analyticDocUuid = analyticDocUuid;
        this.path = path;
        this.dateTimeSettings = dateTimeSettings;
    }

    public OffsetRange getRequestedRange() {
        return requestedRange;
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public String getAnalyticDocUuid() {
        return analyticDocUuid;
    }

    public String getPath() {
        return path;
    }

    public DateTimeSettings getDateTimeSettings() {
        return dateTimeSettings;
    }
}
