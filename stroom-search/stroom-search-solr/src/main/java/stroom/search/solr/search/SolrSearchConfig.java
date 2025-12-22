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

package stroom.search.solr.search;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class SolrSearchConfig extends AbstractConfig implements IsStroomConfig {

    /**
     * We don't want to collect more than 10k doc's data into the queue by
     * default. When the queue is full the index shard data tasks will pause
     * until the docs are drained from the queue.
     */
    private static final int DEFAULT_MAX_STORED_DATA_QUEUE_SIZE = 1000;
    private static final int DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT = 1024;

    private final int maxStoredDataQueueSize;
    private final int maxBooleanClauseCount;

    public SolrSearchConfig() {

        maxStoredDataQueueSize = DEFAULT_MAX_STORED_DATA_QUEUE_SIZE;
        maxBooleanClauseCount = DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public SolrSearchConfig(@JsonProperty("maxStoredDataQueueSize") final int maxStoredDataQueueSize,
                            @JsonProperty("maxBooleanClauseCount") final int maxBooleanClauseCount) {
        this.maxStoredDataQueueSize = maxStoredDataQueueSize;
        this.maxBooleanClauseCount = maxBooleanClauseCount;
    }

    @JsonPropertyDescription("The maximum number documents that will have stored data retrieved from the index " +
            "shard and queued prior to further processing")
    public int getMaxStoredDataQueueSize() {
        return maxStoredDataQueueSize;
    }

    @JsonPropertyDescription("The maximum number of clauses that a boolean search can contain.")
    public int getMaxBooleanClauseCount() {
        return maxBooleanClauseCount;
    }

    @Override
    public String toString() {
        return "SolrSearchConfig{" +
                "maxStoredDataQueueSize=" + maxStoredDataQueueSize +
                ", maxBooleanClauseCount=" + maxBooleanClauseCount +
                '}';
    }
}
