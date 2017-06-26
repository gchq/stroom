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

package stroom.index.server;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.AlreadyClosedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean;
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventHandler;
import stroom.entity.shared.EntityAction;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexShardService;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.node.server.NodeCache;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.Node;
import stroom.query.shared.IndexFields;
import stroom.security.Secured;
import stroom.task.server.GenericServerTask;
import stroom.task.server.TaskManager;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomShutdown;
import stroom.util.spring.StroomSpringProfiles;
import stroom.util.spring.StroomStartup;
import stroom.util.thread.ThreadScopeRunnable;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

/**
 * Pool API into open index shards.
 */
@Component("indexShardManager")
@Secured(IndexShard.MANAGE_INDEX_SHARDS_PERMISSION)
@Profile(StroomSpringProfiles.PROD)
@EntityEventHandler(type = Index.ENTITY_TYPE)
public class IndexShardManagerImpl extends AbstractCacheBean<IndexShardKey, IndexShardWriter> implements IndexShardManager, EntityEvent.Handler {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardManagerImpl.class);
    private static final int MAX_CACHE_ENTRIES = 1000000;
    private static final int MAX_ATTEMPTS = 100;

    private final IndexService indexService;
    private final IndexShardService indexShardService;
    private final NodeCache nodeCache;
    private final TaskManager taskManager;

    private final StroomPropertyService stroomPropertyService;

    private final ConcurrentHashMap<IndexShard, IndexShardWriter> ownedWriters = new ConcurrentHashMap<>();
    private final AtomicBoolean deletingShards = new AtomicBoolean();

    private final StripedLock keyLocks = new StripedLock();

    @Inject
    IndexShardManagerImpl(final CacheManager cacheManager, final StroomPropertyService stroomPropertyService,
                          final IndexService indexService, final IndexShardService indexShardService, final NodeCache nodeCache, final TaskManager taskManager) {
        super(cacheManager, "Index Shard Writer Cache", MAX_CACHE_ENTRIES);
        this.stroomPropertyService = stroomPropertyService;
        this.indexService = indexService;
        this.indexShardService = indexShardService;
        this.nodeCache = nodeCache;
        this.taskManager = taskManager;

        setMaxIdleTime(10, TimeUnit.SECONDS);
        setMaxLiveTime(1, TimeUnit.DAYS);
    }

    @Override
    public void addDocument(final IndexShardKey indexShardKey, final Document document) {
        if (document != null) {
            // Try and add the document silently without locking.
            boolean success = false;
            try {
                final IndexShardWriter indexShardWriter = get(indexShardKey);
                indexShardWriter.addDocument(document);
                success = true;
            } catch (final Throwable t) {
                LOGGER.trace(t::getMessage, t);
            }

            // Attempt a few more times under lock.
            for (int attempt = 0; !success && attempt < MAX_ATTEMPTS; attempt++) {
                // If we failed then try under lock to make sure we get a new writer.
                final Lock lock = keyLocks.getLockForKey(indexShardKey);
                lock.lock();
                try {
                    // Ask the cache for the current one (it might have been changed by another thread) and try again.
                    final IndexShardWriter indexShardWriter = get(indexShardKey);
                    success = addDocument(indexShardWriter, document);

                    if (!success) {
                        // Failed to add it so remove this object from the cache and try to get another one.
                        remove(indexShardKey);
                    }

                } catch (final Throwable t) {
                    LOGGER.trace(t::getMessage, t);

                    // If we've already tried once already then give up.
                    if (attempt > 0) {
                        throw t;
                    }
                } finally {
                    lock.unlock();
                }
            }

            // One final try that will throw an index exception if needed.
            if (!success) {
                try {
                    final IndexShardWriter indexShardWriter = get(indexShardKey);
                    indexShardWriter.addDocument(document);
                } catch (final IndexException e) {
                    throw e;
                } catch (final Throwable e) {
                    throw new IndexException(e.getMessage(), e);
                }
            }
        }
    }

    private boolean addDocument(final IndexShardWriter indexShardWriter, final Document document) {
        boolean success = false;
        try {
            indexShardWriter.addDocument(document);
            success = true;
        } catch (final AlreadyClosedException | IndexException e) {
            LOGGER.trace(e::getMessage, e);

        } catch (final Throwable t) {
            LOGGER.error(t::getMessage, t);

            // Mark the shard as corrupt as this should be the
            // only reason we can't add a document.
            indexShardWriter.setStatus(IndexShardStatus.CORRUPT);
        }

        return success;
    }

    /**
     * Overrides method in simple pool. Will be called when an item is created
     * by the pool.
     */
    @Override
    public IndexShardWriter create(final IndexShardKey key) {
        // Try and get an existing writer.
        IndexShardWriter writer = getExistingWriter(key);
        if (writer == null) {
            // Create a new one
            writer = createNewWriter(key);
        }

        return writer;
    }

    /**
     * Finds an existing writer in the list of writers and that matches the
     * index shard key and then opens it for writing. Returns null if no
     * existing writer can be found.
     */
    private IndexShardWriter getExistingWriter(final IndexShardKey key) {
        for (final Entry<IndexShard, IndexShardWriter> entry : ownedWriters.entrySet()) {
            final IndexShard indexShard = entry.getKey();
            final IndexShardWriter writer = entry.getValue();

            // Look for closed, non deleted, non full, non corrupt index shard
            // to add to
            if (indexShard.getIndex().getId() == key.getIndex().getId()
                    && writer.getPartition().equals(key.getPartition()) && IndexShardStatus.CLOSED.equals(writer.getStatus()) && !writer.isFull()) {
                try {
                    // Open the writer.
                    final boolean success = writer.open(false);
                    if (success) {
                        LOGGER.debug(() -> "getExistingWriter() - Opened existing index shard " + indexShard.getId() + " for index " + key.getIndex().getName() + " and partition " + key.getPartition());
                        return writer;
                    }
                } catch (final Exception e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
        }

        return null;
    }

    /**
     * Creates a new shard for the specified key, attaches a writer and opens it
     * for creation.
     */
    private IndexShardWriter createNewWriter(final IndexShardKey key) {
        // Create a new one
        final IndexShard indexShard = indexShardService.createIndexShard(key, nodeCache.getDefaultNode());

        // Create a writer to use the location.
        final IndexShardWriter writer = connectWrapper(indexShard, new HashMap<>());

        // Open the writer.
        final boolean success = writer.open(true);
        if (!success) {
            try {
                indexShardService.delete(indexShard);
            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
            }
            throw new IndexException("Unable to create new index shard for index " + key.getIndex().getName()
                    + " and partition " + key.getPartition());
        }

        LOGGER.debug(() -> "getOrCreateIndexShard() - Created new index shard " + indexShard.getId() + " for index " + key.getIndex().getName() + " and partition " + key.getPartition());

        // Remember this writer for future use.
        ownedWriters.put(indexShard, writer);

        return writer;
    }

    /**
     * Wrap the entity bean with our manager class
     */
    private IndexShardWriter connectWrapper(final IndexShard indexShard, final Map<Index, Index> loadedIndexes) {
        // Load the index.
        Index index = indexShard.getIndex();
        // In tests the index service is often null but normally we want to load
        // the index so we unmarshal fields.
        if (indexService != null) {
            index = loadedIndexes.computeIfAbsent(index, indexService::load);
        }

        final int ramBufferSizeMB = getRamBufferSize();

        // Get the index fields.
        final IndexFields indexFields = index.getIndexFieldsObject();

        // Create the writer.
        return new IndexShardWriterImpl(indexShardService, indexFields, index, indexShard,
                ramBufferSizeMB);
    }

    private int getRamBufferSize() {
        int ramBufferSizeMB = 1024;
        if (stroomPropertyService != null) {
            try {
                final String property = stroomPropertyService.getProperty("stroom.index.ramBufferSizeMB");
                if (property != null) {
                    ramBufferSizeMB = Integer.parseInt(property);
                }
            } catch (final Exception ex) {
                LOGGER.error(() -> "connectWrapper() - Integer.parseInt stroom.index.ramBufferSizeMB", ex);
            }
        }
        return ramBufferSizeMB;
    }

    @StroomStartup
    public void startup() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        loadAllAtStartup();

        // Asynchronously check that all of the shards can be opened etc.
        checkShards();

        // Asynchronously delete all logically deleted shards.
        deleteLogicallyDeleted();

        LOGGER.debug(() -> "Started in " + logExecutionTime);
    }

    @Override
    @StroomShutdown
    public void shutdown() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        findClose(criteria);
        super.clear();
        ownedWriters.clear();
        LOGGER.debug(() -> "Shutdown in " + logExecutionTime);
    }

    /**
     * ONLY EVER DO ONCE AT JVM Start Up. When we start find all open or closed
     * indexes and check their state. From this point on thee indexes will be
     * cached so that they can be used by the index filter.
     */
    private void loadAllAtStartup() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        // Get all index shards that are owned by this node.
        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        criteria.getNodeIdSet().add(nodeCache.getDefaultNode());
        criteria.getFetchSet().add(Index.ENTITY_TYPE);
        criteria.getFetchSet().add(Node.ENTITY_TYPE);

        final List<IndexShard> list = indexShardService.find(criteria);
        final Map<Index, Index> loadedIndexes = new HashMap<>();

        for (final IndexShard indexShard : list) {
            try {
                final IndexShardWriter writer = connectWrapper(indexShard, loadedIndexes);
                // Take ownership and remember this writer.
                ownedWriters.put(indexShard, writer);

            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        LOGGER.debug(() -> "loadAllAtStartup() - Completed in " + logExecutionTime);
    }

    /**
     * Check the health of all owned shards.
     */
    private void checkShards() {
        final GenericServerTask task = GenericServerTask.create("Check Shards", "Checking Shards...");
        final Runnable runnable = () -> {
            final LogExecutionTime logExecutionTime = new LogExecutionTime();
            final Iterator<Entry<IndexShard, IndexShardWriter>> iter = ownedWriters.entrySet().iterator();
            while (!task.isTerminated() && iter.hasNext()) {
                final Entry<IndexShard, IndexShardWriter> entry = iter.next();
                final IndexShardWriter writer = entry.getValue();
                try {
                    if (writer != null) {
                        LOGGER.debug(() -> "checkShards() - Checking index shard " + writer.getIndexShard().getId());
                        writer.check();
                    }
                } catch (final Exception e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
            LOGGER.debug(() -> "checkShards() - Completed in " + logExecutionTime);
        };

        // In tests we don't have a task manager.
        if (taskManager == null) {
            runnable.run();
        } else {
            task.setRunnable(runnable);
            taskManager.execAsync(task);
        }
    }

    /**
     * Delete anything that has been marked to delete
     */
    private void deleteLogicallyDeleted() {
        if (deletingShards.compareAndSet(false, true)) {
            try {
                final GenericServerTask task = GenericServerTask.create("Delete Logically Deleted Shards", "Deleting Logically Deleted Shards...");
                final Runnable runnable = () -> {
                    try {
                        final LogExecutionTime logExecutionTime = new LogExecutionTime();
                        final Iterator<Entry<IndexShard, IndexShardWriter>> iter = ownedWriters.entrySet().iterator();
                        while (!task.isTerminated() && iter.hasNext()) {
                            final Entry<IndexShard, IndexShardWriter> entry = iter.next();
                            final IndexShardWriter writer = entry.getValue();
                            try {
                                if (writer != null && IndexShardStatus.DELETED.equals(writer.getStatus())) {
                                    LOGGER.debug(() -> "deleteLogicallyDeleted() - Deleting index shard " + writer.getIndexShard().getId());

                                    if (writer.deleteFromDisk()) {
                                        iter.remove();
                                    }
                                }
                            } catch (final Exception e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        }
                        LOGGER.debug(() -> "deleteLogicallyDeleted() - Completed in " + logExecutionTime);
                    } finally {
                        deletingShards.set(false);
                    }
                };

                // In tests we don't have a task manager.
                if (taskManager == null) {
                    runnable.run();
                } else {
                    task.setRunnable(runnable);
                    taskManager.execAsync(task);
                }

            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
                deletingShards.set(false);
            }
        }
    }

    /**
     * This is called by the lifecycle service and will call flush on all
     * objects in the pool without locking the pool for use in the mean time.
     */
    @Override
    public void flushAll() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try {
            final List<?> keys = getCache().getKeys();
            for (final Object key : keys) {
                try {
                    // Try and get the element quietly as we don't want this
                    // call to extend the life of sessions
                    // that should be dying.
                    final Element element = getCache().getQuiet(key);
                    flush(element);
                } catch (final Throwable t) {
                    LOGGER.error(t::getMessage, t);
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t::getMessage, t);
        }
        LOGGER.debug(() -> "flushAll() - Completed in " + logExecutionTime);
    }

    private void flush(final Element element) {
        if (element != null && element.getObjectValue() != null
                && element.getObjectValue() instanceof IndexShardWriter) {
            final IndexShardWriter indexShardWriter = (IndexShardWriter) element.getObjectValue();
            try {
                indexShardWriter.flush();
            } catch (final Exception ex) {
                LOGGER.error(() -> "flush() - Error flushing writer " + indexShardWriter);
            }
        }
    }

    /**
     * This is called by the node command service and is a result of a user
     * interaction. The map is synchronised so no writers will be created or
     * destroyed while this is called.
     */
    @Override
    public Long findFlush(final FindIndexShardCriteria criteria) {
        return performAction(criteria, IndexShardAction.FLUSH);
    }

    /**
     * This is called when a user wants to close some index shards or during shutdown.
     * This method returns the number of index shard writers that have been closed.
     */
    @Override
    public Long findClose(final FindIndexShardCriteria criteria) {
        return performAction(criteria, IndexShardAction.CLOSE);
    }

    /**
     * This is called by the node command service and is a result of a user
     * interaction. The map is synchronised so no writers will be created or
     * destroyed while this is called.
     */
    @Override
    public Long findDelete(final FindIndexShardCriteria criteria) {
        return performAction(criteria, IndexShardAction.DELETE);
    }

    private Long performAction(final FindIndexShardCriteria criteria, final IndexShardAction action) {
        final List<IndexShardWriter> writers = getFilteredWriters(criteria);
        if (writers.size() == 0) {
            LOGGER.info(() -> "No index shard writers have been found that need " + action.getActivity());
        } else {
            LOGGER.info(() -> "Preparing to " + action.getName() + " " + writers.size() + " index shards");
        }

        if (writers.size() > 0) {
            // Create an atomic integer to count the number of index shard writers yet to complete the specified action.
            final AtomicInteger remaining = new AtomicInteger(writers.size());

            // Create a scheduled executor for us to continually log index shard writer action progress.
            final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            // Start logging action progress.
            executor.scheduleAtFixedRate(() -> LOGGER.info(() -> "Waiting for " + remaining.get() + " index shards to " + action.getName()), 10, 10, TimeUnit.SECONDS);

            // Perform action on all of the index shard writers in parallel.
            writers.parallelStream().forEach(writer -> new ThreadScopeRunnable() {
                @Override
                protected void exec() {
                    try {
                        LOGGER.debug(() -> action.getActivity() + " index shard " + writer.getIndexShard().getId());
                        switch (action) {
                            case FLUSH:
                                writer.flush();
                                break;
                            case CLOSE:
                                writer.close();
                                break;
                            case DELETE:
                                writer.delete();
                                break;
                        }
                    } catch (final Exception e) {
                        LOGGER.error(e::getMessage, e);
                    }

                    remaining.getAndDecrement();
                }
            }.run());

            // Shut down the progress logging executor.
            executor.shutdown();

            LOGGER.info(() -> "Finished " + action.getActivity() + " index shards");
        }

        return (long) writers.size();
    }

    /**
     * Gets a list of index shard writers filtered using the supplied criteria.
     */
    private List<IndexShardWriter> getFilteredWriters(final FindIndexShardCriteria criteria) {
        final List<IndexShardWriter> filteredWriters = new ArrayList<>();

        for (final Entry<IndexShard, IndexShardWriter> entry : ownedWriters.entrySet()) {
            final IndexShard indexShard = entry.getKey();
            final IndexShardWriter writer = entry.getValue();
            if (indexShard != null && writer != null) {
                if (criteria.isMatch(indexShard)) {
                    filteredWriters.add(writer);
                }
            }
        }

        return filteredWriters;
    }

    @StroomFrequencySchedule("10m")
    @JobTrackedSchedule(jobName = "Index Shard Retention", description = "Job to delete index shards that have past their retention period")
    public void execute() {
        flushAll();
        deleteLogicallyDeleted();
        updateAllIndexes();
    }

    @Override
    public IndexWriter getWriter(final IndexShard indexShard) {
        final IndexShardWriter indexShardWriter = ownedWriters.get(indexShard);
        if (indexShardWriter != null) {
            return indexShardWriter.getWriter();
        }
        return null;
    }

    @Override
    public void onChange(final EntityEvent event) {
        // If an index has been updated then update field analyzers for the
        // index.
        if (EntityAction.UPDATE.equals(event.getAction()) && Index.ENTITY_TYPE.equals(event.getDocRef().getType())) {
            final Index index = indexService.loadByUuid(event.getDocRef().getUuid());
            if (index != null) {
                for (final Entry<IndexShard, IndexShardWriter> entry : ownedWriters.entrySet()) {
                    final IndexShard indexShard = entry.getKey();
                    final IndexShardWriter writer = entry.getValue();
                    if (indexShard != null && writer != null) {
                        if (indexShard.getIndex().equals(index)) {
                            writer.updateIndex(index);
                        }
                    }
                }
            }
        }
    }

    private void updateAllIndexes() {
        for (final Entry<IndexShard, IndexShardWriter> entry : ownedWriters.entrySet()) {
            final IndexShard indexShard = entry.getKey();
            final IndexShardWriter writer = entry.getValue();
            if (indexShard != null && writer != null) {
                final Index index = indexService.load(indexShard.getIndex());
                if (index != null) {
                    writer.updateIndex(index);
                }
            }
        }
    }

    private enum IndexShardAction {
        FLUSH("flush", "flushing"), CLOSE("close", "closing"), DELETE("delete", "deleting");

        private final String name;
        private final String activity;

        IndexShardAction(final String name, final String activity) {
            this.name = name;
            this.activity = activity;
        }

        public String getName() {
            return name;
        }

        public String getActivity() {
            return activity;
        }
    }
}
