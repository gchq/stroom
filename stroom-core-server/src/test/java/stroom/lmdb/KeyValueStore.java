package stroom.lmdb;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface KeyValueStore {

    void put(final String key, final String value);

    void putBatch(final List<Map.Entry<String, String>> entries);

    Optional<String> get(final String key);

    void clear();



}
