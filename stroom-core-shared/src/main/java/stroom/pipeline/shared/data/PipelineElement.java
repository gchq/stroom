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

    /**
     * Elements are equal if they identify the same node in the pipeline graph, i.e. they have the
     * same id and type. Name and description are deliberately excluded: elements are used as map
     * keys and lookup values throughout the client (child/parent maps, tree selection, the canonical
     * {@code PipelineModel.SOURCE_ELEMENT} constant), all of which must keep matching an element
     * whose name or description has since been edited.
     * <p>
     * Use {@link #contentEquals(PipelineElement, PipelineElement)} to test whether two elements hold
     * the same values, e.g. when deciding if a document has unsaved changes.
     */
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

    /**
     * Tests whether two elements hold the same values, including name and description, unlike
     * {@link #equals(Object)} which tests identity within the pipeline graph.
     */
    public static boolean contentEquals(final PipelineElement element, final PipelineElement other) {
        if (element == other) {
            return true;
        }
        if (element == null || other == null) {
            return false;
        }
        return Objects.equals(element.id, other.id) &&
               Objects.equals(element.type, other.type) &&
               Objects.equals(element.name, other.name) &&
               Objects.equals(element.description, other.description);
    }

    /**
     * @see #contentEquals(PipelineElement, PipelineElement)
     */
    public static int contentHashCode(final PipelineElement element) {
        return element == null
                ? 0
                : Objects.hash(element.id, element.type, element.name, element.description);
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
