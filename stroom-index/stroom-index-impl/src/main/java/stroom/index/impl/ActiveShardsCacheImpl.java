/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.index.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.LuceneIndexDoc;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

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
}
