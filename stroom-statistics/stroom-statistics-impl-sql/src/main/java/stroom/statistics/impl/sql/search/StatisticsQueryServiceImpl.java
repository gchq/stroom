package stroom.statistics.impl.sql.search;

import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.security.api.SecurityContext;
import stroom.statistics.impl.sql.SQLStatisticCacheImpl;
import stroom.statistics.impl.sql.StatisticsQueryService;
import stroom.statistics.impl.sql.entity.StatisticsDataSourceProvider;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;

@SuppressWarnings("unused") //used by DI
public class StatisticsQueryServiceImpl implements StatisticsQueryService {

    public static final long PROCESS_PAYLOAD_INTERVAL_SECS = 1L;
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SQLStatisticCacheImpl.class);
    private final StatisticsDataSourceProvider statisticsDataSourceProvider;
    private final SearchResponseCreatorManager searchResponseCreatorManager;
    private final SqlStatisticStoreFactory storeFactory;
    private final SecurityContext securityContext;

    @Inject
    public StatisticsQueryServiceImpl(final StatisticsDataSourceProvider statisticsDataSourceProvider,
                                      final SearchResponseCreatorManager searchResponseCreatorManager,
                                      final SqlStatisticStoreFactory storeFactory,
                                      final SecurityContext securityContext) {
        this.statisticsDataSourceProvider = statisticsDataSourceProvider;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.storeFactory = storeFactory;
        this.securityContext = securityContext;
    }


    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            LOGGER.debug(() -> "getDataSource called for docRef " + docRef);
            return statisticsDataSourceProvider.getDataSource(docRef);
        });
    }

    @Override
    public SearchResponse search(final SearchRequest searchRequest) {
        return securityContext.useAsReadResult(() -> searchResponseCreatorManager.search(storeFactory, searchRequest));
    }

    @Override
    public Boolean keepAlive(final QueryKey queryKey) {
        return searchResponseCreatorManager.keepAlive(queryKey);
    }

    @Override
    public Boolean destroy(final QueryKey queryKey) {
        return searchResponseCreatorManager.remove(queryKey);
    }

    @Override
    public String getType() {
        return StatisticStoreDoc.DOCUMENT_TYPE;
    }
}
