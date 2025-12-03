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


import stroom.docref.HasType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({
        "elementType",
        "name",
        "type",
        "description",
        "defaultValue",
        "pipelineReference",
        "docRefTypes",
        "displayPriority"})
public class PipelinePropertyType implements Comparable<PipelinePropertyType>, HasType {

    @JsonProperty
    private final PipelineElementType elementType;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String type;
    @JsonProperty
    private final String description;
    @JsonProperty
    private final String defaultValue;
    @JsonProperty
    private final boolean pipelineReference;
    @JsonProperty
    private final String[] docRefTypes;
    @JsonProperty
    private final int displayPriority;

    @JsonCreator
    public PipelinePropertyType(@JsonProperty("elementType") final PipelineElementType elementType,
                                @JsonProperty("name") final String name,
                                @JsonProperty("type") final String type,
                                @JsonProperty("description") final String description,
                                @JsonProperty("defaultValue") final String defaultValue,
                                @JsonProperty("pipelineReference") final boolean pipelineReference,
                                @JsonProperty("docRefTypes") final String[] docRefTypes,
                                @JsonProperty("displayPriority") final int displayPriority) {
        this.elementType = elementType;
        this.name = name;
        this.type = type;
        this.description = description;
        this.defaultValue = defaultValue;
        this.pipelineReference = pipelineReference;
        this.docRefTypes = docRefTypes;
        this.displayPriority = displayPriority;
    }

    public PipelineElementType getElementType() {
        return elementType;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isPipelineReference() {
        return pipelineReference;
    }

    public String[] getDocRefTypes() {
        return docRefTypes;
    }

    public int getDisplayPriority() {
        return displayPriority;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PipelinePropertyType that = (PipelinePropertyType) o;
        return elementType.equals(that.elementType) &&
                name.equals(that.name) &&
                type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elementType, name, type);
    }

    @Override
    public int compareTo(final PipelinePropertyType o) {
        return name.compareTo(o.name);
    }

    @Override
    public String toString() {
        return name;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private PipelineElementType elementType;
        private String name;
        private String type;
        private String description;
        private String defaultValue;
        private boolean pipelineReference;
        private String[] docRefTypes;
        private int displayPriority;

        private Builder() {
        }

        private Builder(final PipelinePropertyType pipelinePropertyType) {
            this.elementType = pipelinePropertyType.elementType;
            this.name = pipelinePropertyType.name;
            this.type = pipelinePropertyType.type;
            this.description = pipelinePropertyType.description;
            this.defaultValue = pipelinePropertyType.defaultValue;
            this.pipelineReference = pipelinePropertyType.pipelineReference;
            this.docRefTypes = pipelinePropertyType.docRefTypes;
            this.displayPriority = pipelinePropertyType.displayPriority;
        }

        public Builder elementType(final PipelineElementType value) {
            this.elementType = value;
            return this;
        }

        public Builder name(final String value) {
            this.name = value;
            return this;
        }

        public Builder type(final String value) {
            this.type = value;
            return this;
        }

        public Builder description(final String value) {
            this.description = value;
            return this;
        }

        public Builder defaultValue(final String value) {
            this.defaultValue = value;
            return this;
        }

        public Builder pipelineReference(final boolean value) {
            this.pipelineReference = value;
            return this;
        }

        public Builder docRefTypes(final String[] value) {
            this.docRefTypes = value;
            return this;
        }

        public Builder displayPriority(final int displayPriority) {
            this.displayPriority = displayPriority;
            return this;
        }

        public PipelinePropertyType build() {
            return new PipelinePropertyType(
                    elementType,
                    name,
                    type,
                    description,
                    defaultValue,
                    pipelineReference,
                    docRefTypes,
                    displayPriority);
        }
    }
}
