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

package stroom.entity.shared;

import java.io.Serializable;

public class PageRequest implements Serializable, Copyable<PageRequest> {
    private static final long serialVersionUID = 6838082084157676358L;

    public static final int DEFAULT_PAGE_SIZE = 100;

    /**
     * Offset from the start 0 is no offset.
     */
    private Long offset;

    /**
     * Page size to use, e.g. 10 is 10 records
     */
    private Integer length;

    public PageRequest() {
        this(Long.valueOf(0), null);
    }

    public PageRequest(final Long offset) {
        this(offset, DEFAULT_PAGE_SIZE);
    }

    public PageRequest(final Long offset, final Integer length) {
        this.offset = offset;
        this.length = length;
    }

    public static PageRequest createBoundedPageRequest(final long offset, final int length) {
        final PageRequest request = new PageRequest();
        request.setOffset(offset);
        request.setLength(length);
        return request;
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

    public void setLength(final Integer maxLength) {
        this.length = maxLength;
    }

    @Override
    public String toString() {
        return "offset=" + offset + ", maxLength=" + length + " ";
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof PageRequest)) {
            return false;
        }

        final PageRequest pageRequest = (PageRequest) obj;
        if (notEqual(length, pageRequest.length)) {
            return false;
        }

        return !notEqual(offset, pageRequest.offset);

    }

    private boolean notEqual(final Object obj1, final Object obj2) {
        if (obj1 == null) {
            return obj2 != null;
        }

        return !obj1.equals(obj2);
    }

    private static final int MAGIC_HASH_VALUE = 31;

    @Override
    public int hashCode() {
        int hashCode = MAGIC_HASH_VALUE;
        if (length != null) {
            hashCode = hashCode * MAGIC_HASH_VALUE + length.hashCode();
        }
        if (offset != null) {
            hashCode = hashCode * MAGIC_HASH_VALUE + offset.hashCode();
        }

        return hashCode;
    }

    @Override
    public void copyFrom(final PageRequest other) {
        this.offset = other.offset;
        this.length = other.length;
    }
}
