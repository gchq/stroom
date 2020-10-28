package stroom.statistics.server.sql.search;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.ResultHandler;
import stroom.query.common.v2.SearchResultHandler;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.TableCoprocessor;
import stroom.query.common.v2.TableCoprocessorSettings;
import stroom.query.common.v2.TablePayload;
import stroom.search.coprocessor.Error;
import stroom.search.coprocessor.Receiver;
import stroom.search.coprocessor.ReceiverImpl;
import stroom.search.coprocessor.Values;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.task.server.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasTerminate;
import stroom.util.task.TaskWrapper;

import javax.inject.Provider;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SqlStatisticsStore implements Store {
    private static final Logger LOGGER = LoggerFactory.getLogger(SqlStatisticsStore.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SqlStatisticsStore.class);

    public static final String TASK_NAME = "Sql Statistic Search";

    public static final Duration RESULT_SEND_INTERVAL = Duration.ofSeconds(1);

    private final ResultHandler resultHandler;
    private final int resultHandlerBatchSize;
    private final Sizes defaultMaxResultsSizes;
    private final Sizes storeSize;
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());
    private final CompletionState completionState = new CompletionState();
    private final HasTerminate terminationMonitor;
    private final String searchKey;

    SqlStatisticsStore(final SearchRequest searchRequest,
                       final StatisticStoreEntity statisticStoreEntity,
                       final StatisticsSearchService statisticsSearchService,
                       final Sizes defaultMaxResultsSizes,
                       final Sizes storeSize,
                       final int resultHandlerBatchSize,
                       final Executor executor,
                       final Provider<TaskWrapper> taskWrapperProvider,
                       final TaskContext taskContext) {

        this.defaultMaxResultsSizes = defaultMaxResultsSizes;
        this.storeSize = storeSize;
        this.searchKey = searchRequest.getKey().toString();
        this.resultHandlerBatchSize = resultHandlerBatchSize;

        final CoprocessorSettingsMap coprocessorSettingsMap = CoprocessorSettingsMap.create(searchRequest);
        Preconditions.checkNotNull(coprocessorSettingsMap);

        final FieldIndexMap fieldIndexMap = new FieldIndexMap(true);
        final Map<String, String> paramMap = getParamMap(searchRequest);

        terminationMonitor = taskContext;

        final Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> coprocessorMap = getCoprocessorMap(
                coprocessorSettingsMap, fieldIndexMap, paramMap);

        // convert the search into something stats understands
        final FindEventCriteria criteria = StatStoreCriteriaBuilder.buildCriteria(searchRequest, statisticStoreEntity);

        resultHandler = new SearchResultHandler(coprocessorSettingsMap, defaultMaxResultsSizes, storeSize);

        // Create the object that will receive results.
        final Receiver receiver = createReceiver(coprocessorMap, taskContext);

        // Execute the search asynchronously.
        // We have to create a wrapped runnable so that the task context references a managed task.
        Runnable runnable = () -> statisticsSearchService.search(
                statisticStoreEntity, criteria, fieldIndexMap, receiver, completionState);
        runnable = taskWrapperProvider.get().wrap(runnable);
        executor.execute(runnable);

        LOGGER.debug("Async search task started for key {}", searchKey);
    }

    @Override
    public void destroy() {
        LOGGER.debug("destroy called");

        complete();
    }

    public void complete() {
        LOGGER.debug("complete called");
        completionState.complete();
    }

    @Override
    public boolean isComplete() {
        return completionState.isComplete();
    }

    @Override
    public boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException {
        return completionState.awaitCompletion(timeout, unit);
    }


    @Override
    public Data getData(String componentId) {
        LOGGER.debug("getData called for componentId {}", componentId);

        return resultHandler.getResultStore(componentId);
    }

    @Override
    public List<String> getErrors() {
        return errors;
    }

    @Override
    public List<String> getHighlights() {
        return null;
    }

    @Override
    public Sizes getDefaultMaxResultsSizes() {
        return defaultMaxResultsSizes;
    }

    @Override
    public Sizes getStoreSize() {
        return storeSize;
    }

    @Override
    public String toString() {
        return "SqlStatisticsStore{" +
                "defaultMaxResultsSizes=" + defaultMaxResultsSizes +
                ", storeSize=" + storeSize +
                ", completionState=" + completionState +
//                ", isTerminated=" + isTerminated +
                ", searchKey='" + searchKey + '\'' +
                '}';
    }

    private Map<String, String> getParamMap(final SearchRequest searchRequest) {
        final Map<String, String> paramMap;
        if (searchRequest.getQuery().getParams() != null) {
            paramMap = searchRequest.getQuery().getParams().stream()
                    .collect(Collectors.toMap(Param::getKey, Param::getValue));
        } else {
            paramMap = Collections.emptyMap();
        }
        return paramMap;
    }

    private Receiver createReceiver(
            final Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> coprocessorMap,
            final TaskContext taskContext) {

        LOGGER.debug("Starting search with key {}", searchKey);
        taskContext.setName(TASK_NAME);
        taskContext.info("Sql Statistics search " + searchKey + " - running query");

        final LongAdder counter = new LongAdder();
        final AtomicLong nextProcessPayloadsTime = new AtomicLong(Instant.now().plus(RESULT_SEND_INTERVAL).toEpochMilli());
        final AtomicLong countSinceLastSend = new AtomicLong(0);
        final Instant queryStart = Instant.now();

        final Consumer<Values> valuesConsumer = values -> {
            counter.increment();
            countSinceLastSend.incrementAndGet();
            LAMBDA_LOGGER.trace(() -> String.format("data: [%s]", Arrays.toString(values.getValues())));

            // give the data array to each of our coprocessors
            coprocessorMap.values().forEach(coprocessor ->
                    coprocessor.receive(values.getValues()));
            // send what we have every 1s or when the batch reaches a set size
            long now = System.currentTimeMillis();
            if (now >= nextProcessPayloadsTime.get() ||
                    countSinceLastSend.get() >= resultHandlerBatchSize) {

                LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage("{} vs {}, {} vs {}",
                        now, nextProcessPayloadsTime,
                        countSinceLastSend.get(), resultHandlerBatchSize));

                processPayloads(resultHandler, coprocessorMap, terminationMonitor);
                taskContext.setName(TASK_NAME);
                taskContext.info(searchKey +
                        " - running database query (" + counter.longValue() + " rows fetched)");
                nextProcessPayloadsTime.set(Instant.now().plus(RESULT_SEND_INTERVAL).toEpochMilli());
                countSinceLastSend.set(0);
            }
        };

        final Consumer<Error> errorConsumer = error -> {
            LOGGER.error("Error in windowed flow: {}", error.getMessage(), error.getThrowable());
            errors.add(error.getMessage());
        };

        final Consumer<Long> completionConsumer = count -> {
            LAMBDA_LOGGER.debug(() ->
                    String.format("onComplete of flowable called, counter: %s",
                            counter.longValue()));
            // completed our window so create and pass on a payload for the
            // data we have gathered so far
            processPayloads(resultHandler, coprocessorMap, terminationMonitor);
            taskContext.info(searchKey + " - complete");
            complete();

            LAMBDA_LOGGER.debug(() ->
                    LambdaLogger.buildMessage("Query finished in {}", Duration.between(queryStart, Instant.now())));
        };

        return new ReceiverImpl(valuesConsumer, errorConsumer, completionConsumer);
    }

    private Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> getCoprocessorMap(
            final CoprocessorSettingsMap coprocessorSettingsMap,
            final FieldIndexMap fieldIndexMap,
            final Map<String, String> paramMap) {

        return coprocessorSettingsMap.getMap()
                .entrySet()
                .stream()
                .map(entry -> Maps.immutableEntry(
                        entry.getKey(),
                        createCoprocessor(entry.getValue(), fieldIndexMap, paramMap, terminationMonitor)))
                .filter(entry -> entry.getKey() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Synchronized to ensure multiple threads don't fight over the coprocessors which is unlikely to
     * happen anyway as it is mostly used in
     */
    private synchronized void processPayloads(final ResultHandler resultHandler,
                                              final Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> coprocessorMap,
                                              final HasTerminate terminationMonitor) {

        if (!Thread.currentThread().isInterrupted()) {
            LAMBDA_LOGGER.debug(() ->
                    LambdaLogger.buildMessage("processPayloads called for {} coprocessors", coprocessorMap.size()));

            //build a payload map from whatever the coprocessors have in them, if anything
            final Map<CoprocessorSettingsMap.CoprocessorKey, Payload> payloadMap = coprocessorMap.entrySet().stream()
                    .map(entry ->
                            Maps.immutableEntry(entry.getKey(), entry.getValue().createPayload()))
                    .filter(entry ->
                            entry.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            // log the queue sizes in the payload map
            if (LOGGER.isDebugEnabled()) {
                final String contents = payloadMap.entrySet().stream()
                        .map(entry -> {
                            String key = entry.getKey() != null ? entry.getKey().toString() : "null";
                            String size;
                            // entry checked for null in stream above
                            if (entry.getValue() instanceof TablePayload) {
                                TablePayload tablePayload = (TablePayload) entry.getValue();
                                if (tablePayload.getQueue() != null) {
                                    size = Integer.toString(tablePayload.getQueue().size());
                                } else {
                                    size = "null";
                                }
                            } else {
                                size = "?";
                            }
                            return key + ": " + size;
                        })
                        .collect(Collectors.joining(", "));
                LOGGER.debug("payloadMap: [{}]", contents);
            }

            // give the processed results to the collector, it will handle nulls
            resultHandler.handle(payloadMap, terminationMonitor);
        } else {
            LOGGER.debug("Thread is interrupted, not processing payload");
        }
    }

    private static Coprocessor createCoprocessor(final CoprocessorSettings settings,
                                                 final FieldIndexMap fieldIndexMap,
                                                 final Map<String, String> paramMap,
                                                 final HasTerminate taskMonitor) {
        if (settings instanceof TableCoprocessorSettings) {
            final TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) settings;
            return new TableCoprocessor(
                    tableCoprocessorSettings, fieldIndexMap, taskMonitor, paramMap);
        }
        return null;
    }
}
