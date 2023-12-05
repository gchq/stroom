package stroom.proxy.repo.dao;

import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.RepoSourceItem;
import stroom.proxy.repo.RepoSourceItemRef;
import stroom.proxy.repo.SourceItems;
import stroom.proxy.repo.queue.Batch;
import stroom.util.shared.Flushable;

import org.jooq.DSLContext;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public interface SourceItemDao extends Flushable {

    void clear();

    int countItems();

    void addItems(RepoSource source,
                  Collection<RepoSourceItem> items);

    Batch<RepoSourceItemRef> getNewSourceItems();

    Batch<RepoSourceItemRef> getNewSourceItems(long timeout,
                                               TimeUnit timeUnit);

    void deleteBySourceId(DSLContext context, long sourceId);

    /**
     * Fetch a list of all source entries that belong to the specified aggregate.
     *
     * @param aggregateId The id of the aggregate to get source entries for.
     * @return A list of source entries for the aggregate.
     */
    List<SourceItems> fetchSourceItemsByAggregateId(long aggregateId);
}
