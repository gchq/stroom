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

package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@JsonInclude(Include.NON_NULL)
public class DataRange {

    // The start of the data range
    // Need one of these
    @JsonProperty
    private final Location locationFrom;
    @JsonProperty
    private final Long charOffsetFrom;
    @JsonProperty
    private final Long byteOffsetFrom; // zero based, inclusive (for multi-byte, the first byte of the 'char')

    // The extent of the data range, absolute or relative
    // Need one of these
    @JsonProperty
    private final Location locationTo;
    @JsonProperty
    private final Long charOffsetTo;
    @JsonProperty
    private final Long byteOffsetTo; // zero based, inclusive (for multi-byte, the last byte of the 'char')

    @JsonProperty
    private final Long length; // number of chars from the start position

    // data range limited by a hard max length set in config to protect UI

    @JsonCreator
    public DataRange(@JsonProperty("locationFrom") final Location locationFrom,
                     @JsonProperty("charOffsetFrom") final Long charOffsetFrom,
                     @JsonProperty("byteOffsetFrom") final Long byteOffsetFrom,
                     @JsonProperty("locationTo") final Location locationTo,
                     @JsonProperty("charOffsetTo") final Long charOffsetTo,
                     @JsonProperty("byteOffsetTo") final Long byteOffsetTo,
                     @JsonProperty("length") final Long length) {
        this.locationFrom = locationFrom;
        this.charOffsetFrom = charOffsetFrom;
        this.byteOffsetFrom = byteOffsetFrom;
        this.locationTo = locationTo;
        this.charOffsetTo = charOffsetTo;
        this.byteOffsetTo = byteOffsetTo;
        this.length = length;
    }

    private DataRange(final Builder builder) {
        locationFrom = builder.locationFrom;
        charOffsetFrom = builder.charOffsetFrom;
        byteOffsetFrom = builder.byteOffsetFrom;
        locationTo = builder.locationTo;
        charOffsetTo = builder.charOffsetTo;
        byteOffsetTo = builder.byteOffsetTo;
        length = builder.length;
    }

    public static DataRange between(final Location fromInclusive, final Location toInclusive) {
        return new DataRange(fromInclusive,
                null,
                null,
                toInclusive,
                null,
                null,
                null);
    }

