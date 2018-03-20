/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.search;

import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dictionary.DictionaryStore;
import stroom.index.IndexService;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldsMap;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.MessageUtil;
import stroom.properties.StroomPropertyService;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Param;
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Payload;
import stroom.search.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.extraction.ExtractionTaskExecutor;
import stroom.search.extraction.ExtractionTaskHandler;
import stroom.search.extraction.ExtractionTaskProducer;
import stroom.search.extraction.ExtractionTaskProperties;
import stroom.search.extraction.StreamMapCreator;
import stroom.search.shard.IndexShardSearchTask.IndexShardQueryFactory;
import stroom.search.shard.IndexShardSearchTaskExecutor;
import stroom.search.shard.IndexShardSearchTaskHandler;
import stroom.search.shard.IndexShardSearchTaskProducer;
import stroom.search.shard.IndexShardSearchTaskProperties;
import stroom.search.shard.IndexShardSearcherCache;
import stroom.security.SecurityContext;
import stroom.security.SecurityHelper;
import stroom.streamstore.StreamStore;
import stroom.task.ExecutorProvider;
import stroom.task.TaskCallback;
import stroom.task.TaskContext;
import stroom.task.TaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.task.TaskTerminatedException;
import stroom.task.ThreadPoolImpl;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;
import stroom.util.shared.ThreadPool;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@TaskHandlerBean(task = ClusterSearchTask.class)
class ClusterSearchTaskHandler implements TaskHandler<ClusterSearchTask, NodeResult>, ErrorReceiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterSearchTaskHandler.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ClusterSearchTaskHandler.class);

    /**
     * We don't want to collect more than 1 million doc's data into the queue by
     * default. When the queue is full the index shard data tasks will pause
     * until the docs are drained from the queue.
     */
    private static final int DEFAULT_MAX_STORED_DATA_QUEUE_SIZE = 1000;
    private static final int DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT = 1024;

    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
            "Search Result Sender",
            5,
            0,
            Integer.MAX_VALUE);

    private final IndexService indexService;
    private final DictionaryStore dictionaryStore;
    private final TaskContext taskContext;
    private final CoprocessorFactory coprocessorFactory;
    private final IndexShardSearchTaskExecutor indexShardSearchTaskExecutor;
    private final IndexShardSearchTaskProperties indexShardSearchTaskProperties;
    private final IndexShardSearcherCache indexShardSearcherCache;
    private final ExtractionTaskExecutor extractionTaskExecutor;
    private final ExtractionTaskProperties extractionTaskProperties;
    private final StreamStore streamStore;
    private final SecurityContext securityContext;
    private final int maxBooleanClauseCount;
    private final int maxStoredDataQueueSize;
    private final LinkedBlockingQueue<String> errors = new LinkedBlockingQueue<>();
    private final AtomicBoolean searchComplete = new AtomicBoolean();
    private final CountDownLatch searchCompleteLatch = new CountDownLatch(1);
    private final AtomicBoolean sendingData = new AtomicBoolean();
    private final Provider<IndexShardSearchTaskHandler> indexShardSearchTaskHandlerProvider;
    private final Provider<ExtractionTaskHandler> extractionTaskHandlerProvider;
    private final ExecutorProvider executorProvider;

    private ClusterSearchTask task;

    private LinkedBlockingQueue<String[]> storedData;

    @Inject
    ClusterSearchTaskHandler(final IndexService indexService,
                             final DictionaryStore dictionaryStore,
                             final TaskContext taskContext,
                             final CoprocessorFactory coprocessorFactory,
                             final IndexShardSearchTaskExecutor indexShardSearchTaskExecutor,
                             final IndexShardSearchTaskProperties indexShardSearchTaskProperties,
                             final IndexShardSearcherCache indexShardSearcherCache,
                             final ExtractionTaskExecutor extractionTaskExecutor,
                             final ExtractionTaskProperties extractionTaskProperties,
                             final StreamStore streamStore,
                             final SecurityContext securityContext,
                             final StroomPropertyService propertyService,
                             final Provider<IndexShardSearchTaskHandler> indexShardSearchTaskHandlerProvider,
                             final Provider<ExtractionTaskHandler> extractionTaskHandlerProvider,
                             final ExecutorProvider executorProvider) {
        this.indexService = indexService;
        this.dictionaryStore = dictionaryStore;
        this.taskContext = taskContext;
        this.coprocessorFactory = coprocessorFactory;
        this.indexShardSearchTaskExecutor = indexShardSearchTaskExecutor;
        this.indexShardSearchTaskProperties = indexShardSearchTaskProperties;
        this.indexShardSearcherCache = indexShardSearcherCache;
        this.extractionTaskExecutor = extractionTaskExecutor;
        this.extractionTaskProperties = extractionTaskProperties;
        this.streamStore = streamStore;
        this.securityContext = securityContext;
        this.maxBooleanClauseCount = propertyService.getIntProperty("stroom.search.maxBooleanClauseCount", DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT);
        this.maxStoredDataQueueSize = propertyService.getIntProperty("stroom.search.maxStoredDataQueueSize", DEFAULT_MAX_STORED_DATA_QUEUE_SIZE);
        this.indexShardSearchTaskHandlerProvider = indexShardSearchTaskHandlerProvider;
        this.extractionTaskHandlerProvider = extractionTaskHandlerProvider;
        this.executorProvider = executorProvider;
    }

    @Override
    public void exec(final ClusterSearchTask task, final TaskCallback<NodeResult> callback) {
        try (final SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
            if (!taskContext.isTerminated()) {
                taskContext.info("Initialising...");

                this.task = task;
                final stroom.query.api.v2.Query query = task.getQuery();

                try {
                    final long frequency = task.getResultSendFrequency();

                    // Reload the index.
                    final Index index = indexService.loadByUuid(query.getDataSource().getUuid());

                    // Make sure we have a search index.
                    if (index == null) {
                        throw new SearchException("Search index has not been set");
                    }

                    // Get the stored fields that search is hoping to use.
                    final IndexField[] storedFields = task.getStoredFields();
                    if (storedFields == null || storedFields.length == 0) {
                        throw new SearchException("No stored fields have been requested");
                    }

                    // Get an array of stored index fields that will be used for getting stored data.
                    final String[] storedFieldNames = new String[storedFields.length];
                    final FieldIndexMap storedFieldIndexMap = new FieldIndexMap();
                    for (int i = 0; i < storedFields.length; i++) {
                        storedFieldNames[i] = storedFields[i].getFieldName();
                        storedFieldIndexMap.create(storedFieldNames[i], true);
                    }

                    // See if we need to filter steams and if any of the coprocessors need us to extract data.
                    boolean filterStreams;

                    Map<CoprocessorKey, Coprocessor> coprocessorMap = null;
                    Map<DocRef, Set<Coprocessor>> extractionCoprocessorsMap = null;

                    final FieldIndexMap extractionFieldIndexMap = new FieldIndexMap(true);

                    filterStreams = true;

                    // Create a map of index fields keyed by name.
                    final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(index.getIndexFieldsObject());

                    // Compile all of the result component options to optimise pattern matching etc.
                    if (task.getCoprocessorMap() != null) {
                        coprocessorMap = new HashMap<>();
                        extractionCoprocessorsMap = new HashMap<>();

                        for (final Entry<CoprocessorKey, CoprocessorSettings> entry : task.getCoprocessorMap().entrySet()) {
                            final CoprocessorKey coprocessorId = entry.getKey();
                            final CoprocessorSettings coprocessorSettings = entry.getValue();

                            // Figure out where the fields required by this coprocessor will be found.
                            FieldIndexMap fieldIndexMap = storedFieldIndexMap;
                            if (coprocessorSettings.extractValues() && coprocessorSettings.getExtractionPipeline() != null
                                    && coprocessorSettings.getExtractionPipeline().getUuid() != null) {
                                fieldIndexMap = extractionFieldIndexMap;
                            }

                            // Create a parameter map.
                            final Map<String, String> paramMap;
                            if (query.getParams() != null) {
                                paramMap = query.getParams().stream()
                                        .collect(Collectors.toMap(Param::getKey, Param::getValue));
                            } else {
                                paramMap = Collections.emptyMap();
                            }
                            final Coprocessor coprocessor = coprocessorFactory.create(coprocessorSettings, fieldIndexMap, paramMap, taskContext);

                            if (coprocessor != null) {
                                coprocessorMap.put(coprocessorId, coprocessor);

                                // Find out what data extraction might be needed for the coprocessors.
                                DocRef pipelineRef = null;
                                if (coprocessorSettings.extractValues()
                                        && coprocessorSettings.getExtractionPipeline() != null) {
                                    pipelineRef = coprocessorSettings.getExtractionPipeline();
                                    filterStreams = true;
                                }

                                extractionCoprocessorsMap.computeIfAbsent(pipelineRef, k -> new HashSet<>()).add(coprocessor);
                            }
                        }
                    }

                    // Start forwarding data to target node.
                    final Executor executor = executorProvider.getExecutor(THREAD_POOL);
                    sendData(coprocessorMap, callback, frequency, executor);

                    // Start searching.
                    search(task, query, storedFieldNames, filterStreams, indexFieldsMap, extractionFieldIndexMap, extractionCoprocessorsMap);

                } catch (final Throwable t) {
                    try {
                        callback.onFailure(t);
                    } catch (final Throwable t2) {
                        // If we failed to send the result or the source node rejected the result because the source task has been terminated then terminate the task.
                        LOGGER.info("Terminating search because we were unable to send result");
                        task.terminate();
                    }
                } finally {
                    // Tell the client that the search has completed.
                    searchComplete.set(true);
                    //countDown the latch so sendData knows we are complete
                    searchCompleteLatch.countDown();
                }

                // Now we must wait for results to be sent to the requesting node.
                taskContext.info("Sending final results");
                while (!task.isTerminated() && sendingData.get()) {
                    ThreadUtil.sleep(1000);
                }
            }
        }
    }

    private void sendData(final Map<CoprocessorKey, Coprocessor> coprocessorMap,
                          final TaskCallback<NodeResult> callback,
                          final long frequency,
                          final Executor executor) {
        final long now = System.currentTimeMillis();

        LOGGER.trace("sendData() called");

        final Supplier<Boolean> supplier = () -> {
            // Find out if searching is complete.
            final boolean searchComplete = ClusterSearchTaskHandler.this.searchComplete.get();

            if (!taskContext.isTerminated()) {
                taskContext.setName("Search Result Sender");
                taskContext.info("Creating search result");

                // Produce payloads for each coprocessor.
                Map<CoprocessorKey, Payload> payloadMap = null;
                if (coprocessorMap != null && coprocessorMap.size() > 0) {
                    for (final Entry<CoprocessorKey, Coprocessor> entry : coprocessorMap.entrySet()) {
                        final Payload payload = entry.getValue().createPayload();
                        if (payload != null) {
                            if (payloadMap == null) {
                                payloadMap = new HashMap<>();
                            }

                            payloadMap.put(entry.getKey(), payload);
                        }
                    }
                }

                // Drain all current errors to a list.
                List<String> errorsSnapshot = new ArrayList<>();
                errors.drainTo(errorsSnapshot);
                if (errorsSnapshot.size() == 0) {
                    errorsSnapshot = null;
                }

                // Only send a result if we have something new to send.
                if (payloadMap != null || errorsSnapshot != null || searchComplete) {
                    // Form a result to send back to the requesting node.
                    final NodeResult result = new NodeResult(payloadMap, errorsSnapshot, searchComplete);

                    // Give the result to the callback.
                    taskContext.info("Sending search result");
                    callback.onSuccess(result);
                }
            }

            return searchComplete;
        };

        // Run the sending code asynchronously.
        sendingData.set(true);
        CompletableFuture.supplyAsync(supplier, executor)
                .thenAccept(complete -> {
                    if (complete) {
                        // We have sent the last data we were expected to so tell the parent cluster search that we have finished sending data.
                        sendingData.set(false);
                        LOGGER.debug("sendingData is false");

                    } else {
                        // If we aren't complete then send more using the supplied sending frequency.
                        final long latestSendTimeMs = now + frequency;

                        while (!taskContext.isTerminated() &&
                                !searchComplete.get() &&
                                System.currentTimeMillis() < latestSendTimeMs) {
                            //wait until the next send frequency time or drop out as soon
                            //as the search completes and the latch is counted down.
                            //Compute the wait time as we may have used up some of the frequency getting to here
                            long waitTime = latestSendTimeMs - System.currentTimeMillis() + 1;
                            LOGGER.trace("frequency [{}], waitTime [{}]", frequency, waitTime);

                            boolean awaitResult = LAMBDA_LOGGER.logDurationIfTraceEnabled(
                                    () -> {
                                        try {
                                            return searchCompleteLatch.await(waitTime, TimeUnit.MILLISECONDS);
                                        } catch (InterruptedException e) {
                                            //Don't want to reset interrupt status as this thread will go back into
                                            //the executor's pool. Throwing an exception will terminate the task
                                            throw new RuntimeException("Thread interrupted");
                                        }
                                    },
                                    "sendData wait");
                            LOGGER.trace("await finished with result {}", awaitResult);
                        }

                        // Make sure we don't continue to execute this task if it should have terminated.
                        if (!taskContext.isTerminated()) {
                            // Try to send more data.
                            sendData(coprocessorMap, callback, frequency, executor);
                        }
                    }
                })
                .exceptionally(t -> {
                    // If we failed to send the result or the source node rejected the result because the source
                    // task has been terminated then terminate the task.
                    LOGGER.info("Terminating search because we were unable to send result");
                    task.terminate();
                    return null;
                });
    }

    private void search(final ClusterSearchTask task,
                        final stroom.query.api.v2.Query query,
                        final String[] storedFieldNames,
                        final boolean filterStreams,
                        final IndexFieldsMap indexFieldsMap,
                        final FieldIndexMap extractionFieldIndexMap,
                        final Map<DocRef, Set<Coprocessor>> extractionCoprocessorsMap) {
        taskContext.info("Searching...");

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Incoming search request:\n" + query.getExpression().toString());
        }

        try {
            if (extractionCoprocessorsMap != null && extractionCoprocessorsMap.size() > 0
                    && task.getShards().size() > 0) {
                // Make sure we are searching a specific index.
                if (query.getExpression() == null) {
                    throw new SearchException("Search expression has not been set");
                }

                // Search all index shards.
                final Map<Version, SearchExpressionQuery> queryMap = new HashMap<>();

                final IndexShardQueryFactory queryFactory = createIndexShardQueryFactory(
                        task, query, indexFieldsMap, queryMap);

                // Create a transfer list to capture stored data from the index that can be used by coprocessors.
                storedData = new LinkedBlockingQueue<>(maxStoredDataQueueSize);
                final AtomicLong hitCount = new AtomicLong();

                // Update the configuration.
                final int maxOpenShards = indexShardSearchTaskProperties.getMaxOpenShards();
                if (indexShardSearcherCache.getMaxOpenShards() != maxOpenShards) {
                    indexShardSearcherCache.setMaxOpenShards(maxOpenShards);
                }

                // Update config for the index shard search task executor.
                indexShardSearchTaskExecutor.setMaxThreads(indexShardSearchTaskProperties.getMaxThreads());

                // Make a task producer that will create event data extraction tasks when requested by the executor.
                final IndexShardSearchTaskProducer indexShardSearchTaskProducer = new IndexShardSearchTaskProducer(
                        indexShardSearchTaskExecutor,
                        task,
                        storedData,
                        indexShardSearcherCache,
                        task.getShards(),
                        queryFactory,
                        storedFieldNames,
                        this,
                        hitCount,
                        indexShardSearchTaskProperties.getMaxThreadsPerTask(),
                        executorProvider,
                        indexShardSearchTaskHandlerProvider);

                if (!filterStreams) {
                    // If we aren't required to filter streams and aren't using pipelines to feed data to coprocessors then just do a simple data transfer to the coprocessors.
                    transfer(extractionCoprocessorsMap, indexShardSearchTaskProducer);

                } else {
                    // Update config for extraction task executor.
                    extractionTaskExecutor.setMaxThreads(extractionTaskProperties.getMaxThreads());

                    // Create an object to make event lists from raw index data.
                    final StreamMapCreator streamMapCreator = new StreamMapCreator(task.getStoredFields(), this,
                            streamStore, securityContext);

                    // Make a task producer that will create event data extraction tasks when requested by the executor.
                    final ExtractionTaskProducer extractionTaskProducer = new ExtractionTaskProducer(
                            extractionTaskExecutor,
                            task,
                            streamMapCreator,
                            storedData,
                            extractionFieldIndexMap,
                            extractionCoprocessorsMap,
                            this,
                            extractionTaskProperties.getMaxThreadsPerTask(),
                            executorProvider,
                            extractionTaskHandlerProvider,
                            indexShardSearchTaskProducer);

                    // Wait for completion.
                    while (!indexShardSearchTaskProducer.isComplete() || !extractionTaskProducer.isComplete()) {
                        taskContext.info(
                                "Searching... " +
                                        indexShardSearchTaskProducer.getRemainingTasks() +
                                        " shards and " +
                                        extractionTaskProducer.getRemainingTasks() +
                                        " extractions remaining");

                        ThreadUtil.sleep(1000);
                    }
                }
            }
        } catch (final Exception pEx) {
            throw SearchException.wrap(pEx);
        }
    }

    private IndexShardQueryFactory createIndexShardQueryFactory(final ClusterSearchTask task, final stroom.query.api.v2.Query query, final IndexFieldsMap indexFieldsMap, final Map<Version, SearchExpressionQuery> queryMap) {
        return new IndexShardQueryFactory() {

            @Override
            public Query getQuery(final Version luceneVersion) {
                SearchExpressionQuery searchExpressionQuery = queryMap.get(luceneVersion);
                if (searchExpressionQuery == null) {
                    // Get a query for the required lucene version.
                    searchExpressionQuery = getQuery(luceneVersion, query.getExpression(), indexFieldsMap);
                    queryMap.put(luceneVersion, searchExpressionQuery);
                }

                return searchExpressionQuery.getQuery();
            }

            private SearchExpressionQuery getQuery(final Version version, final ExpressionOperator expression,
                                                   final IndexFieldsMap indexFieldsMap1) {
                SearchExpressionQuery query1 = null;
                try {
                    final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                            dictionaryStore,
                            indexFieldsMap1,
                            maxBooleanClauseCount,
                            task.getDateTimeLocale(),
                            task.getNow());
                    query1 = searchExpressionQueryBuilder.buildQuery(version, expression);

                    // Make sure the query was created successfully.
                    if (query1.getQuery() == null) {
                        throw new SearchException("Failed to build Lucene query given expression");
                    } else if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Lucene Query is " + query1.toString());
                    }
                } catch (final Exception e) {
                    error(e.getMessage(), e);
                }

                if (query1 == null) {
                    query1 = new SearchExpressionQuery(null, null);
                }

                return query1;
            }
        };
    }

    private void transfer(final Map<DocRef, Set<Coprocessor>> extractionCoprocessorsMap,
                          final IndexShardSearchTaskProducer indexShardSearchTaskProducer) {
        try {
            // If we aren't required to filter streams and aren't using pipelines to feed data to coprocessors then just do a simple data transfer to the coprocessors.
            final Set<Coprocessor> coprocessors = extractionCoprocessorsMap.get(null);
            boolean complete = false;
            while (!complete && !task.isTerminated()) {
                // Check if search is finished before polling for stored data.
                final boolean searchComplete = indexShardSearchTaskProducer.isComplete();
                // Poll for the next stored data result.
                final String[] values = storedData.poll(1, TimeUnit.SECONDS);

                if (values != null) {
                    // Send the data to all coprocessors.
                    for (final Coprocessor coprocessor : coprocessors) {
                        coprocessor.receive(values);
                    }
                } else {
                    complete = searchComplete;
                }
            }
        } catch (final Exception e) {
            error(e.getMessage(), e);
        }
    }

    private void error(final String message, final Throwable t) {
        log(Severity.ERROR, null, null, message, t);
    }

    @Override
    public void log(final Severity severity, final Location location, final String elementId, final String message,
                    final Throwable e) {
        if (e != null) {
            LOGGER.debug(e.getMessage(), e);
        }

        if (e == null || !(e instanceof TaskTerminatedException)) {
            final String msg = MessageUtil.getMessage(message, e);
            errors.offer(msg);
        }
    }

    public ClusterSearchTask getTask() {
        return task;
    }
}
