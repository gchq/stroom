package stroom.index.impl;

import stroom.index.shared.IndexShard;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.task.api.TaskContext;

import java.util.concurrent.atomic.LongAdder;

public interface LuceneShardSearcher {

    void searchShard(final TaskContext taskContext,
                     final IndexShard indexShard,
                     final String[] storedFieldNames,
                     final LongAdder hitCount,
                     final int shardNumber,
                     final int shardTotal,
                     final ValuesConsumer valuesConsumer,
                     final ErrorConsumer errorConsumer);
}