    public static DataRange fromLocation(final Location fromInclusive) {
        return new DataRange(fromInclusive,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * @param charOffsetFrom Zero based
     */
    public static DataRange fromCharOffset(final long charOffsetFrom, final long length) {
        return new DataRange(null,
                charOffsetFrom,
                null,
                null,
                null,
                null,
                length);
    }

    /**
     * @param charOffsetFrom Zero based
     */
    public static DataRange fromCharOffset(final long charOffsetFrom) {
        return new DataRange(null,
                charOffsetFrom,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * @param byteOffsetFrom Zero based
     * @param length         The number of chars in the range
     */
    public static DataRange fromByteOffset(final long byteOffsetFrom, final long length) {
        return new DataRange(null,
                null,
                byteOffsetFrom,
                null,
                null,
                null,
                length);
    }

    /**
     * @param byteOffsetFrom Zero based
     */
    public static DataRange fromByteOffset(final long byteOffsetFrom) {
        return new DataRange(null,
                null,
                byteOffsetFrom,
                null,
                null,
                null,
                null);
    }

    /**
     * @return The start of the data range, inclusive
     */
    @JsonIgnore
    public Optional<Location> getOptLocationFrom() {
        return Optional.ofNullable(locationFrom);
    }

    public Location getLocationFrom() {
        return locationFrom;
    }

    /**
     * @return The start of the data range, inclusive, zero based
     */
    @JsonIgnore
    public Optional<Long> getOptCharOffsetFrom() {
        return Optional.ofNullable(charOffsetFrom);
    }

    /**
     * @return The start of the data range, inclusive, zero based
     */
    public Long getCharOffsetFrom() {
        return charOffsetFrom;
    }

    /**
     * @return The end of the data range, inclusive
     */
    @JsonIgnore
    public Optional<Location> getOptLocationTo() {
        return Optional.ofNullable(locationTo);
    }

    public Location getLocationTo() {
        return locationTo;
    }

    /**
     * @return The end of the data range, inclusive, zero based
     */
    @JsonIgnore
    public Optional<Long> getOptCharOffsetTo() {
        return Optional.ofNullable(charOffsetTo);
    }

    /**
     * @return The end of the data range, inclusive, zero based
     */
    public Long getCharOffsetTo() {
        return charOffsetTo;
    }

    /**
     * @return The number of chars of data to get
     */
    @JsonIgnore
    public Optional<Long> getOptLength() {
        return Optional.ofNullable(length);
    }

    /**
     * Zero based, inclusive
     */
    public Long getByteOffsetFrom() {
        return byteOffsetFrom;
    }

    /**
     * Zero based, inclusive
     */
    @JsonIgnore
    public Optional<Long> getOptByteOffsetFrom() {
        return Optional.ofNullable(byteOffsetFrom);
    }

    /**
     * Zero based, inclusive
     */
    public Long getByteOffsetTo() {
        return byteOffsetTo;
    }

    /**
     * Zero based, inclusive
     */
    @JsonIgnore
    public Optional<Long> getOptByteOffsetTo() {
        return Optional.ofNullable(byteOffsetTo);
    }

    @JsonIgnore
    public Optional<TextRange> getAsTextRange() {
        if (locationFrom != null && locationTo != null) {
            return Optional.of(new TextRange(locationFrom, locationTo));
        } else {
            return Optional.empty();
        }
    }

    /**
     * @return True if all of this range is inside or is identical to
     * the other range.
     */
    public boolean isInsideRange(final DataRange other) {
        if (other == null) {
            return false;
        } else if (locationFrom != null && locationTo != null) {
            return isInsideRange(other, DataRange::getLocationFrom, DataRange::getLocationTo);
        } else if (byteOffsetFrom != null && byteOffsetTo != null) {
            return isInsideRange(other, DataRange::getByteOffsetFrom, DataRange::getByteOffsetTo);
        } else {
            return false;
        }
    }

    public boolean isInsideRange(final Location from, final Location to) {
        final boolean result;
        if (this.locationFrom == null
                || this.locationTo == null
                || from == null
                || to == null) {
            result = false;
        } else {
            result = this.locationFrom.compareTo(from) >= 0
                    && this.locationTo.compareTo(to) <= 0;
        }
        return result;
    }

    private <T extends Comparable<T>> boolean isInsideRange(final DataRange other,
                                                            final Function<DataRange, T> fromFunc,
                                                            final Function<DataRange, T> toFunc) {
        final boolean result;
        if (other == null) {
            result = false;
        } else {
            final T thisFrom = fromFunc.apply(this);
            final T thisTo = toFunc.apply(this);
            if (thisFrom == null || thisTo == null) {
                result = false;
            } else {
                final T otherFrom = fromFunc.apply(other);
                final T otherTo = toFunc.apply(other);
                if (otherFrom == null || otherTo == null) {
                    result = false;
                } else {
                    result = thisFrom.compareTo(otherFrom) >= 0
                            && thisTo.compareTo(otherTo) <= 0;
                }
            }
        }
        return result;
    }

    @JsonIgnore
    public boolean isOnOneLine() {
        return locationFrom != null
                && locationTo != null
                && locationFrom.getLineNo() == locationTo.getLineNo();
    }

    public boolean isBefore(final DataRange other) {
        if (other == null) {
            return false;
        } else if (locationFrom != null) {
            return Comparator.comparing(DataRange::getLocationFrom).compare(this, other) < 0;
        } else if (byteOffsetFrom != null) {
            return Comparator.comparing(DataRange::getByteOffsetFrom).compare(this, other) < 0;
        } else {
            return false;
        }
    }

    public boolean isAfter(final DataRange other) {
        if (other == null) {
            return false;
        } else if (locationFrom != null) {
            return Comparator.comparing(DataRange::getLocationFrom).compare(this, other) > 0;
        } else if (byteOffsetFrom != null) {
            return Comparator.comparing(DataRange::getByteOffsetFrom).compare(this, other) > 0;
        } else {
            return false;
        }
    }

    /**
     * @return The number of chars in the range
     */
    public Long getLength() {
        return length;
    }

    public boolean hasBoundedStart() {
        return locationFrom != null
                || charOffsetFrom != null
                || byteOffsetFrom != null;
    }

    public boolean hasBoundedEnd() {
        return locationTo != null
                || charOffsetTo != null
                || length != null
                || byteOffsetTo != null;
    }

    @JsonIgnore
    public Optional<Integer> getLineCount() {
        if (locationFrom != null && locationTo != null) {
            return Optional.of(locationTo.getLineNo() - locationFrom.getLineNo() + 1);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataRange dataRange = (DataRange) o;
        return Objects.equals(locationFrom, dataRange.locationFrom) &&
                Objects.equals(charOffsetFrom, dataRange.charOffsetFrom) &&
                Objects.equals(byteOffsetFrom, dataRange.byteOffsetFrom) &&
                Objects.equals(locationTo, dataRange.locationTo) &&
                Objects.equals(charOffsetTo, dataRange.charOffsetTo) &&
                Objects.equals(byteOffsetTo, dataRange.byteOffsetTo) &&
                Objects.equals(length, dataRange.length);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationFrom,
                charOffsetFrom,
                byteOffsetFrom,
                locationTo,
                charOffsetTo,
                byteOffsetTo,
                length);
    }

    @Override
    public String toString() {
        return "DataRange{" +
                "locationFrom=" + locationFrom +
                ", charOffsetFrom=" + charOffsetFrom +
                ", byteOffsetFrom=" + byteOffsetFrom +
                ", locationTo=" + locationTo +
                ", charOffsetTo=" + charOffsetTo +
                ", byteOffsetTo=" + byteOffsetTo +
                ", length=" + length +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private Location locationFrom;
        private Long charOffsetFrom;
        private Long byteOffsetFrom;
        private Location locationTo;
        private Long charOffsetTo;
        private Long byteOffsetTo;
        private Long length;

        private Builder() {
        }

        private Builder(final DataRange dataRange) {
            locationFrom = dataRange.locationFrom;
            charOffsetFrom = dataRange.charOffsetFrom;
            byteOffsetFrom = dataRange.byteOffsetFrom;
            locationTo = dataRange.locationTo;
            charOffsetTo = dataRange.charOffsetTo;
            byteOffsetTo = dataRange.byteOffsetTo;
            length = dataRange.length;
        }

        public Builder fromLocation(final Location locationFrom) {
            this.locationFrom = locationFrom;
            return this;
        }

        public Builder fromCharOffset(final Long charOffsetFrom) {
            this.charOffsetFrom = charOffsetFrom;
            return this;
        }

        /**
         * Zero based, inclusive
         */
        public Builder fromByteOffset(final Long byteOffsetFrom) {
            this.byteOffsetFrom = byteOffsetFrom;
            return this;
        }

        public Builder toLocation(final Location locationFrom) {
            this.locationTo = locationFrom;
            return this;
        }

        public Builder toCharOffset(final Long charOffsetTo) {
            this.charOffsetTo = charOffsetTo;
            return this;
        }

        /**
         * Zero based, inclusive
         */
        public Builder toByteOffset(final Long byteOffsetTo) {
            this.byteOffsetTo = byteOffsetTo;
            return this;
        }

        public Builder withLength(final Long length) {
            this.length = length;
            return this;
        }

        public DataRange build() {
            return new DataRange(this);
        }
    }
}
