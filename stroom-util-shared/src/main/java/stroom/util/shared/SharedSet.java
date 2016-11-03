/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.shared;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class SharedSet<E extends SharedObject> implements Set<E>, SharedObject {
    private static final long serialVersionUID = 3481789353378918333L;

    private Set<E> set;

    public SharedSet() {
        this.set = new HashSet<E>();
    }

    public SharedSet(final int initialCapacity) {
        this.set = new HashSet<E>(initialCapacity);
    }

    public SharedSet(final Set<E> set) {
        this.set = set;
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
        return set.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return set.iterator();
    }

    @Override
    public Object[] toArray() {
        return set.toArray();
    }

    @Override
    public <T> T[] toArray(final T[] a) {
        return set.toArray(a);
    }

    @Override
    public boolean add(final E e) {
        return set.add(e);
    }

    @Override
    public boolean remove(final Object o) {
        return set.remove(o);
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        return set.containsAll(c);
    }

    @Override
    public boolean addAll(final Collection<? extends E> c) {
        return set.addAll(c);
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        return set.retainAll(c);
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        return set.removeAll(c);
    }

    @Override
    public void clear() {
        set.clear();
    }

    @Override
    public int hashCode() {
        return set.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof SharedSet)) {
            return false;
        }

        final SharedSet<?> s = (SharedSet<?>) obj;
        return set.equals(s.set);
    }

    @Override
    public String toString() {
        return set.toString();
    }
}
