package stroom.searchable.impl;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.datasource.api.v2.AbstractField;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Store;
import stroom.searchable.api.Searchable;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

class SearchableStore implements Store {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchableStore.class);

    private static final String TASK_NAME = "DB Search";

    private final String searchKey;

    private final CompletionState completionState = new CompletionState();
    private final Coprocessors coprocessors;
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());
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
        LOGGER.debug(() -> LogUtil.message("Starting search with key {}", searchKey));
        taskContext.info(() -> "DB search " + searchKey + " - running query");

        final ExpressionCriteria criteria = new ExpressionCriteria(expression);

        final Map<String, AbstractField> fieldMap = searchable.getDataSource().getFields()
                .stream()
                .collect(Collectors.toMap(AbstractField::getName, Function.identity()));

        final FieldIndex fieldIndex = coprocessors.getFieldIndex();
        final AbstractField[] fieldArray = new AbstractField[fieldIndex.size()];
        for (int i = 0; i < fieldArray.length; i++) {
            fieldArray[i] = fieldMap.get(fieldIndex.getField(i));
        }

        final Runnable runnable = taskContextFactory.context(taskContext, TASK_NAME, tc ->
                searchAsync(tc, searchable, criteria, fieldArray, coprocessors));
        CompletableFuture.runAsync(runnable, executor);
    }

    private void searchAsync(final TaskContext taskContext,
                             final Searchable searchable,
                             final ExpressionCriteria criteria,
                             final AbstractField[] fieldArray,
                             final Coprocessors coprocessors) {
        synchronized (SearchableStore.class) {
            thread = Thread.currentThread();
            if (terminate.get()) {
                return;
            }
        }

        final Instant queryStart = Instant.now();
        try {
            // Give the data array to each of our coprocessors
            searchable.search(criteria, fieldArray, coprocessors.getValuesConsumer());

        } catch (final RuntimeException e) {
            errors.add(e.getMessage());
        }

        LOGGER.debug(() ->
                String.format("complete called, counter: %s",
                        coprocessors.getValueCount()));
        taskContext.info(() -> searchKey + " - complete");
        LOGGER.debug(() -> "completeSearch called");
        complete();

        LOGGER.debug(() -> "Query finished in " + Duration.between(queryStart, Instant.now()));
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
        }
    }

    public void complete() {
        completionState.complete();
    }

    @Override
    public boolean isComplete() {
        return completionState.isComplete();
    }

    @Override
    public void awaitCompletion() throws InterruptedException {
        completionState.awaitCompletion();
    }

    @Override
    public boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException {
        // Results are currently assembled synchronously in getMeta so the store is always complete.
        return completionState.awaitCompletion(timeout, unit);
    }

    @Override
    public Data getData(String componentId) {
        return coprocessors.getData(componentId);
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
    public String toString() {
        return "DbStore{" +
                ", completionState=" + completionState +
                ", searchKey='" + searchKey + '\'' +
                '}';
    }
}
