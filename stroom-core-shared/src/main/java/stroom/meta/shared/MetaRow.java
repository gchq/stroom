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

package stroom.meta.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(Include.NON_NULL)
public class MetaRow {

    @JsonProperty
    private final Meta meta;
    @JsonProperty
    private final DocRef pipeline;
    @JsonProperty
    private final Map<String, String> attributes;

    @JsonCreator
    public MetaRow(@JsonProperty("meta") final Meta meta,
                   @JsonProperty("pipeline") final DocRef pipeline,
                   @JsonProperty("attributes") final Map<String, String> attributes) {
        this.meta = meta;
        this.pipeline = pipeline;
        this.attributes = attributes;
    }

    public Meta getMeta() {
        return meta;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public String getAttributeValue(final String name) {
        return attributes.get(name);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MetaRow)) {
            return false;
        }

        final MetaRow that = (MetaRow) o;

        return meta.equals(that.meta);
    }

    @Override
    public int hashCode() {
        return meta.hashCode();
    }

    @Override
    public String toString() {
        return meta.toString();
    }
}
