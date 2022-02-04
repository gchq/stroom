package stroom.searchable.impl;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.datasource.api.v2.AbstractField;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.DataStore;
import stroom.query.common.v2.Store;
import stroom.searchable.api.Searchable;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.string.ExceptionStringUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

class SearchableStore implements Store {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchableStore.class);

    private final String searchKey;

    private final Coprocessors coprocessors;
    private final Set<Throwable> errors = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicBoolean terminate = new AtomicBoolean();
    private volatile Thread thread;

    SearchableStore(final Searchable searchable,
                    final TaskContextFactory taskContextFactory,
                    final TaskContext taskContext,
                    final SearchRequest searchRequest,
                    final Executor executor,
                    final Coprocessors coprocessors,
                    final ExpressionOperator expression) {
        this.coprocessors = coprocessors;
        searchKey = searchRequest.getKey().toString();
        final String taskName = getTaskName(searchable.getDocRef());

        final String infoPrefix = LogUtil.message(
                "Querying {} {} - ",
                getStoreName(searchable.getDocRef()),
                searchKey);

        LOGGER.debug(() -> LogUtil.message("{} Starting search with key {}", taskName, searchKey));
        taskContext.info(() -> infoPrefix + "initialising query");

        final ExpressionCriteria criteria = new ExpressionCriteria(expression);

        final Map<String, AbstractField> fieldMap = searchable.getDataSource().getFields()
                .stream()
                .collect(Collectors.toMap(AbstractField::getName, Function.identity()));

        final FieldIndex fieldIndex = coprocessors.getFieldIndex();
        final AbstractField[] fieldArray = new AbstractField[fieldIndex.size()];
        for (int i = 0; i < fieldArray.length; i++) {
            fieldArray[i] = fieldMap.get(fieldIndex.getField(i));
        }

        final Runnable runnable = taskContextFactory.context(taskName, tc -> {
            tc.info(() -> infoPrefix + "running query");
            searchAsync(tc, searchable, criteria, fieldArray, coprocessors, taskName, infoPrefix);
        });
        CompletableFuture.runAsync(runnable, executor);
    }

    private void searchAsync(final TaskContext taskContext,
                             final Searchable searchable,
                             final ExpressionCriteria criteria,
                             final AbstractField[] fieldArray,
                             final Coprocessors coprocessors,
                             final String taskName,
                             final String infoPrefix) {
        synchronized (SearchableStore.class) {
            thread = Thread.currentThread();
            if (terminate.get()) {
                return;
            }
        }

        final Instant queryStart = Instant.now();
        try {
            // Give the data array to each of our coprocessors
            searchable.search(criteria, fieldArray, coprocessors);

        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            errors.add(e);
        }

        LOGGER.debug(() ->
                String.format("%s complete called, counter: %s",
                        taskName,
                        coprocessors.getValueCount()));
        taskContext.info(() -> infoPrefix + "complete");
        LOGGER.debug(() -> taskName + " completeSearch called");
        complete();

        LOGGER.debug(() -> taskName + " Query finished in " + Duration.between(queryStart, Instant.now()));
    }

    @Override
    public void destroy() {
        synchronized (SearchableStore.class) {
            LOGGER.debug(() -> "destroy called");
            // Terminate the search
            terminate.set(true);
            if (thread != null) {
                thread.interrupt();
            }

            complete();
            coprocessors.clear();
        }
    }

    public void complete() {
        coprocessors.getCompletionState().signalComplete();
    }

    @Override
    public boolean isComplete() {
        return coprocessors.getCompletionState().isComplete();
    }

    @Override
    public void awaitCompletion() throws InterruptedException {
        coprocessors.getCompletionState().awaitCompletion();
    }

    @Override
    public boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException {
        // Results are currently assembled synchronously in getMeta so the store is always complete.
        return coprocessors.getCompletionState().awaitCompletion(timeout, unit);
    }

    @Override
    public DataStore getData(String componentId) {
        return coprocessors.getData(componentId);
    }

    @Override
    public List<String> getErrors() {
        return errors
                .stream()
                .map(ExceptionStringUtil::getMessage)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getHighlights() {
        return null;
    }

    @Override
    public String toString() {
        return "DbStore{" +
                ", completionState=" + coprocessors.getCompletionState() +
                ", searchKey='" + searchKey + '\'' +
                '}';
    }

    private static String getStoreName(final DocRef docRef) {
        return NullSafe.toStringOrElse(
                docRef,
                DocRef::getName,
                "Unknown Store");
    }

    static String getTaskName(final DocRef docRef) {
        return getStoreName(docRef) + " Search";
    }
}
