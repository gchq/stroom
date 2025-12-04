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

package stroom.pathways.shared.otel.trace;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(Include.NON_NULL)
public class Trace {

    @JsonProperty
    private final String traceId;
    @JsonProperty
    private final Map<String, List<Span>> parentSpanIdMap;

    @JsonCreator
    public Trace(@JsonProperty("traceId") final String traceId,
                 @JsonProperty("parentSpanIdMap") final Map<String, List<Span>> parentSpanIdMap) {
        this.traceId = traceId;
        this.parentSpanIdMap = parentSpanIdMap;
    }

    public String getTraceId() {
        return traceId;
    }

    public Map<String, List<Span>> getParentSpanIdMap() {
        return parentSpanIdMap;
    }

    public Span root() {
        return parentSpanIdMap.get("").get(0);
    }

    public List<Span> children(final Span span) {
        return NullSafe.list(parentSpanIdMap.get(span.getSpanId()));
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        appendChild(sb, "", 0);
        return sb.toString();
    }

    private void appendChild(final StringBuilder sb, final String parentId, final int depth) {
        final List<Span> children = NullSafe.list(parentSpanIdMap.get(parentId));
        for (final Span span : children) {
            final NanoTime startTime = NanoTime.fromString(span.getStartTimeUnixNano());
            final NanoTime endTime = NanoTime.fromString(span.getEndTimeUnixNano());
            final NanoTime duration = endTime.subtract(startTime);

            for (int i = 0; i < depth * 3; i++) {
                sb.append(' ');
            }

            // Append name
            sb.append(span.getName());

            // Append duration
            sb.append(" (");

            duration.append(sb);

            sb.append(")\n");

            appendChild(sb, span.getSpanId(), depth + 1);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<Trace, Builder> {

        private String traceId;
        private Map<String, List<Span>> parentSpanIdMap;

        private Builder() {
        }

        private Builder(final Trace trace) {
            this.traceId = trace.traceId;
            this.parentSpanIdMap = trace.parentSpanIdMap;
        }

        public Builder traceId(final String traceId) {
            this.traceId = traceId;
            return self();
        }

        public Builder parentSpanIdMap(final Map<String, List<Span>> parentSpanIdMap) {
            this.parentSpanIdMap = parentSpanIdMap;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public Trace build() {
            if (parentSpanIdMap == null || parentSpanIdMap.isEmpty()) {
                throw new RuntimeException("No spans found");
            }
            final List<Span> roots = parentSpanIdMap.get("");
            if (roots == null) {
                throw new RuntimeException("No root found");
            }
            if (roots.isEmpty()) {
                throw new RuntimeException("No root found");
            } else if (roots.size() > 1) {
                throw new RuntimeException("Multiple roots found");
            }

            return new Trace(traceId, parentSpanIdMap);
        }
    }
}
