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
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@ApiModel(description = "Details of the page of results being returned.")
public class PageResponse implements Serializable {
    private static final long serialVersionUID = -8613411971150227752L;

    @JsonProperty
    @ApiModelProperty(value = "The offset of the first item in the page relative to the full result set, zero based.")
    private final long offset;

    @JsonProperty
    @ApiModelProperty(value = "The number of items in this page of results.")
    private final int length;

    @JsonProperty
    @ApiModelProperty(value = "The total number of items in the full result set.")
    private final Long total;

    @JsonProperty
    @ApiModelProperty(value = "True if the total is exact, false if not known or an estimate.")
    private final boolean exact;

    @JsonCreator
    public PageResponse(@JsonProperty("offset") final long offset,
                        @JsonProperty("length") final int length,
                        @JsonProperty("total") final Long total,
                        @JsonProperty("exact") final boolean exact) {
        this.offset = offset;
        this.length = length;
        this.total = total;
        this.exact = exact;
    }

    public long getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public Long getTotal() {
        return total;
    }

    public boolean isExact() {
        return exact;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PageResponse that = (PageResponse) o;
        return offset == that.offset &&
                length == that.length &&
                exact == that.exact &&
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
