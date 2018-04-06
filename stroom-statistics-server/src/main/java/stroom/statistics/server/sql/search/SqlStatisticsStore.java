package stroom.statistics.server.sql.search;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.CompletionListener;
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.ResultHandler;
import stroom.query.common.v2.SearchResultHandler;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.StoreSize;
import stroom.query.common.v2.TableCoprocessor;
import stroom.query.common.v2.TableCoprocessorSettings;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.task.server.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasTerminate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class SqlStatisticsStore implements Store {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlStatisticsStore.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SqlStatisticsStore.class);

    private static final long PROCESS_PAYLOAD_INTERVAL_SECS = 1L;

    private final ResultHandler resultHandler;
    private final Disposable searchResultsDisposable;

    private final List<Integer> defaultMaxResultsSizes;
    private final StoreSize storeSize;
    private final AtomicBoolean isComplete;
    private final AtomicBoolean isTerminated;
    private final Queue<CompletionListener> completionListeners = new ConcurrentLinkedQueue<>();
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());
    private final HasTerminate terminationMonitor;
    private final String searchKey;

    SqlStatisticsStore(final SearchRequest searchRequest,
                       final StatisticStoreEntity statisticStoreEntity,
                       final StatisticsSearchService statisticsSearchService,
                       final List<Integer> defaultMaxResultsSizes,
                       final StoreSize storeSize,
                       final Executor executor,
                       final TaskContext taskContext) {

        this.defaultMaxResultsSizes = defaultMaxResultsSizes;
        this.storeSize = storeSize;
        this.isComplete = new AtomicBoolean(false);
        this.isTerminated = new AtomicBoolean(false);
        this.searchKey = searchRequest.getKey().toString();

        final CoprocessorSettingsMap coprocessorSettingsMap = CoprocessorSettingsMap.create(searchRequest);
        Preconditions.checkNotNull(coprocessorSettingsMap);

        final FieldIndexMap fieldIndexMap = new FieldIndexMap(true);
        final Map<String, String> paramMap = getParamMap(searchRequest);

        terminationMonitor = getTerminationMonitor();

        final Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> coprocessorMap = getCoprocessorMap(
                coprocessorSettingsMap, fieldIndexMap, paramMap);

        // convert the search into something stats understands
        final FindEventCriteria criteria = StatStoreCriteriaBuilder.buildCriteria(searchRequest, statisticStoreEntity);

        resultHandler = new SearchResultHandler(coprocessorSettingsMap, defaultMaxResultsSizes, storeSize);

        //get the flowable for the search results
        final Flowable<String[]> searchResultsFlowable = statisticsSearchService.search(
                statisticStoreEntity, criteria, fieldIndexMap);

        this.searchResultsDisposable = startSearch(searchResultsFlowable, coprocessorMap, executor, taskContext);
    }


    @Override
    public void destroy() {
        LOGGER.debug("destroy called");
        //mark this store as terminated so the taskMonitor make it available
        isTerminated.set(true);
        //terminate the search
        searchResultsDisposable.dispose();
    }

    @Override
    public boolean isComplete() {
        return isComplete.get();
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
    public List<Integer> getDefaultMaxResultsSizes() {
        return defaultMaxResultsSizes;
    }

    @Override
    public StoreSize getStoreSize() {
        return storeSize;
    }

    @Override
    public void registerCompletionListener(final CompletionListener completionListener) {
        completionListeners.add(Objects.requireNonNull(completionListener));

        if (isComplete.get()) {
            //immediate notification
            notifyListenersOfCompletion();
        }
    }

    @Override
    public String toString() {
        return "SqlStatisticsStore{" +
                "defaultMaxResultsSizes=" + defaultMaxResultsSizes +
                ", storeSize=" + storeSize +
                ", isComplete=" + isComplete +
                ", isTerminated=" + isTerminated +
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

    private Disposable startSearch(final Flowable<String[]> searchResultsFlowable,
                                   final Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> coprocessorMap,
                                   final Executor executor,
                                   final TaskContext taskContext) {

        final AtomicLong counter = new AtomicLong(0);
        // subscribe to the flowable, mapping each resultSet to a String[]
        // After the window period has elapsed a new flowable is create for those rows received
        // in that window, which can all be processed and sent
        // If the task is canceled, the flowable produced by search() will stop emitting
        // Set up the results flowable, the search wont be executed until subscribe is called
        final Disposable searchResultsDisposable = searchResultsFlowable
                .subscribeOn(Schedulers.from(executor))
                .window(PROCESS_PAYLOAD_INTERVAL_SECS, TimeUnit.SECONDS)
                .subscribe(
                        windowedFlowable -> {
                            LOGGER.trace("onNext called for outer flowable");
                            windowedFlowable.subscribe(
                                    data -> {
                                        LAMBDA_LOGGER.trace(() -> String.format("data: [%s]", Arrays.toString(data)));
                                        counter.incrementAndGet();

                                        // give the data array to each of our coprocessors
                                        coprocessorMap.values().forEach(coprocessor ->
                                                coprocessor.receive(data));
                                    },
                                    throwable -> {
                                        LOGGER.error("Error in windowed flow: {}", throwable.getMessage(), throwable);
                                        errors.add(throwable.getMessage());
                                    },
                                    () -> {
                                        LAMBDA_LOGGER.debug(() ->
                                                String.format("onComplete of inner flowable called, processing results so far, counter: %s",
                                                        counter.get()));
                                        //completed our timed window so create and pass on a payload for the
                                        //data we have gathered so far
                                        processPayloads(resultHandler, coprocessorMap, terminationMonitor);

                                        taskContext.info("Sql Statistics search " + searchKey +
                                                " - running database query (" + counter.get() + " rows fetched)");
                                    });
                        },
                        throwable -> {
                            LOGGER.error("Error in flow: {}", throwable.getMessage(), throwable);
                            errors.add(throwable.getMessage());
                            completeSearch();
                        },
                        () -> {
                            LOGGER.debug("onComplete of outer flowable called");
                            //flows all complete, so process any remaining data
                            taskContext.info("Sql Statistics search " + searchKey +
                                    " - completed database query (" + counter.get() + " rows fetched)");
                            processPayloads(resultHandler, coprocessorMap, terminationMonitor);
                            completeSearch();
                        });

        LOGGER.debug("Out of flowable");

        return searchResultsDisposable;
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
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void notifyListenersOfCompletion() {
        //Call isComplete to ensure we are complete and not terminated
        LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage("notifyListenersOfCompletion called for {} listeners",
                completionListeners.size()));

        if (isComplete()) {
            for (CompletionListener listener; (listener = completionListeners.poll()) != null; ) {
                //when notified they will check isComplete
                LOGGER.debug("Notifying {} {} that we are complete", listener.getClass().getName(), listener);
                listener.onCompletion();
            }
        }
    }

    private void completeSearch() {
        LOGGER.debug("completeSearch called");
        isComplete.set(true);
        notifyListenersOfCompletion();
        resultHandler.setComplete(true);
    }

    /**
     * Synchronized to ensure multiple threads don't fight over the coprocessors which is unlikely to
     * happen anyway as it is mostly used in
     */
    private synchronized void processPayloads(final ResultHandler resultHandler,
                                              final Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> coprocessorMap,
                                              final HasTerminate terminationMonitor) {

        LAMBDA_LOGGER.debug(() ->
                LambdaLogger.buildMessage("processPayloads called for {} coprocessors", coprocessorMap.size()));

        //build a payload map from whatever the coprocessors have in them, if anything
        final Map<CoprocessorSettingsMap.CoprocessorKey, Payload> payloadMap = coprocessorMap.entrySet().stream()
                .map(entry ->
                        Maps.immutableEntry(entry.getKey(), entry.getValue().createPayload()))
                .filter(entry ->
                        entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // give the processed results to the collector, it will handle nulls
        resultHandler.handle(payloadMap, terminationMonitor);
    }

    private HasTerminate getTerminationMonitor() {

        return new HasTerminate() {

            @Override
            public void terminate() {
                isTerminated.set(true);
            }

            @Override
            public boolean isTerminated() {
                return isTerminated.get();
            }
        };
    }

    private static Coprocessor createCoprocessor(final CoprocessorSettings settings,
                                                 final FieldIndexMap fieldIndexMap,
                                                 final Map<String, String> paramMap,
                                                 final HasTerminate taskMonitor) {
        if (settings instanceof TableCoprocessorSettings) {
            final TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) settings;
            final TableCoprocessor tableCoprocessor = new TableCoprocessor(
                    tableCoprocessorSettings, fieldIndexMap, taskMonitor, paramMap);
            return tableCoprocessor;
        }
        return null;
    }

}
