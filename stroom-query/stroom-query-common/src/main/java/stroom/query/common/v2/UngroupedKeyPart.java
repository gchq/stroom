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
import stroom.query.language.functions.ref.DataWriter;

import java.util.Objects;

class UngroupedKeyPart implements KeyPart {

    private final long sequenceNumber;

    public UngroupedKeyPart(final long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public void write(final DataWriter writer) {
        writer.writeLong(sequenceNumber);
    }

    @Override
    public boolean isGrouped() {
        return false;
    }

    @Override
    public Val[] getGroupValues() {
        throw new RuntimeException("No group values exist for ungrouped parts");
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UngroupedKeyPart that = (UngroupedKeyPart) o;
        return sequenceNumber == that.sequenceNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceNumber);
    }

    @Override
    public void append(final StringBuilder sb) {
        sb.append("~");
        sb.append(sequenceNumber);
    }

    @Override
    public String toString() {
        return "";
    }
}
