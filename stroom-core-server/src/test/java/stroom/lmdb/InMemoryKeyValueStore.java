package stroom.lmdb;

import com.google.common.base.Preconditions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryKeyValueStore implements KeyValueStore {

    private final ConcurrentMap<String, String> map;

    public InMemoryKeyValueStore() {
        map = new ConcurrentHashMap<>();
    }

    @Override
    public void put(final String key, final String value) {
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(value);
        map.put(key, value);
    }

    @Override
    public void putBatch(final List<Map.Entry<String, String>> entries) {
        Preconditions.checkNotNull(entries);

        entries.forEach(entry -> {
            String key = entry.getKey();
            String value = entry.getValue();
            Preconditions.checkNotNull(key);
            Preconditions.checkNotNull(value);
            map.put(key, value);
        });
    }

    @Override
    public Optional<String> get(final String key) {
        Preconditions.checkNotNull(key);
        return Optional.ofNullable(map.get(key));
    }

    @Override
    public void clear() {
        map.clear();
    }
}
