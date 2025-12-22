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

package stroom.pipeline.shared.data;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"from", "to"})
public class PipelineLink implements Comparable<PipelineLink> {

    @JsonProperty
    private final String from;
    @JsonProperty
    private final String to;

    @JsonCreator
    public PipelineLink(@JsonProperty("from") final String from,
                        @JsonProperty("to") final String to) {
        this.from = from;
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PipelineLink that = (PipelineLink) o;
        return from.equals(that.from) &&
               to.equals(that.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }

    @Override
    public int compareTo(final PipelineLink o) {
        if (!(from.equals(o.from))) {
            return from.compareTo(o.from);
        }

        if (!(to.equals(o.to))) {
            return to.compareTo(o.to);
        }

        return 0;
    }

    @Override
    public String toString() {
        return "from=" + from + ", to=" + to;
    }

    public static class Builder {

        private String from;
        private String to;

        public Builder() {
        }

        public Builder(final PipelineLink link) {
            if (link != null) {
                this.from = link.from;
                this.to = link.to;
            }
        }

        public Builder from(final String from) {
            this.from = from;
            return this;
        }

        public Builder to(final String to) {
            this.to = to;
            return this;
        }

        public PipelineLink build() {
            return new PipelineLink(from, to);
        }
    }
}
