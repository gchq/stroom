package stroom.statistics.impl.sql.search;

import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.query.common.v2.CompletionState;
import stroom.search.coprocessor.Receiver;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.task.api.TaskContext;

public interface StatisticsSearchService {
    void search(final TaskContext parentTaskContext,
                final StatisticStoreDoc statisticStoreEntity,
                final FindEventCriteria criteria,
                final FieldIndexMap fieldIndexMap,
                final Receiver receiver,
                final CompletionState completionState);
}
