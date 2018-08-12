/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore;

import java.util.Objects;

public class StringValue extends RefDataValue {

    public static final int TYPE_ID = 0;

    private final String value;

    public StringValue(final String value) {
        this.value = value;
    }

    public static StringValue of(String value) {
        return new StringValue(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final StringValue that = (StringValue) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public int getValueHashCode() {
        return Objects.hash(value);
    }


    @Override
    public int getTypeId() {
        return TYPE_ID;
    }

//    @Override
//    void putValue(final ByteBuffer byteBuffer) {
//        byteBuffer.put(value.getBytes(StandardCharsets.UTF_8));
//    }

//    static StringValue fromByteBuffer(final ByteBuffer byteBuffer) {
//        return new StringValue(StandardCharsets.UTF_8.decode(byteBuffer).toString());
//    }

    @Override
    public String toString() {
        return "StringValue{" +
                "value='" + value + '\'' +
                '}';
    }
}
