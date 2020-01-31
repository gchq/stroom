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

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * List that knows how big the whole set is.
 */
public class ResultPage<T> {
    private List<T> values;
    private PageResponse pageResponse;

    public ResultPage() {
        // Default constructor necessary for GWT serialisation.
    }

    public ResultPage(final List<T> values) {
        this.values = values;
        this.pageResponse = new PageResponse(0L, values.size(), (long) values.size(), true);
    }

    public ResultPage(final List<T> values, final PageResponse pageResponse) {
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
    public void init(final List<T> values) {
        this.values = values;
        this.pageResponse = new PageResponse(0L, values.size(), (long) values.size(), true);
    }
}
