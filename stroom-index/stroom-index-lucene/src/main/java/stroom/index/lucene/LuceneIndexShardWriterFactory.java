package stroom.index.lucene;

import stroom.index.impl.IndexConfig;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexShardWriter;
import stroom.index.shared.IndexShard;
import stroom.util.io.PathCreator;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

class LuceneIndexShardWriterFactory {

    private final IndexShardDao indexShardDao;
    private final Provider<IndexConfig> indexConfigProvider;
    private final PathCreator pathCreator;

    @Inject
    LuceneIndexShardWriterFactory(final IndexShardDao indexShardDao,
                                  final Provider<IndexConfig> indexConfigProvider,
                                  final PathCreator pathCreator) {
        this.indexShardDao = indexShardDao;
        this.indexConfigProvider = indexConfigProvider;
        this.pathCreator = pathCreator;
    }

    IndexShardWriter create(final IndexShard indexShard,
                            final int maxDocumentCount) {
        return new LuceneIndexShardWriter(
                indexShardDao,
                indexConfigProvider.get(),
                indexShard,
                pathCreator,
                maxDocumentCount);
    }
}
