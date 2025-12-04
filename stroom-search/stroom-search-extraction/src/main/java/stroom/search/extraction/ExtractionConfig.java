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

package stroom.search.extraction;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ExtractionConfig extends AbstractConfig implements IsStroomConfig {

    /**
     * We don't want to collect more than 10k doc's data into the queue by
     * default. When the queue is full the index shard data tasks will pause
     * until the docs are drained from the queue.
     */
    private static final int DEFAULT_MAX_STORED_DATA_QUEUE_SIZE = 1000;
    private static final int DEFAULT_MAX_THREADS_PER_TASK = 5;
    private static final int DEFAULT_MAX_STREAM_EVENT_MAP_SIZE = 1000000;
    private static final long DEFAULT_EXTRACTION_DELAY_MS = 100;

    private final int maxStoredDataQueueSize;
    private final int maxThreadsPerTask;
    private final int maxStreamEventMapSize;
    private final long extractionDelayMs;

    public ExtractionConfig() {
        maxStoredDataQueueSize = DEFAULT_MAX_STORED_DATA_QUEUE_SIZE;
        maxThreadsPerTask = DEFAULT_MAX_THREADS_PER_TASK;
        maxStreamEventMapSize = DEFAULT_MAX_STREAM_EVENT_MAP_SIZE;
        extractionDelayMs = DEFAULT_EXTRACTION_DELAY_MS;
    }

    @JsonCreator
    public ExtractionConfig(@JsonProperty("maxStoredDataQueueSize") final int maxStoredDataQueueSize,
                            @JsonProperty("maxThreadsPerTask") final int maxThreadsPerTask,
                            @JsonProperty("maxStreamEventMapSize") final int maxStreamEventMapSize,
                            @JsonProperty("extractionDelayMs") final long extractionDelayMs) {
        this.maxStoredDataQueueSize = maxStoredDataQueueSize;
        this.maxThreadsPerTask = maxThreadsPerTask;
        this.maxStreamEventMapSize = maxStreamEventMapSize;
        this.extractionDelayMs = extractionDelayMs;
    }

    @JsonPropertyDescription("The maximum number documents that will have stored data retrieved from the index " +
            "shard and queued prior to further processing")
    public int getMaxStoredDataQueueSize() {
        return maxStoredDataQueueSize;
    }

    @JsonPropertyDescription("The maximum number of threads per search, per node, used to extract search results " +
            "from streams using a pipeline")
    public int getMaxThreadsPerTask() {
        return maxThreadsPerTask;
    }

    @JsonPropertyDescription("The maximum size of the stream event map used to queue events prior to extraction")
    public int getMaxStreamEventMapSize() {
        return maxStreamEventMapSize;
    }

    @JsonPropertyDescription("Extraction delay in milliseconds. " +
            "A delay reduces the chance of a stream being extracted more than once.")
    public long getExtractionDelayMs() {
        return extractionDelayMs;
    }

    @Override
    public String toString() {
        return "ExtractionConfig{" +
                "maxStoredDataQueueSize=" + maxStoredDataQueueSize +
                ", maxThreadsPerTask=" + maxThreadsPerTask +
                ", maxStreamEventMapSize=" + maxStreamEventMapSize +
                ", extractionDelayMs=" + extractionDelayMs +
                '}';
    }
}
