/*
 * Copyright 2026 Crown Copyright
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

package stroom.search.elastic.client.model;

import java.util.List;

/// One name and value of how an Elasticsearch field is mapped, e.g. a mapping parameter, shown on
/// the Fields tab's Info panel. A value is either one piece of text or a list, e.g. the source
/// indices.
public final class ElasticMappingEntry {

    private final String name;
    private final String value;
    private final List<String> values;

    /// @param name  The name of the detail, e.g. a mapping parameter's name.
    /// @param value Its value.
    public ElasticMappingEntry(final String name, final String value) {
        this.name = name;
        this.value = value;
        this.values = null;
    }

    /// @param name   The name of the detail.
    /// @param values Its values, shown as a list, each with a button to copy it.
    public ElasticMappingEntry(final String name, final List<String> values) {
        this.name = name;
        this.value = null;
        this.values = values;
    }

    /// @return The name of the detail.
    public String getName() {
        return name;
    }

    /// @return Its value, or null if it has a list of values.
    public String getValue() {
        return value;
    }

    /// @return Its values, or null if it has one value.
    public List<String> getValues() {
        return values;
    }
}
