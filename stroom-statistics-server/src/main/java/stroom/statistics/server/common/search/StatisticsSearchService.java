package stroom.statistics.server.common.search;

import io.reactivex.Flowable;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Var;
import stroom.statistics.common.FindEventCriteria;
import stroom.statistics.shared.StatisticStoreEntity;

public interface StatisticsSearchService {
    Flowable<Var[]> search(final StatisticStoreEntity statisticStoreEntity,
                           final FindEventCriteria criteria,
                           final FieldIndexMap fieldIndexMap);
}
