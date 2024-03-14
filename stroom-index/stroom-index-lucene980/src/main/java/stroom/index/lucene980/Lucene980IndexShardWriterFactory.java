package stroom.index.lucene980;

import stroom.index.impl.IndexConfig;
import stroom.index.impl.IndexShardManager;
import stroom.index.impl.IndexShardWriter;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.util.io.PathCreator;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

class Lucene980IndexShardWriterFactory {
    private final IndexShardManager indexShardManager;
    private final Provider<IndexConfig> indexConfigProvider;
    private final PathCreator pathCreator;

    @Inject
    Lucene980IndexShardWriterFactory(final IndexShardManager indexShardManager,
                                            final Provider<IndexConfig> indexConfigProvider,
                                            final PathCreator pathCreator) {
        this.indexShardManager = indexShardManager;
        this.indexConfigProvider = indexConfigProvider;
        this.pathCreator = pathCreator;
    }

    IndexShardWriter create(final IndexShardKey indexShardKey,
                            final IndexShard indexShard,
                            final int maxDocumentCount) {
        return new Lucene980IndexShardWriter(
                indexShardManager,
                indexConfigProvider.get(),
                indexShardKey,
                indexShard,
                pathCreator,
                maxDocumentCount);
    }
}
