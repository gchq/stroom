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

package stroom.cell.valuespinner.shared;

public class EditableLong extends Number implements Comparable<EditableLong>, Editable {

    private static final long serialVersionUID = 2999109513859666073L;

    private Long value;
    private boolean editable = true;

    public EditableLong() {
    }

    public EditableLong(final Long value) {
        this.value = value;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(final boolean editable) {
        this.editable = editable;
    }

    public Long getLong() {
        return value;
    }

    public void setLong(final Long value) {
        this.value = value;
    }

    @Override
    public int intValue() {
        return value.intValue();
    }

    @Override
    public long longValue() {
        return value.longValue();
    }

    @Override
    public float floatValue() {
        return value.floatValue();
    }

    @Override
    public double doubleValue() {
        return value.doubleValue();
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj instanceof EditableLong) {
            return ((EditableLong) obj).value.equals(value);
        }

        return false;
    }

    @Override
    public int compareTo(final EditableLong sharedLong) {
        return value.compareTo(sharedLong.value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
