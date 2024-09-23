package stroom.index.lucene980;

import stroom.index.impl.IndexConfig;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexShardWriter;
import stroom.index.shared.IndexShard;
import stroom.util.io.PathCreator;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

class Lucene980IndexShardWriterFactory {

    private final IndexShardDao indexShardDao;
    private final Provider<IndexConfig> indexConfigProvider;
    private final PathCreator pathCreator;

    @Inject
    Lucene980IndexShardWriterFactory(final IndexShardDao indexShardDao,
                                     final Provider<IndexConfig> indexConfigProvider,
                                     final PathCreator pathCreator) {
        this.indexShardDao = indexShardDao;
        this.indexConfigProvider = indexConfigProvider;
        this.pathCreator = pathCreator;
    }

    IndexShardWriter create(final IndexShard indexShard,
                            final int maxDocumentCount) {
        return new Lucene980IndexShardWriter(
                indexShardDao,
                indexConfigProvider.get(),
                indexShard,
                pathCreator,
                maxDocumentCount);
    }
}
