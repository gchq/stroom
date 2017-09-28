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

package stroom.util.collections;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class TypedSet<E> {
    private final Set<E> set;

    private TypedSet(final Set<E> set) {
        this.set = set;
    }

    public static <E> TypedSet<E> fromSet(final Set<E> set) {
        return new TypedSet<>(set);
    }

    public int size() {
        return set.size();
    }

    public boolean isEmpty() {
        return set.isEmpty();
    }

    public boolean contains(final E o) {
        return set.contains(o);
    }

    public Iterator<E> iterator() {
        return set.iterator();
    }

    public E[] toArray(final E[] a) {
        return set.toArray(a);
    }

    public boolean add(final E e) {
        return set.add(e);
    }

    public boolean remove(final E o) {
        return set.remove(o);
    }

    public boolean containsAll(final Collection<E> c) {
        return set.containsAll(c);
    }

    public boolean addAll(final Collection<E> c) {
        return set.addAll(c);
    }

    public boolean retainAll(final Collection<E> c) {
        return set.retainAll(c);
    }

    public boolean removeAll(final Collection<E> c) {
        return set.removeAll(c);
    }

    public void clear() {
        set.clear();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof TypedSet) {
            return ((TypedSet<?>) obj).set.equals(set);
        }

        return set.equals(obj);
    }

    @Override
    public int hashCode() {
        return set.hashCode();
    }

    @Override
    public String toString() {
        return set.toString();
    }
}
