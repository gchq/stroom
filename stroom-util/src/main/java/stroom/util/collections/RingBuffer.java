/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.util.shared.NullSafe;

import com.google.common.collect.ForwardingQueue;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;

public class RingBuffer<E> extends ForwardingQueue<E> {

    private final int maxSize;
    private final ArrayDeque<E> delegate;

    public RingBuffer(final int mazSize) {
        this.delegate = new ArrayDeque<>(mazSize);
        this.maxSize = mazSize;
    }

    public static <E> RingBuffer<E> create(final int maxSize) {
        return new RingBuffer<>(maxSize);
    }

    @Override
    protected Queue<E> delegate() {
        return delegate;
    }

    @Override
    public boolean offer(final E e) {
        return add(e);
    }

    @Override
    public boolean add(final E e) {
        Objects.requireNonNull(e);
        if (maxSize == 0) {
            return true;
        }
        if (size() == maxSize) {
            delegate.remove();
        }
        delegate.add(e);
        return true;
    }

    @Override
    public boolean addAll(final Collection<? extends E> collection) {
        Objects.requireNonNull(collection);
        if (!collection.isEmpty()) {
            for (final E elm : collection) {
                add(elm);
            }
            return true;
        } else {
            return false;
        }
    }

    public Iterator<E> descendingIterator() {
        return delegate.descendingIterator();
    }

    /**
     * @param items
     * @return
     */
    public boolean containsTailElements(final E... items) {
        if (NullSafe.isEmptyArray(items)) {
            return false;
        } else if (NullSafe.isEmptyCollection(delegate)) {
            return false;
        } else if (items.length > delegate.size()) {
            return false;
        } else {
            int arrIdx = items.length - 1;
            final Iterator<E> iterator = delegate.descendingIterator();
            boolean isMatch = true;
            while (iterator.hasNext() && arrIdx >= 0) {
                final E elm = iterator.next();
                if (!Objects.equals(items[arrIdx], elm)) {
                    isMatch = false;
                    break;
                }
                arrIdx--;
            }
            return isMatch;
        }
    }
}
