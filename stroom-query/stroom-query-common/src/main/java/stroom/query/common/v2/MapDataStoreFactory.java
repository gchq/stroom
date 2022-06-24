package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.TableSettings;

import java.util.Map;

public class MapDataStoreFactory implements DataStoreFactory {

    @Override
    public DataStore create(final QueryKey queryKey,
                            final String componentId,
                            final TableSettings tableSettings,
                            final FieldIndex fieldIndex,
                            final Map<String, String> paramMap,
                            final Sizes maxResults,
                            final Sizes storeSize,
                            boolean producePayloads,
                            final ErrorConsumer errorConsumer) {
        if (producePayloads) {
            throw new RuntimeException("MapDataStore cannot produce payloads");
        }

        return new MapDataStore(
                tableSettings,
                fieldIndex,
                paramMap,
                maxResults,
                storeSize,
                errorConsumer);
    }

    @Override
    public StoreSizeSummary getTotalSizeOnDisk() {
        // Heap based so no disk used
        // No way to get the number of stores
        return new StoreSizeSummary(0, -1);
    }
}
