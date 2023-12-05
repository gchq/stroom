package stroom.proxy.repo.dao;

import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.queue.Batch;
import stroom.util.shared.Flushable;

import org.jooq.DSLContext;

import java.util.List;
import java.util.concurrent.TimeUnit;

public interface SourceDao extends Flushable {


    long getMaxFileStoreId();

    void clear();

    /**
     * Add a new source to the database.
     * <p>
     * If a new source is successfully added then return it in the optional result. If a source for the supplied path
     * already exists then return an empty optional.
     * <p>
     * This method is synchronized to cope with sources being added via receipt and repo scanning at the same time.
     *
     * @param fileStoreId The file store id of the source to add.
     * @param feedName    The feed name associated with the source.
     * @param typeName    The type name associated with the source.
     */
    void addSource(long fileStoreId,
                   String feedName,
                   String typeName);

    Batch<RepoSource> getNewSources();

    Batch<RepoSource> getNewSources(long timeout,
                                    TimeUnit timeUnit);

    int countSources();

    /**
     * Mark sources as being ready for deletion.
     */
    void markDeletableSources();

    /**
     * Get a list of sources that have either been successfully forwarded to all destinations or have been examined and
     * the examined source entries and items have since been deleted, i.e. were no longer needed as aggregate forwarding
     * completed for all entries.
     *
     * @return A list of sources that are ready to be deleted.
     */
    List<RepoSource> getDeletableSources(long minSourceId,
                                         int limit);

    /**
     * Used for testing.
     *
     * @return
     */
    int countDeletableSources();

    /**
     * Delete sources that have already been marked for deletion.
     *
     * @return The number of rows changed.
     */
    int deleteSources();

    void resetExamined();

    void setSourceExamined(long sourceId,
                           boolean examined,
                           int itemCount);

    void setSourceExamined(DSLContext context,
                           long sourceId,
                           boolean examined,
                           int itemCount);


    void clearQueue();
}
