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

package stroom.pathways.shared;

import stroom.docref.DocRef;
import stroom.query.api.TimeRange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Requests a time histogram of trace counts over {@code timeRange}, split into {@code bucketCount}
 * equal buckets and narrowed by the same quick {@code filter} the list uses. The server only serves
 * a window no wider than one archival-granularity bucket (see {@link TraceHistogram#isAvailable()}).
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class TraceHistogramRequest {

    @JsonProperty
    private final DocRef dataSourceRef;
    @JsonProperty
    private final String filter;
    @JsonProperty
    private final TimeRange timeRange;
    @JsonProperty
    private final int bucketCount;

    @JsonCreator
    public TraceHistogramRequest(@JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                                 @JsonProperty("filter") final String filter,
                                 @JsonProperty("timeRange") final TimeRange timeRange,
                                 @JsonProperty("bucketCount") final int bucketCount) {
        this.dataSourceRef = dataSourceRef;
        this.filter = filter;
        this.timeRange = timeRange;
        this.bucketCount = bucketCount;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    public String getFilter() {
        return filter;
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public int getBucketCount() {
        return bucketCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TraceHistogramRequest that = (TraceHistogramRequest) o;
        return bucketCount == that.bucketCount &&
               Objects.equals(dataSourceRef, that.dataSourceRef) &&
               Objects.equals(filter, that.filter) &&
               Objects.equals(timeRange, that.timeRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSourceRef, filter, timeRange, bucketCount);
    }

    @Override
    public String toString() {
        return "TraceHistogramRequest{" +
               "dataSourceRef=" + dataSourceRef +
               ", filter='" + filter + '\'' +
               ", timeRange=" + timeRange +
               ", bucketCount=" + bucketCount +
               '}';
    }
}
