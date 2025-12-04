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

package stroom.query.common.v2;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValSerialiser;
import stroom.query.language.functions.ref.DataWriter;

import java.util.Arrays;

class GroupKeyPart implements KeyPart {

    private final Val[] groupValues;

    public GroupKeyPart(final Val[] groupValues) {
        this.groupValues = groupValues;
    }

    @Override
    public void write(final DataWriter writer) {
        ValSerialiser.writeArray(writer, groupValues);
    }

    @Override
    public boolean isGrouped() {
        return true;
    }

    @Override
    public Val[] getGroupValues() {
        return groupValues;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GroupKeyPart that = (GroupKeyPart) o;
        return Arrays.equals(groupValues, that.groupValues);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(groupValues);
    }

    @Override
    public void append(final StringBuilder sb) {
        for (int i = 0; i < groupValues.length; i++) {
            final Val val = groupValues[i];
            if (i > 0) {
                sb.append("|");
            }
            if (val != null) {
                sb.append(val);
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb);
        return sb.toString();
    }
}
