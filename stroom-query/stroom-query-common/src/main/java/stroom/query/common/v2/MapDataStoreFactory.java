package stroom.query.common.v2;

import stroom.expression.api.ExpressionContext;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.TableSettings;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ref.ErrorConsumer;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Map;

public class MapDataStoreFactory implements DataStoreFactory {

    private final Provider<Serialisers> serialisersProvider;

    @Inject
    public MapDataStoreFactory(final Provider<Serialisers> serialisersProvider) {
        this.serialisersProvider = serialisersProvider;
    }

    @Override
    public DataStore create(final ExpressionContext expressionContext,
                            final SearchRequestSource searchRequestSource,
                            final QueryKey queryKey,
                            final String componentId,
                            final TableSettings tableSettings,
                            final FieldIndex fieldIndex,
                            final Map<String, String> paramMap,
                            final DataStoreSettings dataStoreSettings,
                            final ErrorConsumer errorConsumer) {
        if (dataStoreSettings.isProducePayloads()) {
            throw new RuntimeException("MapDataStore cannot produce payloads");
        }

        return new MapDataStore(
                serialisersProvider.get(),
                componentId,
                tableSettings,
                expressionContext,
                fieldIndex,
                paramMap,
                dataStoreSettings,
                errorConsumer);
    }

    @Override
    public StoreSizeSummary getTotalSizeOnDisk() {
        // Heap based so no disk used
        // No way to get the number of stores
        return new StoreSizeSummary(0, -1);
    }
}
