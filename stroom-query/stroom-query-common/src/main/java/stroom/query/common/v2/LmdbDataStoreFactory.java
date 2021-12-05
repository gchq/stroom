package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.lmdb.LmdbEnvFactory;
import stroom.query.api.v2.TableSettings;

import java.util.Map;
import javax.inject.Inject;

public class LmdbDataStoreFactory implements DataStoreFactory {

    private final LmdbEnvFactory lmdbEnvFactory;
    private final ResultStoreConfig resultStoreConfig;

    @Inject
    public LmdbDataStoreFactory(final LmdbEnvFactory lmdbEnvFactory,
                                final ResultStoreConfig resultStoreConfig) {
        this.lmdbEnvFactory = lmdbEnvFactory;
        this.resultStoreConfig = resultStoreConfig;
    }

    @Override
    public DataStore create(final String queryKey,
                            final String componentId,
                            final TableSettings tableSettings,
                            final FieldIndex fieldIndex,
                            final Map<String, String> paramMap,
                            final Sizes maxResults,
                            final Sizes storeSize) {

        if (!resultStoreConfig.isOffHeapResults()) {
            return new MapDataStore(
                    tableSettings,
                    fieldIndex,
                    paramMap,
                    maxResults,
                    storeSize);
        } else {
            return new LmdbDataStore(
                    lmdbEnvFactory,
                    resultStoreConfig,
                    queryKey,
                    componentId,
                    tableSettings,
                    fieldIndex,
                    paramMap,
                    maxResults,
                    storeSize);
        }
    }
}
