package stroom.query.common.v2;

public interface DataStore {
    Items get();

    Items get(final RawKey key);

    long getSize();

    long getTotalSize();
}
