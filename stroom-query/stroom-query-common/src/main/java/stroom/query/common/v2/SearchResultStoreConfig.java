/*
 * Copyright 2018 Crown Copyright
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
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class SearchResultStoreConfig extends AbstractResultStoreConfig implements IsStroomConfig {

    private static final ResultStoreMapConfig DEFAULT_MAP_CONFIG = new ResultStoreMapConfig();
    static final long DEFAULT_JOIN_MAX_ROWS = 1_000_000;
    static final ByteSize DEFAULT_JOIN_MAX_SIZE = ByteSize.ofMebibytes(256);
    static final ResultStoreLmdbConfig DEFAULT_LMDB_CONFIG =
            ResultStoreLmdbConfig.builder().localDir("search_results").build();

    private final ResultStoreMapConfig mapConfig;
    private final long joinMaxRows;
    private final ByteSize joinMaxSize;

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
        this.joinMaxRows = DEFAULT_JOIN_MAX_ROWS;
        this.joinMaxSize = DEFAULT_JOIN_MAX_SIZE;
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
                                   @JsonProperty("map") final ResultStoreMapConfig mapConfig,
                                   @JsonProperty("joinMaxRows") final Long joinMaxRows,
                                   @JsonProperty("joinMaxSize") final ByteSize joinMaxSize) {
        super(Objects.requireNonNullElse(maxPutsBeforeCommit, DEFAULT_MAX_PUTS_BEFORE_COMMIT),
                Objects.requireNonNullElse(offHeapResults, DEFAULT_OFF_HEAP_RESULTS),
                Objects.requireNonNullElse(minPayloadSize, DEFAULT_MIN_PAYLOAD_SIZE),
                Objects.requireNonNullElse(maxPayloadSize, DEFAULT_MAX_PAYLOAD_SIZE),
                Objects.requireNonNullElse(maxStringFieldLength, DEFAULT_MAX_STRING_FIELD_LENGTH),
                Objects.requireNonNullElse(valueQueueSize, DEFAULT_VALUE_QUEUE_SIZE),
                Objects.requireNonNullElse(maxSortedItems, DEFAULT_MAX_SORTED_ITEMS),
                Objects.requireNonNullElse(lmdbConfig, DEFAULT_LMDB_CONFIG));
        this.mapConfig = Objects.requireNonNullElse(mapConfig, DEFAULT_MAP_CONFIG);
        this.joinMaxRows = Objects.requireNonNullElse(joinMaxRows, DEFAULT_JOIN_MAX_ROWS);
        this.joinMaxSize = Objects.requireNonNullElse(joinMaxSize, DEFAULT_JOIN_MAX_SIZE);
    }

    @JsonProperty("map")
    public ResultStoreMapConfig getMapConfig() {
        return mapConfig;
    }

    /// Gets the most rows the joined side of a StroomQL join may have.
    ///
    /// @return The most rows.
    @Min(0)
    @JsonPropertyDescription("The most rows the joined side of a StroomQL join may have. The joined side is " +
                             "searched first and held in full while the rows it is joined to are searched. A " +
                             "query whose joined side has more rows fails, suggesting a filter.")
    @JsonProperty("joinMaxRows")
    public long getJoinMaxRows() {
        return joinMaxRows;
    }

    /// Gets the most space the joined side of a StroomQL join may take up.
    ///
    /// @return The most space.
    @JsonPropertyDescription("The most space the joined side of a StroomQL join may take up, as held on disk " +
                             "while the rows it is joined to are searched. A query whose joined side needs " +
                             "more fails, suggesting a filter.")
    @JsonProperty("joinMaxSize")
    public ByteSize getJoinMaxSize() {
        return joinMaxSize;
    }
}
