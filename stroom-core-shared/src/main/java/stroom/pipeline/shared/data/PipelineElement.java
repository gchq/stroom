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

import stroom.util.shared.ElementId;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"id", "type", "name", "description"})
public class PipelineElement implements Comparable<PipelineElement> {

    @JsonProperty
    private String id;
    @JsonProperty
    private String type;
    @JsonProperty
    private String name;
    @JsonProperty
    private String description;

    public PipelineElement(final String id,
                           final String type) {
        this(id, type, null, null);
    }

    @JsonCreator
    public PipelineElement(
            @JsonProperty("id") final String id,
            @JsonProperty("type") final String type,
            @JsonProperty("name") final String name,
            @JsonProperty("description") final String description) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.description = description;
    }

    public PipelineElement() {
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
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
     * <pre>{@code 'myCombinedParser'}</pre>
     */
    @JsonIgnore
    public String getDisplayName() {
        return !NullSafe.isBlankString(name)
                ? name
                : id;
    }

    @JsonIgnore
    public ElementId getElementId() {
        return new ElementId(id, name);
    }

    public static class Builder {

        private String id;
        private String type;
        private String name;
        private String description;

        public Builder id(final String id) {
            this.id = id;
            return this;
        }

        public Builder type(final String type) {
            this.type = type;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public PipelineElement build() {
            return new PipelineElement(id, type, name, description);
        }
    }
}
