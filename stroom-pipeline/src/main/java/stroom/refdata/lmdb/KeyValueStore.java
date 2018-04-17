package stroom.refdata.lmdb;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface KeyValueStore extends AutoCloseable {

    void put(final String key, final String value);

    void putBatch(final List<Map.Entry<String, String>> entries);

    Optional<String> get(final String key);

    Optional<String> getWithTxn(final String key);

    void clear();

}
