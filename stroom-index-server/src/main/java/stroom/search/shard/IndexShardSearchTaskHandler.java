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

package stroom.search.shard;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.index.LuceneVersionUtil;
import stroom.index.shared.IndexShard;
import stroom.properties.StroomPropertyService;
import stroom.task.ExecutorProvider;
import stroom.task.TaskContext;
import stroom.util.shared.Severity;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class IndexShardSearchTaskHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexShardSearchTaskHandler.class);

    private final IndexShardSearcherCache indexShardSearcherCache;
    private final StroomPropertyService propertyService;
    private final ExecutorProvider executorProvider;
    private final TaskContext taskContext;

    @Inject
    IndexShardSearchTaskHandler(final IndexShardSearcherCache indexShardSearcherCache,
                                final StroomPropertyService propertyService,
                                final ExecutorProvider executorProvider,
                                final TaskContext taskContext) {
        this.indexShardSearcherCache = indexShardSearcherCache;
        this.propertyService = propertyService;
        this.executorProvider = executorProvider;
        this.taskContext = taskContext;
    }

    public VoidResult exec(final IndexShardSearchTask task) {
        try {
            taskContext.setName("Search Index Shard");
            if (!taskContext.isTerminated()) {
                taskContext.info("Searching shard " + task.getShardNumber() + " of " + task.getShardTotal() + " (id="
                        + task.getIndexShardId() + ")");

                // Borrow a searcher from the pool.
                final IndexShardSearcher indexShardSearcher = indexShardSearcherCache.get(task.getIndexShardId());

                // Start searching.
                searchShard(task, indexShardSearcher);
            }
        } catch (final Throwable t) {
            error(task, t.getMessage(), t);
        }

        return VoidResult.INSTANCE;
    }

    private void searchShard(final IndexShardSearchTask task, final IndexShardSearcher indexShardSearcher) {
        // Get the index shard that this searcher uses.
        final IndexShard indexShard = indexShardSearcher.getIndexShard();
        // Get the Lucene version being used.
        final Version luceneVersion = LuceneVersionUtil.getLuceneVersion(indexShard.getIndexVersion());
        // Get a query for this lucene version.
        final Query query = task.getQueryFactory().getQuery(luceneVersion);

        // If there is an error building the query then it will be null here.
        if (query != null) {
            final int maxDocIdQueueSize = getIntProperty("stroom.search.shard.maxDocIdQueueSize", 1000);
            final LinkedBlockingQueue<Optional<Integer>> docIdStore = new LinkedBlockingQueue<>(maxDocIdQueueSize);

            // Create a collector.
            final IndexShardHitCollector collector = new IndexShardHitCollector(taskContext, docIdStore,
                    task.getHitCount());

            try {
                final SearcherManager searcherManager = indexShardSearcher.getSearcherManager();
                final IndexSearcher searcher = searcherManager.acquire();
                try {
                    final Executor executor = executorProvider.getExecutor(IndexShardSearchTaskProducer.THREAD_POOL);
                    final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
                        try {
                            taskContext.setName("Index Searcher");
                            searcher.search(query, collector);
                        } catch (final Throwable t) {
                            error(task, t.getMessage(), t);
                        } finally {
                            try {
                                while (!docIdStore.offer(Optional.empty(), 1, TimeUnit.SECONDS) && !taskContext.isTerminated()) {
                                    // Loop
                                }
                            } catch (final InterruptedException e) {
                                // Ignore.
                            }
                        }
                    }, executor);

                    // Start retrieving stored data from the shard.
                    boolean complete = false;
                    while (!complete && !taskContext.isTerminated()) {
                        // Check if search is finished before polling for doc ids.
                        final boolean searchFinished = completableFuture.isDone();
                        // Poll for the next doc id.
                        final Optional<Integer> docId = docIdStore.poll(1, TimeUnit.SECONDS);

                        if (docId != null) {
                            if (docId.isPresent()) {
                                // If we have a doc id then retrieve the stored data for it.
                                getStoredData(task, searcher, docId.get());
                            } else {
                                // If we did not get a doc id then this search is complete if we got an empty optional.
                                complete = true;
                            }
                        } else {
                            // If we did not get a doc id then this search is complete if the shard has finished being searched.
                            complete = searchFinished;
                        }
                    }
                } catch (final Throwable t) {
                    error(task, t.getMessage(), t);
                } finally {
                    searcherManager.release(searcher);
                }
            } catch (final Throwable t) {
                error(task, t.getMessage(), t);
            }
        }
    }

    /**
     * This method takes a list of document id's and extracts the stored fields
     * that are required for data display. In some cases such as batch search we
     * only want to get stream and event ids, in these cases no values are
     * retrieved, only stream and event ids.
     */
    private void getStoredData(final IndexShardSearchTask task, final IndexSearcher searcher, final int docId) {
        final String[] fieldNames = task.getFieldNames();
        try {
            final Document document = searcher.doc(docId);
            String[] values = null;

            for (int i = 0; i < fieldNames.length; i++) {
                final String storedField = fieldNames[i];
                final IndexableField indexableField = document.getField(storedField);

                // If the field is not in fact stored then it will be null here.
                if (indexableField != null) {
                    final String value = indexableField.stringValue();
                    if (value != null) {
                        final String trimmed = value.trim();
                        if (trimmed.length() > 0) {
                            if (values == null) {
                                values = new String[fieldNames.length];
                            }
                            values[i] = trimmed;
                        }
                    }
                }
            }

            if (values != null) {
                task.getResultReceiver().receive(task.getIndexShardId(), Optional.of(values));
            }
        } catch (final Exception e) {
            error(task, e.getMessage(), e);
        }
    }

    private void error(final IndexShardSearchTask task, final String message, final Throwable t) {
        if (task == null) {
            LOGGER.error(message, t);
        } else {
            task.getErrorReceiver().log(Severity.ERROR, null, null, message, t);
        }
    }

    private int getIntProperty(final String propertyName, final int defaultValue) {
        int value = defaultValue;

        final String string = propertyService.getProperty(propertyName);
        if (string != null && !string.isEmpty()) {
            try {
                value = Integer.parseInt(string);
            } catch (final NumberFormatException e) {
                LOGGER.error("Unable to parse property '" + propertyName + "' value '" + string
                        + "', using default of '" + defaultValue + "' instead", e);
            }
        }

        return value;
    }
}
