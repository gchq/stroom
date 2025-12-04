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

package stroom.planb.impl.serde.trace;

import stroom.pathways.shared.otel.trace.Span;
import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class SpanKey {

    @JsonProperty("traceId")
    private final String traceId;

    @JsonProperty("spanId")
    private final String spanId;

    @JsonProperty("parentSpanId")
    private final String parentSpanId;

    @JsonCreator
    public SpanKey(@JsonProperty("traceId") final String traceId,
                   @JsonProperty("spanId") final String spanId,
                   @JsonProperty("parentSpanId") final String parentSpanId) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SpanKey spanKey = (SpanKey) o;
        return Objects.equals(traceId, spanKey.traceId) &&
               Objects.equals(spanId, spanKey.spanId) &&
               Objects.equals(parentSpanId, spanKey.parentSpanId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId,
                spanId,
                parentSpanId);
    }

    @Override
    public String toString() {
        return "Span{" +
               "traceId='" + traceId + '\'' +
               ", spanId='" + spanId + '\'' +
               ", parentSpanId='" + parentSpanId + '\'' +
               '}';
    }

    public static SpanKey create(final Span span) {
        return new Builder(span).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<SpanKey, SpanKey.Builder> {

        private String traceId;
        private String spanId;
        private String parentSpanId;

        private Builder() {
        }

        public Builder(final Span span) {
            this.traceId = span.getTraceId();
            this.spanId = span.getSpanId();
            this.parentSpanId = span.getParentSpanId();
        }

        private Builder(final SpanKey span) {
            this.traceId = span.traceId;
            this.spanId = span.spanId;
            this.parentSpanId = span.parentSpanId;
        }

        public Builder traceId(final String traceId) {
            this.traceId = traceId;
            return self();
        }

        public Builder spanId(final String spanId) {
            this.spanId = spanId;
            return self();
        }

        public Builder parentSpanId(final String parentSpanId) {
            this.parentSpanId = parentSpanId;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public SpanKey build() {
            return new SpanKey(
                    traceId,
                    spanId,
                    parentSpanId
            );
        }
    }
}
