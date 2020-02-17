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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * List that knows how big the whole set is.
 */
@JsonInclude(Include.NON_DEFAULT)
public class ResultPage<T> {
    @JsonProperty
    private List<T> values;
    @JsonProperty
    private PageResponse pageResponse;

    public ResultPage() {
        // Default constructor necessary for GWT serialisation.
    }

    public ResultPage(final List<T> values) {
        this.values = values;
        this.pageResponse = new PageResponse(0L, values.size(), (long) values.size(), true);
    }

    @JsonCreator
    public ResultPage(@JsonProperty("values") final List<T> values,
                      @JsonProperty("pageResponse") final PageResponse pageResponse) {
        this.values = values;
        this.pageResponse = pageResponse;
    }

    public List<T> getValues() {
        return values;
    }

    public void setValues(final List<T> values) {
        this.values = values;
    }

    public PageResponse getPageResponse() {
        return pageResponse;
    }

    public void setPageResponse(final PageResponse pageResponse) {
        this.pageResponse = pageResponse;
    }

    @JsonIgnore
    public <R extends ResultPage<T>> R unlimited(final List<T> values) {
        return limited(values, null);
    }

    @JsonIgnore
    @SuppressWarnings("unchecked")
    public <R extends ResultPage<T>> R limited(final List<T> fullList, final PageRequest pageRequest) {
        values = fullList;
        pageResponse = new PageResponse((long) 0, fullList.size(), (long) fullList.size(), true);

        if (pageRequest != null) {
            int offset = 0;
            if (pageRequest.getOffset() != null) {
                offset = pageRequest.getOffset().intValue();
            }

            int length = fullList.size() - offset;
            if (pageRequest.getLength() != null) {
                length = Math.min(length, pageRequest.getLength());
            }

            // If the page request will lead to a limited number of results then apply that limit here.
            if (offset != 0 || length < fullList.size()) {
                // Ideally we'd use List.subList here but can't as GWT can't serialise the returned list type.
//                final List<T> limited = fullList.subList(offset, offset + length);
                final List<T> limited = new ArrayList<>(length);
                for (int i = offset; i < offset + length; i++) {
                    limited.add(fullList.get(i));
                }

                values = limited;
                pageResponse = new PageResponse((long) offset, limited.size(), (long) fullList.size(), true);
            }
        }

        return (R) this;
    }
}
