package stroom.statistics.sql.search;

import io.reactivex.Flowable;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Val;
import stroom.statistics.shared.StatisticStoreEntity;

//TODO StatisticsDatabaseSearchService
public interface StatisticsSearchService {

    Flowable<Val[]> search(final StatisticStoreEntity statisticStoreEntity,
                           final FindEventCriteria criteria,
                           final FieldIndexMap fieldIndexMap);
}
