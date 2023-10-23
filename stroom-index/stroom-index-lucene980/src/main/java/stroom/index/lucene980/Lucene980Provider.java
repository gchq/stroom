package stroom.index.lucene980;

import stroom.expression.api.DateTimeSettings;
import stroom.index.impl.HighlightProvider;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexStructure;
import stroom.index.impl.IndexSystemInfoProvider;
import stroom.index.impl.LuceneProvider;
import stroom.index.impl.LuceneShardSearcher;
import stroom.index.shared.IndexFieldsMap;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.LuceneVersion;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.QueryKey;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class Lucene980Provider implements LuceneProvider {

    private final Lucene980HighlightProvider highlightProvider;
    private final Lucene980ShardSearcherFactory shardSearcherFactory;
    private final Lucene980SystemInfoProvider systemInfoProvider;
    private final Lucene980IndexShardWriterFactory indexShardWriterFactory;

    @Inject
    Lucene980Provider(final Lucene980HighlightProvider highlightProvider,
                      final Lucene980ShardSearcherFactory shardSearcherFactory,
                      final Lucene980SystemInfoProvider systemInfoProvider,
                      final Lucene980IndexShardWriterFactory indexShardWriterFactory) {
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
        return LuceneVersion.LUCENE_5_5_3;
    }
}
