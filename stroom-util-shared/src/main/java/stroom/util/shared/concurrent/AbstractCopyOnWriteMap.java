/*
 * This class was copied from
 * https://bitbucket.org/atlassian/atlassian-util-concurrent/src/master/
 * Modified to use synchronized instead of ReentrantLock so it is GWT safe
 */

/*
 * Copyright 2008 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.shared.concurrent;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for COW {@link Map} implementations that delegate to an
 * internal map.
 *
 * @param <K> The key type
 * @param <V> The value type
 * @param <M> the internal {@link Map} or extension for things like sorted and
 *            navigable maps.
 */
abstract class AbstractCopyOnWriteMap<K, V, M
        extends Map<K, V>>
        implements ConcurrentMap<K, V>, Serializable {

    @Serial
    private static final long serialVersionUID = 4508989182041753878L;

    private volatile M delegate;

    private final View<K, V> view;

    /**
     * Create a new {@link CopyOnWriteMap} with the
     * supplied {@link java.util.Map} to initialize the values.
     *
     * @param map      the initial map to initialize with
     * @param viewType for writable or read-only key, value and entrySet views
     * @param <N>      original map type.
     */
    protected <N extends Map<? extends K, ? extends V>> AbstractCopyOnWriteMap(final N map,
                                                                               final View.Type viewType) {
        this.delegate = requireNonNull(copy(requireNonNull(map, "map")), "delegate");
        this.view = requireNonNull(viewType, "viewType").get(this);
    }

    /**
     * Copy function, implemented by sub-classes.
     *
     * @param <N> the map to copy and return.
     * @param map the initial values of the newly created map.
     * @return a new map. Will never be modified after construction.
     */
    abstract <N extends Map<? extends K, ? extends V>> M copy(N map);

    //
    // mutable operations
    //

    /**
     * Return a new copy containing no elements
     */
    public final synchronized void clear() {
        set(copy(Collections.<K, V>emptyMap()));
    }

    /**
     * {@inheritDoc}
     */
    public final synchronized V remove(final Object key) {
        // short circuit if key doesn't exist
        if (!delegate.containsKey(key)) {
            return null;
        }
        final M map = copy();
        try {
            return map.remove(key);
        } finally {
            set(map);
        }
    }

    /**
     * {@inheritDoc}
     */
    public final synchronized boolean remove(final Object key, final Object value) {
        if (delegate.containsKey(key) && equals(value, delegate.get(key))) {
            final M map = copy();
            map.remove(key);
            set(map);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Attempt to replace the value of the key with newValues so long as the key
     * is still associated with old value
     *
     * @param key      a K key to to define a new association for.
     * @param oldValue if this value is still present replace it with newValue.
     * @param newValue a V to add to the map.
     * @return a boolean if the replacement is successful
     */
    public final synchronized boolean replace(final K key, final V oldValue, final V newValue) {
        if (!delegate.containsKey(key) || !equals(oldValue, delegate.get(key))) {
            return false;
        }
        final M map = copy();
        map.put(key, newValue);
        set(map);
        return true;
    }

    /**
     * Replace the value of the key if the key is present returning the value.
     *
     * @param key   a K key.
     * @param value a V value.
     * @return a V if the key is present or null if it is not.
     */
    public final synchronized V replace(final K key, final V value) {
        if (!delegate.containsKey(key)) {
            return null;
        }
        final M map = copy();
        try {
            return map.put(key, value);
        } finally {
            set(map);
        }
    }

    /**
     * Add this key and its value to the map
     *
     * @param key   a K key.
     * @param value a V value.
     * @return a V value added.
     */
    public final synchronized V put(final K key, final V value) {
        final M map = copy();
        try {
            return map.put(key, value);
        } finally {
            set(map);
        }
    }

    /**
     * If the key is not currently contained in the map add it. Returns the
     * current value associated with the key whether or not the put succeeds.
     *
     * @param key   a K key.
     * @param value a V value.
     * @return a V associated with the key, may or may not be the input value to
     * this method.
     */
    public final synchronized V putIfAbsent(final K key, final V value) {
        if (!delegate.containsKey(key)) {
            final M map = copy();
            try {
                return map.put(key, value);
            } finally {
                set(map);
            }
        }
        return delegate.get(key);
    }

    /**
     * {@inheritDoc}
     */
    public final synchronized void putAll(final Map<? extends K, ? extends V> t) {
        final M map = copy();
        map.putAll(t);
        set(map);
    }

    /**
     * Create a copy of the underlying map.
     *
     * @return a M map.
     */
    protected synchronized M copy() {
        return copy(delegate);
    }

    /**
     * Set the contained map
     *
     * @param map a M contained by this class.
     */
    protected final void set(final M map) {
        delegate = map;
    }

    //
    // Collection views
    //

    /**
     * {@inheritDoc}
     */
    public final Set<Map.Entry<K, V>> entrySet() {
        return view.entrySet();
    }

    /**
     * {@inheritDoc}
     */
    public final Set<K> keySet() {
        return view.keySet();
    }

    /**
     * {@inheritDoc}
     */
    public final Collection<V> values() {
        return view.values();
    }

    //
    // delegate operations
    //

    /**
     * {@inheritDoc}
     */
    public final boolean containsKey(final Object key) {
        return delegate.containsKey(key);
    }

    /**
     * {@inheritDoc}
     */
    public final boolean containsValue(final Object value) {
        return delegate.containsValue(value);
    }

    /**
     * {@inheritDoc}
     */
    public final V get(final Object key) {
        return delegate.get(key);
    }

    /**
     * {@inheritDoc}
     */
    public final boolean isEmpty() {
        return delegate.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    public final int size() {
        return delegate.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean equals(final Object o) {
        return delegate.equals(o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        return delegate.hashCode();
    }

    /**
     * Return the internal delegate map.
     *
     * @return a M map.
     */
    protected final M getDelegate() {
        return delegate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return delegate.toString();
    }

    //
    // inner classes
    //


    // --------------------------------------------------------------------------------


    private class KeySet extends CollectionView<K> implements Set<K> {

        @Override
        Collection<K> getDelegate() {
            return delegate.keySet();
        }

        //
        // mutable operations
        //

        public void clear() {
            synchronized (AbstractCopyOnWriteMap.this) {
                final M map = copy();
                map.keySet().clear();
                set(map);
            }
        }

        public boolean remove(final Object o) {
            return AbstractCopyOnWriteMap.this.remove(o) != null;
        }

        public boolean removeAll(final Collection<?> c) {
            synchronized (AbstractCopyOnWriteMap.this) {
                final M map = copy();
                try {
                    return map.keySet().removeAll(c);
                } finally {
                    set(map);
                }
            }
        }

        public boolean retainAll(final Collection<?> c) {
            synchronized (AbstractCopyOnWriteMap.this) {
                final M map = copy();
                try {
                    return map.keySet().retainAll(c);
                } finally {
                    set(map);
                }
            }
        }
    }


    // --------------------------------------------------------------------------------


    private final class Values extends CollectionView<V> {

        @Override
        Collection<V> getDelegate() {
            return delegate.values();
        }

        public void clear() {
            synchronized (AbstractCopyOnWriteMap.this) {
                final M map = copy();
                map.values().clear();
                set(map);
            }
        }

        public boolean remove(final Object o) {
            synchronized (AbstractCopyOnWriteMap.this) {
                if (!contains(o)) {
                    return false;
                }
                final M map = copy();
                try {
                    return map.values().remove(o);
                } finally {
                    set(map);
                }
            }
        }

        public boolean removeAll(final Collection<?> c) {
            synchronized (AbstractCopyOnWriteMap.this) {
                final M map = copy();
                try {
                    return map.values().removeAll(c);
                } finally {
                    set(map);
                }
            }
        }

        public boolean retainAll(final Collection<?> c) {
            synchronized (AbstractCopyOnWriteMap.this) {
                final M map = copy();
                try {
                    return map.values().retainAll(c);
                } finally {
                    set(map);
                }
            }
        }
    }


    // --------------------------------------------------------------------------------


    private class EntrySet extends CollectionView<Entry<K, V>> implements Set<Map.Entry<K, V>> {

        @Override
        Collection<java.util.Map.Entry<K, V>> getDelegate() {
            return delegate.entrySet();
        }

        public void clear() {
            synchronized (AbstractCopyOnWriteMap.this) {
                final M map = copy();
                map.entrySet().clear();
                set(map);
            }
        }

        public boolean remove(final Object o) {
            synchronized (AbstractCopyOnWriteMap.this) {
                if (!contains(o)) {
                    return false;
                }
                final M map = copy();
                try {
                    return map.entrySet().remove(o);
                } finally {
                    set(map);
                }
            }
        }

        public boolean removeAll(final Collection<?> c) {
            synchronized (AbstractCopyOnWriteMap.this) {
                final M map = copy();
                try {
                    return map.entrySet().removeAll(c);
                } finally {
                    set(map);
                }
            }
        }

        public boolean retainAll(final Collection<?> c) {
            synchronized (AbstractCopyOnWriteMap.this) {
                final M map = copy();
                try {
                    return map.entrySet().retainAll(c);
                } finally {
                    set(map);
                }
            }
        }
    }


    // --------------------------------------------------------------------------------


    private static class UnmodifiableIterator<T> implements Iterator<T> {

        private final Iterator<T> delegate;

        public UnmodifiableIterator(final Iterator<T> delegate) {
            this.delegate = delegate;
        }

        public boolean hasNext() {
            return delegate.hasNext();
        }

        public T next() {
            return delegate.next();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }


    // --------------------------------------------------------------------------------


    protected abstract static class CollectionView<E> implements Collection<E> {

        abstract Collection<E> getDelegate();

        //
        // delegate operations
        //

        public final boolean contains(final Object o) {
            return getDelegate().contains(o);
        }

        public final boolean containsAll(final Collection<?> c) {
            return getDelegate().containsAll(c);
        }

        public final Iterator<E> iterator() {
            return new UnmodifiableIterator<E>(getDelegate().iterator());
        }

        public final boolean isEmpty() {
            return getDelegate().isEmpty();
        }

        public final int size() {
            return getDelegate().size();
        }

        public final Object[] toArray() {
            return getDelegate().toArray();
        }

        public final <T> T[] toArray(final T[] a) {
            return getDelegate().toArray(a);
        }

        @Override
        public int hashCode() {
            return getDelegate().hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            return getDelegate().equals(obj);
        }

        @Override
        public String toString() {
            return getDelegate().toString();
        }

        //
        // unsupported operations
        //

        public final boolean add(final E o) {
            throw new UnsupportedOperationException();
        }

        public final boolean addAll(final Collection<? extends E> c) {
            throw new UnsupportedOperationException();
        }
    }


    // --------------------------------------------------------------------------------


    private boolean equals(final Object o1, final Object o2) {
        if (o1 == null) {
            return o2 == null;
        }
        return o1.equals(o2);
    }


    // --------------------------------------------------------------------------------


    /**
     * Provides access to the views of the underlying key, value and entry
     * collections.
     */
    public abstract static class View<K, V> {

        View() {
        }

        abstract Set<K> keySet();

        abstract Set<Entry<K, V>> entrySet();

        abstract Collection<V> values();

        /**
         * The different types of {@link View} available
         */
        public enum Type {
            STABLE {
                @Override
                <K, V, M extends Map<K, V>> View<K, V> get(final AbstractCopyOnWriteMap<K, V, M> host) {
                    return host.new Immutable();
                }
            },
            LIVE {
                @Override
                <K, V, M extends Map<K, V>> View<K, V> get(final AbstractCopyOnWriteMap<K, V, M> host) {
                    return host.new Mutable();
                }
            };

            abstract <K, V, M extends Map<K, V>> View<K, V> get(AbstractCopyOnWriteMap<K, V, M> host);
        }
    }


    // --------------------------------------------------------------------------------


    final class Immutable extends View<K, V> implements Serializable {

        private static final long serialVersionUID = -4158727180429303818L;

        @Override
        public Set<K> keySet() {
            return unmodifiableSet(delegate.keySet());
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return unmodifiableSet(delegate.entrySet());
        }

        @Override
        public Collection<V> values() {
            return unmodifiableCollection(delegate.values());
        }
    }


    // --------------------------------------------------------------------------------


    final class Mutable extends View<K, V> implements Serializable {

        private static final long serialVersionUID = 1624520291194797634L;

        private final transient KeySet keySet = new KeySet();
        private final transient EntrySet entrySet = new EntrySet();
        private final transient Values values = new Values();

        @Override
        public Set<K> keySet() {
            return keySet;
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return entrySet;
        }

        @Override
        public Collection<V> values() {
            return values;
        }
    }
}
