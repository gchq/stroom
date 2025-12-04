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

import stroom.util.shared.CompareBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"element", "name", "value"})
public class PipelineProperty implements Comparable<PipelineProperty> {

    @JsonProperty
    private final String element;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final PipelinePropertyValue value;

    @JsonCreator
    public PipelineProperty(@JsonProperty("element") final String element,
                            @JsonProperty("name") final String name,
                            @JsonProperty("value") final PipelinePropertyValue value) {
        this.element = element;
        this.name = name;
        this.value = value;
    }

    public String getElement() {
        return element;
    }

    public String getName() {
        return name;
    }

    public PipelinePropertyValue getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PipelineProperty that = (PipelineProperty) o;
        return element.equals(that.element) &&
               name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(element, name);
    }

    @Override
    public int compareTo(final PipelineProperty o) {
        final CompareBuilder builder = new CompareBuilder();
        builder.append(element, o.element);
        builder.append(name, o.name);
        return builder.toComparison();
    }

    @Override
    public String toString() {
        return "element=" + element + ", name=" + name + ", value=" + value;
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private String element;
        private String name;
        private PipelinePropertyValue value;

        public Builder() {
        }

        public Builder(final PipelineProperty property) {
            if (property != null) {
                this.element = property.element;
                this.name = property.name;
                this.value = property.value;
            }
        }

        public Builder element(final String element) {
            this.element = element;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder value(final PipelinePropertyValue value) {
            this.value = value;
            return this;
        }

        public PipelineProperty build() {
            return new PipelineProperty(element, name, value);
        }
    }
}
