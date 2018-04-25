package stroom.refdata.saxevents;

import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.WeakHashMap;

class OnHeapKeyedInternPool<V extends KeyedInternPool.AbstractKeyedInternPoolValue>
        implements KeyedInternPool<V> {

    public static final long UNIQUE_ID = 0;

    // Entries will be removed when no more references exist to the key
//    private final Map<Key, V> map = Collections.synchronizedMap(new WeakHashMap<>());
    private final WeakHashMap<Key, WeakReference<V>> map = new WeakHashMap<>();

    @Override
    public ValueSupplier<V> intern(final V value) {
        return null;
    }

    synchronized public Key put(final V value) {
//        Key key = new Key(value.hashCode(), UNIQUE_ID);
//
//        WeakReference<V> valueInMap = map.computeIfAbsent(key, k ->
//                new WeakReference<>(value));
        return null;
    }

    public Optional<V> get(final Key key) {
        return Optional.empty();
    }

    @Override
    public void clear() {

    }

    @Override
    public long size() {
        return 0;
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
