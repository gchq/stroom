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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class SharedList<E extends SharedObject> implements List<E>, SharedObject {
    private static final long serialVersionUID = 3481789353378918333L;

    private List<E> list;

    public SharedList() {
        this.list = new ArrayList<E>();
    }

    public SharedList(final int initialCapacity) {
        this.list = new ArrayList<E>(initialCapacity);
    }

    public SharedList(final List<E> list) {
        this.list = list;
    }

    public SharedList(final Collection<? extends E> c) {
        list = new ArrayList<E>(c);
    }

    public static SharedList<SharedString> convert(final List<String> list) {
        final SharedList<SharedString> newList = new SharedList<SharedString>(list.size());
        for (final String string : list) {
            newList.add(SharedString.wrap(string));
        }
        return newList;
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
        return list.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return list.iterator();
    }

    @Override
    public Object[] toArray() {
        return list.toArray();
    }

    @Override
    public <T> T[] toArray(final T[] a) {
        return list.toArray(a);
    }

    @Override
    public boolean add(final E e) {
        return list.add(e);
    }

    @Override
    public boolean remove(final Object o) {
        return list.remove(o);
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        return list.containsAll(c);
    }

    @Override
    public boolean addAll(final Collection<? extends E> c) {
        return list.addAll(c);
    }

    @Override
    public boolean addAll(final int index, final Collection<? extends E> c) {
        return list.addAll(index, c);
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        return list.removeAll(c);
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        return list.retainAll(c);
    }

    @Override
    public void clear() {
        list.clear();
    }

    @Override
    public E get(final int index) {
        return list.get(index);
    }

    @Override
    public E set(final int index, final E element) {
        return list.set(index, element);
    }

    @Override
    public void add(final int index, final E element) {
        list.add(index, element);
    }

    @Override
    public E remove(final int index) {
        return list.remove(index);
    }

    @Override
    public int indexOf(final Object o) {
        return list.indexOf(o);
    }

    @Override
    public int lastIndexOf(final Object o) {
        return list.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return list.listIterator();
    }

    @Override
    public ListIterator<E> listIterator(final int index) {
        return list.listIterator(index);
    }

    @Override
    public List<E> subList(final int fromIndex, final int toIndex) {
        return list.subList(fromIndex, toIndex);
    }

    @Override
    final public int hashCode() {
        return list.hashCode();
    }

    @Override
    final public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof SharedList)) {
            return false;
        }

        final SharedList<?> l = (SharedList<?>) obj;
        return list.equals(l.list);
    }

    @Override
    public String toString() {
        return list.toString();
    }

    /**
     * Added to ensure list is not made final which would break GWT
     * serialisation.
     */
    public void setList(final List<E> list) {
        this.list = list;
    }
}
