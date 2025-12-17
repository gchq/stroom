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

package stroom.docstore.impl.db.migration.v710.pipeline.legacy.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"id", "type"})
public class PipelineElement implements Comparable<PipelineElement> {

    @JsonProperty
    private final String id;
    @JsonProperty
    private final String type;

    @JsonCreator
    public PipelineElement(@JsonProperty("id") final String id,
                           @JsonProperty("type") final String type) {
        this.id = id;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    @Override
    public int compareTo(final PipelineElement o) {
        return id.compareTo(o.id);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PipelineElement that = (PipelineElement) o;
        return id.equals(that.id) &&
               type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }

    @Override
    public String toString() {
        return "id=" + id + ", type=" + type;
    }

    /**
     * E.g.
     * <pre>{@code CombinedParser 'myCombinedParser'}</pre>
     */
    @JsonIgnore
    public String getDisplayName() {
        return type + " '" + id + "'";
    }

    public static class Builder {

        private String id;
        private String type;

        public Builder() {
        }

        public Builder(final PipelineElement element) {
            if (element != null) {
                this.id = element.id;
                this.type = element.type;
            }
        }

        public Builder id(final String id) {
            this.id = id;
            return this;
        }

        public Builder type(final String type) {
            this.type = type;
            return this;
        }

        public PipelineElement build() {
            return new PipelineElement(id, type);
        }
    }
}
