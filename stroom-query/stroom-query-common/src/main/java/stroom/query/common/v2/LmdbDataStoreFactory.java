package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.pipeline.refdata.util.ByteBufferPool;
import stroom.query.api.v2.TableSettings;
import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;

import javax.inject.Inject;
import java.util.Map;

public class LmdbDataStoreFactory implements DataStoreFactory {
    private final ByteBufferPool byteBufferPool;
    private final TempDirProvider tempDirProvider;
    private final LmdbConfig lmdbConfig;
    private final PathCreator pathCreator;

    @Inject
    public LmdbDataStoreFactory(final ByteBufferPool byteBufferPool,
                                final TempDirProvider tempDirProvider,
                                final LmdbConfig lmdbConfig,
                                final PathCreator pathCreator) {
        this.byteBufferPool = byteBufferPool;
        this.tempDirProvider = tempDirProvider;
        this.lmdbConfig = lmdbConfig;
        this.pathCreator = pathCreator;
    }

    public DataStore create(final TableSettings tableSettings,
                            final FieldIndex fieldIndex,
                            final Map<String, String> paramMap,
                            final Sizes maxResults,
                            final Sizes storeSize) {
        return new LmdbDataStore(
                byteBufferPool,
                tempDirProvider,
                lmdbConfig,
                pathCreator,
                tableSettings,
                fieldIndex,
                paramMap,
                maxResults,
                storeSize);
    }
}
