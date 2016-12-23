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

package stroom.search.server.shard;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.springframework.context.annotation.Scope;
import stroom.index.shared.IndexShard;
import stroom.node.server.StroomPropertyService;
import stroom.pipeline.server.errorhandler.TerminatedException;
import stroom.search.server.IndexShardSearcher;
import stroom.search.server.shard.IndexShardSearcherCache.IndexShardSearcherPool;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.GenericServerTask;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskManager;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Severity;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@TaskHandlerBean(task = IndexShardSearchTask.class)
@Scope(StroomScope.TASK)
public class IndexShardSearchTaskHandler extends AbstractTaskHandler<IndexShardSearchTask, VoidResult> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(IndexShardSearchTaskHandler.class);
    private static final long ONE_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final IndexShardSearcherCache indexShardSearcherCache;
    private final StroomPropertyService propertyService;
    private final TaskManager taskManager;
    private final TaskMonitor taskMonitor;

    @Inject
    public IndexShardSearchTaskHandler(final IndexShardSearcherCache indexShardSearcherCache,
                                       final StroomPropertyService propertyService, final TaskManager taskManager, final TaskMonitor taskMonitor) {
        this.indexShardSearcherCache = indexShardSearcherCache;
        this.propertyService = propertyService;
        this.taskManager = taskManager;
        this.taskMonitor = taskMonitor;
    }

    @Override
    public VoidResult exec(final IndexShardSearchTask task) {
        try {
            if (!taskMonitor.isTerminated()) {
                taskMonitor.info("Searching shard " + task.getShardNumber() + " of " + task.getShardTotal() + " (id="
                        + task.getIndexShardId() + ")");

                IndexShardSearcherPool pool = null;
                try {
                    pool = indexShardSearcherCache.getOrCreate(task.getIndexShardId());
                    searchPool(pool, task);
                } finally {
                    copyPoolExceptions(task, pool);
                }
            }
        } finally {
            task.getResultReceiver().complete(task.getIndexShardId());
        }

        return VoidResult.INSTANCE;
    }

    private void searchPool(final IndexShardSearcherPool pool, final IndexShardSearchTask task) {
        try {
            if (pool == null) {
                error(task, "Null searcher", null);

            } else if (!pool.hasExceptions()) {
                // Borrow a searcher from this pool.
                final IndexShardSearcher indexShardSearcher = pool.borrowObject();
                try {
                    // Exceptions might have been created when the searcher was
                    // borrowed from the pool.
                    if (!pool.hasExceptions()) {
                        searchShard(task, indexShardSearcher);
                    }
                } catch (final Throwable t) {
                    error(task, t.getMessage(), t);

                } finally {
                    pool.returnObject(indexShardSearcher);
                }
            }
        } catch (final Throwable t) {
            error(task, t.getMessage(), t);
        }
    }

    private void copyPoolExceptions(final IndexShardSearchTask task, final IndexShardSearcherPool pool) {
        if (pool != null && pool.hasExceptions()) {
            // If any exceptions were added to the pool then copy them out here.
            for (final Throwable t : pool.getExceptions()) {
                error(task, t.getMessage(), t);
            }
        }
    }

    private void searchShard(final IndexShardSearchTask task, final IndexShardSearcher indexShardSearcher) {
        // Get the index shard that this searcher uses.
        final IndexShard indexShard = indexShardSearcher.getIndexShard();
        // Get a query for this lucene version.
        final Query query = task.getQuery();

        // If there is an error building the query then it will be null here.
        if (query != null) {
            final int maxDocIdQueueSize = getIntProperty("stroom.search.shard.maxDocIdQueueSize", 1000);
            final TransferList<Integer> docIdStore = new TransferList<>(maxDocIdQueueSize);
            final AtomicBoolean completedSearch = new AtomicBoolean();

            // Create a collector.
            final IndexShardHitCollector collector = new IndexShardHitCollector(task.getMonitor(), docIdStore,
                    task.getHitCount());

            final IndexReader reader = indexShardSearcher.getReader();
            final IndexSearcher searcher = new IndexSearcher(reader);

            try {
                final GenericServerTask searchingTask = new GenericServerTask(task, task.getSessionId(),
                        task.getUserId(), "Index Searcher", "");
                searchingTask.setRunnable(() -> {
                    try {
                        searcher.search(query, collector);
                    } catch (final Throwable t) {
                        error(task, t.getMessage(), t);
                    } finally {
                        completedSearch.set(true);
                    }
                });
                taskManager.execAsync(searchingTask, IndexShardSearchTask.THREAD_POOL);

                // Start retrieving stored data from the shard.
                boolean complete = false;
                List<Integer> list = null;

                while (!complete && !task.isTerminated()) {
                    complete = completedSearch.get();

                    if (complete) {
                        // If we are finished then we don't need to wait for
                        // items to arrive in the list.
                        list = docIdStore.swap();
                    } else {
                        // Search is in progress so wait for items to arrive in
                        // the list if necessary.
                        try {
                            list = docIdStore.swap(ONE_SECOND);
                        } catch (final InterruptedException e) {
                            // Ignore.
                        }
                    }

                    // Get stored data for every doc id in the list.
                    if (list != null && list.size() > 0) {
                        for (final Integer docId : list) {
                            if (task.isTerminated()) {
                                throw new TerminatedException();
                            }

                            getStoredData(task, reader, docId);
                        }
                    }
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
    private void getStoredData(final IndexShardSearchTask task, final IndexReader reader, final int docId) {
        final String[] fieldNames = task.getFieldNames();
        try {
            final Document document = reader.document(docId);
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
                task.getResultReceiver().receive(task.getIndexShardId(), values);
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
        if (string != null && string.length() > 0) {
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
