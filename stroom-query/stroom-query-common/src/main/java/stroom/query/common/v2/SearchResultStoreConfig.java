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

import stroom.query.language.functions.TimeWindows;
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
    static final int DEFAULT_MAX_WINDOW_COPIES_PER_EVENT = TimeWindows.MAX_WINDOWS_PER_TIME;
    static final ResultStoreLmdbConfig DEFAULT_LMDB_CONFIG =
            ResultStoreLmdbConfig.builder().localDir("search_results").build();

    private final ResultStoreMapConfig mapConfig;
    private final long joinMaxRows;
    private final ByteSize joinMaxSize;
    private final int maxWindowCopiesPerEvent;

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
        this.maxWindowCopiesPerEvent = DEFAULT_MAX_WINDOW_COPIES_PER_EVENT;
    }

    /// Creates the config with the default limit on the copies of each event that a StroomQL window
    /// makes.
    ///
    /// @param maxPutsBeforeCommit  The most puts into the store before it is committed.
    /// @param offHeapResults       Whether results are stored off heap.
    /// @param minPayloadSize       The least size of a payload buffer.
    /// @param maxPayloadSize       The most size of a payload buffer.
    /// @param maxStringFieldLength The most length of a string field value.
    /// @param valueQueueSize       The size of the value queue.
    /// @param maxSortedItems       The most results in a page of sorted results.
    /// @param lmdbConfig           The LMDB config.
    /// @param mapConfig            The config of results held in memory.
    /// @param joinMaxRows          The most rows the joined side of a join may have.
    /// @param joinMaxSize          The most space the joined side of a join may take up.
    public SearchResultStoreConfig(final Integer maxPutsBeforeCommit,
                                   final Boolean offHeapResults,
                                   final ByteSize minPayloadSize,
                                   final ByteSize maxPayloadSize,
                                   final Integer maxStringFieldLength,
                                   final Integer valueQueueSize,
                                   final Integer maxSortedItems,
                                   final ResultStoreLmdbConfig lmdbConfig,
                                   final ResultStoreMapConfig mapConfig,
                                   final Long joinMaxRows,
                                   final ByteSize joinMaxSize) {
        this(maxPutsBeforeCommit,
                offHeapResults,
                minPayloadSize,
                maxPayloadSize,
                maxStringFieldLength,
                valueQueueSize,
                maxSortedItems,
                lmdbConfig,
                mapConfig,
                joinMaxRows,
                joinMaxSize,
                null);
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
                                   @JsonProperty("joinMaxSize") final ByteSize joinMaxSize,
                                   @JsonProperty("maxWindowCopiesPerEvent")
                                   final Integer maxWindowCopiesPerEvent) {
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
        this.maxWindowCopiesPerEvent = Objects.requireNonNullElse(
                maxWindowCopiesPerEvent, DEFAULT_MAX_WINDOW_COPIES_PER_EVENT);
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

    /// Gets the most times one event may be added to the results of a StroomQL query that has a
    /// window clause.
    ///
    /// @return The most copies of each event.
    @Min(1)
    @JsonPropertyDescription("The most times one event may be added to the results of a StroomQL query that " +
                             "has a window clause. An event is added once for each window it is in, which is " +
                             "the window size divided by its advance, and once more for each of those for " +
                             "every different distance that lag() and lead() read. A query that would add " +
                             "an event more times is refused, suggesting a larger advance.")
    @JsonProperty("maxWindowCopiesPerEvent")
    public int getMaxWindowCopiesPerEvent() {
        return maxWindowCopiesPerEvent;
    }
}
