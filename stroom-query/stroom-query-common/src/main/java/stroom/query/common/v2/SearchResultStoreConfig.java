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

package stroom.query.common.v2;

import stroom.util.io.ByteSize;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class SearchResultStoreConfig extends AbstractResultStoreConfig implements IsStroomConfig {

    private static final ResultStoreMapConfig DEFAULT_MAP_CONFIG = new ResultStoreMapConfig();
    static final ResultStoreLmdbConfig DEFAULT_LMDB_CONFIG =
            ResultStoreLmdbConfig.builder().localDir("search_results").build();

    private final ResultStoreMapConfig mapConfig;

    public SearchResultStoreConfig() {
        super(DEFAULT_MAX_PUTS_BEFORE_COMMIT,
                DEFAULT_OFF_HEAP_RESULTS,
                DEFAULT_MIN_PAYLOAD_SIZE,
                DEFAULT_MAX_PAYLOAD_SIZE,
                DEFAULT_MAX_STRING_FIELD_LENGTH,
                DEFAULT_VALUE_QUEUE_SIZE,
                DEFAULT_MAX_SORTED_ITEMS,
                DEFAULT_LMDB_CONFIG);
        this.mapConfig = DEFAULT_MAP_CONFIG;
    }

    @JsonCreator
    public SearchResultStoreConfig(@JsonProperty("maxPutsBeforeCommit") final Integer maxPutsBeforeCommit,
                                   @JsonProperty("offHeapResults") final Boolean offHeapResults,
                                   @JsonProperty("minPayloadSize") final ByteSize minPayloadSize,
                                   @JsonProperty("maxPayloadSize") final ByteSize maxPayloadSize,
                                   @JsonProperty("maxStringFieldLength") final Integer maxStringFieldLength,
                                   @JsonProperty("valueQueueSize") final Integer valueQueueSize,
                                   @JsonProperty("maxSortedItems") final Integer maxSortedItems,
                                   @JsonProperty("lmdb") final ResultStoreLmdbConfig lmdbConfig,
                                   @JsonProperty("map") final ResultStoreMapConfig mapConfig) {
        super(Objects.requireNonNullElse(maxPutsBeforeCommit, DEFAULT_MAX_PUTS_BEFORE_COMMIT),
                Objects.requireNonNullElse(offHeapResults, DEFAULT_OFF_HEAP_RESULTS),
                Objects.requireNonNullElse(minPayloadSize, DEFAULT_MIN_PAYLOAD_SIZE),
                Objects.requireNonNullElse(maxPayloadSize, DEFAULT_MAX_PAYLOAD_SIZE),
                Objects.requireNonNullElse(maxStringFieldLength, DEFAULT_MAX_STRING_FIELD_LENGTH),
                Objects.requireNonNullElse(valueQueueSize, DEFAULT_VALUE_QUEUE_SIZE),
                Objects.requireNonNullElse(maxSortedItems, DEFAULT_MAX_SORTED_ITEMS),
                Objects.requireNonNullElse(lmdbConfig, DEFAULT_LMDB_CONFIG));
        this.mapConfig = Objects.requireNonNullElse(mapConfig, DEFAULT_MAP_CONFIG);
    }

    @JsonProperty("map")
    public ResultStoreMapConfig getMapConfig() {
        return mapConfig;
    }
}
