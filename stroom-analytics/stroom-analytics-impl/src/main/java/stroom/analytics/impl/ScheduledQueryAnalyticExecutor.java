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

package stroom.analytics.impl;

import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionTracker;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.index.shared.IndexConstants;
import stroom.node.api.NodeInfo;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.OffsetRange;
import stroom.query.api.ParamUtil;
import stroom.query.api.Query;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.common.v2.ConditionalFormattingMapper;
import stroom.query.common.v2.DataStore;
import stroom.query.common.v2.ErrorConsumerImpl;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.FilteredMapper;
import stroom.query.common.v2.Item;
import stroom.query.common.v2.ItemMapper;
import stroom.query.common.v2.OpenGroups;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.common.v2.ResultStoreManager.RequestAndStore;
import stroom.query.common.v2.RowValueFilter;
import stroom.query.common.v2.SimpleMapper;
import stroom.query.common.v2.ValFilter;
import stroom.query.common.v2.format.ColumnFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.ui.config.shared.AnalyticUiDefaultConfig;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.scheduler.Trigger;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ScheduledQueryAnalyticExecutor extends AbstractScheduledQueryExecutor<AnalyticRuleDoc> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ScheduledQueryAnalyticExecutor.class);

    private final AnalyticRuleStore analyticRuleStore;
    private final ResultStoreManager searchResponseCreatorManager;
    private final Provider<DetectionConsumerProxy> detectionConsumerProxyProvider;
    private final Provider<ErrorReceiverProxy> errorReceiverProxyProvider;
    private final DetectionConsumerFactory detectionConsumerFactory;
    private final SearchRequestFactory searchRequestFactory;
    private final ExpressionContextFactory expressionContextFactory;
    private final ExecutionScheduleDao executionScheduleDao;
    private final DuplicateCheckFactory duplicateCheckFactory;
    private final ExpressionPredicateFactory expressionPredicateFactory;
    private final Provider<AnalyticUiDefaultConfig> analyticUiDefaultConfigProvider;
    private final DuplicateCheckDirs duplicateCheckDirs;
    final WordListProvider wordListProvider;

    @Inject
    ScheduledQueryAnalyticExecutor(final AnalyticRuleStore analyticRuleStore,
                                   final ExecutorProvider executorProvider,
                                   final ResultStoreManager searchResponseCreatorManager,
                                   final Provider<DetectionConsumerProxy> detectionConsumerProxyProvider,
                                   final Provider<AnalyticErrorWriter> analyticErrorWriterProvider,
                                   final TaskContextFactory taskContextFactory,
                                   final NodeInfo nodeInfo,
                                   final Provider<ErrorReceiverProxy> errorReceiverProxyProvider,
                                   final DetectionConsumerFactory detectionConsumerFactory,
                                   final SearchRequestFactory searchRequestFactory,
                                   final ExpressionContextFactory expressionContextFactory,
                                   final SecurityContext securityContext,
                                   final ExecutionScheduleDao executionScheduleDao,
                                   final DuplicateCheckFactory duplicateCheckFactory,
                                   final DuplicateCheckDirs duplicateCheckDirs,
                                   final Provider<DocRefInfoService> docRefInfoServiceProvider,
                                   final ExpressionPredicateFactory expressionPredicateFactory,
                                   final Provider<AnalyticUiDefaultConfig> analyticUiDefaultConfigProvider,
                                   final WordListProvider wordListProvider) {
        super(executorProvider,
                analyticErrorWriterProvider,
                taskContextFactory,
                nodeInfo,
                securityContext,
                executionScheduleDao,
                docRefInfoServiceProvider,
                "analytic rule");
        this.analyticRuleStore = analyticRuleStore;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.detectionConsumerProxyProvider = detectionConsumerProxyProvider;
        this.errorReceiverProxyProvider = errorReceiverProxyProvider;
        this.detectionConsumerFactory = detectionConsumerFactory;
        this.searchRequestFactory = searchRequestFactory;
        this.expressionContextFactory = expressionContextFactory;
        this.executionScheduleDao = executionScheduleDao;
        this.duplicateCheckFactory = duplicateCheckFactory;
        this.expressionPredicateFactory = expressionPredicateFactory;
        this.analyticUiDefaultConfigProvider = analyticUiDefaultConfigProvider;
        this.duplicateCheckDirs = duplicateCheckDirs;
        this.wordListProvider = wordListProvider;
    }

    @Override
    boolean process(final AnalyticRuleDoc analytic,
                    final Trigger trigger,
                    final Instant executionTime,
                    final Instant effectiveExecutionTime,
                    final ExecutionSchedule executionSchedule,
                    final ExecutionTracker currentTracker) {
        LOGGER.debug(() -> LogUtil.message(
                "Executing analytic: {} with executionTime: {}, effectiveExecutionTime: {}, currentTracker: {}",
                analytic.asDocRef().toShortString(), executionTime, effectiveExecutionTime, currentTracker));

        boolean success = false;
        final ErrorConsumer errorConsumer = new ErrorConsumerImpl();
        ExecutionResult executionResult = new ExecutionResult(null, null);

        try {
            final MappedRequestBundle mappedRequestBundle = buildMappedSearchRequest(analytic, effectiveExecutionTime);
            final ExpressionContext expressionContext = mappedRequestBundle.expressionContext;
            final SearchRequest mappedRequest = mappedRequestBundle.mappedRequest;
            final SearchRequest sampleRequest = mappedRequestBundle.sampleRequest;

            // Fix table result requests.
            final List<ResultRequest> resultRequests = mappedRequest.getResultRequests();
            if (NullSafe.size(resultRequests) == 1) {
                final ResultRequest resultRequest = resultRequests.getFirst().copy()
                        .openGroups(null)
                        .requestedRange(OffsetRange.UNBOUNDED)
                        .build();

                // Create a result store and begin search.
                final RequestAndStore requestAndStore = searchResponseCreatorManager
                        .getResultStore(mappedRequest);
                final SearchRequest modifiedRequest = requestAndStore.searchRequest();
                try {
                    final DataStore dataStore = requestAndStore
                            .resultStore().getData(SearchRequestFactory.TABLE_COMPONENT_ID);
                    // Wait for search to complete.
                    dataStore.getCompletionState().awaitCompletion();

                    // Now consume all results as detections.
                    final TableSettings tableSettings = resultRequest.getMappings().getFirst();
                    final List<Column> columns = tableSettings.getColumns();
                    final Map<String, String> paramMap = ParamUtil
                            .createParamMap(mappedRequest.getQuery().getParams());
                    final CompiledColumns compiledColumns = CompiledColumns.create(
                            expressionContext,
                            columns,
                            paramMap);
                    final Predicate<Val[]> valFilter = ValFilter.create(
                            tableSettings.getValueFilter(),
                            compiledColumns,
                            modifiedRequest.getDateTimeSettings(),
                            expressionPredicateFactory,
                            paramMap, wordListProvider);

                    final Provider<DetectionConsumer> detectionConsumerProvider =
                            detectionConsumerFactory.create(analytic);
                    final DetectionConsumerProxy detectionConsumerProxy = detectionConsumerProxyProvider.get();
                    detectionConsumerProxy.setAnalyticRuleDoc(analytic);
                    detectionConsumerProxy.setExecutionSchedule(executionSchedule);
                    detectionConsumerProxy.setExecutionTime(executionTime);
                    detectionConsumerProxy.setEffectiveExecutionTime(effectiveExecutionTime);
                    detectionConsumerProxy.setCompiledColumns(compiledColumns);
                    detectionConsumerProxy.setValFilter(valFilter);
                    detectionConsumerProxy.setDetectionsConsumerProvider(detectionConsumerProvider);

                    final ColumnFormatter fieldFormatter =
                            new ColumnFormatter(new FormatterFactory(expressionContext.getDateTimeSettings()));

                    try (final DuplicateCheck duplicateCheck =
                            duplicateCheckFactory.create(analytic, compiledColumns)) {
                        detectionConsumerProxy.start();
                        final Consumer<Item> itemConsumer = item -> {
                            if (duplicateCheck.check(item)) {
                                Long streamId = null;
                                Long eventId = null;
                                final List<DetectionValue> values = new ArrayList<>();
                                for (int i = 0; i < dataStore.getColumns().size(); i++) {
                                    final Column column = dataStore.getColumns().get(i);
                                    final String columnName = column.getName();
                                    final Val val = item.getValue(i);
                                    if (val != null) {
                                        if (IndexConstants.STREAM_ID.equals(columnName)) {
                                            streamId = DetectionConsumerProxy.getSafeLong(val);
                                        } else if (IndexConstants.EVENT_ID.equals(columnName)) {
                                            eventId = DetectionConsumerProxy.getSafeLong(val);
                                        }
                                        final String fieldValStr = fieldFormatter.format(column, val);
                                        values.add(new DetectionValue(columnName, fieldValStr));
                                    }
                                }

                                List<DetectionLinkedEvent> linkedEvents = null;
                                if (streamId != null || eventId != null) {
                                    linkedEvents = List.of(new DetectionLinkedEvent(null, streamId, eventId));
                                }

                                final Detection detection = Detection
                                        .builder()
                                        .withDetectTime(DateUtil.createNormalDateTimeString())
                                        .withDetectorName(analytic.getName())
                                        .withDetectorUuid(analytic.getUuid())
                                        .withDetectorVersion(analytic.getVersion())
                                        .withDetailedDescription(analytic.getDescription())
                                        .withRandomDetectionUniqueId()
                                        .withDetectionRevision(0)
                                        .withExecutionSchedule(NullSafe
                                                .get(executionSchedule, ExecutionSchedule::getName))
                                        .withExecutionTime(executionTime)
                                        .withEffectiveExecutionTime(effectiveExecutionTime)
                                        .notDefunct()
                                        .withValues(values)
                                        .withLinkedEvents(linkedEvents)
                                        .build();
                                detectionConsumerProxy.getDetectionConsumer().accept(detection);
                            }
                        };
                        final Consumer<Long> countConsumer = count -> {

                        };

                        if (RowValueFilter.matches(columns)) {
                            ItemMapper mapper;
                            mapper = SimpleMapper.create(dataStore.getColumns(), columns);
                            mapper = FilteredMapper.create(
                                    columns,
                                    tableSettings.applyValueFilters(),
                                    tableSettings.getAggregateFilter(),
                                    expressionContext.getDateTimeSettings(),
                                    errorConsumer,
                                    expressionPredicateFactory,
                                    mapper);

                            mapper = ConditionalFormattingMapper.create(resultRequest.getSourceComponentId(),
                                    resultRequest.getSourceComponentName(),
                                    columns,
                                    tableSettings.getConditionalFormattingRules(),
                                    expressionContext.getDateTimeSettings(),
                                    expressionPredicateFactory,
                                    errorConsumer,
                                    mapper);

                            dataStore.fetch(
                                    columns,
                                    OffsetRange.UNBOUNDED,
                                    OpenGroups.NONE,
                                    resultRequest.getTimeFilter(),
                                    mapper,
                                    itemConsumer,
                                    countConsumer);
                        }

                    } finally {
                        final List<ErrorMessage> errorMessages = errorConsumer.getErrorMessages();
                        if (errorMessages != null) {
                            for (final ErrorMessage errorMessage : errorMessages) {
                                if (executionResult.status() == null) {
                                    executionResult = new ExecutionResult("Error", errorMessage.getMessage());
                                }

                                errorReceiverProxyProvider.get()
                                        .getErrorReceiver()
                                        .log(errorMessage.getSeverity(), null, null, errorMessage.getMessage(), null);
                            }
                        }

                        detectionConsumerProxy.end();
                    }
                } finally {
                    // Destroy search result store.
                    searchResponseCreatorManager.destroy(modifiedRequest.getKey(), DestroyReason.NO_LONGER_NEEDED);
                }
            }

            // Remember last successful execution time and compute next execution time.
            final Instant now = Instant.now();
            final Instant nextExecutionTime;
            if (executionSchedule.isContiguous()) {
                nextExecutionTime = trigger.getNextExecutionTimeAfter(effectiveExecutionTime);
            } else {
                nextExecutionTime = trigger.getNextExecutionTimeAfter(now);
            }

            // Update tracker.
            final ExecutionTracker executionTracker = new ExecutionTracker(
                    now.toEpochMilli(),
                    effectiveExecutionTime.toEpochMilli(),
                    nextExecutionTime.toEpochMilli());
            if (currentTracker != null) {
                executionScheduleDao.updateTracker(executionSchedule, executionTracker);
            } else {
                executionScheduleDao.createTracker(executionSchedule, executionTracker);
            }

            if (executionResult.status() == null) {
                executionResult = new ExecutionResult("Complete", executionResult.message());
                success = true;
            }

        } catch (final Exception e) {
            executionResult = new ExecutionResult("Error", e.getMessage());

            try {
                LOGGER.debug(e::getMessage, e);
                errorReceiverProxyProvider.get()
                        .getErrorReceiver()
                        .log(Severity.ERROR, null, null, e.getMessage(), e);
            } catch (final RuntimeException e2) {
                LOGGER.error(e2::getMessage, e2);
            }

            // Disable future execution if the error was not an interrupted exception.
            if (!(e instanceof InterruptedException) &&
                !(e instanceof UncheckedInterruptedException)) {
                // Disable future execution.
                LOGGER.info(() -> LogUtil.message("Disabling: {}", RuleUtil.getRuleIdentity(analytic)));
                executionScheduleDao.updateExecutionSchedule(executionSchedule.copy().enabled(false).build());
            }

        } finally {
            // Record the execution.
            addExecutionHistory(executionSchedule,
                    executionTime,
                    effectiveExecutionTime,
                    executionResult);
        }

        return success;
    }

    @Override
    void postExecuteTidyUp(final List<AnalyticRuleDoc> analyticDocs) {
        // Start by finding a set of UUIDs for existing rule checking stores.
        final List<String> duplicateStoreUuids = duplicateCheckDirs.getAnalyticRuleUUIDList();

        // Delete unused duplicate stores.
        duplicateCheckDirs.deleteUnused(duplicateStoreUuids, analyticDocs);
    }

    @Override
    AnalyticRuleDoc load(final DocRef docRef) {
        return analyticRuleStore.readDocument(docRef);
    }

    @Override
    List<AnalyticRuleDoc> getRules() {
        // TODO this is not very efficient. It fetches all the docrefs from the DB,
        //  then loops over them to fetch+deser the associated doc for each one (one by one)
        //  so the caller can filter half of them out by type.
        //  It would be better if we had a json type col in the doc table, so that the
        //  we can pass some kind of json path query to the persistence layer that the DBPersistence
        //  can translate to a MySQL json path query.
        final List<AnalyticRuleDoc> currentRules = new ArrayList<>();
        final List<DocRef> docRefs = analyticRuleStore.list();
        for (final DocRef docRef : docRefs) {
            try {
                final AnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(docRef);
                if (analyticRuleDoc != null) {
                    currentRules.add(analyticRuleDoc);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        return currentRules;
    }

    @Override
    String getErrorFeedName(final AnalyticRuleDoc doc) {
        String errorFeedName = null;
        if (doc.getErrorFeed() != null) {
            errorFeedName = doc.getErrorFeed().getName();
        }
        if (errorFeedName == null) {
            LOGGER.debug(() -> LogUtil.message("Error feed not defined: {}", RuleUtil.getRuleIdentity(doc)));

            final DocRef defaultErrorFeed = analyticUiDefaultConfigProvider.get().getDefaultErrorFeed();
            if (defaultErrorFeed == null) {
                throw new RuntimeException("Default error feed not defined");
            }
            errorFeedName = defaultErrorFeed.getName();
        }
        return errorFeedName;
    }

    private MappedRequestBundle buildMappedSearchRequest(final AnalyticRuleDoc analytic,
                                                         final Instant effectiveExecutionTime) {
        final SearchRequestSource searchRequestSource = SearchRequestSource
                .builder()
                .sourceType(SourceType.SCHEDULED_QUERY_ANALYTIC)
                .componentId(SearchRequestFactory.TABLE_COMPONENT_ID)
                .build();

        final String query = analytic.getQuery();
        final Query sampleQuery = Query
                .builder()
                .params(analytic.getParameters())
                .timeRange(analytic.getTimeRange())
                .build();
        final SearchRequest sampleRequest = new SearchRequest(
                searchRequestSource,
                null,
                sampleQuery,
                null,
                DateTimeSettings.builder()
                        .referenceTime(effectiveExecutionTime.toEpochMilli())
                        .build(),
                false);
        final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
        final SearchRequest mappedRequest = searchRequestFactory.create(query, sampleRequest, expressionContext);

        return new MappedRequestBundle(
                mappedRequest,
                sampleRequest,
                expressionContext);
    }

    public List<String> extractColumnNames(final AnalyticRuleDoc analytic) {
        if (analytic.getDuplicateNotificationConfig() != null) {
            // Time doesn't matter, so just use now()
            final MappedRequestBundle mappedRequestBundle = buildMappedSearchRequest(analytic, Instant.now());
            final SearchRequest mappedRequest = mappedRequestBundle.mappedRequest;
            final List<ResultRequest> resultRequests = mappedRequest.getResultRequests();
            final TableSettings tableSettings = NullSafe.get(
                    resultRequests,
                    List::getFirst,
                    req -> NullSafe.first(req.getMappings()));
            if (tableSettings != null) {
                final List<Column> columns = tableSettings.getColumns();
                if (NullSafe.hasItems(columns)) {
                    final Map<String, String> paramMap = ParamUtil
                            .createParamMap(mappedRequest.getQuery().getParams());
                    final CompiledColumns compiledColumns = CompiledColumns.create(
                            mappedRequestBundle.expressionContext,
                            columns,
                            paramMap);

                    final List<String> filteredColumnNames = getFilteredColumnNames(analytic, compiledColumns);
                    LOGGER.debug("extractColumnNames() - analytic: {}, filteredColumnNames: {}",
                            analytic, filteredColumnNames);
                    return filteredColumnNames;
                }
            }
        }
        return Collections.emptyList();
    }

    private List<String> getFilteredColumnNames(final AnalyticRuleDoc analytic,
                                                final CompiledColumns compiledColumns) {
        final DuplicateCheckRowFactory duplicateCheckRowFactory = new DuplicateCheckRowFactory(
                analytic.getDuplicateNotificationConfig(),
                compiledColumns);
        return duplicateCheckRowFactory.getColumnNames();
    }


    // --------------------------------------------------------------------------------


    private record MappedRequestBundle(SearchRequest mappedRequest,
                                       SearchRequest sampleRequest,
                                       ExpressionContext expressionContext) {

    }
}
