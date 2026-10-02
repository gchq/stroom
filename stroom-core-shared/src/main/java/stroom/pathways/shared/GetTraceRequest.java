/*
 * Copyright 2025 Crown Copyright
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
import stroom.util.shared.time.SimpleDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class GetTraceRequest {

    @JsonProperty
    private final DocRef dataSourceRef;
    @JsonProperty
    private final String traceId;
    @JsonProperty
    private final SimpleDuration temporalOrderingTolerance;
    /**
     * The trace's root-span start time in epoch milliseconds, if known. Used to
     * locate the archive shard bucket (labelled by start time) for a trace that
     * has been purged from the live shard. May be {@code null}, in which case the
     * server falls back to scanning all archive buckets for the trace's shard.
     */
    @JsonProperty
    private final Long startTimeMs;

    @JsonCreator
    public GetTraceRequest(@JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                           @JsonProperty("traceId") final String traceId,
                           @JsonProperty("temporalOrderingTolerance") final SimpleDuration temporalOrderingTolerance,
                           @JsonProperty("startTimeMs") final Long startTimeMs) {
        this.dataSourceRef = dataSourceRef;
        this.traceId = traceId;
        this.temporalOrderingTolerance = temporalOrderingTolerance;
        this.startTimeMs = startTimeMs;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    public String getTraceId() {
        return traceId;
    }

    public SimpleDuration getTemporalOrderingTolerance() {
        return temporalOrderingTolerance;
    }

    public Long getStartTimeMs() {
        return startTimeMs;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GetTraceRequest that = (GetTraceRequest) o;
        return Objects.equals(dataSourceRef, that.dataSourceRef) &&
               Objects.equals(traceId, that.traceId) &&
               Objects.equals(temporalOrderingTolerance, that.temporalOrderingTolerance) &&
               Objects.equals(startTimeMs, that.startTimeMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSourceRef, traceId, temporalOrderingTolerance, startTimeMs);
    }

    @Override
    public String toString() {
        return "GetTraceRequest{" +
               "dataSourceRef=" + dataSourceRef +
               ", traceId='" + traceId + '\'' +
               ", temporalOrderingTolerance='" + temporalOrderingTolerance + '\'' +
               ", startTimeMs=" + startTimeMs +
               '}';
    }
}
