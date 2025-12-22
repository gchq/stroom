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

import stroom.query.api.QueryKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SearchProgressLog {

    private static final Logger SEARCH_PROGRESS_TRACE = LoggerFactory.getLogger("search_progress_trace");

    private static final Map<QueryKey, Stats> map = new ConcurrentHashMap<>();
    private static long lastTraceTimeMs;
    private static boolean writtenHeader;

    public static void increment(final QueryKey queryKey, final SearchPhase searchPhase) {
        if (queryKey != null) {
            add(queryKey, searchPhase, 1);
        }
    }

    public static void add(final QueryKey queryKey, final SearchPhase searchPhase, final long delta) {
        if (queryKey != null) {
            if (SEARCH_PROGRESS_TRACE.isTraceEnabled()) {
                map
                        .computeIfAbsent(queryKey, k -> new Stats(queryKey))
                        .add(searchPhase, delta);
                periodicTrace();
            }
        }
    }

    private static synchronized void periodicTrace() {
        final long now = System.currentTimeMillis();
        if (lastTraceTimeMs < now - 1000) {
            lastTraceTimeMs = now;

            if (!writtenHeader) {
                writtenHeader = true;
                writeHeader();
            }

            map.values().forEach(stats -> {
                write(stats.trace());

                // Has the stat been idle for more than a minute?
                if (stats.getLastUpdateTime() + 60000 < now) {
                    // Remove old stat.
                    map.remove(stats.getQueryKey());
                }
            });
        }
    }

    private static synchronized void writeHeader() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Query Key,");
        sb.append("Elapsed Time (ms)");
        for (final SearchPhase searchPhase : SearchPhase.values()) {
            sb.append(",");
            sb.append(searchPhase.getDisplayName());
        }
        write(sb.toString());
    }

    private static synchronized void write(final String line) {
        SEARCH_PROGRESS_TRACE.trace(line);
    }

    public enum SearchPhase {
        STREAM_EVENT_MAP_TAKE("ExtractionDecoratorFactory - streamEventMap.take"),
        LMDB_DATA_STORE_ADD("LmdbDataStore - add"),
        LMDB_DATA_STORE_PUT("LmdbDataStore - put"),
        LMDB_DATA_STORE_QUEUE_POLL("LmdbDataStore - queue.poll"),
        LMDB_DATA_STORE_INSERT("LmdbDataStore - insert"),
        LMDB_DATA_STORE_DBI_PUT("LmdbDataStore - dbi.put"),
        LMDB_DATA_STORE_COMBINE("LmdbDataStore - combine"),
        LMDB_DATA_STORE_CREATE_PAYLOAD("LmdbDataStore - createPayload"),
        LMDB_DATA_STORE_GET("LmdbDataStore - get"),
        LMDB_DATA_STORE_GET_CHILDREN("LmdbDataStore - getChildren"),
        LMDB_DATA_STORE_CLEAR("LmdbDataStore - clear"),
        LMDB_DATA_STORE_READ_PAYLOAD("LmdbDataStore - readPayload"),
        LMDB_DATA_STORE_WRITE_PAYLOAD("LmdbDataStore - writePayload"),
        EXTRACTION_TASK_HANDLER_EXEC("ExtractionTaskHandler - exec"),
        EXTRACTION_TASK_HANDLER_EXTRACT("ExtractionTaskHandler - extract"),
        EXTRACTION_TASK_HANDLER_EXTRACT_EVENTS("ExtractionTaskHandler - extract - events"),
        EXTRACTION_TASK_HANDLER_EXTRACT2("ExtractionTaskHandler - extract2"),
        EXTRACTION_TASK_HANDLER_EXTRACT2_EVENTS("ExtractionTaskHandler - extract2 - events"),
        SEARCH_RESULT_OUTPUT_FILTER_START_DATA("SearchResultOutputFilter - startData"),
        SEARCH_RESULT_OUTPUT_FILTER_START_RECORD("SearchResultOutputFilter - startRecord"),
        SEARCH_RESULT_OUTPUT_FILTER_END_RECORD("SearchResultOutputFilter - endRecord"),
        EXTRACTION_DECORATOR_FACTORY_STORED_DATA_QUEUE_PUT("ExtractionDecoratorFactory - storedDataQueue.put"),
        EXTRACTION_DECORATOR_FACTORY_STORED_DATA_QUEUE_TAKE("ExtractionDecoratorFactory - storedDataQueue.take"),
        EXTRACTION_DECORATOR_FACTORY_STREAM_EVENT_MAP_PUT("ExtractionDecoratorFactory - streamEventMap.put"),
        EXTRACTION_DECORATOR_FACTORY_STREAM_EVENT_MAP_TAKE("ExtractionDecoratorFactory - streamEventMap.take"),
        EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS("ExtractionDecoratorFactory - createTasks"),
        EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS_EVENTS("ExtractionDecoratorFactory - createTasks - events"),
        EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS_DOCREF("ExtractionDecoratorFactory - createTasks - docref"),
        EXTRACTION_DECORATOR_FACTORY_CREATE_TASKS_NO_DOCREF("ExtractionDecoratorFactory - createTasks - no docref"),
        CLUSTER_SEARCH_TASK_HANDLER_EXEC("ClusterSearchTaskHandler - exec"),
        CLUSTER_SEARCH_TASK_HANDLER_SEARCH("ClusterSearchTaskHandler - search"),
        INDEX_SHARD_SEARCH_FACTORY_SEARCH("IndexShardSearchFactory - search"),
        INDEX_SHARD_SEARCH_TASK_HANDLER_SEARCH_SHARD("IndexShardSearchTaskHandler - searchShard"),
        INDEX_SHARD_SEARCH_TASK_HANDLER_DOC_ID_STORE_PUT("IndexShardSearchTaskHandler - docIdStore.put"),
        INDEX_SHARD_SEARCH_TASK_HANDLER_DOC_ID_STORE_TAKE("IndexShardSearchTaskHandler - docIdStore.take"),
        INDEX_SHARD_SEARCH_TASK_HANDLER_GET_STORED_DATA("IndexShardSearchTaskHandler - getStoredData");

        private final String displayName;

        SearchPhase(final String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static class Stats {

        private final QueryKey queryKey;
        private final long createTime = System.currentTimeMillis();
        private final Map<SearchPhase, AtomicLong> map = new ConcurrentHashMap<>();
        private final AtomicLong lastUpdateTime = new AtomicLong(createTime);

        public Stats(final QueryKey queryKey) {
            this.queryKey = queryKey;
        }

        void add(final SearchPhase searchPhase, final long delta) {
            map.computeIfAbsent(searchPhase, k -> new AtomicLong())
                    .addAndGet(delta);
            lastUpdateTime.set(System.currentTimeMillis());
        }

        String trace() {
            final long now = System.currentTimeMillis();
            final long elapsedTime = now - createTime;
            final StringBuilder sb = new StringBuilder();
            sb.append(queryKey);
            sb.append(",");
            sb.append(elapsedTime);

            for (final SearchPhase searchPhase : SearchPhase.values()) {
                sb.append(",");
                final AtomicLong count = map.get(searchPhase);
                if (count == null) {
                    sb.append("0");
                } else {
                    sb.append(count.get());
                }
            }
            return sb.toString();
        }

        QueryKey getQueryKey() {
            return queryKey;
        }

        long getLastUpdateTime() {
            return lastUpdateTime.get();
        }
    }
}
