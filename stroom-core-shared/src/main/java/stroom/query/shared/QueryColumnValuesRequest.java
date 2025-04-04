/*
 * Copyright 2017 Crown Copyright
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

package stroom.query.shared;

import stroom.query.api.Column;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class QueryColumnValuesRequest {

    @JsonProperty
    private final QuerySearchRequest searchRequest;
    @JsonProperty
    private final Column column;
    @JsonProperty
    private final String filter;
    @JsonProperty
    private final PageRequest pageRequest;

    @JsonCreator
    public QueryColumnValuesRequest(@JsonProperty("searchRequest") final QuerySearchRequest searchRequest,
                                    @JsonProperty("column") final Column column,
                                    @JsonProperty("filter") final String filter,
                                    @JsonProperty("pageRequest") final PageRequest pageRequest) {
        this.searchRequest = searchRequest;
        this.column = column;
        this.filter = filter;
        this.pageRequest = pageRequest;
    }

    public QuerySearchRequest getSearchRequest() {
        return searchRequest;
    }

    public Column getColumn() {
        return column;
    }

    public String getFilter() {
        return filter;
    }

    public PageRequest getPageRequest() {
        return pageRequest;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private QuerySearchRequest searchRequest;
        private Column column;
        private String filter;
        private PageRequest pageRequest;

        private Builder() {
        }

        private Builder(final QueryColumnValuesRequest searchRequest) {
            this.searchRequest = searchRequest.searchRequest;
            this.column = searchRequest.column;
            this.filter = searchRequest.filter;
            this.pageRequest = searchRequest.pageRequest;
        }

        public Builder searchRequest(final QuerySearchRequest searchRequest) {
            this.searchRequest = searchRequest;
            return this;
        }

        public Builder column(final Column column) {
            this.column = column;
            return this;
        }

        public Builder filter(final String filter) {
            this.filter = filter;
            return this;
        }

        public Builder pageRequest(final PageRequest pageRequest) {
            this.pageRequest = pageRequest;
            return this;
        }

        public QueryColumnValuesRequest build() {
            return new QueryColumnValuesRequest(
                    searchRequest,
                    column,
                    filter,
                    pageRequest);
        }
    }
}
