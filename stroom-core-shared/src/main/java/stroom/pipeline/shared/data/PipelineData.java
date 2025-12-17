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

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"elements", "properties", "pipelineReferences", "links"})
public class PipelineData {

    @JsonProperty
    private final PipelineElements elements;
    @JsonProperty
    private final PipelineProperties properties;
    @JsonProperty
    private final PipelineReferences pipelineReferences;
    @JsonProperty
    private final PipelineLinks links;

    @JsonCreator
    public PipelineData(@JsonProperty("elements") final PipelineElements elements,
                        @JsonProperty("properties") final PipelineProperties properties,
                        @JsonProperty("pipelineReferences") final PipelineReferences pipelineReferences,
                        @JsonProperty("links") final PipelineLinks links) {
        this.elements = elements;
        this.properties = properties;
        this.pipelineReferences = pipelineReferences;
        this.links = links;
    }

    public PipelineElements getElements() {
        return elements;
    }

    public PipelineProperties getProperties() {
        return properties;
    }

    public PipelineReferences getPipelineReferences() {
        return pipelineReferences;
    }

    public PipelineLinks getLinks() {
        return links;
    }

    /**
     * Convenience methods
     */
    @JsonIgnore
    public List<PipelineElement> getAddedElements() {
        return NullSafe.getOrElse(elements, PipelineElements::getAdd, Collections.emptyList());
    }

    @JsonIgnore
    public List<PipelineElement> getRemovedElements() {
        return NullSafe.getOrElse(elements, PipelineElements::getRemove, Collections.emptyList());
    }

    @JsonIgnore
    public List<PipelineProperty> getAddedProperties() {
        return NullSafe.getOrElse(properties, PipelineProperties::getAdd, Collections.emptyList());
    }

    @JsonIgnore
    public List<PipelineProperty> getRemovedProperties() {
        return NullSafe.getOrElse(properties, PipelineProperties::getRemove, Collections.emptyList());
    }

    @JsonIgnore
    public List<PipelineReference> getAddedPipelineReferences() {
        return NullSafe.getOrElse(pipelineReferences, PipelineReferences::getAdd, Collections.emptyList());
    }

    @JsonIgnore
    public List<PipelineReference> getRemovedPipelineReferences() {
        return NullSafe.getOrElse(pipelineReferences, PipelineReferences::getRemove, Collections.emptyList());
    }

    @JsonIgnore
    public List<PipelineLink> getAddedLinks() {
        return NullSafe.getOrElse(links, PipelineLinks::getAdd, Collections.emptyList());
    }

    @JsonIgnore
    public List<PipelineLink> getRemovedLinks() {
        return NullSafe.getOrElse(links, PipelineLinks::getRemove, Collections.emptyList());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PipelineData that = (PipelineData) o;
        return Objects.equals(elements, that.elements) &&
               Objects.equals(properties, that.properties) &&
               Objects.equals(pipelineReferences, that.pipelineReferences) &&
               Objects.equals(links, that.links);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elements, properties, pipelineReferences, links);
    }
}
