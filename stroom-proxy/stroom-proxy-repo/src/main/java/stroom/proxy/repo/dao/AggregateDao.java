package stroom.proxy.repo.dao;

import stroom.proxy.repo.Aggregate;
import stroom.proxy.repo.RepoSourceItemRef;
import stroom.proxy.repo.queue.Batch;

import java.util.concurrent.TimeUnit;

public interface AggregateDao {

    void clear();

    /**
     * Close all aggregates that meet the supplied criteria.
     */
    long closeAggregates(int maxItemsPerAggregate,
                         long maxUncompressedByteSize,
                         long maxAggregateAgeMs,
                         long limit);

    Batch<Aggregate> getNewAggregates();

    Batch<Aggregate> getNewAggregates(long timeout,
                                      TimeUnit timeUnit);

    void addItems(Batch<RepoSourceItemRef> newSourceItems,
                  int maxItemsPerAggregate,
                  long maxUncompressedByteSize);

    int countAggregates();
}
