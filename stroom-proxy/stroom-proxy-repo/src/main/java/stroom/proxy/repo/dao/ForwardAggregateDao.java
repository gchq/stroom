package stroom.proxy.repo.dao;

import stroom.proxy.repo.Aggregate;
import stroom.proxy.repo.ForwardAggregate;
import stroom.proxy.repo.ForwardDest;
import stroom.proxy.repo.queue.Batch;
import stroom.util.shared.Flushable;

import java.util.List;
import java.util.concurrent.TimeUnit;

public interface ForwardAggregateDao extends Flushable {

    void clear();

    /**
     * Add forward aggregates for any new dests that have been added since the application last ran.
     *
     * @param newForwardDests New dests to add forward aggregate entries for.
     */
    void addNewForwardAggregates(final List<ForwardDest> newForwardDests);

    void removeOldForwardAggregates(final List<ForwardDest> oldForwardDests);

    /**
     * Create a record of the fact that we forwarded an aggregate or at least tried to.
     */
    void createForwardAggregates(Batch<Aggregate> aggregates,
                                 List<ForwardDest> forwardDests);

    Batch<ForwardAggregate> getNewForwardAggregates();

    Batch<ForwardAggregate> getRetryForwardAggregate();


    Batch<ForwardAggregate> getNewForwardAggregates(long timeout,
                                                    TimeUnit timeUnit);

    Batch<ForwardAggregate> getRetryForwardAggregate(long timeout,
                                                     TimeUnit timeUnit);

    void update(ForwardAggregate forwardAggregate);


    int countForwardAggregates();
}
