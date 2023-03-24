package stroom.alert.impl;

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

        // TODO : LOAD EXISTING DATA STORES FROM DISK.

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

            final Sizes storeSizes = sizesProvider.getStoreSizes();

            String componentId = null;
            TableSettings tableSettings = null;
            for (final ResultRequest resultRequest : searchRequest.getResultRequests()) {
                if (resultRequest.getMappings() != null && resultRequest.getMappings().size() > 0) {
                    componentId = resultRequest.getComponentId();
                    tableSettings = resultRequest.getMappings().get(0);
                }
            }


            // Create a set of sizes that are the minimum values for the combination of user provided sizes for the
            // table and the default maximum sizes.
            final Sizes defaultMaxResultsSizes = sizesProvider.getDefaultMaxResultsSizes();
            final Sizes maxResults = Sizes.min(Sizes.create(tableSettings.getMaxResults()), defaultMaxResultsSizes);

            final DataStoreSettings dataStoreSettings = DataStoreSettings
                    .builder()
                    .requireStreamIdValue(false)
                    .requireEventIdValue(false)
                    .requireTimeValue(true)
                    .build();

            return lmdbDataStoreFactory.createLmdbDataStore(
                    queryKey,
                    componentId,
                    tableSettings,
                    fieldIndex,
                    paramMap,
                    maxResults,
                    storeSizes,
                    dataStoreSettings,
                    errorConsumer);
        });


//        final Optional<ResultStore> existingResultStore = resultStoreManager.getIfPresent(queryKey);
//        ResultStore resultStore;
//        if (existingResultStore.isPresent()) {
//            resultStore = existingResultStore.get();
//        } else {
//            final DataStoreSettings dataStoreSettings = DataStoreSettings
//                    .builder()
//                    .requireStreamIdValue(false)
//                    .requireEventIdValue(false)
//                    .requireTimeValue(true)
//                    .build();
//            final Coprocessors coprocessors = coprocessorsFactory.create(searchRequest, dataStoreSettings);
//            resultStore = resultStoreFactory.create(
//                    searchRequest.getSearchRequestSource(),
//                    coprocessors);
//            final Lifespan lifespan = Lifespan.builder()
//                    .timeToLive(StroomDuration.ofDays(100))
//                    .timeToIdle(StroomDuration.ofDays(100))
//                    .destroyOnTabClose(false)
//                    .destroyOnWindowClose(false)
//                    .build();
//            final ResultStoreSettings resultStoreSettings = ResultStoreSettings.builder()
//                    .storeLifespan(lifespan)
//                    .searchProcessLifespan(lifespan)
//                    .build();
//            resultStore.setResultStoreSettings(resultStoreSettings);
//            resultStoreManager.put(queryKey, resultStore);
//        }
//        return resultStore.getCoprocessors();
    }

    public Optional<LmdbDataStore> getIfPresent(final QueryKey queryKey) {
        return Optional.ofNullable(dataStoreMap.get(queryKey));
    }
}
