package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;

@JsonInclude(Include.NON_NULL)
public class DataRange {

    // The start of the data range
    // Need one of these
    @JsonProperty
    private final Location locationFrom;

    @JsonProperty
    private final Long charOffsetFrom;

    // The extent of the data range, absolute or relative
    // Need one of these
    @JsonProperty
    private final Location locationTo;

    @JsonProperty
    private final Long charOffsetTo;

    @JsonProperty
    private final Long length; // number of chars from the start position

    // data range limited by a hard max length set in config to protect UI

    @JsonCreator
    public DataRange(@JsonProperty("locationFrom") final Location locationFrom,
                     @JsonProperty("charOffsetFrom") final Long charOffsetFrom,
                     @JsonProperty("locationTo") final Location locationTo,
                     @JsonProperty("charOffsetTo") final Long charOffsetTo,
                     @JsonProperty("length") final Long length) {
        this.locationFrom = locationFrom;
        this.charOffsetFrom = charOffsetFrom;
        this.locationTo = locationTo;
        this.charOffsetTo = charOffsetTo;
        this.length = length;
    }

    private DataRange(final Builder builder) {
        locationFrom = builder.locationFrom;
        charOffsetFrom = builder.charOffsetFrom;
        locationTo = builder.locationTo;
        charOffsetTo = builder.charOffsetTo;
        length = builder.length;
    }

    public static DataRange between(final Location fromInclusive, final Location toInclusive) {
        return new DataRange(fromInclusive,
                null,
                toInclusive,
                null,
                null);
    }

    public static DataRange from(final Location fromInclusive) {
        return new DataRange(fromInclusive,
                null,
                null,
                null,
                null);
    }

    /**
     * @param charOffsetFrom Zero based
     */
    public static DataRange from(final long charOffsetFrom, final long length) {
        return new DataRange(null,
                charOffsetFrom,
                null,
                null,
                length);
    }

    /**
     * @param charOffsetFrom Zero based
     */
    public static DataRange from(final long charOffsetFrom) {
        return new DataRange(null,
                charOffsetFrom,
                null,
                null,
                null);
    }

    public static Builder builder() {
        return new Builder();
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
     * @return The number of chars in the range
     */
    public Long getLength() {
        return length;
    }

    public boolean hasBoundedStart() {
        return locationFrom != null || charOffsetFrom != null;
    }

    public boolean hasBoundedEnd() {
        return locationTo != null || charOffsetTo != null || length != null;
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DataRange dataRange = (DataRange) o;
        return Objects.equals(locationFrom, dataRange.locationFrom) &&
                Objects.equals(charOffsetFrom, dataRange.charOffsetFrom) &&
                Objects.equals(locationTo, dataRange.locationTo) &&
                Objects.equals(charOffsetTo, dataRange.charOffsetTo) &&
                Objects.equals(length, dataRange.length);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationFrom, charOffsetFrom, locationTo, charOffsetTo, length);
    }

    @Override
    public String toString() {
        return "DataRange{" +
                "locationFrom=" + locationFrom +
                ", charOffsetFrom=" + charOffsetFrom +
                ", locationTo=" + locationTo +
                ", charOffsetTo=" + charOffsetTo +
                ", length=" + length +
                '}';
    }

    public static final class Builder {
        private Location locationFrom;
        private Long charOffsetFrom;
        private Location locationTo;
        private Long charOffsetTo;
        private Long length;

        private Builder() {
        }

        public Builder fromLocation(final Location locationFrom) {
            this.locationFrom = locationFrom;
            return this;
        }

        public Builder fromCharOffset(final Long charOffsetFrom) {
            this.charOffsetFrom = charOffsetFrom;
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

        public Builder withLength(final Long length) {
            this.length = length;
            return this;
        }

        public DataRange build() {
            return new DataRange(this);
        }
    }

}
