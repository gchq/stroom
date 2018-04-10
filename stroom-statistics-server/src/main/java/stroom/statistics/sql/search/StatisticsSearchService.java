package stroom.statistics.sql.search;

import io.reactivex.Flowable;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.statistics.shared.StatisticStoreDoc;

//TODO StatisticsDatabaseSearchService
public interface StatisticsSearchService {

    Flowable<String[]> search(final StatisticStoreDoc statisticStoreEntity,
                              final FindEventCriteria criteria,
                              final FieldIndexMap fieldIndexMap);
}
