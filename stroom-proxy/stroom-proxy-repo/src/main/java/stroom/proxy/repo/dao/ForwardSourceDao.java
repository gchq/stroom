package stroom.proxy.repo.dao;

import stroom.proxy.repo.ForwardDest;
import stroom.proxy.repo.ForwardSource;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.queue.Batch;
import stroom.util.shared.Flushable;

import java.util.List;
import java.util.concurrent.TimeUnit;

public interface ForwardSourceDao extends Flushable {


    void clear();

    /**
     * Add forward sources for any new dests that have been added since the application last ran.
     *
     * @param newForwardDests New dests to add forward aggregate entries for.
     */
    void addNewForwardSources(List<ForwardDest> newForwardDests);

    void removeOldForwardSources(List<ForwardDest> oldForwardDests);

    /**
     * Create a record of the fact that we forwarded an aggregate or at least tried to.
     */
    void createForwardSources(Batch<RepoSource> sources,
                              List<ForwardDest> forwardDests);

    Batch<ForwardSource> getNewForwardSources();

    Batch<ForwardSource> getRetryForwardSources();


    Batch<ForwardSource> getNewForwardSources(long timeout,
                                              TimeUnit timeUnit);

    Batch<ForwardSource> getRetryForwardSources(long timeout,
                                                TimeUnit timeUnit);


    void update(final ForwardSource forwardSource);

    int countForwardSource();
}
