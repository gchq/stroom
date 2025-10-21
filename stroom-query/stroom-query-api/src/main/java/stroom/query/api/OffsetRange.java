/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.api;

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

    public static final OffsetRange ZERO_100 = new OffsetRange(0L, 100L);
    public static final OffsetRange ZERO_1000 = new OffsetRange(0L, 1000L);
    public static final OffsetRange UNBOUNDED = new OffsetRange(0, Integer.MAX_VALUE);
    @Schema(description = "The start offset for this sub-set of data, where zero is the offset of the first record " +
                          "in the full result set",
            example = "0")
    @JsonProperty
    private final long offset;

    @Schema(description = "The length in records of the sub-set of results",
            example = "100")
    @JsonProperty
    private final long length;

    public OffsetRange(final int offset, final int length) {
        this.offset = offset;
        this.length = length;
    }

    @JsonCreator
    public OffsetRange(@JsonProperty("offset") final Long offset,
                       @JsonProperty("length") final Long length) {
        this.offset = offset == null
                ? 0
                : offset;
        this.length = length == null
                ? 100
                : length;
    }

    public long getOffset() {
        return offset;
    }

    public long getLength() {
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
        return offset == that.offset &&
               length == that.length;
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

        private long offset;
        private long length = 100;

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
        public Builder offset(final long value) {
            this.offset = value;
            return this;
        }

        /**
         * @param value The length in records of the sub-set of results
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder length(final long value) {
            this.length = value;
            return this;
        }

        public OffsetRange build() {
            return new OffsetRange(offset, length);
        }
    }
}
