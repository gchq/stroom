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

import java.io.Serializable;
import java.util.Objects;

@JsonPropertyOrder({"offset", "length"})
@JsonInclude(Include.NON_NULL)
public class PageRequest implements Serializable, Copyable<PageRequest> {

    public static final int DEFAULT_PAGE_LENGTH = 100;

    /**
     * Offset from the start 0 is no offset.
     */
    @JsonProperty
    private Integer offset;
    /**
     * Page size to use, e.g. 10 is 10 records
     */
    @JsonProperty
    private Integer length;

    public PageRequest() {
        offset = 0;
    }

    @JsonCreator
    public PageRequest(@JsonProperty("offset") final Integer offset,
                       @JsonProperty("length") final Integer length) {
        this.offset = offset;
        this.length = length;
    }

    public static PageRequest createDefault() {
        return new PageRequest(0, DEFAULT_PAGE_LENGTH);
    }

    public static PageRequest oneRow() {
        return new PageRequest(0, 1);
    }

    public static PageRequest unlimited() {
        return new PageRequest(0, 10_000_000);
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(final Integer offset) {
        this.offset = offset;
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(final Integer maxLength) {
        this.length = maxLength;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PageRequest that = (PageRequest) o;
        return Objects.equals(offset, that.offset) &&
               Objects.equals(length, that.length);
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, length);
    }

    @Override
    public String toString() {
        return "PageRequest{" +
               "offset=" + offset +
               ", length=" + length +
               '}';
    }

    @Override
    public void copyFrom(final PageRequest other) {
        this.offset = other.offset;
        this.length = other.length;
    }
}
