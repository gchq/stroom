package stroom.data.store.impl.fs;

public interface FsOrphanedMetaDao {

    long getMetaIdTrackerValue();

    void updateMetaIdTracker(final long lastMinMetaId);
}
