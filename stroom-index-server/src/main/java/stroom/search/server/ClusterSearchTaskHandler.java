/*
 * Copyright 2016 Crown Copyright
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

package stroom.search.server;

import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.util.StringUtils;
import stroom.dashboard.expression.FieldIndexMap;
import stroom.dictionary.shared.DictionaryService;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexService;
import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.pipeline.server.errorhandler.MessageUtil;
import stroom.pipeline.server.errorhandler.TerminatedException;
import stroom.query.CoprocessorMap.CoprocessorKey;
import stroom.query.CoprocessorSettings;
import stroom.query.api.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Param;
import stroom.search.server.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.server.extraction.ExtractionTaskExecutor;
import stroom.search.server.extraction.ExtractionTaskProducer;
import stroom.search.server.extraction.ExtractionTaskProperties;
import stroom.search.server.extraction.StreamMapCreator;
import stroom.search.server.sender.SenderTask;
import stroom.search.server.shard.IndexShardSearchTask.IndexShardQueryFactory;
import stroom.search.server.shard.IndexShardSearchTaskExecutor;
import stroom.search.server.shard.IndexShardSearchTaskProducer;
import stroom.search.server.shard.IndexShardSearchTaskProperties;
import stroom.search.server.shard.IndexShardSearcherCache;
import stroom.search.server.shard.TransferList;
import stroom.security.SecurityContext;
import stroom.streamstore.server.StreamStore;
import stroom.task.server.TaskCallback;
import stroom.task.server.TaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskManager;
import stroom.task.server.TaskTerminatedException;
import stroom.util.shared.Location;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@TaskHandlerBean(task = ClusterSearchTask.class)
@Scope(StroomScope.TASK)
public class ClusterSearchTaskHandler implements TaskHandler<ClusterSearchTask, NodeResult>, ErrorReceiver {
    /**
     * We don't want to collect more than 1 million doc's data into the queue by
     * default. When the queue is full the index shard data tasks will pause
     * until the docs are drained from the queue.
     */
    public static final int DEFAULT_MAX_STORED_DATA_QUEUE_SIZE = 1000000;
    private static final StroomLogger LOGGER = StroomLogger.getLogger(ClusterSearchTaskHandler.class);
    private static final long ONE_SECOND = TimeUnit.SECONDS.toNanos(1);
    private static final int DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT = 1024;
    private final TaskManager taskManager;
    private final IndexService indexService;
    private final DictionaryService dictionaryService;
    private final TaskMonitor taskMonitor;
    private final CoprocessorFactory coprocessorFactory;
    private final IndexShardSearchTaskExecutor indexShardSearchTaskExecutor;
    private final IndexShardSearchTaskProperties indexShardSearchTaskProperties;
    private final IndexShardSearcherCache indexShardSearcherCache;
    private final ExtractionTaskExecutor extractionTaskExecutor;
    private final ExtractionTaskProperties extractionTaskProperties;
    private final StreamStore streamStore;
    private final SecurityContext securityContext;
    private final LinkedBlockingDeque<String> errors = new LinkedBlockingDeque<>();
    private final AtomicBoolean searchComplete = new AtomicBoolean();
    private final AtomicBoolean sendingComplete = new AtomicBoolean();
    private int maxStoredDataQueueSize = DEFAULT_MAX_STORED_DATA_QUEUE_SIZE;
    private int maxBooleanClauseCount = DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT;
    private ClusterSearchTask task;
    private Index index;

    private TransferList<String[]> storedData;

    @Inject
    public ClusterSearchTaskHandler(final TaskManager taskManager, final IndexService indexService,
                                    final DictionaryService dictionaryService, final TaskMonitor taskMonitor,
                                    final CoprocessorFactory coprocessorFactory,
                                    final IndexShardSearchTaskExecutor indexShardSearchTaskExecutor,
                                    final IndexShardSearchTaskProperties indexShardSearchTaskProperties,
                                    final IndexShardSearcherCache indexShardSearcherCache, final ExtractionTaskExecutor extractionTaskExecutor,
                                    final ExtractionTaskProperties extractionTaskProperties, final StreamStore streamStore, final SecurityContext securityContext) {
        this.taskManager = taskManager;
        this.indexService = indexService;
        this.dictionaryService = dictionaryService;
        this.taskMonitor = taskMonitor;
        this.coprocessorFactory = coprocessorFactory;
        this.indexShardSearchTaskExecutor = indexShardSearchTaskExecutor;
        this.indexShardSearchTaskProperties = indexShardSearchTaskProperties;
        this.indexShardSearcherCache = indexShardSearcherCache;
        this.extractionTaskExecutor = extractionTaskExecutor;
        this.extractionTaskProperties = extractionTaskProperties;
        this.streamStore = streamStore;
        this.securityContext = securityContext;
    }

    @Override
    public void exec(final ClusterSearchTask task, final TaskCallback<NodeResult> callback) {
        try {
            securityContext.elevatePermissions();

            if (!taskMonitor.isTerminated()) {
                taskMonitor.info("Initialising...");

                this.task = task;
                final stroom.query.api.Query query = task.getQuery();

                try {
                    final long frequency = task.getResultSendFrequency();

                    // Reload the index.
                    index = indexService.loadByUuid(query.getDataSource().getUuid());

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
                    boolean filterStreams = false;

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
                            final Map<String, String> paramMap = Collections.emptyMap();
                            if (query.getParams() != null) {
                                for (final Param param : query.getParams()) {
                                    paramMap.put(param.getKey(), param.getValue());
                                }
                            }
                            final Coprocessor coprocessor = coprocessorFactory.create(
                                    coprocessorSettings, fieldIndexMap, paramMap, taskMonitor);

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

                                Set<Coprocessor> extractionCoprocessors = extractionCoprocessorsMap.get(pipelineRef);
                                if (extractionCoprocessors == null) {
                                    extractionCoprocessors = new HashSet<>();
                                    extractionCoprocessorsMap.put(pipelineRef, extractionCoprocessors);
                                }
                                extractionCoprocessors.add(coprocessor);
                            }
                        }
                    }

                    // Start forwarding data to target node.
                    final SenderTask senderTask = new SenderTask(task, coprocessorMap, callback, frequency, sendingComplete,
                            searchComplete, errors);
                    taskManager.execAsync(senderTask);

                    taskMonitor.info("Searching...");
                    search(task, query, storedFieldNames, filterStreams, indexFieldsMap, extractionFieldIndexMap,
                            extractionCoprocessorsMap);

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
                taskMonitor.info("Sending final results");
                while (!task.isTerminated() && !sendingComplete.get()) {
                    ThreadUtil.sleep(1000);
                }
            }
        } finally {
            securityContext.restorePermissions();
        }
    }

    private void search(final ClusterSearchTask task, final stroom.query.api.Query query, final String[] storedFieldNames,
                        final boolean filterStreams, final IndexFieldsMap indexFieldsMap,
                        final FieldIndexMap extractionFieldIndexMap,
                        final Map<DocRef, Set<Coprocessor>> extractionCoprocessorsMap) {
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
                                    dictionaryService, indexFieldsMap, maxBooleanClauseCount, task.getNow());
                            query = searchExpressionQueryBuilder.buildQuery(version, expression);

                            // Make sure the query was created successfully.
                            if (query.getQuery() == null) {
                                throw new SearchException("Failed to build LUCENE query given expression");
                            } else if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("Lucence Query is " + query.toString());
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
                        storedData, indexShardSearcherCache, task.getShards(), queryFactory, storedFieldNames, this,
                        hitCount, indexShardSearchTaskProperties.getMaxThreadsPerTask());

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
                                streamMapCreator, storedData, extractionFieldIndexMap, extractionCoprocessorsMap, this,
                                extractionTaskProperties.getMaxThreadsPerTask());

                        // Add the task producer to the task executor.
                        extractionTaskExecutor.addProducer(extractionTaskProducer);
                        try {
                            // Wait for completion.
                            while (!task.isTerminated() && (!indexShardSearchTaskProducer.isComplete()
                                    || !extractionTaskProducer.isComplete())) {
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
        LOGGER.debug(e, e);

        if (e == null || !(e instanceof TaskTerminatedException)) {
            final String msg = MessageUtil.getMessage(message, e);
            errors.push(msg);
        }
    }

    public ClusterSearchTask getTask() {
        return task;
    }

    @Value("#{propertyConfigurer.getProperty('stroom.search.maxBooleanClauseCount')}")
    public void setMaxBooleanClauseCount(final String maxBooleanClauseCount) {
        try {
            this.maxBooleanClauseCount = ModelStringUtil.parseNumberStringAsInt(maxBooleanClauseCount);
        } catch (final NumberFormatException e) {
            LOGGER.error("Unable to parse property 'stroom.search.maxBooleanClauseCount' value '" + maxBooleanClauseCount
                    + "', using default of '" + DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT + "' instead", e);
        }
    }

    @Value("#{propertyConfigurer.getProperty('stroom.search.maxStoredDataQueueSize')}")
    public void setMaxStoredDataQueueSize(final String maxStoredDataQueueSize) {
        if (StringUtils.hasText(maxStoredDataQueueSize)) {
            try {
                this.maxStoredDataQueueSize = ModelStringUtil.parseNumberStringAsInt(maxStoredDataQueueSize);
            } catch (final NumberFormatException e) {
                LOGGER.error(
                        "Unable to parse property 'stroom.search.maxStoredDataQueueSize' value '" + maxStoredDataQueueSize
                                + "', using default of '" + DEFAULT_MAX_STORED_DATA_QUEUE_SIZE + "' instead",
                        e);
            }
        }
    }
}
