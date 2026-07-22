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

    @JsonCreator
    public TraceSpanPage(@JsonProperty("rows") final List<TraceSpanRow> rows,
                         @JsonProperty("more") final boolean more,
                         @JsonProperty("nextCursor") final String nextCursor) {
        this.rows = rows;
        this.more = more;
        this.nextCursor = nextCursor;
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
               && Objects.equals(nextCursor, that.nextCursor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rows, more, nextCursor);
    }

    @Override
    public String toString() {
        return "TraceSpanPage{" +
               "rows=" + (rows == null ? 0 : rows.size()) +
               ", more=" + more +
               ", nextCursor='" + nextCursor + '\'' +
               '}';
    }
}
