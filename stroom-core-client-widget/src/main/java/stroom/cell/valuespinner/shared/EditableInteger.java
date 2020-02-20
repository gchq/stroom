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

package stroom.cell.valuespinner.shared;

public class EditableInteger extends Number implements Comparable<EditableInteger>, Editable {
    private static final long serialVersionUID = -6502263322370395720L;

    private Integer _integer;
    private boolean editable = true;

    public EditableInteger() {
    }

    public EditableInteger(final Integer _integer) {
        this._integer = _integer;
    }

    @Override
    public boolean isEditable() {
        return editable;
    }

    @Override
    public void setEditable(final boolean editable) {
        this.editable = editable;
    }

    public Integer getInteger() {
        return _integer;
    }

    public void setInteger(final Integer _integer) {
        this._integer = _integer;
    }

    @Override
    public int intValue() {
        return _integer.intValue();
    }

    @Override
    public long longValue() {
        return _integer.longValue();
    }

    @Override
    public float floatValue() {
        return _integer.floatValue();
    }

    @Override
    public double doubleValue() {
        return _integer.doubleValue();
    }

    @Override
    public int hashCode() {
        return _integer.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj instanceof EditableInteger) {
            return ((EditableInteger) obj)._integer.equals(_integer);
        }

        return false;
    }

    @Override
    public int compareTo(final EditableInteger sharedInteger) {
        return _integer.compareTo(sharedInteger._integer);
    }

    @Override
    public String toString() {
        return String.valueOf(_integer);
    }
}