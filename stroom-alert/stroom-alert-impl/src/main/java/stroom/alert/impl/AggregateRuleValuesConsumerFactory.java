package stroom.alert.impl;

import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.Lifespan;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.common.v2.ResultStoreSettings;
import stroom.util.time.StroomDuration;

import java.util.Optional;
import javax.inject.Inject;

public class AggregateRuleValuesConsumerFactory {

    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final ResultStoreManager resultStoreManager;

    @Inject
    public AggregateRuleValuesConsumerFactory(final CoprocessorsFactory coprocessorsFactory,
                                              final ResultStoreFactory resultStoreFactory,
                                              final ResultStoreManager resultStoreManager) {
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.resultStoreManager = resultStoreManager;
    }

    public Coprocessors create(final SearchRequest searchRequest) {
        final QueryKey queryKey = searchRequest.getKey();
        final Optional<ResultStore> existingResultStore = resultStoreManager.getIfPresent(queryKey);
        ResultStore resultStore;
        if (existingResultStore.isPresent()) {
            resultStore = existingResultStore.get();
        } else {
            final Coprocessors coprocessors = coprocessorsFactory.create(searchRequest);
            resultStore = resultStoreFactory.create(
                    searchRequest.getSearchRequestSource(),
                    coprocessors);
            final Lifespan lifespan = Lifespan.builder()
                    .timeToLive(StroomDuration.ofDays(100))
                    .timeToIdle(StroomDuration.ofDays(100))
                    .destroyOnTabClose(false)
                    .destroyOnWindowClose(false)
                    .build();
            final ResultStoreSettings resultStoreSettings = ResultStoreSettings.builder()
                    .storeLifespan(lifespan)
                    .searchProcessLifespan(lifespan)
                    .build();
            resultStore.setResultStoreSettings(resultStoreSettings);
            resultStoreManager.put(queryKey, resultStore);
        }
        return resultStore.getCoprocessors();
    }
}
