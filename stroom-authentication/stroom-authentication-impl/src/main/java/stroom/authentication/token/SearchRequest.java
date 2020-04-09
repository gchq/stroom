/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.authentication.token;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.Map;

public class SearchRequest {
    @NotNull
    private int page;

    @NotNull
    private int limit;

    @Nullable
    @Pattern(
            regexp = "^enabled$|^userEmail$|^issueByUser$|^token$|^tokenType$|^updatedByUser$|^expiresOn$|^issuedOn$|^updatedOn$",
            message = "orderBy must be one of: 'enabled', 'userEmail', 'issuedByUser', 'token', 'tokenType', 'updatedByUser', 'expiresOn', 'issuedOn', 'updatedOn'")
    private String orderBy;

    @Nullable
    @Pattern(regexp = "^asc$|^desc$", message = "orderDirection must be 'asc' or 'desc'")
    private String orderDirection;

    @Nullable
    private Map<String, String> filters;

    public int getPage() {
        return page;
    }

    public void setPage(int page){
        this.page = page;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public String getOrderDirection() {
        return orderDirection;
    }

    public void setOrderDirection(String orderDirection) {
        this.orderDirection = orderDirection;
    }

    public Map<String, String> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, String> filters){
        this.filters = filters;
    }

    public static final class SearchRequestBuilder {
        private int page;
        private int limit;
        private String orderBy;
        private String orderDirection;
        private Map<String, String> filters;

        public SearchRequestBuilder() {
        }

        public SearchRequestBuilder page(int page) {
            this.page = page;
            return this;
        }

        public SearchRequestBuilder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public SearchRequestBuilder orderBy(String orderBy) {
            this.orderBy = orderBy;
            return this;
        }

        public SearchRequestBuilder orderDirection(String orderDirection) {
            this.orderDirection = orderDirection;
            return this;
        }

        public SearchRequestBuilder filters(Map<String, String> filters) {
            this.filters = filters;
            return this;
        }

        public SearchRequest build() {
            SearchRequest searchRequest = new SearchRequest();
            searchRequest.orderBy = this.orderBy;
            searchRequest.limit = this.limit;
            searchRequest.orderDirection = this.orderDirection;
            searchRequest.filters = this.filters;
            searchRequest.page = this.page;
            return searchRequest;
        }
    }
}
