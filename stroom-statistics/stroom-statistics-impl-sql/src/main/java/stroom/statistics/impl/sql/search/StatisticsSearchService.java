package stroom.statistics.impl.sql.search;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.query.common.v2.ErrorConsumer;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.task.api.TaskContext;

public interface StatisticsSearchService {

    void search(final TaskContext parentTaskContext,
                final StatisticStoreDoc statisticStoreEntity,
                final FindEventCriteria criteria,
                final FieldIndex fieldIndex,
                final ValuesConsumer valuesConsumer,
                final ErrorConsumer errorConsumer);
}
