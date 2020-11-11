package stroom.statistics.impl.sql.search;

import stroom.dashboard.expression.v1.Val;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsFactory;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Receiver;
import stroom.query.common.v2.ReceiverImpl;
import stroom.query.common.v2.Store;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SqlStatisticsStore implements Store {
    public static final String TASK_NAME = "Sql Statistic Search";
    private static final Logger LOGGER = LoggerFactory.getLogger(SqlStatisticsStore.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SqlStatisticsStore.class);
    private final Coprocessors coprocessors;
    private final CompletionState completionState = new CompletionState();
    private final String searchKey;

    SqlStatisticsStore(final SearchRequest searchRequest,
                       final StatisticStoreDoc statisticStoreDoc,
                       final StatisticsSearchService statisticsSearchService,
                       final Executor executor,
                       final TaskContextFactory taskContextFactory,
                       final CoprocessorsFactory coprocessorsFactory) {
        this.searchKey = searchRequest.getKey().toString();

        final List<CoprocessorSettings> coprocessorSettingsList = CoprocessorSettingsFactory.create(searchRequest);
        Preconditions.checkNotNull(coprocessorSettingsList);

        // convert the search into something stats understands
        final ExpressionOperator expression = ExpressionUtil.replaceExpressionParameters(searchRequest);
        final FindEventCriteria criteria = StatStoreCriteriaBuilder.buildCriteria(statisticStoreDoc, expression, searchRequest.getDateTimeLocale());

        // Create coprocessors.
        coprocessors = coprocessorsFactory.create(
                coprocessorSettingsList,
                searchRequest.getQuery().getParams());

        final Runnable runnable = taskContextFactory.context(TASK_NAME, taskContext -> {
            // Create the object that will receive results.
            final Receiver receiver = createReceiver(coprocessors, taskContext);

            // Execute the search asynchronously.
            // We have to create a wrapped runnable so that the task context references a managed task.
            statisticsSearchService.search(
                    taskContext, statisticStoreDoc, criteria, coprocessors.getFieldIndex(), receiver, completionState);
        });
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
        return coprocessors.getErrorConsumer().getErrors();
    }

    @Override
    public List<String> getHighlights() {
        return null;
    }

    @Override
    public String toString() {
        return "SqlStatisticsStore{" +
                ", completionState=" + completionState +
                ", searchKey='" + searchKey + '\'' +
                '}';
    }

    private Receiver createReceiver(
            final Coprocessors coprocessors,
            final TaskContext taskContext) {

        LOGGER.debug("Starting search with key {}", searchKey);
        taskContext.info(() -> "Sql Statistics search " + searchKey + " - running query");

        final Instant queryStart = Instant.now();
        final Consumer<Val[]> valuesConsumer = coprocessors.getValuesConsumer();

        final Consumer<Throwable> errorConsumer = error -> {
            LOGGER.error("Error in windowed flow: {}", error.getMessage(), error);
            coprocessors.getErrorConsumer().accept(error);
        };

        final Consumer<Long> completionConsumer = count -> {
            taskContext.info(() -> searchKey + " - complete");
            complete();

            LAMBDA_LOGGER.debug(() ->
                    LogUtil.message("Query finished in {}", Duration.between(queryStart, Instant.now())));
        };

        return new ReceiverImpl(valuesConsumer, errorConsumer, completionConsumer);
    }
}
