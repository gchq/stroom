package stroom.lmdb;

import java.util.Optional;

public interface KeyValueStore {

    void put(final String key, final String value);

    Optional<String> get(final String key);
}
