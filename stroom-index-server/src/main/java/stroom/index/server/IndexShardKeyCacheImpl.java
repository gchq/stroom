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

package stroom.index.server;

import net.sf.ehcache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.node.server.NodeCache;
import stroom.node.shared.Node;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Profile(StroomSpringProfiles.PROD)
public class IndexShardKeyCacheImpl extends AbstractCacheBean<IndexShardKey, IndexShard> implements IndexShardKeyCache {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardKeyCacheImpl.class);
    private static final int MAX_CACHE_ENTRIES = 1000000;

    private final IndexShardService indexShardService;
    private final NodeCache nodeCache;

    @Inject
    IndexShardKeyCacheImpl(final CacheManager cacheManager, final IndexShardService indexShardService, final NodeCache nodeCache) {
        super(cacheManager, "Index Shard Key Cache", MAX_CACHE_ENTRIES);
        this.indexShardService = indexShardService;
        this.nodeCache = nodeCache;

        setMaxIdleTime(10, TimeUnit.SECONDS);
        setMaxLiveTime(1, TimeUnit.DAYS);
    }

    @Override
    public IndexShard getOrCreate(final IndexShardKey key) {
        return computeIfAbsent(key, this::create);
    }


    /**
     * Overrides method in simple pool. Will be called when an item is created
     * by the pool.
     */
    private IndexShard create(final IndexShardKey key) {
        // Try and get an existing shard.
        IndexShard indexShard = getExisting(key);
        if (indexShard == null) {
            // Create a new one
            indexShard = createNew(key);
        }

        return indexShard;
    }

    /**
     * Finds an existing shard for the specified key.
     */
    private IndexShard getExisting(final IndexShardKey key) {
        // Get all index shards that are owned by this node.
        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        criteria.getNodeIdSet().add(nodeCache.getDefaultNode());
        criteria.getFetchSet().add(Index.ENTITY_TYPE);
        criteria.getFetchSet().add(Node.ENTITY_TYPE);
        criteria.getIndexIdSet().add(key.getIndex());
        criteria.getPartition().setString(key.getPartition());
        final List<IndexShard> list = indexShardService.find(criteria);
        for (final IndexShard indexShard : list) {
            // Look for non deleted, non full, non corrupt index shards.
            if (!IndexShardStatus.CORRUPT.equals(indexShard.getStatus())
                    && !IndexShardStatus.DELETED.equals(indexShard.getStatus())
                    && indexShard.getDocumentCount() < indexShard.getIndex().getMaxDocsPerShard()) {
                return indexShard;
            }
        }

        return null;
    }

    /**
     * Creates a new shard for the specified key.
     */
    private IndexShard createNew(final IndexShardKey key) {
        return indexShardService.createIndexShard(key, nodeCache.getDefaultNode());
    }
}
