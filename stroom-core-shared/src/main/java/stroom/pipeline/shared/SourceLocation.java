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

package stroom.pipeline.shared;

import stroom.data.shared.DataRange;
import stroom.util.shared.TextRange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;

/**
 * Defines the location of some (typically character) data
 */
@JsonInclude(Include.NON_NULL)
//public class SourceLocation implements Comparable<SourceLocation> {
public class SourceLocation {

    @JsonProperty
    private final long id; // The meta ID
    @JsonProperty
    private final String childType; // null for actual data, else non null (e.g. context/meta)
    @JsonProperty
    private final long partNo; // For multipart data only, aka streamNo, 0 for non multi-part data
    // TODO @AT Change to an OffsetRange to support error segments
    @JsonProperty
    private final long segmentNo; // optional for segmented data only (segment aka record)
//    private final OffsetRange<Long> segmentNoRange;
    @JsonProperty
    private final DataRange dataRange; // The optional specified range of the character data which may be a subset
    @JsonProperty
    private final TextRange highlight; // The optional highlighted range of the character data which may be a subset
    @JsonProperty
    private final boolean truncateToWholeLines;

    @JsonCreator
    public SourceLocation(@JsonProperty("id") final long id,
                          @JsonProperty("childType") final String childType,
                          @JsonProperty("partNo") final long partNo,
                          @JsonProperty("segmentNo") final long segmentNo,
//                          @JsonProperty("segmentNoRage") final OffsetRange<Long> segmentNoRange,
                          @JsonProperty("dataRange") final DataRange dataRange,
                          @JsonProperty("highlight") final TextRange highlight,
                          @JsonProperty("truncateToWholeLines") final boolean truncateToWholeLines) {
        this.id = id;
        this.childType = childType;
        this.partNo = partNo;
        this.segmentNo = segmentNo;
//        this.segmentNoRange = segmentNoRange;
        this.dataRange = dataRange;
        this.highlight = highlight;
        this.truncateToWholeLines = truncateToWholeLines;
    }

    private SourceLocation(final Builder builder) {
        id = builder.id;
        partNo = builder.partNo;
        childType = builder.childType;
        segmentNo = builder.segmentNo;
//        segmentNoRange = builder.segmentNoRange;
        dataRange = builder.dataRange;
        highlight = builder.highlight;
        truncateToWholeLines = builder.truncateToWholeLines;
    }

    public static Builder builder(final long id) {
        return new Builder(id);
    }

    public Builder clone() {
        return new Builder(this);
    }

    public long getId() {
        return id;
    }

    /**
     * @return The type of the child stream that is being requested.
     */
    public String getChildType() {
        return childType;
    }

    @JsonIgnore
    public Optional<String> getOptChildType() {
        return Optional.ofNullable(childType);
    }

    /**
     * @return Part number in the stream (aka streamNo), zero based. Non multi-part streams would have
     * a single part with number zero.
     */
    public long getPartNo() {
        return partNo;
    }

    /**
     * @return The segment number (AKA record number), zero based
     */
    public long getSegmentNo() {
        return segmentNo;
    }

    @JsonIgnore
    public OptionalLong getOptSegmentNo() {
        return OptionalLong.of(segmentNo);
    }

//    /**
//     * @return The segment number (AKA record number), zero based
//     */
//    public OffsetRange<Long> getSegmentNoRange() {
//        return segmentNoRange;
//    }
//
//    @JsonIgnore
//    public Optional<OffsetRange<Long>> getOptSegmentNoRange() {
//        return Optional.of(segmentNoRange);
//    }

    /**
     * @return The range of data specified, may be null
     */
    public DataRange getDataRange() {
        return dataRange;
    }

    @JsonIgnore
    public Optional<DataRange> getOptDataRange() {
        return Optional.ofNullable(dataRange);
    }

    /**
     * @return The range of data that is highlighted, may be null.
     */
    public TextRange getHighlight() {
        return highlight;
    }

    @JsonIgnore
    public Optional<TextRange> getOptHighlight() {
        return Optional.ofNullable(highlight);
    }

    public boolean isTruncateToWholeLines() {
        return truncateToWholeLines;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SourceLocation that = (SourceLocation) o;
        return id == that.id &&
                partNo == that.partNo &&
                segmentNo == that.segmentNo &&
                Objects.equals(childType, that.childType) &&
                Objects.equals(dataRange, that.dataRange) &&
                Objects.equals(highlight, that.highlight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, childType, partNo, segmentNo, dataRange, highlight);
    }

    //    @Override
//    public int compareTo(final SourceLocation o) {
//        final CompareBuilder builder = new CompareBuilder();
//        builder.append(id, o.id);
//        builder.append(childType, o.childType);
//        builder.append(partNo, o.partNo);
//        builder.append(segmentNo, o.segmentNo);
//        builder.append(highlight, o.highlight);
//        return builder.toComparison();
//    }


    @Override
    public String toString() {
        return "SourceLocation{" +
                "id=" + id +
                ", childType='" + childType + '\'' +
                ", partNo=" + partNo +
                ", segmentNo=" + segmentNo +
                ", dataRange=" + dataRange +
                ", highlight=" + highlight +
                '}';
    }

    public static final class Builder {
        private final long id;
        private long partNo = 0; // Non multipart data has segment no of zero by default
        private String childType;
        private long segmentNo = 0; // Non-segmented data has no segment no.
//        private OffsetRange<Long> segmentNoRange = null;
        private DataRange dataRange;
        private TextRange highlight;
        private boolean truncateToWholeLines = false;

        private Builder(final long id) {
            this.id = id;
        }

        private Builder(final SourceLocation currentSourceLocation) {
            this.id = currentSourceLocation.id;
            this.partNo = currentSourceLocation.partNo;
            this.childType = currentSourceLocation.childType;
            this.segmentNo = currentSourceLocation.segmentNo;
            this.dataRange = currentSourceLocation.dataRange;
            this.highlight = currentSourceLocation.highlight;
            this.truncateToWholeLines = currentSourceLocation.truncateToWholeLines;
        }

        public Builder withPartNo(final Long partNo) {
            if (partNo != null) {
                this.partNo = partNo;
            }
            return this;
        }

        public Builder withChildStreamType(final String childStreamType) {
            this.childType = childStreamType;
            return this;
        }

        public Builder withSegmentNumber(final Long segmentNo) {
            if (segmentNo != null) {
                this.segmentNo = segmentNo;
            }
            return this;
        }

//        public Builder withSegmentNumber(final Long segmentNo) {
//            if (segmentNo != null) {
//                this.segmentNoRange = OffsetRange.of(segmentNo, 1L);
//            }
//            return this;
//        }
//
//        public Builder withSegmentNumberRange(final OffsetRange<Long> segmentNoRange) {
//            if (segmentNoRange != null) {
//                this.segmentNoRange = segmentNoRange;
//            }
//            return this;
//        }

        public Builder withDataRange(final DataRange dataRange) {
            this.dataRange = dataRange;
            return this;
        }

        public Builder withDataRangeBuilder(final Consumer<DataRange.Builder> dataRangeBuilder) {
            final DataRange.Builder builder = DataRange.builder();
            dataRangeBuilder.accept(builder);
            this.dataRange = builder.build();
            return this;
        }

        public Builder withHighlight(final TextRange highlight) {
            this.highlight = highlight;
            return this;
        }

        public Builder truncateToWholeLines() {
            this.truncateToWholeLines = true;
            return this;
        }

        public SourceLocation build() {
            return new SourceLocation(this);
        }
    }
}
