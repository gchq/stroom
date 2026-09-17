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

import stroom.pathways.shared.otel.trace.Span;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/**
 * A downsampled whole-trace overview: a small set of representative spans spanning the
 * trace's full time extent, for drawing the overarching timeline strip of a very large
 * trace in response to a {@link GetTraceOverviewRequest}.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class TraceOverview {

    @JsonProperty
    private final List<Span> spans;

    @JsonCreator
    public TraceOverview(@JsonProperty("spans") final List<Span> spans) {
        this.spans = spans;
    }

    public List<Span> getSpans() {
        return spans;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TraceOverview that = (TraceOverview) o;
        return Objects.equals(spans, that.spans);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spans);
    }

    @Override
    public String toString() {
        return "TraceOverview{" +
               "spans=" + (spans == null ? 0 : spans.size()) +
               '}';
    }
}
