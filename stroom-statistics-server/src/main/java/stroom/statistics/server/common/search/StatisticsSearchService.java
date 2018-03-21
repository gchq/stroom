package stroom.statistics.server.common.search;

import io.reactivex.Flowable;
import stroom.dashboard.expression.FieldIndexMap;
import stroom.statistics.common.FindEventCriteria;
import stroom.statistics.shared.StatisticStoreEntity;

public interface StatisticsSearchService {

    Flowable<String[]> search(final StatisticStoreEntity statisticStoreEntity,
                              final FindEventCriteria criteria,
                              final FieldIndexMap fieldIndexMap,
                              final StatStoreSearchTask task);
}
