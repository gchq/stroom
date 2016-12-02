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

/**
 * Utility class for handling byte size units such as Mb, Gb, Tb etc.
 */
public enum ByteSizeUnit {
    BYTE(1, "B", "bytes"),
    KILOBYTE(1024, "KB", "Kilobytes"),
    MEGABYTE(1024 * 1024, "MB", "Megabytes"),
    GIGABYTE(1024 * 1024 * 1024, "GB", "Gigabytes"),
    TERABYTE(1024 * 1024 * 1024 * 1024, "TB", "Terabytes"),
    PETABYTE(1024 * 1024 * 1024 * 1024 * 1024, "PB", "Petabytes");

    private final int bytes;
    private final String shortName;
    private final String longName;

    private static final Map<CaseInsensitiveString, ByteSizeUnit> shortNameToEnumMap = new HashMap<>();
    private static final Map<Integer, ByteSizeUnit> intToEnumMap = new HashMap<>();

    static {
        for (ByteSizeUnit byteSizeUnit : ByteSizeUnit.values()) {
            shortNameToEnumMap.put(CaseInsensitiveString.fromString(byteSizeUnit.shortName), byteSizeUnit);
            intToEnumMap.put(byteSizeUnit.intBytes(), byteSizeUnit);
        }
    }

    private ByteSizeUnit(final int bytes, final String shortName, final String longName) {
        this.bytes = bytes;
        this.shortName = shortName;
        this.longName = longName;
    }

    /**
     * @return The number of bytes in this byte size unit
     */
    public long longBytes() {
        return (long) bytes;
    }

    /**
     * Converts the value from the units of this into bytes
     */
    public long longBytes(long fromValue) {
        return this.bytes * fromValue;
    }

    /**
     * @return The number of bytes in this byte size unit
     */
    public int intBytes() {
        return bytes;
    }

    /**
     * Converts the value from the units of this into bytes
     */
    public int intBytes(final int fromValue) {
        return this.bytes * fromValue;
    }

    /**
     * Converts from a value in Bytes to a value in the unit of 'this'
     */
    public double unitValue(final long bytes) {
        return (double) bytes / this.bytes;
    }

    /**
     * Converts from a value in Bytes to a value in the unit of 'this'
     */
    public double unitValue(final int bytes) {
        return (double) bytes / this.bytes;
    }

    /**
     * @return The abbreviated form, e.g. MB or KB
     */
    public String getShortName() {
        return shortName;
    }

    /**
     * @return The long name, e.g. Megabytes
     */
    public String getLongName() {
        return longName;
    }

    public static ByteSizeUnit fromShortName(String shortName) {
        ByteSizeUnit val = shortNameToEnumMap.get(CaseInsensitiveString.fromString(shortName));
        if (val == null) {
            String allShortNames = Arrays.stream(ByteSizeUnit.values())
                    .map(byteSizeUnit -> {
                        return byteSizeUnit.shortName;
                    })
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(String.format("ShortName [%s] is not valid. Should be one of [%s] (case insensitive).", shortName, allShortNames));
        }
        return val;
    }

    public static ByteSizeUnit fromBytes(final int bytes){
        ByteSizeUnit val = intToEnumMap.get(bytes);
        if (val == null) {
            throw new IllegalArgumentException(String.format("The byte value %s is not a valid value for conversion into a ByteSizeUnit unit", bytes));
        }
        return val;
    }

    public static ByteSizeUnit fromBytes(final long bytes){
        return fromBytes((int) bytes);
    }

    /**
     * Converts a value from one byte size unit into another, e.g. MB into KB
     */
    public double convert(double fromValue, final ByteSizeUnit toUnits) {
        return (fromValue * this.bytes) / toUnits.bytes;
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
