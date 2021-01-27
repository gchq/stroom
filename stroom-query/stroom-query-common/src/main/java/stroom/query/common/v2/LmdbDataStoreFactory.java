package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.OutputFactory;
import stroom.query.api.v2.TableSettings;

import javax.inject.Inject;
import java.util.Map;

public class LmdbDataStoreFactory implements DataStoreFactory {
    private final LmdbEnvironment lmdbEnvironment;
    private final OutputFactory outputFactory;
    private final LmdbConfig lmdbConfig;

    @Inject
    public LmdbDataStoreFactory(final LmdbEnvironment lmdbEnvironment,
                                final OutputFactory outputFactory,
                                final LmdbConfig lmdbConfig) {
        this.lmdbEnvironment = lmdbEnvironment;
        this.outputFactory = outputFactory;
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
                    storeSize,
                    outputFactory);
        }

        return new LmdbDataStore(
                lmdbEnvironment,
                lmdbConfig,
                queryKey,
                componentId,
                tableSettings,
                fieldIndex,
                paramMap,
                maxResults,
                storeSize,
                outputFactory);
    }
}
