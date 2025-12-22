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

package stroom.util.io;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for handling byte size units such as MiB, GiB, TiB etc.
 */
public enum ByteSizeUnit {
    BYTE("B", "bytes", 1),

    KIBIBYTE("KiB", "Kibibytes", 1024L),
    MEBIBYTE("MiB", "Mebibytes", 1024L * 1024),
    GIBIBYTE("GiB", "Gibibytes", 1024L * 1024 * 1024),
    TEBIBYTE("TiB", "Tebibytes", 1024L * 1024 * 1024 * 1024),
    PEBIBYTE("PiB", "Pebibytes", 1024L * 1024 * 1024 * 1024 * 1024),
    EXBIBYTE("EiB", "Exbibytes", 1024L * 1024 * 1024 * 1024 * 1024 * 1024),

    KILOBYTE("kB", "Kilobytes", 1000L),
    MEGABYTE("MB", "Megabytes", 1000L * 1000),
    GIGABYTE("GB", "Gigabytes", 1000L * 1000 * 1000),
    TERABYTE("TB", "Terabytes", 1000L * 1000 * 1000 * 1000),
    PETABYTE("PB", "Petabytes", 1000L * 1000 * 1000 * 1000 * 1000),
    EXABYTE("EB", "Exabytes", 1000L * 1000 * 1000 * 1000 * 1000 * 1000);

    private static final Map<CaseInsensitiveString, ByteSizeUnit> shortNameToEnumMap = new HashMap<>();
    private static final Map<Long, ByteSizeUnit> intToEnumMap = new HashMap<>();

    static {
        for (final ByteSizeUnit byteSizeUnit : ByteSizeUnit.values()) {
            shortNameToEnumMap.put(CaseInsensitiveString.fromString(byteSizeUnit.shortName), byteSizeUnit);
            intToEnumMap.put(byteSizeUnit.longBytes(), byteSizeUnit);
        }
    }

    private final long bytes;
    private final String shortName;
    private final String longName;

    ByteSizeUnit(final String shortName, final String longName, final long bytes) {
        this.bytes = bytes;
        this.shortName = shortName;
        this.longName = longName;
    }

    public static ByteSizeUnit fromShortName(final String shortName) {
        final ByteSizeUnit val = shortNameToEnumMap.get(CaseInsensitiveString.fromString(shortName));
        if (val == null) {
            final String allShortNames = Arrays.stream(ByteSizeUnit.values())
                    .map(byteSizeUnit -> {
                        return byteSizeUnit.shortName;
                    })
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(String.format(
                    "ShortName [%s] is not valid. Should be one of [%s] (case insensitive).",
                    shortName,
                    allShortNames));
        }
        return val;
    }

    public static ByteSizeUnit fromBytes(final long bytes) {
        final ByteSizeUnit val = intToEnumMap.get(bytes);
        if (val == null) {
            throw new IllegalArgumentException(String.format(
                    "The byte value %s is not a valid value for conversion into a ByteSizeUnit unit", bytes));
        }
        return val;
    }

    /**
     * @return The number of bytes in this byte size unit
     */
    public long longBytes() {
        return bytes;
    }

    /**
     * Converts the value from the units of this into bytes
     */
    public long longBytes(final long fromValue) {
        return this.bytes * fromValue;
    }

    /**
     * Converts the value from the units of this into bytes
     */
    public int intBytes(final int fromValue) {
        return (int) (this.bytes * fromValue);
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
     * @return The abbreviated form, e.g. MiB or KiB
     */
    public String getShortName() {
        return shortName;
    }

    /**
     * @return The long name, e.g. Mebibytes
     */
    public String getLongName() {
        return longName;
    }

    /**
     * Converts a value from one byte size unit into another, e.g. MiB into KiB
     */
    public double convert(final double fromValue, final ByteSizeUnit toUnits) {
        return (fromValue * this.bytes) / toUnits.bytes;
    }

    private static class CaseInsensitiveString {

        private final String value;

        private CaseInsensitiveString(final String value) {
            this.value = value.toLowerCase();
        }

        public static CaseInsensitiveString fromString(final String value) {
            return new CaseInsensitiveString(value);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final CaseInsensitiveString that = (CaseInsensitiveString) o;

            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

}
