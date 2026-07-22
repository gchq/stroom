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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/**
 * A tree-order window of spans in response to a {@link GetSpansRequest}: the rows for
 * {@code [offset, offset + limit)} and whether more rows follow the window. The scrollbar
 * total is the trace's known {@code totalSpans}, so no total is carried here.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class TraceSpanPage {

    @JsonProperty
    private final List<TraceSpanRow> rows;
    @JsonProperty
    private final boolean more;
    /**
     * Opaque resume cursor for sequential (split live+archive) paging: non-null when there are more
     * rows to fetch by cursor (the client sends it back as {@code GetSpansRequest.cursor}). {@code null}
     * for the offset/random path or when there are no further rows.
     */
    @JsonProperty
    private final String nextCursor;
    /**
     * The exact total span count of the trace, known only once the server has built the merged
     * checkpoint index (i.e. after a random-access/offset request for a split trace). {@code null} when
     * unknown — the client then keeps the (possibly over-counted) {@code TraceRoot.getTotalSpans()}.
     */
    @JsonProperty
    private final Integer totalSpans;

    @JsonCreator
    public TraceSpanPage(@JsonProperty("rows") final List<TraceSpanRow> rows,
                         @JsonProperty("more") final boolean more,
                         @JsonProperty("nextCursor") final String nextCursor,
                         @JsonProperty("totalSpans") final Integer totalSpans) {
        this.rows = rows;
        this.more = more;
        this.nextCursor = nextCursor;
        this.totalSpans = totalSpans;
    }

    public List<TraceSpanRow> getRows() {
        return rows;
    }

    public boolean isMore() {
        return more;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public Integer getTotalSpans() {
        return totalSpans;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TraceSpanPage that = (TraceSpanPage) o;
        return more == that.more
               && Objects.equals(rows, that.rows)
               && Objects.equals(nextCursor, that.nextCursor)
               && Objects.equals(totalSpans, that.totalSpans);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rows, more, nextCursor, totalSpans);
    }

    @Override
    public String toString() {
        return "TraceSpanPage{" +
               "rows=" + (rows == null ? 0 : rows.size()) +
               ", more=" + more +
               ", nextCursor='" + nextCursor + '\'' +
               ", totalSpans=" + totalSpans +
               '}';
    }
}
