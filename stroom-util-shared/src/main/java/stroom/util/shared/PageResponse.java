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

package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

@JsonInclude(Include.NON_DEFAULT)
public class PageResponse implements Serializable {
    private static final long serialVersionUID = -8613411971150227752L;

    @JsonProperty
    private Long offset;
    @JsonProperty
    private Integer length;
    @JsonProperty
    private Long total;
    @JsonProperty
    private boolean exact;

    @JsonCreator
    public PageResponse(@JsonProperty("offset") final Long offset,
                        @JsonProperty("length") final Integer length,
                        @JsonProperty("total") final Long total,
                        @JsonProperty("exact") final boolean exact) {
        this.offset = offset;
        this.length = length;
        this.total = total;
        this.exact = exact;
    }

    public Long getOffset() {
        return offset;
    }

    public void setOffset(final Long offset) {
        this.offset = offset;
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(final Integer length) {
        this.length = length;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(final Long total) {
        this.total = total;
    }

    public boolean isExact() {
        return exact;
    }

    public void setExact(final boolean exact) {
        this.exact = exact;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PageResponse that = (PageResponse) o;
        return exact == that.exact &&
                Objects.equals(offset, that.offset) &&
                Objects.equals(length, that.length) &&
                Objects.equals(total, that.total);
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, length, total, exact);
    }

    @Override
    public String toString() {
        return offset + ".." + length + " of " + (total == null ? "?" : total) + " " + (exact ? "exact" : "");
    }
}
