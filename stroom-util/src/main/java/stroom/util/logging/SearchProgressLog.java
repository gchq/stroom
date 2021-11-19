package stroom.util.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SearchProgressLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchProgressLog.class);
    private static final Logger SEARCH_PROGRESS_TRACE = LoggerFactory.getLogger("search_progress_trace");

    private static final Map<SearchPhase, AtomicLong> map = new ConcurrentHashMap<>();
    private static final AtomicLong startTime = new AtomicLong(0);
    private static long lastLogTimeMs;
    private static long lastTraceTimeMs;

    public static void clear() {
        if (SEARCH_PROGRESS_TRACE.isTraceEnabled()) {
            map.clear();
            startTime.set(System.currentTimeMillis());
            lastLogTimeMs = 0;
            writeHeader();
        }
    }

    public static void increment(final SearchPhase searchPhase) {
        add(searchPhase, 1);
    }

    public static void add(final SearchPhase searchPhase, final long delta) {
        if (SEARCH_PROGRESS_TRACE.isTraceEnabled()) {
            map.computeIfAbsent(searchPhase, k -> new AtomicLong()).addAndGet(delta);
//            if (LOGGER.isDebugEnabled()) {
//            periodicReport();
//            }
            periodicTrace();
        }
    }

    private static synchronized void writeHeader() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Elapsed Time (ms)");
        for (SearchPhase searchPhase : SearchPhase.values()) {
            sb.append(",");
            sb.append(searchPhase.getDisplayName());
        }
        write(sb.toString());
    }

    private static synchronized void periodicTrace() {
        final long now = System.currentTimeMillis();
        if (lastTraceTimeMs < now - 1000) {
            lastTraceTimeMs = now;
            final long elapsedTime = now - startTime.get();
            final StringBuilder sb = new StringBuilder();
            sb.append(elapsedTime);
            for (SearchPhase searchPhase : SearchPhase.values()) {
                sb.append(",");
                final AtomicLong count = map.get(searchPhase);
                if (count == null) {
                    sb.append("0");
                } else {
                    sb.append(count.get());
                }
            }
            write(sb.toString());
        }
    }

    private static synchronized void write(final String line) {
        SEARCH_PROGRESS_TRACE.trace(line);
//        if (writer != null) {
//            try {
//                writer.write(line + "\n");
//                writer.flush();
//            } catch (final IOException e) {
//                LOGGER.error(e.getMessage(), e);
//            }
//        }
    }

    private static synchronized void periodicReport() {
        final long now = System.currentTimeMillis();
        if (lastLogTimeMs < now - 10000) {
            lastLogTimeMs = now;
            report();
        }
    }

    private static String report() {
        final StringBuilder sb = new StringBuilder();
        sb.append("\n");
        map.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    sb.append(entry.getKey());
                    sb.append(" = ");
                    sb.append(entry.getValue().get());
                    sb.append("\n");
                });
        sb.append("Elapsed Time = ");
        sb.append(Duration.ofMillis(System.currentTimeMillis() - startTime.get()).toString());
        final String report = sb.toString();
        LOGGER.info(report);
        return report;
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
        LMDB_DATA_STORE_SHUTDOWN("LmdbDataStore - shutdown"),
        LMDB_DATA_STORE_READ_PAYLOAD("LmdbDataStore - readPayload"),
        LMDB_DATA_STORE_WRITE_PAYLOAD("LmdbDataStore - writePayload"),
        LMDB_DATA_STORE_AWAIT_TRANSFER("LmdbDataStore - awaitTransfer"),
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
        //        ASYNC_SEARCH_TASK_HANDLER_EXEC("AsyncSearchTaskHandler - exec"),
//        ASYNC_SEARCH_TASK_HANDLER_SEARCH_NODE("AsyncSearchTaskHandler - searchNode"),
//        LOCAL_NODE_SEARCH_SEARCH_NODE("LocalNodeSearch - searchNode"),
//        REMOTE_NODE_SEARCH_SEARCH_NODE("RemoteNodeSearch - searchNode"),
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
}
