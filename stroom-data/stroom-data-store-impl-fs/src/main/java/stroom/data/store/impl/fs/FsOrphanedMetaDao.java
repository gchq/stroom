package stroom.data.store.impl.fs;

public interface FsOrphanedMetaDao {

    long getLastMinMetaId();

    void updateLastMinMetaId(final long lastMinMetaId);
}
