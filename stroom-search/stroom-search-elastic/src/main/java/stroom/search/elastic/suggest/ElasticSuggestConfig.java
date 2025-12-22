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

package stroom.search.elastic.suggest;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ElasticSuggestConfig extends AbstractConfig implements IsStroomConfig {

    private final Boolean enabled;

    public ElasticSuggestConfig() {
        enabled = true;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ElasticSuggestConfig(@JsonProperty("enabled") final Boolean enabled) {
        this.enabled = enabled;
    }

    @JsonPropertyDescription("Suggest terms in query expressions when using an Elasticsearch index as the data source.")
    public Boolean getEnabled() {
        return enabled;
    }
}
