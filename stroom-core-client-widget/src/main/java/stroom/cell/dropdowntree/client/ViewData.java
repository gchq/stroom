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

package stroom.cell.dropdowntree.client;

import com.google.gwt.cell.client.ValueUpdater;

/**
 * The {@code ViewData} for this cell.
 */
public class ViewData<E> {
    /**
     * The last value that was updated.
     */
    private E lastValue;

    /**
     * The current value.
     */
    private E curValue;

    /**
     * Construct a ViewData instance containing a given value.
     *
     * @param value a String value
     */
    public ViewData(E value) {
        this.lastValue = value;
        this.curValue = value;
    }

    /**
     * Return true if the last and current values of this ViewData object are
     * equal to those of the other object.
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ViewData)) {
            return false;
        }
        final ViewData<E> vd = (ViewData<E>) other;
        return equalsOrNull(lastValue, vd.lastValue) && equalsOrNull(curValue, vd.curValue);
    }

    /**
     * Return the current value of the input element.
     *
     * @return the current value String
     */
    public E getCurrentValue() {
        return curValue;
    }

    /**
     * Set the current value.
     *
     * @param curValue the current value
     * @see #getCurrentValue()
     */
    protected void setCurrentValue(E curValue) {
        this.curValue = curValue;
    }

    /**
     * Return the last value sent to the {@link ValueUpdater}.
     *
     * @return the last value String
     */
    public E getLastValue() {
        return lastValue;
    }

    /**
     * Set the last value.
     *
     * @param lastValue the last value
     * @see #getLastValue()
     */
    protected void setLastValue(E lastValue) {
        this.lastValue = lastValue;
    }

    /**
     * Return a hash code based on the last and current values.
     */
    @Override
    public int hashCode() {
        return (lastValue + "_*!@HASH_SEPARATOR@!*_" + curValue).hashCode();
    }

    private boolean equalsOrNull(Object a, Object b) {
        return (a != null) ? a.equals(b) : ((b == null));
    }
}
