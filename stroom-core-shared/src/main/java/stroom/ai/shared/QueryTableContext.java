/*
 * Copyright 2025 Crown Copyright
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

package stroom.ai.shared;

import stroom.query.shared.QuerySearchRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class QueryTableContext extends AskStroomAiContext {

    @JsonProperty
    private final String description;
    @JsonProperty
    private final String node;
    @JsonProperty
    private final QuerySearchRequest searchRequest;

    @JsonCreator
    public QueryTableContext(@JsonProperty("description") final String description,
                             @JsonProperty("node") final String node,
                             @JsonProperty("searchRequest") final QuerySearchRequest searchRequest) {
        this.description = description;
        this.node = node;
        this.searchRequest = searchRequest;
    }

    @Override
    public String getDescription() {
        return description != null
                ? description
                : "Query table";
    }

    public QuerySearchRequest getSearchRequest() {
        return searchRequest;
    }

    public String getNode() {
        return node;
    }
}
