package stroom.index.lucene553;

import stroom.index.impl.IndexConfig;
import stroom.index.impl.IndexShardManager;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexStructure;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.util.io.PathCreator;

import javax.inject.Inject;
import javax.inject.Provider;

class Lucene553IndexShardWriterFactory {
    private final IndexShardManager indexShardManager;
    private final Provider<IndexConfig> indexConfigProvider;
    private final PathCreator pathCreator;

    @Inject
    Lucene553IndexShardWriterFactory(final IndexShardManager indexShardManager,
                                            final Provider<IndexConfig> indexConfigProvider,
                                            final PathCreator pathCreator) {
        this.indexShardManager = indexShardManager;
        this.indexConfigProvider = indexConfigProvider;
        this.pathCreator = pathCreator;
    }

    IndexShardWriter create(final IndexStructure indexStructure,
                            final IndexShardKey indexShardKey,
                            final IndexShard indexShard) {
        return new Lucene553IndexShardWriter(
                indexShardManager,
                indexConfigProvider.get(),
                indexStructure,
                indexShardKey,
                indexShard,
                pathCreator);
    }
}
