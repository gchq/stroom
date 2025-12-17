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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class SpanLink {

    @JsonProperty("traceId")
    private final String traceId;

    @JsonProperty("spanId")
    private final String spanId;

    @JsonProperty("traceState")
    private final String traceState;

    @JsonProperty("attributes")
    private final List<KeyValue> attributes;

    @JsonProperty("droppedAttributesCount")
    private final int droppedAttributesCount;

    @JsonCreator
    public SpanLink(@JsonProperty("traceId") final String traceId,
                    @JsonProperty("spanId") final String spanId,
                    @JsonProperty("traceState") final String traceState,
                    @JsonProperty("attributes") final List<KeyValue> attributes,
                    @JsonProperty("droppedAttributesCount") final int droppedAttributesCount) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.traceState = traceState;
        this.attributes = attributes;
        this.droppedAttributesCount = droppedAttributesCount;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getTraceState() {
        return traceState;
    }

    public List<KeyValue> getAttributes() {
        return attributes;
    }

    public int getDroppedAttributesCount() {
        return droppedAttributesCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SpanLink spanLink = (SpanLink) o;
        return droppedAttributesCount == spanLink.droppedAttributesCount &&
               Objects.equals(traceId, spanLink.traceId) &&
               Objects.equals(spanId, spanLink.spanId) &&
               Objects.equals(traceState, spanLink.traceState) &&
               Objects.equals(attributes, spanLink.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, spanId, traceState, attributes, droppedAttributesCount);
    }

    @Override
    public String toString() {
        return "SpanLink{" +
               "traceId='" + traceId + '\'' +
               ", spanId='" + spanId + '\'' +
               ", traceState='" + traceState + '\'' +
               ", attributes=" + attributes +
               ", droppedAttributesCount=" + droppedAttributesCount +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<SpanLink, Builder> {

        private String traceId;
        private String spanId;
        private String traceState;
        private List<KeyValue> attributes;
        private int droppedAttributesCount;

        private Builder() {
        }

        private Builder(final SpanLink spanLink) {
            this.traceId = spanLink.traceId;
            this.spanId = spanLink.spanId;
            this.traceState = spanLink.traceState;
            this.attributes = spanLink.attributes;
            this.droppedAttributesCount = spanLink.droppedAttributesCount;
        }

        public Builder traceId(final String traceId) {
            this.traceId = traceId;
            return self();
        }

        public Builder spanId(final String spanId) {
            this.spanId = spanId;
            return self();
        }

        public Builder traceState(final String traceState) {
            this.traceState = traceState;
            return self();
        }

        public Builder attributes(final List<KeyValue> attributes) {
            this.attributes = attributes;
            return self();
        }

        public Builder droppedAttributesCount(final int droppedAttributesCount) {
            this.droppedAttributesCount = droppedAttributesCount;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public SpanLink build() {
            return new SpanLink(
                    traceId,
                    spanId,
                    traceState,
                    attributes,
                    droppedAttributesCount
            );
        }
    }
}
