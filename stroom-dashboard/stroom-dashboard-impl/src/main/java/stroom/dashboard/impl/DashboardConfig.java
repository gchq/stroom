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

package stroom.dashboard.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class DashboardConfig extends AbstractConfig implements IsStroomConfig {

    private final CacheConfig visualisationDocCache;

    public DashboardConfig() {
        visualisationDocCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
    }

    @JsonCreator
    public DashboardConfig(@JsonProperty("visualisationDocCache") final CacheConfig visualisationDocCache) {
        this.visualisationDocCache = visualisationDocCache;
    }

    @JsonProperty("visualisationDocCache")
    public CacheConfig getVisualisationDocCache() {
        return visualisationDocCache;
    }

    @Override
    public String toString() {
        return "DashboardConfig{" +
               "visualisationDocCache=" + visualisationDocCache +
               '}';
    }
}
