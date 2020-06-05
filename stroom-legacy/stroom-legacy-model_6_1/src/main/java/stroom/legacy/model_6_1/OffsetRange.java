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

package stroom.legacy.model_6_1;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@JsonPropertyOrder({"offset", "length"})
@XmlType(name = "OffsetRange", propOrder = {"offset", "length"})
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(description = "The offset and length of a range of data in a sub-set of a query result set")
@Deprecated
public final class OffsetRange implements Serializable {
    private static final long serialVersionUID = 5045453517852867315L;

    @XmlElement
    @ApiModelProperty(
            value = "The start offset for this sub-set of data, where zero is the offset of the first record " +
                    "in the full result set",
            example = "0",
            required = true)
    private Long offset;

    @XmlElement
    @ApiModelProperty(
            value = "The length in records of the sub-set of results",
            example = "100",
            required = true)
    private Long length;

    private OffsetRange() {
    }

    public OffsetRange(final Integer offset, final Integer length) {
        this.offset = offset.longValue();
        this.length = length.longValue();
    }

    public OffsetRange(final Long offset, final Long length) {
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
        if (this == o) return true;
        if (!(o instanceof OffsetRange)) return false;

        final OffsetRange that = (OffsetRange) o;

        if (offset != null ? !offset.equals(that.offset) : that.offset != null) return false;
        return length != null ? length.equals(that.length) : that.length == null;
    }

    @Override
    public int hashCode() {
        int result = offset != null ? offset.hashCode() : 0;
        result = 31 * result + (length != null ? length.hashCode() : 0);
        return result;
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
