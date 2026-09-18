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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Requests a downsampled whole-trace overview for a very large trace: at most
 * {@code maxBars} representative spans (the longest in each of {@code maxBars} equal
 * time buckets) across the [{@code fromMs}, {@code toMs}] extent. Extents come from the
 * caller's already-known {@link stroom.pathways.shared.otel.trace.TraceRoot}, so the axis
 * is whole before any span is loaded.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class GetTraceOverviewRequest {

    @JsonProperty
    private final DocRef dataSourceRef;
    @JsonProperty
    private final String traceId;
    @JsonProperty
    private final long fromMs;
    @JsonProperty
    private final long toMs;
    @JsonProperty
    private final int maxBars;

    @JsonCreator
    public GetTraceOverviewRequest(@JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                                   @JsonProperty("traceId") final String traceId,
                                   @JsonProperty("fromMs") final long fromMs,
                                   @JsonProperty("toMs") final long toMs,
                                   @JsonProperty("maxBars") final int maxBars) {
        this.dataSourceRef = dataSourceRef;
        this.traceId = traceId;
        this.fromMs = fromMs;
        this.toMs = toMs;
        this.maxBars = maxBars;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    public String getTraceId() {
        return traceId;
    }

    public long getFromMs() {
        return fromMs;
    }

    public long getToMs() {
        return toMs;
    }

    public int getMaxBars() {
        return maxBars;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GetTraceOverviewRequest that = (GetTraceOverviewRequest) o;
        return fromMs == that.fromMs &&
               toMs == that.toMs &&
               maxBars == that.maxBars &&
               Objects.equals(dataSourceRef, that.dataSourceRef) &&
               Objects.equals(traceId, that.traceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSourceRef, traceId, fromMs, toMs, maxBars);
    }

    @Override
    public String toString() {
        return "GetTraceOverviewRequest{" +
               "dataSourceRef=" + dataSourceRef +
               ", traceId='" + traceId + '\'' +
               ", fromMs=" + fromMs +
               ", toMs=" + toMs +
               ", maxBars=" + maxBars +
               '}';
    }
}
