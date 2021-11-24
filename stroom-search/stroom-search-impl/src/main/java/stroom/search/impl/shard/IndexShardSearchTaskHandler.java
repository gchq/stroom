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

package stroom.search.impl.shard;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.index.impl.IndexShardService;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.LuceneVersionUtil;
import stroom.index.shared.IndexShard;
import stroom.query.common.v2.Receiver;
import stroom.search.impl.SearchException;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.logging.SearchProgressLog;
import stroom.util.logging.SearchProgressLog.SearchPhase;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.util.Version;

import java.io.IOException;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import javax.inject.Inject;

public class IndexShardSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardSearchTaskHandler.class);

    public static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Search Index Shard");

    private final IndexShardWriterCache indexShardWriterCache;
    private final IndexShardService indexShardService;
    private final IndexShardSearchConfig shardConfig;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;

    @Inject
    IndexShardSearchTaskHandler(final IndexShardWriterCache indexShardWriterCache,
                                final IndexShardService indexShardService,
                                final IndexShardSearchConfig shardConfig,
                                final ExecutorProvider executorProvider,
                                final TaskContextFactory taskContextFactory) {
        this.indexShardWriterCache = indexShardWriterCache;
        this.indexShardService = indexShardService;
        this.shardConfig = shardConfig;
        this.executor = executorProvider.get(THREAD_POOL);
        this.taskContextFactory = taskContextFactory;
    }

    public void exec(final TaskContext taskContext, final IndexShardSearchTask task) {
        LOGGER.logDurationIfDebugEnabled(
                () -> {
                    final long indexShardId = task.getIndexShardId();
                    IndexShardSearcher indexShardSearcher = null;

                    try {
                        if (!Thread.currentThread().isInterrupted()) {
                            taskContext.info(() ->
                                    "Searching shard " + task.getShardNumber() + " of " + task.getShardTotal() +
                                            " (id=" + task.getIndexShardId() + ")");


                            final IndexWriter indexWriter = getWriter(indexShardId);

                            final IndexShard indexShard = indexShardService.loadById(indexShardId);
                            if (indexShard == null) {
                                throw new SearchException("Unable to find index shard with id = " + indexShardId);
                            }

                            indexShardSearcher = new IndexShardSearcher(indexShard, indexWriter);

                            // Start searching.
                            searchShard(taskContext, task, indexShardSearcher);
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e::getMessage, e);
                        error(task.getReceiver(), e.getMessage(), e);

                    } finally {
                        taskContext.info(() -> "Closing searcher for index shard " + indexShardId);
                        if (indexShardSearcher != null) {
                            indexShardSearcher.destroy();
                        }
                    }
                },
                () -> LogUtil.message("exec() for shard {}", task.getShardNumber()));
    }

    private IndexWriter getWriter(final Long indexShardId) {
        IndexWriter indexWriter = null;

        // Load the current index shard.
        final IndexShardWriter indexShardWriter = indexShardWriterCache.getWriterByShardId(indexShardId);
        if (indexShardWriter != null) {
            indexWriter = indexShardWriter.getWriter();
        }

        return indexWriter;
    }

    private void searchShard(final TaskContext parentTaskContext,
                             final IndexShardSearchTask task,
                             final IndexShardSearcher indexShardSearcher) {
        SearchProgressLog.increment(SearchPhase.INDEX_SHARD_SEARCH_TASK_HANDLER_SEARCH_SHARD);

        // Get the index shard that this searcher uses.
        final IndexShard indexShard = indexShardSearcher.getIndexShard();
        // Get the Lucene version being used.
        final Version luceneVersion = LuceneVersionUtil.getLuceneVersion(indexShard.getIndexVersion());
        // Get a query for this lucene version.
        final Query query = task.getQueryFactory().getQuery(luceneVersion);

        // If there is an error building the query then it will be null here.
        if (query != null) {
            final int maxDocIdQueueSize = shardConfig.getMaxDocIdQueueSize();
            LOGGER.debug(() -> "Creating docIdStore with size " + maxDocIdQueueSize);
            final LinkedBlockingQueue<OptionalInt> docIdStore = new LinkedBlockingQueue<>(maxDocIdQueueSize);

            // Create a collector.
            final IndexShardHitCollector collector = new IndexShardHitCollector(parentTaskContext,
                    docIdStore,
                    task.getHitCount());

            // Get the receiver.
            final Receiver receiver = task.getReceiver();

            try {
                final SearcherManager searcherManager = indexShardSearcher.getSearcherManager();
                final IndexSearcher searcher = searcherManager.acquire();
                try {
                    final Runnable runnable = taskContextFactory.childContext(parentTaskContext,
                            "Index Searcher",
                            taskContext ->
                                    LOGGER.logDurationIfDebugEnabled(() -> {
                                        try {
                                            searcher.search(query, collector);
                                        } catch (final IOException e) {
                                            error(receiver, e.getMessage(), e);
                                        }

                                        try {
                                            docIdStore.put(OptionalInt.empty());
                                        } catch (final InterruptedException e) {
                                            error(receiver, e.getMessage(), e);

                                            // Continue to interrupt this thread.
                                            Thread.currentThread().interrupt();
                                        }
                                    }, () -> "searcher.search()"));
                    CompletableFuture.runAsync(runnable, executor);

                    // Get an array of field names.
                    final String[] storedFieldNames = task.getStoredFieldNames();

                    // Start converting found docIds into stored data values
                    boolean complete = false;
                    while (!complete) {
                        // Take the next item
                        final OptionalInt optDocId = docIdStore.take();
                        if (optDocId.isPresent()) {
                            // If we have a doc id then retrieve the stored data for it.
                            SearchProgressLog.increment(SearchPhase.INDEX_SHARD_SEARCH_TASK_HANDLER_DOC_ID_STORE_TAKE);
                            getStoredData(storedFieldNames, receiver, searcher, optDocId.getAsInt());
                        } else {
                            complete = true;
                        }
                    }
                } catch (final RuntimeException e) {
                    error(receiver, e.getMessage(), e);
                } finally {
                    searcherManager.release(searcher);
                }
            } catch (final InterruptedException e) {
                error(receiver, e.getMessage(), e);

                // Continue to interrupt.
                Thread.currentThread().interrupt();
            } catch (final RuntimeException | IOException e) {
                error(receiver, e.getMessage(), e);
            }
        }
    }

    /**
     * This method takes a list of document id's and extracts the stored fields
     * that are required for data display. In some cases such as batch search we
     * only want to get stream and event ids, in these cases no values are
     * retrieved, only stream and event ids.
     */
    private void getStoredData(final String[] storedFieldNames,
                               final Receiver receiver,
                               final IndexSearcher searcher,
                               final int docId) {
        try {
            SearchProgressLog.increment(SearchPhase.INDEX_SHARD_SEARCH_TASK_HANDLER_GET_STORED_DATA);
            final Val[] values = new Val[storedFieldNames.length];
            final Document document = searcher.doc(docId);

            for (int i = 0; i < storedFieldNames.length; i++) {
                final String storedField = storedFieldNames[i];

                // If the field is null then it isn't stored.
                if (storedField != null) {
                    final IndexableField indexableField = document.getField(storedField);

                    // If the field is not in fact stored then it will be null here.
                    if (indexableField != null) {
                        final String value = indexableField.stringValue();
                        if (value != null) {
                            final String trimmed = value.trim();
                            if (trimmed.length() > 0) {
                                values[i] = ValString.create(trimmed);
                            }
                        }
                    }
                }
            }

            receiver.getValuesConsumer().accept(values);
        } catch (final IOException | RuntimeException e) {
            error(receiver, e.getMessage(), e);
        }
    }

    private void error(final Receiver receiver,
                       final String message,
                       final Throwable t) {
        if (receiver == null) {
            LOGGER.error(() -> message, t);
        } else {
            receiver.getErrorConsumer().accept(new Error(message, t));
        }
    }
}
