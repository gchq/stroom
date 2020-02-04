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

package stroom.authentication.resources.token.v1;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.Map;

@ApiModel(description = "A request for a search over tokens.")
public class SearchRequest {
    @NotNull
    @ApiModelProperty(value = "The page of search results to retrieve.", required = true)
    private int page;

    @NotNull
    @ApiModelProperty(value = "The number of tokens in a page of search results.", required = true)
    private int limit;

    @Nullable
    @Pattern(
            regexp = "^enabled$|^userEmail$|^issueByUser$|^token$|^tokenType$|^updatedByUser$|^expiresOn$|^issuedOn$|^updatedOn$",
            message = "orderBy must be one of: 'enabled', 'userEmail', 'issuedByUser', 'token', 'tokenType', 'updatedByUser', 'expiresOn', 'issuedOn', 'updatedOn'")
    @ApiModelProperty(value = "The property by which to order the results.", required = false)
    private String orderBy;

    @Nullable
    @Pattern(regexp = "^asc$|^desc$", message = "orderDirection must be 'asc' or 'desc'")
    @ApiModelProperty(value = "The direction in which to order the results.", required = false)
    private String orderDirection;

    @Nullable
    @ApiModelProperty(
            value = "How to filter the results. This is done by property, e.g. user_email, 'someone@someplace.com'.",
            required = false)
    private Map<String, String> filters;

    public int getPage() {
        return page;
    }

    public int getLimit() {
        return limit;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public String getOrderDirection() {
        return orderDirection;
    }

    public Map<String, String> getFilters() {
        return filters;
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
