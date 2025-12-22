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
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Min;

@NotInjectableConfig
public abstract class AbstractResultStoreConfig extends AbstractConfig {

    private final int maxPutsBeforeCommit;
    private final boolean offHeapResults;
    private final int valueQueueSize;
    private final ByteSize minPayloadSize;
    private final ByteSize maxPayloadSize;
    private final int maxStringFieldLength;
    private final int maxSortedItems;

    private final ResultStoreLmdbConfig lmdbConfig;

    AbstractResultStoreConfig() {
        this(10_000,
                true,
                ByteSize.ofMebibytes(1),
                ByteSize.ofGibibytes(1),
                1000,
                10_000,
                500_000,
                ResultStoreLmdbConfig.builder().localDir("search_results").build());
    }

    AbstractResultStoreConfig(final int maxPutsBeforeCommit,
                              final boolean offHeapResults,
                              final ByteSize minPayloadSize,
                              final ByteSize maxPayloadSize,
                              final int maxStringFieldLength,
                              final int valueQueueSize,
                              final int maxSortedItems,
                              final ResultStoreLmdbConfig lmdbConfig) {
        this.maxPutsBeforeCommit = maxPutsBeforeCommit;
        this.offHeapResults = offHeapResults;
        this.minPayloadSize = minPayloadSize;
        this.maxPayloadSize = maxPayloadSize;
        this.maxStringFieldLength = maxStringFieldLength;
        this.valueQueueSize = valueQueueSize;
        this.maxSortedItems = maxSortedItems;
        this.lmdbConfig = lmdbConfig;
    }


    @Min(0)
    @JsonPropertyDescription("The maximum number of puts into the store (in a single load) before the " +
            "transaction is committed. There is only one write transaction available at a time so reducing " +
            "this value allows multiple loads to potentially each load a chunk at a time. However, load times " +
            "increase rapidly with values below around 2,000. For maximum performance of a single load set this " +
            "value to 0 to only commit at the very end of the load.")
    public int getMaxPutsBeforeCommit() {
        return maxPutsBeforeCommit;
    }

    @JsonPropertyDescription("Should search results be stored off heap (experimental feature).")
    @JsonProperty("offHeapResults")
    public boolean isOffHeapResults() {
        return offHeapResults;
    }

    @JsonPropertyDescription("The minimum byte size of a payload buffer.")
    public ByteSize getMinPayloadSize() {
        return minPayloadSize;
    }

    @JsonPropertyDescription("The maximum byte size of a payload buffer.")
    public ByteSize getMaxPayloadSize() {
        return maxPayloadSize;
    }

    @JsonPropertyDescription("The maximum length of a string field value. Longer strings will be truncated.")
    public int getMaxStringFieldLength() {
        return maxStringFieldLength;
    }

    @JsonPropertyDescription("The size of the value queue.")
    @JsonProperty("valueQueueSize")
    public int getValueQueueSize() {
        return valueQueueSize;
    }

    @JsonPropertyDescription("Maximum number of results that can be returned in a single page if the results are " +
            "sorted. This will affect downloading all search results if results are sorted.")
    @JsonProperty("maxSortedItems")
    public int getMaxSortedItems() {
        return maxSortedItems;
    }

    @JsonProperty("lmdb")
    public ResultStoreLmdbConfig getLmdbConfig() {
        return lmdbConfig;
    }

    @Override
    public String toString() {
        return "AbstractResultStoreConfig{" +
                "maxPutsBeforeCommit=" + maxPutsBeforeCommit +
                ", offHeapResults=" + offHeapResults +
                ", valueQueueSize=" + valueQueueSize +
                ", minPayloadSize=" + minPayloadSize +
                ", maxPayloadSize=" + maxPayloadSize +
                ", maxStringFieldLength=" + maxStringFieldLength +
                ", maxSortedItems=" + maxSortedItems +
                ", lmdbConfig=" + lmdbConfig +
                '}';
    }
}
