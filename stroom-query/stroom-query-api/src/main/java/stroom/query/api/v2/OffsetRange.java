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

package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Objects;

@JsonPropertyOrder({"offset", "length"})
@JsonInclude(Include.NON_NULL)
@XmlType(name = "OffsetRange", propOrder = {"offset", "length"})
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(description = "The offset and length of a range of data in a sub-set of a query result set")
public final class OffsetRange implements Serializable {
    private static final long serialVersionUID = 5045453517852867315L;

    @XmlElement
    @ApiModelProperty(
            value = "The start offset for this sub-set of data, where zero is the offset of the first record " +
                    "in the full result set",
            example = "0",
            required = true)
    @JsonProperty
    private Long offset;

    @XmlElement
    @ApiModelProperty(
            value = "The length in records of the sub-set of results",
            example = "100",
            required = true)
    @JsonProperty
    private Long length;

    public OffsetRange() {
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

    public void setOffset(final Long offset) {
        this.offset = offset;
    }

    public Long getLength() {
        return length;
    }

    public void setLength(final Long length) {
        this.length = length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OffsetRange that = (OffsetRange) o;
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

    /**
     * Builder for constructing a {@link OffsetRange}
     */
    public static class Builder {
        private Long offset;
        private Long length;

        /**
         * @param value The start offset for this sub-set of data,
         *              where zero is the offset of the first record in the full result set
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder offset(final Long value) {
            this.offset = value;
            return this;
        }

        /**
         * @param value The length in records of the sub-set of results
         *
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
