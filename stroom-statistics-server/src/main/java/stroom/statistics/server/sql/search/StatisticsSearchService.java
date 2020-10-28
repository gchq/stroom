package stroom.statistics.server.sql.search;

import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.query.common.v2.CompletionState;
import stroom.search.coprocessor.Receiver;
import stroom.statistics.shared.StatisticStoreEntity;

//TODO StatisticsDatabaseSearchService
public interface StatisticsSearchService {

    void search(final StatisticStoreEntity statisticStoreEntity,
                final FindEventCriteria criteria,
                final FieldIndexMap fieldIndexMap,
                final Receiver receiver,
                final CompletionState completionState);
}
