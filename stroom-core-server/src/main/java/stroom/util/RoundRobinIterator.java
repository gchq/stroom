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

package stroom.util;

import java.util.Iterator;
import java.util.List;

/**
 * An iterator that starts at a certain position and increments a cursor.
 */
public abstract class RoundRobinIterator<T> implements Iterator<T> {
    // List to loop over
    private final List<T> list;
    private int initial;
    private T lastItem;

    public RoundRobinIterator(final List<T> list, final int start) {
        assert list.size() > start;
        if (start >= list.size()) {
            throw new IllegalStateException("Start position is greater than list size");
        }

        this.list = list;
        this.initial = start;
    }

    /**
     * @return where we are at
     */
    public abstract int getCursor();

    /**
     * @param newValue
     *            where we are at
     */
    public abstract void setCursor(int newValue);

    /**
     * @return More elements?
     *
     * @see java.util.Iterator#hasNext()
     */
    @Override
    public boolean hasNext() {
        // Wrap
        if (getCursor() >= list.size()) {
            setCursor(0);
        }

        return !((lastItem != null && getCursor() == initial) || list.size() == 0);

    }

    /**
     * @return next item.
     *
     * @see java.util.Iterator#next()
     */
    @Override
    public T next() {
        if (!hasNext()) {
            return null;
        }

        lastItem = list.get(getCursor());
        setCursor(getCursor() + 1);

        return lastItem;
    }

    /**
     * Remove item in list.
     *
     * @see java.util.Iterator#remove()
     */
    @Override
    public void remove() {
        if (lastItem != null) {
            list.remove(lastItem);
        }
    }

}
