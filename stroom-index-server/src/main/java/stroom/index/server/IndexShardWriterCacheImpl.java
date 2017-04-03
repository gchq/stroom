/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean;
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventHandler;
import stroom.entity.shared.EntityAction;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexFields;
import stroom.index.shared.IndexService;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexShardService;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.node.server.NodeCache;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.Node;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomShutdown;
import stroom.util.spring.StroomSpringProfiles;
import stroom.util.spring.StroomStartup;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * Pool API into open index shards.
 */
@Component("indexShardWriterCache")
@Profile(StroomSpringProfiles.PROD)
@EntityEventHandler(type = Index.ENTITY_TYPE)
public class IndexShardWriterCacheImpl extends AbstractCacheBean<IndexShardKey, IndexShardWriter>
        implements IndexShardWriterCache, EntityEvent.Handler {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexShardWriterCacheImpl.class);

    private static final int MAX_CACHE_ENTRIES = 1000000;

    private final IndexService indexService;
    private final IndexShardService indexShardService;
    private final NodeCache nodeCache;

    private final StroomPropertyService stroomPropertyService;

    private final ConcurrentHashMap<IndexShard, IndexShardWriter> ownedWriters = new ConcurrentHashMap<>();
    private final StripedLock writerCreationLocks = new StripedLock();

    @Inject
    public IndexShardWriterCacheImpl(final CacheManager cacheManager, final StroomPropertyService stroomPropertyService,
            final IndexService indexService, final IndexShardService indexShardService, final NodeCache nodeCache) {
        super(cacheManager, "Index Shard Writer Cache", MAX_CACHE_ENTRIES);
        this.stroomPropertyService = stroomPropertyService;
        this.indexService = indexService;
        this.indexShardService = indexShardService;
        this.nodeCache = nodeCache;

        setMaxIdleTime(10, TimeUnit.SECONDS);
        setMaxLiveTime(1, TimeUnit.DAYS);
    }

    /**
     * Overrides method in simple pool. Will be called when an item is created
     * by the pool.
     */
    @Override
    public IndexShardWriter create(final IndexShardKey key) {
        return getOrCreateIndexShard(key);
    }

    /**
     * Gets an existing closed index shard or creates a new index shard if
     * needed.
     */
    private IndexShardWriter getOrCreateIndexShard(final IndexShardKey key) {
        IndexShardWriter writer = null;

        // We must lock here so we don't open up an index that is already being
        // passed back
        final Lock lock = writerCreationLocks.getLockForKey(key);
        lock.lock();
        try {
            // Try and get an existing writer.
            writer = getExistingWriter(key);
            if (writer == null) {
                // Create a new one
                writer = createNewWriter(key);
            }

        } finally {
            lock.unlock();
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
                    && writer.getPartition().equals(key.getPartition()) && writer.isClosed() && !writer.isFull()) {
                try {
                    // Open the writer.
                    final boolean success = writer.open(false);
                    if (success) {
                        LOGGER.debug("getOrCreateIndexShard() - Opened index shard {} for index {} and partition {}",
                                indexShard.getId(), key.getIndex().getName(), key.getPartition());

                        return writer;
                    }
                } catch (final Exception e) {
                    LOGGER.error(e.getMessage(), e);
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
        final IndexShardWriter writer = connectWrapper(indexShard);

        // Open the writer.
        final boolean success = writer.open(true);
        if (!success) {
            try {
                indexShardService.delete(indexShard);
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
            throw new IndexException("Unable to create new index shard for index " + key.getIndex().getName()
                    + " and partition " + key.getPartition());
        }

        LOGGER.debug("getOrCreateIndexShard() - Created new index shard {} for index {} and partition {}",
                indexShard.getId(), key.getIndex().getName(), key.getPartition());

        // Remember this writer for future use.
        ownedWriters.put(indexShard, writer);

        return writer;
    }

    /**
     * Wrap the entity bean with our manager class
     */
    private IndexShardWriter connectWrapper(final IndexShard indexShard) {
        // Load the index.
        Index index = indexShard.getIndex();
        // In tests the index service is often null but normally we want to load
        // the index so we unmarshal fields.
        if (indexService != null) {
            index = indexService.load(index);
        }

        final int ramBufferSizeMB = getRamBufferSize();

        // Get the index fields.
        final IndexFields indexFields = index.getIndexFieldsObject();

        // Create the writer.
        final IndexShardWriterImpl writer = new IndexShardWriterImpl(indexShardService, indexFields, index, indexShard,
                ramBufferSizeMB);

        return writer;
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
                LOGGER.error("connectWrapper() - Integer.parseInt stroom.index.ramBufferSizeMB", ex);
            }
        }
        return ramBufferSizeMB;
    }

    @StroomStartup
    public void startup() {
        loadAllAtStartup();
        deleteLogicallyDeleted();
    }

    @Override
    @StroomShutdown
    public void shutdown() {
        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        findClose(criteria);
        super.clear();
        ownedWriters.clear();
    }

    /**
     * ONLY EVER DO ONCE AT JVM Start Up. When we start find all open or closed
     * indexes and check their state. From this point on thee indexes will be
     * cached so that they can be used by the index filter.
     */
    private void loadAllAtStartup() {
        // Get all index shards that are owned by this node.
        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        criteria.getNodeIdSet().add(nodeCache.getDefaultNode());
        criteria.getFetchSet().add(Index.ENTITY_TYPE);
        criteria.getFetchSet().add(Node.ENTITY_TYPE);

        final List<IndexShard> list = indexShardService.find(criteria);

        for (final IndexShard indexShard : list) {
            try {
                final IndexShardWriter writer = connectWrapper(indexShard);
                // Remember this writer.
                ownedWriters.put(indexShard, writer);

                // Check that the writer is ok.
                LOGGER.debug("loadAllAtStartup() - Checking index shard {}", indexShard.getId());
                writer.check();

            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Delete anything that has been marked to delete
     */
    private void deleteLogicallyDeleted() {
        final long startTime = System.currentTimeMillis();
        LOGGER.debug("deleteLogicallyDeleted() - Started");

        final Iterator<Entry<IndexShard, IndexShardWriter>> iter = ownedWriters.entrySet().iterator();
        while (iter.hasNext()) {
            final Entry<IndexShard, IndexShardWriter> entry = iter.next();
            final IndexShardWriter writer = entry.getValue();
            try {
                if (writer != null && writer.isDeleted()) {
                    LOGGER.debug("deleteLogicallyDeleted() - Deleting index shard {}", writer.getIndexShard().getId());

                    if (writer.deleteFromDisk()) {
                        iter.remove();
                    }
                }
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        LOGGER.debug("deleteLogicallyDeleted() - Completed in {}",
                ModelStringUtil.formatDurationString(System.currentTimeMillis() - startTime));
    }

    /**
     * This is called by the lifecycle service and will call flush on all
     * objects in the pool without locking the pool for use in the mean time.
     */
    @Override
    public void flushAll() {
        final long startTime = System.currentTimeMillis();
        LOGGER.debug("flushAll() - Started");
        try {
            final List<?> keys = getCache().getKeys();
            for (final Object key : keys) {
                try {
                    // Try and get the element quietly as we don't want this
                    // call top extend the life of sessions
                    // that should be dying.
                    final Element element = getCache().getQuiet(key);
                    flush(element);
                } catch (final Throwable t) {
                    LOGGER.error(t.getMessage(), t);
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
        LOGGER.debug("flushAll() - Completed in {}",
                ModelStringUtil.formatDurationString(System.currentTimeMillis() - startTime));
    }

    private void flush(final Element element) {
        if (element != null && element.getObjectValue() != null
                && element.getObjectValue() instanceof IndexShardWriter) {
            final IndexShardWriter indexShardWriter = (IndexShardWriter) element.getObjectValue();
            if (indexShardWriter != null) {
                try {
                    indexShardWriter.flush();
                } catch (final Exception ex) {
                    LOGGER.error("flush() - Error flushing writer {}", indexShardWriter);
                }
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
        final List<IndexShardWriter> writers = getFilteredWriters(criteria);
        for (final IndexShardWriter writer : writers) {
            try {
                LOGGER.debug("flush() - Flushing index shard {}", writer.getIndexShard().getId());
                writer.flush();
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        return null;
    }

    /**
     * This is called by the node command service and is a result of a user
     * interaction. The map is synchronised so no writers will be created or
     * destroyed while this is called.
     */
    @Override
    public Long findClose(final FindIndexShardCriteria criteria) {
        final List<IndexShardWriter> writers = getFilteredWriters(criteria);
        for (final IndexShardWriter writer : writers) {
            try {
                LOGGER.debug("close() - Closing IndexShard {}", writer.getIndexShard().getId());
                writer.close();
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        return null;
    }

    /**
     * This is called by the node command service and is a result of a user
     * interaction. The map is synchronised so no writers will be created or
     * destroyed while this is called.
     */
    @Override
    public Long findDelete(final FindIndexShardCriteria criteria) {
        final List<IndexShardWriter> writers = getFilteredWriters(criteria);
        for (final IndexShardWriter writer : writers) {
            try {
                LOGGER.debug("delete() - Deleting index shard {}", writer.getIndexShard().getId());
                writer.delete();
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        return null;
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
    public IndexShardWriter getWriter(final IndexShard indexShard) {
        return ownedWriters.get(indexShard);
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
}
