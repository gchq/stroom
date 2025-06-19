package stroom.index.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.LuceneIndexDoc;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public class ActiveShardsCacheImpl implements ActiveShardsCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ActiveShardsCacheImpl.class);

    private static final String CACHE_NAME = "Active Index Shard Cache";

    private final NodeInfo nodeInfo;
    private final IndexShardWriterCache indexShardWriterCache;
    private final IndexShardDao indexShardDao;
    private final IndexShardCreator indexShardCreator;
    private final LuceneIndexDocCache luceneIndexDocCache;
    private final SecurityContext securityContext;


    private final LoadingStroomCache<IndexShardKey, ActiveShards> cache;

    @Inject
    ActiveShardsCacheImpl(final IndexShardWriterCache indexShardWriterCache,
                          final NodeInfo nodeInfo,
                          final IndexShardDao indexShardDao,
                          final IndexShardCreator indexShardCreator,
                          final LuceneIndexDocCache luceneIndexDocCache,
                          final CacheManager cacheManager,
                          final Provider<IndexWriterConfig> indexWriterConfigProvider,
                          final SecurityContext securityContext) {
        this.indexShardWriterCache = indexShardWriterCache;
        this.nodeInfo = nodeInfo;
        this.indexShardDao = indexShardDao;
        this.indexShardCreator = indexShardCreator;
        this.luceneIndexDocCache = luceneIndexDocCache;
        this.securityContext = securityContext;

        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> indexWriterConfigProvider.get().getActiveShardCache(),
                this::create);
    }

    @Override
    public ActiveShards get(final IndexShardKey indexShardKey) {
        return cache.get(indexShardKey);
    }

    private ActiveShards create(final IndexShardKey indexShardKey) {
        return securityContext.asProcessingUserResult(() -> {
            // Get the index fields.
            final LuceneIndexDoc luceneIndexDoc = luceneIndexDocCache.get(
                    new DocRef(LuceneIndexDoc.TYPE, indexShardKey.getIndexUuid()));
            if (luceneIndexDoc == null) {
                throw new IndexException("Unable to find index with UUID: " + indexShardKey.getIndexUuid());
            }

            LOGGER.debug("Creating ActiveShards for node: {}, indexShardKey: {}", nodeInfo, indexShardKey);
            return new ActiveShards(
                    nodeInfo,
                    indexShardWriterCache,
                    indexShardDao,
                    indexShardCreator,
                    luceneIndexDoc.getShardsPerPartition(),
                    luceneIndexDoc.getMaxDocsPerShard(),
                    indexShardKey);
        });
    }


    // --------------------------------------------------------------------------------


    public static class ActiveShards {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ActiveShards.class);

        // All shards are CLOSED on boot, but if this cache is cleared or items age off
        // then we may shards in other states.
        private static final EnumSet<IndexShardStatus> REQUIRED_SHARD_STATES = EnumSet.of(
                IndexShardStatus.NEW,
                IndexShardStatus.OPEN,
                IndexShardStatus.OPENING,
                IndexShardStatus.CLOSED,
                IndexShardStatus.CLOSING);

        private static final int MAX_ATTEMPTS = 10_000;

        private final NodeInfo nodeInfo;
        private final IndexShardWriterCache indexShardWriterCache;
        private final IndexShardDao indexShardDao;
        private final IndexShardCreator indexShardCreator;
        private final IndexShardKey indexShardKey;
        private final ReentrantLock lock = new ReentrantLock();
        private final Integer shardsPerPartition;
        private final Integer maxDocsPerShard;
        private final AtomicInteger sequence = new AtomicInteger();
        private volatile List<IndexShard> indexShards;

        public ActiveShards(final NodeInfo nodeInfo,
                            final IndexShardWriterCache indexShardWriterCache,
                            final IndexShardDao indexShardDao,
                            final IndexShardCreator indexShardCreator,
                            final Integer shardsPerPartition,
                            final Integer maxDocsPerShard,
                            final IndexShardKey indexShardKey) {
            this.nodeInfo = nodeInfo;
            this.indexShardWriterCache = indexShardWriterCache;
            this.indexShardDao = indexShardDao;
            this.indexShardCreator = indexShardCreator;
            this.shardsPerPartition = shardsPerPartition;
            this.maxDocsPerShard = maxDocsPerShard;
            this.indexShardKey = indexShardKey;

            indexShards = new ArrayList<>();
            indexShards.addAll(getExistingShards(indexShardKey));
            ensureShards();
        }

        public void addDocument(final IndexDocument document) {
            // Try and add the document silently without locking.
            final boolean success = addDocument(document, false);

            // Attempt under lock if we failed to add.
            if (!success) {
                LOGGER.debug("Trying again under lock");
                // If we failed then try under lock to make sure we get a new writer.
                addDocumentUnderLock(document);
            }
        }

        private void addDocumentUnderLock(final IndexDocument document) {
            boolean success = false;
            lock.lock();
            try {
                for (int attempt = 0; !success && attempt < MAX_ATTEMPTS; attempt++) {
                    success = addDocument(document, false);
                    if (!success) {
                        // If we weren't successful then try adding a shard.
                        addActiveShard(indexShardKey);
                    }
                }

                // One final try that will throw an index exception if needed.
                if (!success) {
                    try {
                        addDocument(document, true);
                    } catch (final IndexException e) {
                        throw e;
                    } catch (final RuntimeException e) {
                        throw new IndexException(e.getMessage(), e);
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        private boolean addDocument(final IndexDocument document,
                                    final boolean throwException) {
            final List<IndexShard> indexShards = getIndexShards();
            final int offset = sequence.getAndIncrement();
            final int rem = Math.abs(offset % indexShards.size());

            for (int i = rem; i < indexShards.size(); i++) {
                final IndexShard indexShard = indexShards.get(i);
                if (addDocument(document, indexShard, throwException)) {
                    return true;
                }
            }
            for (int i = 0; i < rem; i++) {
                final IndexShard indexShard = indexShards.get(i);
                if (addDocument(document, indexShard, throwException)) {
                    return true;
                }
            }
            return false;
        }

        private boolean addDocument(final IndexDocument document,
                                    final IndexShard indexShard,
                                    final boolean throwException) {
            try {
                final IndexShardWriter indexShardWriter = indexShardWriterCache.getOrOpenWriter(indexShard.getId());
                try {
                    indexShardWriter.addDocument(document);
                    return true;

                } catch (final IndexException | UncheckedIOException e) {
                    LOGGER.trace(e::getMessage, e);

                    removeActiveShard(indexShard);
                    indexShardWriterCache.close(indexShardWriter);

                } catch (final RuntimeException e) {
                    if (throwException) {
                        LOGGER.error(e::getMessage, e);
                        throw e;
                    } else {
                        LOGGER.debug(e::getMessage, e);
                    }
                }
            } catch (final RuntimeException e) {
                if (throwException) {
                    LOGGER.error(e::getMessage, e);
                    throw e;
                } else {
                    LOGGER.debug(e::getMessage, e);
                }
                removeActiveShard(indexShard);
            }
            return false;
        }

        private List<IndexShard> getIndexShards() {
            List<IndexShard> indexShards = this.indexShards;

            if (indexShards.size() < shardsPerPartition) {
                indexShards = ensureShards();
            }
            return indexShards;
        }

        private synchronized List<IndexShard> ensureShards() {
            LOGGER.debug(() -> LogUtil.message(
                    "ensureShards, indexShards size before: {}", NullSafe.size(indexShards)));
            List<IndexShard> list = indexShards;
            if (list.size() < shardsPerPartition) {
                list = new ArrayList<>(list);
                for (int i = list.size(); i < shardsPerPartition; i++) {
                    list.add(createNewShard(indexShardKey));
                }
            }
            indexShards = list;
            LOGGER.debug(() -> LogUtil.message(
                    "ensureShards, indexShards size after: {}", NullSafe.size(indexShards)));
            return list;
        }

        private synchronized void addActiveShard(final IndexShardKey indexShardKey) {
            LOGGER.debug("Adding shard for key {}", indexShardKey);
            final IndexShard indexShard = createNewShard(indexShardKey);
            final List<IndexShard> list = new ArrayList<>(indexShards);
            list.add(indexShard);
            indexShards = list;
        }

        private synchronized void removeActiveShard(final IndexShard indexShard) {
            final List<IndexShard> list = new ArrayList<>(indexShards);
            list.remove(indexShard);
            indexShards = list;
        }

        /**
         * Finds existing shards for the specified key.
         */
        private List<IndexShard> getExistingShards(final IndexShardKey indexShardKey) {
            // Get all index shards that are owned by this node.
            final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
            criteria.getNodeNameSet().add(nodeInfo.getThisNodeName());
            criteria.getIndexUuidSet().add(indexShardKey.getIndexUuid());
            criteria.getPartition().setString(indexShardKey.getPartition().getLabel());

            final List<IndexShard> indexShards = new ArrayList<>();
            final ResultPage<IndexShard> indexShardResultPage = indexShardDao.find(criteria);
            LOGGER.debug(() -> LogUtil.message(
                    "getExistingShards(), found {} un-filtered shards, maxDocsPerShard: {}",
                    NullSafe.getOrElse(indexShardResultPage, ResultPage::size, 0),
                    maxDocsPerShard));
            for (final IndexShard indexShard : indexShardResultPage.getValues()) {
                // Look for non deleted, non-full, non-corrupt index shards.
                final IndexShardStatus status = indexShard.getStatus();
                if (status != null
                    && REQUIRED_SHARD_STATES.contains(status)
                    && indexShard.getDocumentCount() < maxDocsPerShard) {
                    indexShards.add(indexShard);
                } else {
                    LOGGER.debug(() -> LogUtil.message("Ignoring shard {} with status: {}, docCount: {}",
                            indexShard.getId(), status, indexShard.getDocumentCount()));
                }
            }
            LOGGER.debug(() -> LogUtil.message(
                    "getExistingShards(), indexShards size: {}", NullSafe.size(indexShards)));
            return indexShards;
        }

        /**
         * Creates a new index shard writer for the specified key and opens a writer for it.
         */
        private IndexShard createNewShard(final IndexShardKey indexShardKey) {
            final String thisNodeName = nodeInfo.getThisNodeName();
            LOGGER.debug("Creating shard for key {} on {}", indexShardKey, thisNodeName);
            return indexShardCreator.createIndexShard(indexShardKey, thisNodeName);
        }
    }
}
