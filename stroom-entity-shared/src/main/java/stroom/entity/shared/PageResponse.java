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

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

import java.io.Serializable;

public class PageResponse implements Serializable {
    private static final long serialVersionUID = -8613411971150227752L;

    private Long offset;
    private Integer length;
    private Long total;
    private boolean more;

    public PageResponse() {
        // Default constructor necessary for GWT serialisation.
    }

    public PageResponse(final Long offset, final Integer length, final Long total, final boolean more) {
        this.offset = offset;
        this.length = length;
        this.total = total;
        this.more = more;
    }

    public Long getOffset() {
        return offset;
    }

    public Integer getLength() {
        return length;
    }

    public Long getTotal() {
        return total;
    }

    public boolean isMore() {
        return more;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PageResponse)) {
            return false;
        }
        PageResponse other = (PageResponse) obj;
        EqualsBuilder builder = new EqualsBuilder();
        builder.append(this.offset, other.offset);
        builder.append(this.length, other.length);
        builder.append(this.total, other.total);
        builder.append(this.more, other.more);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(this.offset);
        builder.append(this.length);
        builder.append(this.total);
        builder.append(this.more);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        return offset + ".." + length + " of " + (total == null ? "?" : total) + " " + (more ? "more" : "");
    }
}
