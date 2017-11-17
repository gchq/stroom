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

package stroom.search.server;

import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dictionary.server.DictionaryService;
import stroom.index.server.IndexService;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldsMap;
import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.pipeline.server.errorhandler.MessageUtil;
import stroom.pipeline.server.errorhandler.TerminatedException;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Param;
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Payload;
import stroom.search.server.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.server.extraction.ExtractionTaskExecutor;
import stroom.search.server.extraction.ExtractionTaskHandler;
import stroom.search.server.extraction.ExtractionTaskProducer;
import stroom.search.server.extraction.ExtractionTaskProperties;
import stroom.search.server.extraction.StreamMapCreator;
import stroom.search.server.shard.IndexShardSearchTask.IndexShardQueryFactory;
import stroom.search.server.shard.IndexShardSearchTaskExecutor;
import stroom.search.server.shard.IndexShardSearchTaskHandler;
import stroom.search.server.shard.IndexShardSearchTaskProducer;
import stroom.search.server.shard.IndexShardSearchTaskProperties;
import stroom.search.server.shard.IndexShardSearcherCache;
import stroom.search.server.shard.TransferList;
import stroom.security.SecurityContext;
import stroom.streamstore.server.StreamStore;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.TaskCallback;
import stroom.task.server.TaskContext;
import stroom.task.server.TaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskTerminatedException;
import stroom.task.server.ThreadPoolImpl;
import stroom.util.config.PropertyUtil;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;
import stroom.util.shared.ThreadPool;
import stroom.util.spring.StroomScope;
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
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@TaskHandlerBean(task = ClusterSearchTask.class)
@Scope(StroomScope.TASK)
class ClusterSearchTaskHandler implements TaskHandler<ClusterSearchTask, NodeResult>, ErrorReceiver {
    /**
     * We don't want to collect more than 1 million doc's data into the queue by
     * default. When the queue is full the index shard data tasks will pause
     * until the docs are drained from the queue.
     */
    private static final int DEFAULT_MAX_STORED_DATA_QUEUE_SIZE = 1000000;
    private static final int DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT = 1024;

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterSearchTaskHandler.class);
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Stroom Result Sender", 5, 0, Integer.MAX_VALUE);

    private static final long ONE_SECOND = TimeUnit.SECONDS.toNanos(1);
    private final IndexService indexService;
    private final DictionaryService dictionaryService;
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
    private final LinkedBlockingDeque<String> errors = new LinkedBlockingDeque<>();
    private final AtomicBoolean searchComplete = new AtomicBoolean();
    private final AtomicBoolean sendingComplete = new AtomicBoolean();
    private final Provider<IndexShardSearchTaskHandler> indexShardSearchTaskHandlerProvider;
    private final Provider<ExtractionTaskHandler> extractionTaskHandlerProvider;
    private final ExecutorProvider executorProvider;

    private ClusterSearchTask task;

    private TransferList<String[]> storedData;

    @Inject
    ClusterSearchTaskHandler(final IndexService indexService,
                             final DictionaryService dictionaryService,
                             final TaskContext taskContext,
                             final CoprocessorFactory coprocessorFactory,
                             final IndexShardSearchTaskExecutor indexShardSearchTaskExecutor,
                             final IndexShardSearchTaskProperties indexShardSearchTaskProperties,
                             final IndexShardSearcherCache indexShardSearcherCache,
                             final ExtractionTaskExecutor extractionTaskExecutor,
                             final ExtractionTaskProperties extractionTaskProperties,
                             final StreamStore streamStore,
                             final SecurityContext securityContext,
                             @Value("#{propertyConfigurer.getProperty('stroom.search.maxBooleanClauseCount')}") final String maxBooleanClauseCount,
                             @Value("#{propertyConfigurer.getProperty('stroom.search.maxStoredDataQueueSize')}") final String maxStoredDataQueueSize,
                             final Provider<IndexShardSearchTaskHandler> indexShardSearchTaskHandlerProvider,
                             final Provider<ExtractionTaskHandler> extractionTaskHandlerProvider,
                             final ExecutorProvider executorProvider) {
        this.indexService = indexService;
        this.dictionaryService = dictionaryService;
        this.taskContext = taskContext;
        this.coprocessorFactory = coprocessorFactory;
        this.indexShardSearchTaskExecutor = indexShardSearchTaskExecutor;
        this.indexShardSearchTaskProperties = indexShardSearchTaskProperties;
        this.indexShardSearcherCache = indexShardSearcherCache;
        this.extractionTaskExecutor = extractionTaskExecutor;
        this.extractionTaskProperties = extractionTaskProperties;
        this.streamStore = streamStore;
        this.securityContext = securityContext;
        this.maxBooleanClauseCount = PropertyUtil.toInt(maxBooleanClauseCount, DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT);
        this.maxStoredDataQueueSize = PropertyUtil.toInt(maxStoredDataQueueSize, DEFAULT_MAX_STORED_DATA_QUEUE_SIZE);
        this.indexShardSearchTaskHandlerProvider = indexShardSearchTaskHandlerProvider;
        this.extractionTaskHandlerProvider = extractionTaskHandlerProvider;
        this.executorProvider = executorProvider;
    }

    @Override
    public void exec(final ClusterSearchTask task, final TaskCallback<NodeResult> callback) {
        try {
            securityContext.elevatePermissions();

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

                    // Get an array of stored index fields that will be used for
                    // getting stored data.
                    final String[] storedFieldNames = new String[storedFields.length];
                    final FieldIndexMap storedFieldIndexMap = new FieldIndexMap();
                    for (int i = 0; i < storedFields.length; i++) {
                        storedFieldNames[i] = storedFields[i].getFieldName();
                        storedFieldIndexMap.create(storedFieldNames[i], true);
                    }

                    // See if we need to filter steams and if any of the
                    // coprocessors need us to extract data.
                    boolean filterStreams;

                    Map<CoprocessorKey, Coprocessor> coprocessorMap = null;
                    Map<DocRef, Set<Coprocessor>> extractionCoprocessorsMap = null;

                    final FieldIndexMap extractionFieldIndexMap = new FieldIndexMap(true);

                    filterStreams = true;

                    // Create a map of index fields keyed by name.
                    final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(index.getIndexFieldsObject());

                    // Compile all of the result component options to optimise
                    // pattern matching etc.
                    if (task.getCoprocessorMap() != null) {
                        coprocessorMap = new HashMap<>();
                        extractionCoprocessorsMap = new HashMap<>();

                        for (final Entry<CoprocessorKey, CoprocessorSettings> entry : task.getCoprocessorMap().entrySet()) {
                            final CoprocessorKey coprocessorId = entry.getKey();
                            final CoprocessorSettings coprocessorSettings = entry.getValue();

                            // Figure out where the fields required by this
                            // coprocessor will be found.
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

                                // Find out what data extraction might be needed for
                                // the coprocessors.
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
                        // If we failed to send the result or the source node
                        // rejected the result because the
                        // source task has been terminated then terminate the task.
                        LOGGER.info("Terminating search because we were unable to send result");
                        task.terminate();
                    }
                } finally {
                    // Tell the client that the search has completed.
                    searchComplete.set(true);
                }

                // Now we must wait for results to be sent to the requesting node.
                taskContext.info("Sending final results");
                while (!task.isTerminated() && !sendingComplete.get()) {
                    ThreadUtil.sleep(1000);
                }
            }
        } finally {
            securityContext.restorePermissions();
        }
    }

    private void sendData(final Map<CoprocessorKey, Coprocessor> coprocessorMap, final TaskCallback<NodeResult> callback, final long frequency, final Executor executor) {
        final long now = System.currentTimeMillis();

        final Supplier<Boolean> supplier = () -> {
            // Find out if searching is complete.
            final boolean searchComplete = ClusterSearchTaskHandler.this.searchComplete.get();

            if (!taskContext.isTerminated()) {
                taskContext.setName("Search result sender");
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
        CompletableFuture.supplyAsync(supplier, executor)
                .thenAccept(complete -> {
                    if (complete) {
                        // We have sent the last data we were expected to so tell the
                        // parent cluster search that we have finished sending data.
                        sendingComplete.set(true);

                    } else {
                        // If we aren't complete then send more using the supplied
                        // sending frequency.
                        final long duration = System.currentTimeMillis() - now;
                        if (duration < frequency) {
                            ThreadUtil.sleep(frequency - duration);
                        }

                        // Make sure we don't continue to execute this task if it should
                        // have terminated.
                        if (!taskContext.isTerminated()) {
                            // Try to send more data.
                            sendData(coprocessorMap, callback, frequency, executor);
                        }
                    }
                })
                .exceptionally(t -> {
                    // If we failed to send the result or the source node
                    // rejected the result because the source task has been
                    // terminated then terminate the task.
                    LOGGER.info("Terminating search because we were unable to send result");
                    task.terminate();
                    return null;
                });
    }

    private void search(final ClusterSearchTask task, final stroom.query.api.v2.Query query, final String[] storedFieldNames,
                        final boolean filterStreams, final IndexFieldsMap indexFieldsMap,
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
                final IndexShardQueryFactory queryFactory = new IndexShardQueryFactory() {
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
                                                           final IndexFieldsMap indexFieldsMap) {
                        SearchExpressionQuery query = null;
                        try {
                            final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                                    dictionaryService, indexFieldsMap, maxBooleanClauseCount, task.getDateTimeLocale(), task.getNow());
                            query = searchExpressionQueryBuilder.buildQuery(version, expression);

                            // Make sure the query was created successfully.
                            if (query.getQuery() == null) {
                                throw new SearchException("Failed to build Lucene query given expression");
                            } else if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("Lucene Query is " + query.toString());
                            }
                        } catch (final Exception e) {
                            error(e.getMessage(), e);
                        }

                        if (query == null) {
                            query = new SearchExpressionQuery(null, null);
                        }

                        return query;
                    }
                };

                // Create a transfer list to capture stored data from the index
                // that can be used by coprocessors.
                storedData = new TransferList<>(maxStoredDataQueueSize);
                final AtomicLong hitCount = new AtomicLong();

                // Update the configuration.
                final int maxOpenShards = indexShardSearchTaskProperties.getMaxOpenShards();
                if (indexShardSearcherCache.getMaxOpenShards() != maxOpenShards) {
                    indexShardSearcherCache.setMaxOpenShards(maxOpenShards);
                }

                // Update config for the index shard search task executor.
                indexShardSearchTaskExecutor.setMaxThreads(indexShardSearchTaskProperties.getMaxThreads());

                // Make a task producer that will create event data extraction
                // tasks when requested by the executor.
                final IndexShardSearchTaskProducer indexShardSearchTaskProducer = new IndexShardSearchTaskProducer(task,
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

                // Add the task producer to the task executor.
                indexShardSearchTaskExecutor.addProducer(indexShardSearchTaskProducer);
                try {
                    // Kick off searching.
                    indexShardSearchTaskExecutor.exec();

                    if (!filterStreams) {
                        // If we aren't required to filter streams and aren't
                        // using pipelines to feed data
                        // to coprocessors then just do a simple data transfer
                        // to the coprocessors.
                        transfer(extractionCoprocessorsMap, indexShardSearchTaskProducer);

                    } else {
                        // Update config for extraction task executor.
                        extractionTaskExecutor.setMaxThreads(extractionTaskProperties.getMaxThreads());

                        // Create an object to make event lists from raw index
                        // data.
                        final StreamMapCreator streamMapCreator = new StreamMapCreator(task.getStoredFields(), this,
                                streamStore, securityContext);

                        // Make a task producer that will create event data
                        // extraction tasks when requested by the executor.
                        final ExtractionTaskProducer extractionTaskProducer = new ExtractionTaskProducer(task,
                                streamMapCreator,
                                storedData,
                                extractionFieldIndexMap,
                                extractionCoprocessorsMap,
                                this,
                                extractionTaskProperties.getMaxThreadsPerTask(),
                                executorProvider,
                                extractionTaskHandlerProvider);

                        // Add the task producer to the task executor.
                        extractionTaskExecutor.addProducer(extractionTaskProducer);
                        try {
                            // Wait for completion.
                            while (!task.isTerminated() && (!indexShardSearchTaskProducer.isComplete()
                                    || !extractionTaskProducer.isComplete())) {
                                taskContext.info(
                                        "Searching... " +
                                                indexShardSearchTaskProducer.remainingTasks() +
                                                " shards and " +
                                                extractionTaskProducer.remainingTasks() +
                                                " extractions remaining");

                                // Keep trying to execute extraction tasks.
                                extractionTaskExecutor.exec();
                                ThreadUtil.sleep(1000);
                            }
                        } finally {
                            // Remove the task producer from the task executor.
                            extractionTaskExecutor.removeProducer(extractionTaskProducer);
                        }
                    }
                } finally {
                    // Remove the task producer from the task executor.
                    indexShardSearchTaskExecutor.removeProducer(indexShardSearchTaskProducer);
                }
            }
        } catch (final Exception pEx) {
            throw SearchException.wrap(pEx);
        }
    }

    private void transfer(final Map<DocRef, Set<Coprocessor>> extractionCoprocessorsMap,
                          final IndexShardSearchTaskProducer indexShardSearchTaskProducer) {
        // If we aren't required to filter streams and aren't using pipelines to
        // feed data to coprocessors then just do a simple data transfer to the
        // coprocessors.
        final Set<Coprocessor> coprocessors = extractionCoprocessorsMap.get(null);
        boolean complete = false;
        List<String[]> list = null;

        while (!complete && !task.isTerminated()) {
            complete = indexShardSearchTaskProducer.isComplete();

            if (complete) {
                // If we are finished then we don't need to wait for items to
                // arrive in the list.
                list = storedData.swap();
            } else {
                // Search is in progress so wait for items to arrive in the list
                // if necessary.
                try {
                    list = storedData.swap(ONE_SECOND);
                } catch (final InterruptedException e) {
                    // Ignore.
                }
            }

            // Get stored data for every doc id in the list.
            if (list != null && list.size() > 0) {
                // We are not filtering data which means we must also not be
                // extracting data. In this case pass
                // raw values to the set of coprocessors that require raw values
                // with no extraction.
                for (final String[] values : list) {
                    if (task.isTerminated()) {
                        throw new TerminatedException();
                    }

                    for (final Coprocessor coprocessor : coprocessors) {
                        coprocessor.receive(values);
                    }
                }
            }
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
