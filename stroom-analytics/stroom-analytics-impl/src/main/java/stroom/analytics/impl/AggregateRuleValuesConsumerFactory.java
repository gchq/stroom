package stroom.analytics.impl;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.query.api.v2.ParamUtil;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.ErrorConsumer;
import stroom.query.common.v2.ErrorConsumerImpl;
import stroom.query.common.v2.LmdbDataStore;
import stroom.query.common.v2.LmdbDataStoreFactory;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.SizesProvider;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AggregateRuleValuesConsumerFactory {

    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final ResultStoreManager resultStoreManager;

    private final SizesProvider sizesProvider;
    private final LmdbDataStoreFactory lmdbDataStoreFactory;

    private final Map<QueryKey, LmdbDataStore> dataStoreMap;

    @Inject
    public AggregateRuleValuesConsumerFactory(final CoprocessorsFactory coprocessorsFactory,
                                              final ResultStoreFactory resultStoreFactory,
                                              final ResultStoreManager resultStoreManager,
                                              final SizesProvider sizesProvider,
                                              final LmdbDataStoreFactory lmdbDataStoreFactory) {
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.resultStoreManager = resultStoreManager;
        this.sizesProvider = sizesProvider;
        this.lmdbDataStoreFactory = lmdbDataStoreFactory;

        dataStoreMap = new ConcurrentHashMap<>();

        // TODO : EXPOSE DATA STORE TO DATA MANAGEMENT.
    }

    public LmdbDataStore create(final SearchRequest searchRequest) {
        final QueryKey queryKey = searchRequest.getKey();

        return dataStoreMap.computeIfAbsent(queryKey, k -> {
            // Create a field index map.
            final FieldIndex fieldIndex = new FieldIndex();

            // Create a parameter map.
            final Map<String, String> paramMap = ParamUtil.createParamMap(searchRequest.getQuery().getParams());

            // Create error consumer.
            final ErrorConsumer errorConsumer = new ErrorConsumerImpl();

            String componentId = null;
            TableSettings tableSettings = null;
            for (final ResultRequest resultRequest : searchRequest.getResultRequests()) {
                if (resultRequest.getMappings() != null && resultRequest.getMappings().size() > 0) {
                    componentId = resultRequest.getComponentId();
                    tableSettings = resultRequest.getMappings().get(0);
                }
            }

            final String uuid = queryKey + "_" + componentId;
            // Make safe for the file system.
            final String subDirectory = uuid.replaceAll("[^A-Za-z0-9]", "_");
            final DataStoreSettings dataStoreSettings = DataStoreSettings.createAnalyticStoreSettings(subDirectory);

            return lmdbDataStoreFactory.createAnalyticLmdbDataStore(
                    queryKey,
                    componentId,
                    tableSettings,
                    fieldIndex,
                    paramMap,
                    dataStoreSettings,
                    errorConsumer);
        });
    }

    public Optional<LmdbDataStore> getIfPresent(final QueryKey queryKey) {
        return Optional.ofNullable(dataStoreMap.get(queryKey));
    }
}
