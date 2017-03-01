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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@JsonPropertyOrder({"offset", "length"})
@XmlType(name = "OffsetRange", propOrder = {"offset", "length"})
@XmlAccessorType(XmlAccessType.FIELD)
public final class OffsetRange implements Serializable {
    private static final long serialVersionUID = 5045453517852867315L;

    @XmlElement
    private Long offset;
    @XmlElement
    private Long length;

    public OffsetRange() {
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
}
