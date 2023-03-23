package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.TableSettings;

import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;

public class MapDataStoreFactory implements DataStoreFactory {

    private final Provider<Serialisers> serialisersProvider;

    @Inject
    public MapDataStoreFactory(final Provider<Serialisers> serialisersProvider) {
        this.serialisersProvider = serialisersProvider;
    }

    @Override
    public DataStore create(final QueryKey queryKey,
                            final String componentId,
                            final TableSettings tableSettings,
                            final FieldIndex fieldIndex,
                            final Map<String, String> paramMap,
                            final Sizes maxResults,
                            final Sizes storeSize,
                            final DataStoreSettings dataStoreSettings,
                            final ErrorConsumer errorConsumer) {
        if (dataStoreSettings.isProducePayloads()) {
            throw new RuntimeException("MapDataStore cannot produce payloads");
        }

        return new MapDataStore(
                serialisersProvider.get(),
                tableSettings,
                fieldIndex,
                paramMap,
                maxResults,
                storeSize);
    }

    @Override
    public StoreSizeSummary getTotalSizeOnDisk() {
        // Heap based so no disk used
        // No way to get the number of stores
        return new StoreSizeSummary(0, -1);
    }
}
