package stroom.query.common.v2;

import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.TableSettings;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ref.ErrorConsumer;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Map;

public class MapDataStoreFactory implements DataStoreFactory {

    private final Provider<SearchResultStoreConfig> resultStoreConfigProvider;

    @Inject
    public MapDataStoreFactory(final Provider<SearchResultStoreConfig> resultStoreConfigProvider) {
        this.resultStoreConfigProvider = resultStoreConfigProvider;
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
        final SearchResultStoreConfig resultStoreConfig = resultStoreConfigProvider.get();
        return new MapDataStore(
                componentId,
                tableSettings,
                expressionContext,
                fieldIndex,
                paramMap,
                dataStoreSettings,
                errorConsumer,
                resultStoreConfig.getMapConfig());
    }

    @Override
    public StoreSizeSummary getTotalSizeOnDisk() {
        // Heap based so no disk used
        // No way to get the number of stores
        return new StoreSizeSummary(0, -1);
    }
}
