package stroom.index.lucene553;

import stroom.expression.api.DateTimeSettings;
import stroom.index.impl.HighlightProvider;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexSystemInfoProvider;
import stroom.index.impl.LuceneProvider;
import stroom.index.impl.LuceneShardSearcher;
import stroom.index.shared.IndexFieldsMap;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.LuceneVersion;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.QueryKey;
import stroom.search.extraction.IndexStructure;

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
    public LuceneShardSearcher createLuceneShardSearcher(final ExpressionOperator expression,
                                                         final IndexFieldsMap indexFieldsMap,
                                                         final DateTimeSettings dateTimeSettings,
                                                         final QueryKey queryKey) {
        return shardSearcherFactory.create(
                expression,
                indexFieldsMap,
                dateTimeSettings,
                queryKey);
    }

    @Override
    public HighlightProvider createHighlightProvider() {
        return highlightProvider;
    }

    @Override
    public IndexShardWriter createIndexShardWriter(final IndexStructure indexStructure,
                                                   final IndexShardKey indexShardKey,
                                                   final IndexShard indexShard) {
        return indexShardWriterFactory.create(
                indexStructure,
                indexShardKey,
                indexShard);
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
