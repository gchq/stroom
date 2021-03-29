package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.pipeline.refdata.util.ByteBufferPool;
import stroom.query.api.v2.TableSettings;

import java.util.Map;
import javax.inject.Inject;

public class LmdbDataStoreFactory implements DataStoreFactory {

    private final LmdbEnvironment lmdbEnvironment;
    private final ByteBufferPool byteBufferPool;
    private final LmdbConfig lmdbConfig;

    @Inject
    public LmdbDataStoreFactory(final LmdbEnvironment lmdbEnvironment,
                                final ByteBufferPool byteBufferPool,
                                final LmdbConfig lmdbConfig) {
        this.lmdbEnvironment = lmdbEnvironment;
        this.byteBufferPool = byteBufferPool;
        this.lmdbConfig = lmdbConfig;
    }

    public DataStore create(final String queryKey,
                            final String componentId,
                            final TableSettings tableSettings,
                            final FieldIndex fieldIndex,
                            final Map<String, String> paramMap,
                            final Sizes maxResults,
                            final Sizes storeSize) {
        if (!lmdbConfig.isOffHeapResults()) {
            return new MapDataStore(
                    tableSettings,
                    fieldIndex,
                    paramMap,
                    maxResults,
                    storeSize);
        }

        return new LmdbDataStore(
                lmdbEnvironment,
                lmdbConfig,
                byteBufferPool,
                queryKey,
                componentId,
                tableSettings,
                fieldIndex,
                paramMap,
                maxResults,
                storeSize);
    }
}
