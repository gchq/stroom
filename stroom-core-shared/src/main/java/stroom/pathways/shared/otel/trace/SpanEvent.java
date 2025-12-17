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
public class SpanEvent {

    @JsonProperty("timeUnixNano")
    private final String timeUnixNano;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("attributes")
    private final List<KeyValue> attributes;

    @JsonProperty("droppedAttributesCount")
    private final int droppedAttributesCount;

    @JsonCreator
    public SpanEvent(@JsonProperty("timeUnixNano") final String timeUnixNano,
                     @JsonProperty("name") final String name,
                     @JsonProperty("attributes") final List<KeyValue> attributes,
                     @JsonProperty("droppedAttributesCount") final int droppedAttributesCount) {
        this.timeUnixNano = timeUnixNano;
        this.name = name;
        this.attributes = attributes;
        this.droppedAttributesCount = droppedAttributesCount;
    }

    public String getTimeUnixNano() {
        return timeUnixNano;
    }

    public String getName() {
        return name;
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
        final SpanEvent spanEvent = (SpanEvent) o;
        return droppedAttributesCount == spanEvent.droppedAttributesCount &&
               Objects.equals(timeUnixNano, spanEvent.timeUnixNano) &&
               Objects.equals(name, spanEvent.name) &&
               Objects.equals(attributes, spanEvent.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeUnixNano, name, attributes, droppedAttributesCount);
    }

    @Override
    public String toString() {
        return "SpanEvent{" +
               "timeUnixNano='" + timeUnixNano + '\'' +
               ", name='" + name + '\'' +
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

    public static final class Builder extends AbstractBuilder<SpanEvent, Builder> {

        private String timeUnixNano;
        private String name;
        private List<KeyValue> attributes;
        private int droppedAttributesCount;

        private Builder() {
        }

        private Builder(final SpanEvent spanEvent) {
            this.timeUnixNano = spanEvent.timeUnixNano;
            this.name = spanEvent.name;
            this.attributes = spanEvent.attributes;
            this.droppedAttributesCount = spanEvent.droppedAttributesCount;
        }

        public Builder timeUnixNano(final NanoTime timeUnixNano) {
            this.timeUnixNano = timeUnixNano.toNanoEpochString();
            return self();
        }

        public Builder timeUnixNano(final String timeUnixNano) {
            this.timeUnixNano = timeUnixNano;
            return self();
        }

        public Builder name(final String name) {
            this.name = name;
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
        public SpanEvent build() {
            return new SpanEvent(
                    timeUnixNano,
                    name,
                    attributes,
                    droppedAttributesCount
            );
        }
    }
}
