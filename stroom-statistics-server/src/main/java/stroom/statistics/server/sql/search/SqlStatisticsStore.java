package stroom.statistics.server.sql.search;

import com.google.common.collect.Maps;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.query.common.v2.CompletionListener;
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.CoprocessorSettingsMap;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.ResultHandler;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.StoreSize;
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

    public static final long PROCESS_PAYLOAD_INTERVAL_SECS = 1L;

    private final ResultHandler resultHandler;
    private final Disposable searchResultsDisposable;
//    private final Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> coprocessorMap;


    private final List<Integer> defaultMaxResultsSizes;
    private final StoreSize storeSize;
    //results are currently assembled synchronously in getData so the store is always complete
    private final AtomicBoolean isComplete;
    private final AtomicBoolean isTerminated;
    private final Queue<CompletionListener> completionListeners = new ConcurrentLinkedQueue<>();
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());
    private final HasTerminate taskMonitor;

    public SqlStatisticsStore(final List<Integer> defaultMaxResultsSizes,
                              final StoreSize storeSize,
                              final ResultHandler resultHandler,
                              final Flowable<String[]> searchResultsFlowable,
                              final Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> coprocessorMap,
                              final Executor executor,
                              final HasTerminate taskMonitor) {
        this.defaultMaxResultsSizes = defaultMaxResultsSizes;
        this.storeSize = storeSize;
        this.resultHandler = resultHandler;
        this.isComplete = new AtomicBoolean(false);
        this.isTerminated = new AtomicBoolean(false);
        this.taskMonitor = taskMonitor;

        this.searchResultsDisposable = startSearch(searchResultsFlowable, coprocessorMap, executor);
    }

    @Override
    public void destroy() {
//        isTerminated.set(true);
        LOGGER.debug("destroy called");
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

//        final CoprocessorSettingsMap.CoprocessorKey coprocessorKey = coprocessorSettingsMap.getCoprocessorKey(componentId);
//        if (coprocessorKey == null) {
//            return null;
//        }
//
//        TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) coprocessorSettingsMap.getMap()
//                .get(coprocessorKey);
//        TableSettings tableSettings = tableCoprocessorSettings.getTableSettings();
//
//        Payload payload = payloadMap.get(coprocessorKey);
//        TablePayload tablePayload = (TablePayload) payload;
//        UnsafePairQueue<Key, Item> queue = tablePayload.getQueue();
//
//        CompiledSorter compiledSorter = new CompiledSorter(tableSettings.getFields());
//        final ResultStoreCreator resultStoreCreator = new ResultStoreCreator(compiledSorter);
//        resultStoreCreator.read(queue);
//
//
//        // Trim the number of results in the store.
//        resultStoreCreator.trim(storeSize);
//
//        return resultStoreCreator.create(queue.size(), queue.size());

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

    private void notifyListenersOfCompletion() {
        //Call isComplete to ensure we are complete and not terminated
        LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage("notifyListenersOfCompletion called for {} listeners",
                completionListeners.size()));

        if (isComplete()) {
            for (CompletionListener listener; (listener = completionListeners.poll()) != null;){
                //when notified they will check isComplete
                LOGGER.debug("Notifying {} {} that we are complete", listener.getClass().getName(), listener);
                listener.onCompletion();
            }
        }
    }

    private Disposable startSearch(final Flowable<String[]> searchResultsFlowable,
                                   final Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> coprocessorMap,
                                   final Executor executor) {

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
//                                        throw new RuntimeException(String.format("Error in flow, %s",
//                                                throwable.getMessage()), throwable);
                                    },
                                    () -> {
                                        LAMBDA_LOGGER.debug(() ->
                                                String.format("onComplete of inner flowable called, processing results so far, counter: %s",
                                                        counter.get()));
                                        //completed our timed window so create and pass on a payload for the
                                        //data we have gathered so far
                                        processPayloads(resultHandler, coprocessorMap, taskMonitor);

//                                        taskMonitor.info(task.getSearchName() +
//                                                " - running database query (" + counter.get() + " rows fetched)");
                                    });
                        },
                        throwable -> {
                            LOGGER.error("Error in flow: {}", throwable.getMessage(), throwable);
                            errors.add(throwable.getMessage());
                            completeSearch();
//                            throw new RuntimeException(String.format("Error in flow, %s",
//                                    throwable.getMessage()), throwable);
                        },
                        () -> {
                            LOGGER.debug("onComplete of outer flowable called");
                            //flows all complete, so process any remaining data
                            processPayloads(resultHandler, coprocessorMap, taskMonitor);
                            completeSearch();
                        });

        LOGGER.debug("Out of flowable");

        return searchResultsDisposable;
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
                                              final HasTerminate taskMonitor) {

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
        resultHandler.handle(payloadMap, taskMonitor);
    }
}
