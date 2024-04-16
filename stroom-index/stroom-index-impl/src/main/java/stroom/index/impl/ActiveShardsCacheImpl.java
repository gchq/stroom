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
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public class ActiveShardsCacheImpl implements ActiveShardsCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ActiveShardsCacheImpl.class);

    private static final String CACHE_NAME = "Active Index Shard Cache";

    private final NodeInfo nodeInfo;
    private final IndexShardWriterCache indexShardWriterCache;
    private final IndexShardService indexShardService;
    private final LuceneIndexDocCache luceneIndexDocCache;
    private final SecurityContext securityContext;


    private final LoadingStroomCache<IndexShardKey, ActiveShards> cache;

    @Inject
    ActiveShardsCacheImpl(final IndexShardWriterCache indexShardWriterCache,
                          final NodeInfo nodeInfo,
                          final IndexShardService indexShardService,
                          final LuceneIndexDocCache luceneIndexDocCache,
                          final CacheManager cacheManager,
                          final Provider<IndexWriterConfig> indexWriterConfigProvider,
                          final SecurityContext securityContext) {
        this.indexShardWriterCache = indexShardWriterCache;
        this.nodeInfo = nodeInfo;
        this.indexShardService = indexShardService;
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
                    new DocRef(LuceneIndexDoc.DOCUMENT_TYPE, indexShardKey.getIndexUuid()));
            if (luceneIndexDoc == null) {
                throw new IndexException("Unable to find index with UUID: " + indexShardKey.getIndexUuid());
            }

            return new ActiveShards(
                    nodeInfo,
                    indexShardWriterCache,
                    indexShardService,
                    luceneIndexDoc.getShardsPerPartition(),
                    luceneIndexDoc.getMaxDocsPerShard(),
                    indexShardKey);
        });
    }

    public static class ActiveShards {

        private static final int MAX_ATTEMPTS = 10000;

        private final NodeInfo nodeInfo;
        private final IndexShardWriterCache indexShardWriterCache;
        private final IndexShardService indexShardService;
        private final IndexShardKey indexShardKey;
        private final ReentrantLock lock = new ReentrantLock();
        private final Integer shardsPerPartition;
        private final Integer maxDocsPerShard;
        private final AtomicInteger sequence = new AtomicInteger();
        private volatile List<IndexShard> indexShards;

        public ActiveShards(final NodeInfo nodeInfo,
                            final IndexShardWriterCache indexShardWriterCache,
                            final IndexShardService indexShardService,
                            final Integer shardsPerPartition,
                            final Integer maxDocsPerShard,
                            final IndexShardKey indexShardKey) {
            this.nodeInfo = nodeInfo;
            this.indexShardWriterCache = indexShardWriterCache;
            this.indexShardService = indexShardService;
            this.shardsPerPartition = shardsPerPartition;
            this.maxDocsPerShard = maxDocsPerShard;
            this.indexShardKey = indexShardKey;

            indexShards = new ArrayList<>();
            indexShards.addAll(getExistingShards(indexShardKey));
            ensureShards();
        }

        public void addDocument(final IndexDocument document) {
            // Try and add the document silently without locking.
            boolean success = addDocument(document, false);

            // Attempt under lock if we failed to add.
            if (!success) {
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
            final IndexShardWriter indexShardWriter = indexShardWriterCache.getOrOpenWriter(indexShard.getId());
            try {
                indexShardWriter.addDocument(document);
                return true;

            } catch (final ShardFullException e) {
                removeActiveShard(indexShard);
                indexShardWriterCache.close(indexShardWriter);

            } catch (final IndexException | IllegalArgumentException e) {
                LOGGER.trace(e::getMessage, e);

            } catch (final RuntimeException e) {
                if (throwException) {
                    LOGGER.error(e::getMessage, e);
                    throw e;
                } else {
                    LOGGER.debug(e::getMessage, e);
                }
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
            List<IndexShard> list = indexShards;
            if (list.size() < shardsPerPartition) {
                list = new ArrayList<>(list);
                for (int i = list.size(); i < shardsPerPartition; i++) {
                    list.add(createNewShard(indexShardKey));
                }
            }
            indexShards = list;
            return list;
        }

        private synchronized void addActiveShard(final IndexShardKey indexShardKey) {
            final IndexShard indexShard = createNewShard(indexShardKey);
            indexShards.add(indexShard);
        }

        private synchronized void removeActiveShard(final IndexShard indexShard) {
            indexShards.remove(indexShard);
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
            final ResultPage<IndexShard> indexShardResultPage = indexShardService.find(criteria);
            for (final IndexShard indexShard : indexShardResultPage.getValues()) {
                // Look for non deleted, non-full, non-corrupt index shards.
                if (IndexShardStatus.CLOSED.equals(indexShard.getStatus()) &&
                        indexShard.getDocumentCount() < maxDocsPerShard) {
                    indexShards.add(indexShard);
                }
            }
            return indexShards;
        }

        /**
         * Creates a new index shard writer for the specified key and opens a writer for it.
         */
        private IndexShard createNewShard(final IndexShardKey indexShardKey) {
            return indexShardService.createIndexShard(indexShardKey, nodeInfo.getThisNodeName());
        }
    }
}
