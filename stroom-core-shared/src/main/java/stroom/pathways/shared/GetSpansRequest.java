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
 * Requests a bounded, tree-order (pre-order DFS) window of a trace's spans, for
 * displaying very large traces without loading them whole. The window is rows
 * {@code [offset, offset + limit)}; {@code offset} maps directly to a scrollbar
 * position (total row count is the trace's known {@code totalSpans}).
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class GetSpansRequest {

    @JsonProperty
    private final DocRef dataSourceRef;
    @JsonProperty
    private final String traceId;
    @JsonProperty
    private final int offset;
    @JsonProperty
    private final int limit;
    /**
     * The trace's root start time in epoch millis, if known (from {@code TraceRoot}). Locates the
     * archive bucket for a trace whose root/bulk has been archived; may be {@code null} (server scans
     * the trace's shard archive).
     */
    @JsonProperty
    private final Long startTimeMs;
    /**
     * Opaque resume cursor for sequential (split live+archive) paging. {@code null} on the first fetch
     * (start at the root) and for the offset/random path; when a response carries a {@code nextCursor}
     * the client sends it back here to fetch the next page.
     */
    @JsonProperty
    private final String cursor;

    @JsonCreator
    public GetSpansRequest(@JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                           @JsonProperty("traceId") final String traceId,
                           @JsonProperty("offset") final int offset,
                           @JsonProperty("limit") final int limit,
                           @JsonProperty("startTimeMs") final Long startTimeMs,
                           @JsonProperty("cursor") final String cursor) {
        this.dataSourceRef = dataSourceRef;
        this.traceId = traceId;
        this.offset = offset;
        this.limit = limit;
        this.startTimeMs = startTimeMs;
        this.cursor = cursor;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    public String getTraceId() {
        return traceId;
    }

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public Long getStartTimeMs() {
        return startTimeMs;
    }

    public String getCursor() {
        return cursor;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GetSpansRequest that = (GetSpansRequest) o;
        return offset == that.offset &&
               limit == that.limit &&
               Objects.equals(dataSourceRef, that.dataSourceRef) &&
               Objects.equals(traceId, that.traceId) &&
               Objects.equals(startTimeMs, that.startTimeMs) &&
               Objects.equals(cursor, that.cursor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSourceRef, traceId, offset, limit, startTimeMs, cursor);
    }

    @Override
    public String toString() {
        return "GetSpansRequest{" +
               "dataSourceRef=" + dataSourceRef +
               ", traceId='" + traceId + '\'' +
               ", offset=" + offset +
               ", limit=" + limit +
               ", startTimeMs=" + startTimeMs +
               ", cursor='" + cursor + '\'' +
               '}';
    }
}
