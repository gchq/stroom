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

@JsonPropertyOrder(alphabetic = true)
public class SearchResultStoreConfig extends AbstractResultStoreConfig implements IsStroomConfig {

    private final ResultStoreMapConfig mapConfig;

    public SearchResultStoreConfig() {
        this(10_000,
                true,
                DEFAULT_MIN_PAYLOAD_SIZE,
                DEFAULT_MAX_PAYLOAD_SIZE,
                1000,
                10_000,
                500_000,
                DEFAULT_RESULT_STORE_LMDB_CONFIG,
                new ResultStoreMapConfig());
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
        super(maxPutsBeforeCommit,
                offHeapResults,
                minPayloadSize,
                maxPayloadSize,
                maxStringFieldLength,
                valueQueueSize,
                maxSortedItems,
                lmdbConfig);
        this.mapConfig = mapConfig;
    }

    @JsonProperty("map")
    public ResultStoreMapConfig getMapConfig() {
        return mapConfig;
    }
}
