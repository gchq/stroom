/*
 * Copyright 2016 Crown Copyright
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

import java.util.HashMap;
import java.util.Map;

@JsonInclude(Include.NON_DEFAULT)
public class MetaRow {
    @JsonProperty
    private Meta meta;
    @JsonProperty
    private Map<String, String> attributes = new HashMap<>();

    public MetaRow() {
    }

    public MetaRow(Meta meta) {
        setMeta(meta);
    }

    @JsonCreator
    public MetaRow(@JsonProperty("meta") final Meta meta,
                   @JsonProperty("attributes") final Map<String, String> attributes) {
        this.meta = meta;
        this.attributes = attributes;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public String getAttributeValue(final String name) {
        return attributes.get(name);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof MetaRow)) return false;

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
