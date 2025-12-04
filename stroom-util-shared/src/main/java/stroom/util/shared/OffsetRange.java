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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@JsonPropertyOrder({"offset", "length"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "The offset and length of a range of data in a sub-set of a query result set")
public final class OffsetRange {

    @Schema(description = "The start offset for this sub-set of data, where zero is the offset of the first record " +
            "in the full result set",
            example = "0",
            required = true)
    @JsonProperty
    private final Long offset;

    @Schema(description = "The length in records of the sub-set of results",
            example = "100",
            required = true)
    @JsonProperty
    private final Long length;

    public static OffsetRange zero() {
        return new OffsetRange(Long.valueOf(0), Long.valueOf(0));
    }

    public static OffsetRange of(final Long offset, final Long length) {
        return new OffsetRange(offset, length);
    }

    public OffsetRange(final Integer offset, final Integer length) {
        this.offset = offset.longValue();
        this.length = length.longValue();
    }

    @JsonCreator
    public OffsetRange(@JsonProperty("offset") final Long offset,
                       @JsonProperty("length") final Long length) {
        this.offset = offset;
        this.length = length;
    }

    public Long getOffset() {
        return offset;
    }

    public Long getLength() {
        return length;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final OffsetRange that = (OffsetRange) o;
        return Objects.equals(offset, that.offset) &&
                Objects.equals(length, that.length);
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, length);
    }

    @Override
    public String toString() {
        return "OffsetRange{" +
                "offset=" + offset +
                ", length=" + length +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    /**
     * Builder for constructing a {@link OffsetRange}
     */
    public static final class Builder {

        private Long offset;
        private Long length;

        private Builder() {
        }

        private Builder(final OffsetRange offsetRange) {
            offset = offsetRange.offset;
            length = offsetRange.length;
        }

        /**
         * @param value The start offset for this sub-set of data,
         *              where zero is the offset of the first record in the full result set
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder offset(final Long value) {
            this.offset = value;
            return this;
        }

        /**
         * @param value The length in records of the sub-set of results
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder length(final Long value) {
            this.length = value;
            return this;
        }

        public OffsetRange build() {
            return new OffsetRange(offset, length);
        }
    }
}
