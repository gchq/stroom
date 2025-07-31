package stroom.index.mock;

import stroom.docref.DocRef;
import stroom.index.impl.ActiveShards;
import stroom.index.impl.ActiveShardsCache;
import stroom.index.impl.IndexShardCreator;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.LuceneIndexDocCache;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.LuceneIndexDoc;
import stroom.node.api.NodeInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MockActiveShardsCache implements ActiveShardsCache {

    private final NodeInfo nodeInfo;
    private final IndexShardWriterCache indexShardWriterCache;
    private final IndexShardDao indexShardDao;
    private final IndexShardCreator indexShardCreator;
    private final LuceneIndexDocCache luceneIndexDocCache;

    private final Map<IndexShardKey, ActiveShards> map = new ConcurrentHashMap<>();

    public MockActiveShardsCache(final NodeInfo nodeInfo,
                                 final IndexShardWriterCache indexShardWriterCache,
                                 final IndexShardDao indexShardDao,
                                 final IndexShardCreator indexShardCreator,
                                 final LuceneIndexDocCache luceneIndexDocCache) {
        this.nodeInfo = nodeInfo;
        this.indexShardWriterCache = indexShardWriterCache;
        this.indexShardDao = indexShardDao;
        this.indexShardCreator = indexShardCreator;
        this.luceneIndexDocCache = luceneIndexDocCache;
    }

    @Override
    public ActiveShards get(final IndexShardKey indexShardKey) {
        return map.computeIfAbsent(indexShardKey, k -> {
            // Get the index fields.
            final LuceneIndexDoc luceneIndexDoc = luceneIndexDocCache.get(
                    new DocRef(LuceneIndexDoc.TYPE, indexShardKey.getIndexUuid()));
            if (luceneIndexDoc == null) {
                throw new IndexException("Unable to find index with UUID: " + indexShardKey.getIndexUuid());
            }

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
