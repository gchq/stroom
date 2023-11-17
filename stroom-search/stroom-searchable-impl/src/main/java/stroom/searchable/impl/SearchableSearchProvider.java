package stroom.searchable.impl;

import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchTaskProgress;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProcess;
import stroom.query.common.v2.SearchProvider;
import stroom.query.common.v2.Sizes;
import stroom.searchable.api.Searchable;
import stroom.searchable.api.SearchableProvider;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskProgress;
import stroom.ui.config.shared.UiConfig;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.google.common.base.Preconditions;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.inject.Inject;

@SuppressWarnings("unused")
class SearchableSearchProvider implements SearchProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchableSearchProvider.class);

    private final Executor executor;
    private final TaskManager taskManager;
    private final TaskContextFactory taskContextFactory;
    private final UiConfig clientConfig;
    private final SearchableProvider searchableProvider;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final SecurityContext securityContext;

    @Inject
    SearchableSearchProvider(final Executor executor,
                             final TaskManager taskManager,
                             final TaskContextFactory taskContextFactory,
                             final UiConfig clientConfig,
                             final SearchableProvider searchableProvider,
                             final CoprocessorsFactory coprocessorsFactory,
                             final ResultStoreFactory resultStoreFactory,
                             final SecurityContext securityContext) {
        this.executor = executor;
        this.taskManager = taskManager;
        this.taskContextFactory = taskContextFactory;
        this.clientConfig = clientConfig;
        this.searchableProvider = searchableProvider;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.securityContext = securityContext;
    }

    @Override
    public ResultPage<FieldInfo> getFieldInfo(final FindFieldInfoCriteria criteria) {
        final Optional<ResultPage<FieldInfo>> optional = securityContext.useAsReadResult(() -> {
            final Searchable searchable = searchableProvider.get(criteria.getDataSourceRef());
            if (searchable != null) {
                return Optional.ofNullable(searchable.getFieldInfo(criteria));
            }
            return Optional.empty();
        });
        return optional.orElseGet(() -> {
            final List<FieldInfo> list = Collections.emptyList();
            return ResultPage.createCriterialBasedList(list, criteria);
        });
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.ofNullable(searchableProvider.get(docRef))
                .flatMap(searchable -> searchable.fetchDocumentation(docRef));
    }

    @Override
    public DocRef fetchDefaultExtractionPipeline(final DocRef dataSourceRef) {
        return null;
    }

    @Override
    public ResultStore createResultStore(final SearchRequest searchRequest) {
        final DocRef docRef = Preconditions.checkNotNull(
                Preconditions.checkNotNull(
                                Preconditions.checkNotNull(searchRequest)
                                        .getQuery())
                        .getDataSource());
        final Searchable searchable = searchableProvider.get(docRef);
        Preconditions.checkNotNull(searchable, "Searchable could not be found for uuid " + docRef.getUuid());

        final String taskName = getTaskName(docRef);

        return taskContextFactory.contextResult(taskName, taskContext -> {
            LOGGER.debug("create called for searchRequest {} ", searchRequest);

            Preconditions.checkNotNull(searchRequest.getResultRequests(),
                    "searchRequest must have at least one resultRequest");
            Preconditions.checkArgument(!searchRequest.getResultRequests().isEmpty(),
                    "searchRequest must have at least one resultRequest");

            Preconditions.checkNotNull(searchable, "Searchable could not be found for " + docRef);

            // Replace expression parameters.
            final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);

            // Create a handler for search results.
            final CoprocessorsImpl coprocessors =
                    coprocessorsFactory.create(modifiedSearchRequest,
                            DataStoreSettings.createBasicSearchResultStoreSettings());

            return buildStore(taskContext,
                    modifiedSearchRequest,
                    searchable,
                    coprocessors,
                    modifiedSearchRequest.getQuery().getExpression());
        }).get();
    }

    private ResultStore buildStore(final TaskContext parentTaskContext,
                                   final SearchRequest searchRequest,
                                   final Searchable searchable,
                                   final CoprocessorsImpl coprocessors,
                                   final ExpressionOperator expression) {
        Preconditions.checkNotNull(searchRequest);
        Preconditions.checkNotNull(searchable);

        final DocRef docRef = searchable.getDocRef();
        final Sizes defaultMaxResultsSizes = getDefaultMaxResultsSizes();
        final int resultHandlerBatchSize = getResultHandlerBatchSize();

        final ResultStore resultStore = resultStoreFactory.create(
                searchRequest.getSearchRequestSource(),
                coprocessors);
        final String searchKey = searchRequest.getKey().toString();
        final String taskName = getTaskName(docRef);

        final String infoPrefix = LogUtil.message(
                "Querying {} {} - ",
                getStoreName(docRef),
                searchKey);

        LOGGER.debug(() -> LogUtil.message("{} Starting search with key {}", taskName, searchKey));
        parentTaskContext.info(() -> infoPrefix + "initialising query");

        final ExpressionCriteria criteria = new ExpressionCriteria(expression);

        final FindFieldInfoCriteria findFieldInfoCriteria = new FindFieldInfoCriteria(
                new PageRequest(0, 1000),
                null,
                docRef,
                null);
        final ResultPage<FieldInfo> resultPage = searchable.getFieldInfo(findFieldInfoCriteria);
        final Runnable runnable = taskContextFactory.context(taskName, taskContext -> {
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
                    searchable.search(criteria, coprocessors.getFieldIndex(), coprocessors);

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

    private String getStoreName(final DocRef docRef) {
        return NullSafe.toStringOrElse(
                docRef,
                DocRef::getName,
                "Unknown Store");
    }

    private String getTaskName(final DocRef docRef) {
        return getStoreName(docRef) + " Search";
    }

    private Sizes getDefaultMaxResultsSizes() {
        final String value = clientConfig.getDefaultMaxResults();
        return extractValues(value);
    }

    private int getResultHandlerBatchSize() {
        return 5000;
    }

    private Sizes extractValues(String value) {
        if (value != null) {
            try {
                return Sizes.create(Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Long::valueOf)
                        .collect(Collectors.toList()));
            } catch (final Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return Sizes.unlimited();
    }

    @Override
    public DateField getTimeField(final DocRef docRef) {
        return searchableProvider.get(docRef).getTimeField();
    }

    @Override
    public List<DocRef> list() {
        return searchableProvider.list();
    }

    @Override
    public String getType() {
        return "Searchable";
    }
}
