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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public enum ByteSize {
    BYTE(1, "B", "bytes"),
    KILOBYTE(1024, "KB", "Kilobytes"),
    MEGABYTE(1024 * 1024, "MB", "Megabytes"),
    GIGABYTE(1024 * 1024 * 1024, "GB", "Gigabytes"),
    TERABYTE(1024 * 1024 * 1024 * 1024, "TB", "Terabytes"),
    PETABYTE(1024 * 1024 * 1024 * 1024 * 1024, "PB", "Petabytes");

    private final int bytes;
    private final String shortName;
    private final String longName;

    private static final Map<CaseInsensitiveString, ByteSize> shortNameToEnumMap = new HashMap<>();
    private static final Map<Integer, ByteSize> intToEnumMap = new HashMap<>();

    static {
        for (ByteSize byteSize : ByteSize.values()) {
            shortNameToEnumMap.put(CaseInsensitiveString.fromString(byteSize.shortName), byteSize);
            intToEnumMap.put(byteSize.intBytes(), byteSize);
        }
    }

    private ByteSize(final int bytes, final String shortName, final String longName) {
        this.bytes = bytes;
        this.shortName = shortName;
        this.longName = longName;
    }

    public long longBytes() {
        return (long) bytes;
    }

    public int intBytes() {
        return bytes;
    }

    public String getShortName() {
        return shortName;
    }

    public String getLongName() {
        return longName;
    }

    public static ByteSize fromShortName(String shortName) {
        ByteSize val = shortNameToEnumMap.get(CaseInsensitiveString.fromString(shortName));
        if (val == null) {
            String allShortNames = Arrays.stream(ByteSize.values())
                    .map(byteSize -> {
                        return byteSize.shortName;
                    })
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(String.format("ShortName [%s] is not valid. Should be one of [%s] (case insensitive).", shortName, allShortNames));
        }
        return val;
    }

    public static ByteSize fromBytes(final int bytes){
        ByteSize val = intToEnumMap.get(bytes);
        if (val == null) {
            throw new IllegalArgumentException(String.format("The byte value %s is not a valid value for conversion into a ByteSize unit", bytes));
        }
        return val;
    }

    public static ByteSize fromBytes(final long bytes){
        return fromBytes((int) bytes);
    }

    public ByteSize convert(int fromValue, final ByteSize toUnits) {
        int bytes = toUnits.bytes / this.bytes * fromValue;
        return ByteSize.fromBytes(bytes);
    }

    private static class CaseInsensitiveString {
        private final String value;

        private CaseInsensitiveString(final String value) {
            this.value = value.toLowerCase();
        }

        public static CaseInsensitiveString fromString(String value){
            return new CaseInsensitiveString(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CaseInsensitiveString that = (CaseInsensitiveString) o;

            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

}
