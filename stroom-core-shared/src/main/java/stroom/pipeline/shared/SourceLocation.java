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

import stroom.util.shared.DataRange;
import stroom.util.shared.TextRange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Defines the location of some (typically character) data
 */
@JsonInclude(Include.NON_NULL)
public class SourceLocation {

    public static final int MAX_ERRORS_PER_PAGE = 100;

    @JsonProperty
    private final long metaId; // The meta ID
    @JsonProperty
    private final String childType; // null for actual data, else non null (e.g. context/meta)
    @JsonProperty
    private final long partIndex; // For multipart data only, 0 for non multi-part data, zero based
    @JsonProperty
    private final long recordIndex; // optional for data where records are split into separate segments only, zero based
    @JsonProperty
    private final DataRange dataRange; // The optional specified range of the character data which may be a subset
    @JsonProperty
    private final TextRange highlight; // The optional highlighted range of the character data which may be a subset

    @JsonCreator
    public SourceLocation(@JsonProperty("metaId") final long metaId,
                          @JsonProperty("childType") final String childType,
                          @JsonProperty("partIndex") final long partIndex,
                          @JsonProperty("recordIndex") final long recordIndex,
                          @JsonProperty("dataRange") final DataRange dataRange,
                          @JsonProperty("highlight") final TextRange highlight) {
        this.metaId = metaId;
        this.childType = childType;
        this.partIndex = partIndex;
        this.recordIndex = recordIndex;
        this.dataRange = dataRange;
        this.highlight = highlight;
    }

    private SourceLocation(final Builder builder) {
        metaId = builder.metaId;
        partIndex = builder.partIndex;
        childType = builder.childType;
        recordIndex = builder.recordIndex;
        dataRange = builder.dataRange;
        highlight = builder.highlight;
    }

    public static Builder builder(final long metaId) {
        return new Builder(metaId);
    }

    public long getMetaId() {
        return metaId;
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
     * @return Part index in the stream, zero based. Non multi-part streams would have
     * a single part with number zero.
     */
    public long getPartIndex() {
        return partIndex;
    }

    /**
     * @return The record number (AKA segment number), zero based
     */
    public long getRecordIndex() {
        return recordIndex;
    }

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

    public boolean isSameSource(final SourceLocation other) {
        if (other == null) {
            return false;
        } else {
            return this.metaId == other.metaId
                    && this.partIndex == other.partIndex
                    && Objects.equals(this.childType, other.childType);
        }
    }

    public boolean isSameLocation(final SourceLocation other) {
        if (other == null) {
            return false;
        } else {
            return this.isSameSource(other)
                    && this.recordIndex == other.recordIndex
                    && Objects.equals(this.dataRange, other.dataRange);
        }
    }

    /**
     * @return The identifier i.e. strm:part:segment (one based for human use)
     */
    @JsonIgnore
    public String getIdentifierString() {
        // Convert to one-based
        return metaId + ":" + (partIndex + 1) + ":" + (recordIndex + 1);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SourceLocation that = (SourceLocation) o;
        return metaId == that.metaId &&
                partIndex == that.partIndex &&
                recordIndex == that.recordIndex &&
                Objects.equals(childType, that.childType) &&
                Objects.equals(dataRange, that.dataRange) &&
                Objects.equals(highlight, that.highlight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaId, childType, partIndex, recordIndex, dataRange, highlight);
    }

    @Override
    public String toString() {
        return "SourceLocation{" +
                "metaId=" + metaId +
                ", childType='" + childType + '\'' +
                ", partIndex=" + partIndex +
                ", recordIndex=" + recordIndex +
                ", dataRange=" + dataRange +
                ", highlight=" + highlight +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private long metaId;
        private long partIndex; // Non multipart data has part no of zero by default, zero based
        private String childType;
        private long recordIndex; // Non-segmented data has no record no., zero based
        private DataRange dataRange;
        private TextRange highlight;
        private boolean truncateToWholeLines = false;

        private Builder(final long metaId) {
            this.metaId = metaId;
        }

        private Builder() {
        }

        private Builder(final SourceLocation currentSourceLocation) {
            this.metaId = currentSourceLocation.metaId;
            this.partIndex = currentSourceLocation.partIndex;
            this.childType = currentSourceLocation.childType;
            this.recordIndex = currentSourceLocation.recordIndex;
            this.dataRange = currentSourceLocation.dataRange;
            this.highlight = currentSourceLocation.highlight;
        }

        /**
         * Zero based
         */
        public Builder withPartIndex(final Long partIndex) {
            if (partIndex != null) {
                this.partIndex = partIndex;
            }
            return this;
        }

        public Builder withChildStreamType(final String childStreamType) {
            this.childType = childStreamType;
            return this;
        }

        /**
         * Zero based
         */
        public Builder withRecordIndex(final Long recordIndex) {
            if (recordIndex != null) {
                this.recordIndex = recordIndex;
            }
            return this;
        }

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
