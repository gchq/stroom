package stroom.statistics.impl.sql.search;

import io.reactivex.Flowable;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Val;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.task.api.TaskContext;

//TODO StatisticsDatabaseSearchService
public interface StatisticsSearchService {

    Flowable<Val[]> search(final TaskContext parentTaskContext,
                           final StatisticStoreDoc statisticStoreEntity,
                           final FindEventCriteria criteria,
                           final FieldIndexMap fieldIndexMap);
}
