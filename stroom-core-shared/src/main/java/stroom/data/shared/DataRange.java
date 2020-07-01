package stroom.data.shared;

import stroom.util.shared.Location;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

@JsonInclude(Include.NON_NULL)
public class DataRange {

    // TODO Presume we are always working with stream No 0, i.e the data stream?

    // These three should get you an input stream
    @JsonProperty
    private long metaId;

    @JsonProperty
    private long partNo; // optional for multipart data only, aka streamNo

    @JsonProperty
    private String childStreamType; // null for actual data, else non null (e.g. context/meta)

    @JsonProperty
    private Long segmentNumber; // optional for segmented data only (segment aka record)

    // The start of the data range
    // Need one of these
    @JsonProperty
    private Location locationFrom;

    @JsonProperty
    private Long charOffsetFrom;

    // The extent of the data range, absolute or relative
    // Need one of these
    @JsonProperty
    private Location locationTo;

    @JsonProperty
    private Long charOffsetTo;

    @JsonProperty
    private Long length; // number of chars from the start position

    // data range limited by a hard max length set in config to protect UI

    @JsonCreator
    public DataRange(@JsonProperty("metaId") final long metaId,
                     @JsonProperty("partNo") final long partNo,
                     @JsonProperty("childStreamType") final String childStreamType,
                     @JsonProperty("segmentNumber") final Long segmentNumber,
                     @JsonProperty("locationFrom") final Location locationFrom,
                     @JsonProperty("charOffsetFrom") final Long charOffsetFrom,
                     @JsonProperty("locationTo") final Location locationTo,
                     @JsonProperty("charOffsetTo") final Long charOffsetTo,
                     @JsonProperty("length") final Long length) {
        this.metaId = metaId;
        this.partNo = partNo;
        this.childStreamType = childStreamType;
        this.segmentNumber = segmentNumber;
        this.locationFrom = locationFrom;
        this.charOffsetFrom = charOffsetFrom;
        this.locationTo = locationTo;
        this.charOffsetTo = charOffsetTo;
        this.length = length;
    }

    private DataRange(final Builder builder) {
        metaId = builder.metaId;
        partNo = builder.partNumber;
        childStreamType = builder.childStreamType;
        segmentNumber = builder.segmentNumber;
        locationFrom = builder.locationFrom;
        charOffsetFrom = builder.charOffsetFrom;
        locationTo = builder.locationTo;
        charOffsetTo = builder.charOffsetTo;
        length = builder.length;
    }

    public static Builder builder(final long metaId) {
        return new Builder(metaId);
    }

    public long getMetaId() {
        return metaId;
    }

    /**
     * @return Part number in the stream (aka streamNo), zero based. Non multi-part streams would have
     * a single part with number zero.
     */
    public long getPartNo() {
        return partNo;
    }

    /**
     * @return The type of the child stream that is being requested.
     */
    @JsonIgnore
    public Optional<String> getOptChildStreamType() {
        return Optional.ofNullable(childStreamType);
    }

    public String getChildStreamType() {
        return childStreamType;
    }

    /**
     * @return The segment number, zero based
     */
    @JsonIgnore
    public OptionalLong getOptSegmentNumber() {
        return segmentNumber != null
                ? OptionalLong.of(segmentNumber)
                : OptionalLong.empty();
    }

    public Long getSegmentNumber() {
        return segmentNumber;
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
     * @return The start of the data range, inclusive
     */
    @JsonIgnore
    public OptionalLong getOptCharOffsetFrom() {
        return charOffsetFrom != null
                ? OptionalLong.of(charOffsetFrom)
                : OptionalLong.empty();
    }

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
     * @return The end of the data range, inclusive
     */
    @JsonIgnore
    public OptionalLong getOptCharOffsetTo() {
        return charOffsetTo != null
                ? OptionalLong.of(charOffsetTo)
                : OptionalLong.empty();
    }

    public Long getCharOffsetTo() {
        return charOffsetTo;
    }

    /**
     * @return The number of chars of data to get
     */
    @JsonIgnore
    public OptionalLong getOptLength() {
        return length != null
                ? OptionalLong.of(length)
                : OptionalLong.empty();
    }

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
    public OptionalInt getLineCount() {
        if (locationFrom != null && locationTo != null) {
            return OptionalInt.of(locationTo.getLineNo() - locationFrom.getLineNo() + 1);
        } else {
            return OptionalInt.empty();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DataRange dataRange = (DataRange) o;
        return metaId == dataRange.metaId &&
                partNo == dataRange.partNo &&
                Objects.equals(childStreamType, dataRange.childStreamType) &&
                Objects.equals(segmentNumber, dataRange.segmentNumber) &&
                Objects.equals(locationFrom, dataRange.locationFrom) &&
                Objects.equals(charOffsetFrom, dataRange.charOffsetFrom) &&
                Objects.equals(locationTo, dataRange.locationTo) &&
                Objects.equals(charOffsetTo, dataRange.charOffsetTo) &&
                Objects.equals(length, dataRange.length);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaId, partNo, childStreamType, segmentNumber, locationFrom, charOffsetFrom, locationTo, charOffsetTo, length);
    }

    @Override
    public String toString() {
        return "DataRange{" +
                "metaId=" + metaId +
                ", partNo=" + partNo +
                ", childStreamType='" + childStreamType + '\'' +
                ", segmentNumber=" + segmentNumber +
                ", locationFrom=" + locationFrom +
                ", charOffsetFrom=" + charOffsetFrom +
                ", locationTo=" + locationTo +
                ", charOffsetTo=" + charOffsetTo +
                ", length=" + length +
                '}';
    }

    public static final class Builder {
        private final long metaId;
        private Long partNumber;
        private String childStreamType;
        private Long segmentNumber;
        private Location locationFrom;
        private Long charOffsetFrom;
        private Location locationTo;
        private Long charOffsetTo;
        private Long length;

        private Builder(final long metaId) {
            this.metaId = metaId;
        }

        public Builder withPartNumber(final Long partNumber) {
            this.partNumber = partNumber;
            return this;
        }

        public Builder withChildStreamType(final String childStreamType) {
            this.childStreamType = childStreamType;
            return this;
        }

        public Builder withSegmentNumber(final Long segmentNumber) {
            this.segmentNumber = segmentNumber;
            return this;
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
//            if (locationFrom != null && charOffsetFrom != null) {
//                throw new RuntimeException("Can't set both location and char offset from positions");
//            }
//            if (locationTo != null && charOffsetTo != null) {
//                throw new RuntimeException("Can't set both location and char offset to positions");
//            }
//            if (locationTo != null && length != null) {
//                throw new RuntimeException("Can't set both location to and length");
//            }
//            if (charOffsetTo != null && length != null) {
//                throw new RuntimeException("Can't set both location to and length");
//            }
            if (partNumber == null) {
                partNumber = 0L;
            }
            return new DataRange(this);
        }
    }

}
