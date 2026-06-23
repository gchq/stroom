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

package stroom.index.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class IndexShardSearchConfig extends AbstractConfig implements IsStroomConfig {

    private static final int DEFAULT_MAX_DOC_ID_QUEUE_SIZE = 1_000_000;
    private static final int DEFAULT_MAX_THREADS_PER_TASK = 5;

    private final int maxDocIdQueueSize;
    private final int maxThreadsPerTask;
    private final CacheConfig remoteSearchResultCache;

    public IndexShardSearchConfig() {
        maxDocIdQueueSize = DEFAULT_MAX_DOC_ID_QUEUE_SIZE;
        maxThreadsPerTask = DEFAULT_MAX_THREADS_PER_TASK;
        remoteSearchResultCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
    }

    @JsonCreator
    public IndexShardSearchConfig(@JsonProperty("maxDocIdQueueSize") final Integer maxDocIdQueueSize,
                                  @JsonProperty("maxThreadsPerTask") final Integer maxThreadsPerTask,
                                  @JsonProperty("remoteSearchResultCache") final CacheConfig remoteSearchResultCache) {
        this.maxDocIdQueueSize = Objects.requireNonNullElse(maxDocIdQueueSize, DEFAULT_MAX_DOC_ID_QUEUE_SIZE);
        this.maxThreadsPerTask = Objects.requireNonNullElse(maxThreadsPerTask, DEFAULT_MAX_THREADS_PER_TASK);
        this.remoteSearchResultCache = remoteSearchResultCache;
    }

    @JsonPropertyDescription("The maximum number of doc ids that will be queued ready for stored data to be " +
                             "retrieved from the index shard")
    public int getMaxDocIdQueueSize() {
        return maxDocIdQueueSize;
    }

    @JsonPropertyDescription("The maximum number of threads per search, per node, used to search Lucene index shards")
    public int getMaxThreadsPerTask() {
        return maxThreadsPerTask;
    }

    public CacheConfig getRemoteSearchResultCache() {
        return remoteSearchResultCache;
    }


    @Override
    public String toString() {
        return "IndexShardSearchConfig{" +
               "maxDocIdQueueSize=" + maxDocIdQueueSize +
               ", maxThreadsPerTask=" + maxThreadsPerTask +
               ", remoteSearchResultCache=" + remoteSearchResultCache +
               '}';
    }
}
