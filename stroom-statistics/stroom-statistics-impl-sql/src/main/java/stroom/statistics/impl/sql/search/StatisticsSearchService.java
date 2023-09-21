package stroom.statistics.impl.sql.search;

import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;
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
