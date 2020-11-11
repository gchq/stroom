package stroom.statistics.impl.sql.search;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.Receiver;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.task.api.TaskContext;

public interface StatisticsSearchService {
    void search(final TaskContext parentTaskContext,
                final StatisticStoreDoc statisticStoreEntity,
                final FindEventCriteria criteria,
                final FieldIndex fieldIndex,
                final Receiver receiver,
                final CompletionState completionState);
}
