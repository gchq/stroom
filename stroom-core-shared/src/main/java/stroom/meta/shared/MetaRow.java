/*
 * Copyright 2024 Crown Copyright
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
    private final String pipelineName;

    // Can't use a CIKey keyed map due to GWT
    @JsonProperty
    private final Map<String, String> attributes;

    @JsonCreator
    public MetaRow(@JsonProperty("meta") final Meta meta,
                   @JsonProperty("pipelineName") final String pipelineName,
                   @JsonProperty("attributes") final Map<String, String> attributes) {
        this.meta = meta;
        this.pipelineName = pipelineName;
        this.attributes = attributes;

    }

    public Meta getMeta() {
        return meta;
    }

    public String getPipelineName() {
        return pipelineName;
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

        //noinspection PatternVariableCanBeUsed // Not in GWT land
        final MetaRow that = (MetaRow) o;
        return meta.equals(that.meta);
    }

    @Override
    public int hashCode() {
        return meta.hashCode();
    }

    @Override
    public String toString() {
        return "meta: " + meta +
                " - pipeline: '" + pipelineName + '\'';
    }
}
