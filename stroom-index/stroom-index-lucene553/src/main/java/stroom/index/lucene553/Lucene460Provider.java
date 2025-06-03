package stroom.index.lucene553;

import stroom.docref.DocRef;
import stroom.index.impl.HighlightProvider;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexSystemInfoProvider;
import stroom.index.impl.LuceneProvider;
import stroom.index.impl.LuceneShardSearcher;
import stroom.index.shared.IndexShard;
import stroom.index.shared.LuceneVersion;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.QueryKey;
import stroom.query.common.v2.IndexFieldCache;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class Lucene460Provider implements LuceneProvider {

    private final Lucene553HighlightProvider highlightProvider;
    private final Lucene553ShardSearcherFactory shardSearcherFactory;
    private final Lucene553SystemInfoProvider systemInfoProvider;
    private final Lucene553IndexShardWriterFactory indexShardWriterFactory;

    @Inject
    Lucene460Provider(final Lucene553HighlightProvider highlightProvider,
                      final Lucene553ShardSearcherFactory shardSearcherFactory,
                      final Lucene553SystemInfoProvider systemInfoProvider,
                      final Lucene553IndexShardWriterFactory indexShardWriterFactory) {
        this.highlightProvider = highlightProvider;
        this.shardSearcherFactory = shardSearcherFactory;
        this.systemInfoProvider = systemInfoProvider;
        this.indexShardWriterFactory = indexShardWriterFactory;
    }

    @Override
    public LuceneShardSearcher createLuceneShardSearcher(final DocRef indexDocRef,
                                                         final IndexFieldCache indexFieldCache,
                                                         final ExpressionOperator expression,
                                                         final DateTimeSettings dateTimeSettings,
                                                         final QueryKey queryKey) {
        return shardSearcherFactory.create(
                indexDocRef,
                indexFieldCache,
                expression,
                dateTimeSettings,
                queryKey);
    }

    @Override
    public HighlightProvider createHighlightProvider() {
        return highlightProvider;
    }

    @Override
    public IndexShardWriter createIndexShardWriter(final IndexShard indexShard,
                                                   final int maxDocumentCount) {
        return indexShardWriterFactory.create(
                indexShard,
                maxDocumentCount);
    }

    @Override
    public IndexSystemInfoProvider getIndexSystemInfoProvider() {
        return systemInfoProvider;
    }

    @Override
    public LuceneVersion getLuceneVersion() {
        return LuceneVersion.LUCENE_4_6_0;
    }
}
