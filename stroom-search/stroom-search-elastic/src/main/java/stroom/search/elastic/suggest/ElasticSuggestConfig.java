/*
 * Copyright 2022 Crown Copyright
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

package stroom.search.elastic.suggest;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class ElasticSuggestConfig extends AbstractConfig implements IsStroomConfig {

    static final int DEFAULT_TOP_VALUES_COUNT = 10;
    static final int MAX_TOP_VALUES_COUNT = 1000;

    private final Boolean enabled;
    private final int topValuesCount;

    public ElasticSuggestConfig() {
        enabled = true;
        topValuesCount = DEFAULT_TOP_VALUES_COUNT;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ElasticSuggestConfig(@JsonProperty("enabled") final Boolean enabled,
                                @JsonProperty("topValuesCount") final Integer topValuesCount) {
        this.enabled = enabled;
        this.topValuesCount = Objects.requireNonNullElse(topValuesCount, DEFAULT_TOP_VALUES_COUNT);
    }

    /// @return Whether the value picker is offered for Elasticsearch fields.
    @JsonPropertyDescription("Show a picker of the most common values of a field in query expressions when " +
                             "using an Elasticsearch index as the data source.")
    public Boolean getEnabled() {
        return enabled;
    }

    /// @return The number of most common values the value picker lists, as configured.
    @JsonPropertyDescription("The number of most common values the value picker lists for a field, " +
                             "from 1 to " + MAX_TOP_VALUES_COUNT + ".")
    public int getTopValuesCount() {
        return topValuesCount;
    }

    /// @return The number of values to list, limited to the allowed range.
    @JsonIgnore
    public int getEffectiveTopValuesCount() {
        return Math.max(1, Math.min(MAX_TOP_VALUES_COUNT, topValuesCount));
    }
}
