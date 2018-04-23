package stroom.refdata.saxevents;

import stroom.pool.WeakPool;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

class OnHeapKeyedInternPool<V extends KeyedInternPool.AbstractKeyedInternPoolValue>
        implements KeyedInternPool<V> {

    public static final long UNIQUE_ID = 0;

    // Entries will be removed when no more references exist to the key
//    private final Map<Key, V> map = Collections.synchronizedMap(new WeakHashMap<>());
    private final WeakHashMap<Key, WeakReference<V>> map = new WeakHashMap<>();

    @Override
    synchronized public Key put(final V value) {
//        Key key = new Key(value.hashCode(), UNIQUE_ID);
//
//        WeakReference<V> valueInMap = map.computeIfAbsent(key, k ->
//                new WeakReference<>(value));
        return null;
    }

    @Override
    public V get(final Key key) {
        return null;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return super.equals(obj);
    }
}
