package stroom.state.impl;

import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.datasource.api.v2.IndexField;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexFieldImpl;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchTaskProgress;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.FieldInfoResultPageBuilder;
import stroom.query.common.v2.IndexFieldProvider;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProcess;
import stroom.query.common.v2.SearchProvider;
import stroom.state.shared.StateDoc;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskProgress;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResultPage;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class StateSearchProvider implements SearchProvider, IndexFieldProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateSearchProvider.class);

    private final Executor executor;
    private final StateDocStore stateDocStore;
    private final StateDocCache stateDocCache;
    private final CqlSessionFactory cqlSessionFactory;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final TaskManager taskManager;
    private final TaskContextFactory taskContextFactory;

    @Inject
    public StateSearchProvider(final Executor executor,
                               final StateDocStore stateDocStore,
                               final StateDocCache stateDocCache,
                               final CqlSessionFactory cqlSessionFactory,
                               final CoprocessorsFactory coprocessorsFactory,
                               final ResultStoreFactory resultStoreFactory,
                               final TaskManager taskManager,
                               final TaskContextFactory taskContextFactory) {
        this.executor = executor;
        this.stateDocStore = stateDocStore;
        this.stateDocCache = stateDocCache;
        this.cqlSessionFactory = cqlSessionFactory;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.taskManager = taskManager;
        this.taskContextFactory = taskContextFactory;
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldInfoCriteria criteria) {
        return FieldInfoResultPageBuilder.builder(criteria).addAll(StateFields.QUERYABLE_FIELDS).build();
    }

    @Override
    public IndexField getIndexField(final DocRef docRef, final String fieldName) {
        final QueryField queryField = StateFields.FIELD_MAP.get(fieldName);
        if (queryField == null) {
            return null;
        }
        return IndexFieldImpl.builder().fldName(queryField.getFldName()).fldType(queryField.getFldType()).build();
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.ofNullable(stateDocCache.get(docRef)).map(StateDoc::getDescription);
    }

    @Override
    public DocRef fetchDefaultExtractionPipeline(final DocRef dataSourceRef) {
        return null;
    }

    @Override
    public ResultStore createResultStore(final SearchRequest searchRequest) {
        // Replace expression parameters.
        final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);

        // Get the search.
        final Query query = modifiedSearchRequest.getQuery();

        // Load the doc.
        final DocRef docRef = query.getDataSource();

        // Check we have permission to read the doc.
        final CqlSession session = cqlSessionFactory.getSession(docRef);

        // Extract highlights.
        final Set<String> highlights = Collections.emptySet();

        // Create a coprocessor settings list.
        final List<CoprocessorSettings> coprocessorSettingsList = coprocessorsFactory
                .createSettings(modifiedSearchRequest);

        // Create a handler for search results.
        final DataStoreSettings dataStoreSettings = DataStoreSettings
                .createBasicSearchResultStoreSettings();
        final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                modifiedSearchRequest.getSearchRequestSource(),
                modifiedSearchRequest.getDateTimeSettings(),
                modifiedSearchRequest.getKey(),
                coprocessorSettingsList,
                query.getParams(),
                dataStoreSettings);

        // Create an asynchronous search task.
        final String searchName = "Search '" + modifiedSearchRequest.getKey().toString() + "'";

        // Create the search result collector.
        final ResultStore resultStore = resultStoreFactory.create(
                modifiedSearchRequest.getSearchRequestSource(),
                coprocessors);
        resultStore.addHighlights(highlights);

        final String infoPrefix = LogUtil.message(
                "Querying {} {} - ",
                getStoreName(docRef),
                modifiedSearchRequest.getKey().toString());
        final String taskName = getTaskName(docRef);
        final ExpressionCriteria criteria = new ExpressionCriteria(query.getExpression());
        final Runnable runnable = taskContextFactory.context(searchName, taskContext -> {
            final AtomicBoolean destroyed = new AtomicBoolean();

            final SearchProcess searchProcess = new SearchProcess() {
                @Override
                public SearchTaskProgress getSearchTaskProgress() {
                    final TaskProgress taskProgress =
                            taskManager.getTaskProgress(taskContext);
                    if (taskProgress != null) {
                        return new SearchTaskProgress(
                                taskProgress.getTaskName(),
                                taskProgress.getTaskInfo(),
                                taskProgress.getUserName(),
                                taskProgress.getThreadName(),
                                taskProgress.getNodeName(),
                                taskProgress.getSubmitTimeMs(),
                                taskProgress.getTimeNowMs());
                    }
                    return null;
                }

                @Override
                public void onTerminate() {
                    destroyed.set(true);
                    taskManager.terminate(taskContext.getTaskId());
                }
            };

            // Set the search process.
            resultStore.setSearchProcess(searchProcess);

            // Don't begin execution if we have been asked to complete already.
            if (!destroyed.get()) {
                taskContext.info(() -> infoPrefix + "running query");

                final Instant queryStart = Instant.now();
                try {
                    // Give the data array to each of our coprocessors
                    StateDao.search(session, criteria, coprocessors.getFieldIndex(), coprocessors);
                    RangedStateDao.search(session, criteria, coprocessors.getFieldIndex(), coprocessors);

                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                    resultStore.addError(e);
                }

                LOGGER.debug(() ->
                        String.format("%s complete called, counter: %s",
                                taskName,
                                coprocessors.getValueCount()));
                taskContext.info(() -> infoPrefix + "complete");
                LOGGER.debug(() -> taskName + " completeSearch called");
                resultStore.signalComplete();
                LOGGER.debug(() -> taskName + " Query finished in " + Duration.between(queryStart, Instant.now()));
            }
        });
        CompletableFuture.runAsync(runnable, executor);

        return resultStore;
    }

    @Override
    public QueryField getTimeField(final DocRef docRef) {
        return StateFields.EFFECTIVE_TIME_FIELD;
    }

    private String getStoreName(final DocRef docRef) {
        return NullSafe.toStringOrElse(
                docRef,
                DocRef::getName,
                "Unknown Store");
    }

    private String getTaskName(final DocRef docRef) {
        return getStoreName(docRef) + " Search";
    }

    @Override
    public List<DocRef> list() {
        return stateDocStore.list();
    }

    @Override
    public String getType() {
        return StateDoc.DOCUMENT_TYPE;
    }
}
