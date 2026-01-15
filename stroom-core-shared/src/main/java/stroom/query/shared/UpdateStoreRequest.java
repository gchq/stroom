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

package stroom.query.shared;

import stroom.query.api.LifespanInfo;
import stroom.query.api.QueryKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class UpdateStoreRequest {

    @JsonProperty
    private final QueryKey queryKey;
    @JsonProperty
    private final LifespanInfo searchProcessLifespan;
    @JsonProperty
    private final LifespanInfo storeLifespan;

    @JsonCreator
    public UpdateStoreRequest(
            @JsonProperty("queryKey") final QueryKey queryKey,
            @JsonProperty("searchProcessLifespan") final LifespanInfo searchProcessLifespan,
            @JsonProperty("storeLifespan") final LifespanInfo storeLifespan) {
        this.queryKey = queryKey;
        this.searchProcessLifespan = searchProcessLifespan;
        this.storeLifespan = storeLifespan;
    }

    public QueryKey getQueryKey() {
        return queryKey;
    }

    public LifespanInfo getSearchProcessLifespan() {
        return searchProcessLifespan;
    }

    public LifespanInfo getStoreLifespan() {
        return storeLifespan;
    }

    @Override
    public String toString() {
        return "UpdateStoreRequest{" +
                "queryKey=" + queryKey +
                ", searchProcessLifespan=" + searchProcessLifespan +
                ", storeLifespan=" + storeLifespan +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private QueryKey queryKey;
        private LifespanInfo searchProcessLifespan;
        private LifespanInfo storeLifespan;

        private Builder() {
        }

        private Builder(final UpdateStoreRequest searchRequest) {
            this.queryKey = searchRequest.queryKey;
            this.searchProcessLifespan = searchRequest.searchProcessLifespan;
            this.storeLifespan = searchRequest.storeLifespan;
        }

        public Builder queryKey(final QueryKey queryKey) {
            this.queryKey = queryKey;
            return this;
        }

        public Builder searchProcessLifespan(final LifespanInfo searchProcessLifespan) {
            this.searchProcessLifespan = searchProcessLifespan;
            return this;
        }

        public Builder storeLifespan(final LifespanInfo storeLifespan) {
            this.storeLifespan = storeLifespan;
            return this;
        }

        public UpdateStoreRequest build() {
            return new UpdateStoreRequest(
                    queryKey,
                    searchProcessLifespan,
                    storeLifespan);
        }
    }
}
