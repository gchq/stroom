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

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import stroom.util.shared.SharedObject;

/**
 * A set that on calling iterator you get back the items in a different round
 * robin order.
 *
 * @param <E>
 *            type of set
 */
public class RoundRobinSet<E extends SharedObject> extends AbstractCollection<E>implements Set<E> {
    private ArrayList<E> realList = new ArrayList<E>();
    private int startCursor = 0;

    /**
     * @return if added
     *
     * @see java.util.AbstractCollection#add(java.lang.Object)
     */
    @Override
    public boolean add(final E o) {
        return realList.add(o);
    }

    /**
     * @see java.util.AbstractCollection#iterator()
     */
    @Override
    public Iterator<E> iterator() {
        return getIterator(true);
    }

    private synchronized Iterator<E> getIterator(final boolean doMove) {
        final int currentStartCursor = startCursor;
        Iterator<E> rtn = new RoundRobinIterator<E>(realList, startCursor) {
            private int cursor = currentStartCursor;

            @Override
            public int getCursor() {
                return cursor;
            }

            @Override
            public void setCursor(final int newValue) {
                cursor = newValue;
            }
        };
        if (doMove) {
            startCursor++;
            if (startCursor >= size()) {
                startCursor = 0;
            }
        }
        return rtn;
    }

    /**
     * @see java.util.AbstractCollection#size()
     */
    @Override
    public int size() {
        return realList.size();
    }

    @Override
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("[");

        Iterator<E> i = getIterator(false);
        boolean hasNext = i.hasNext();
        while (hasNext) {
            E o = i.next();
            buf.append(String.valueOf(o));
            hasNext = i.hasNext();
            if (hasNext) {
                buf.append(", ");
            }
        }

        buf.append("]");
        return buf.toString();
    }

}
