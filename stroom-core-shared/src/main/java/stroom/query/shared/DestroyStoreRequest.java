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

import stroom.query.api.DestroyReason;
import stroom.query.api.QueryKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class DestroyStoreRequest {

    @JsonProperty
    private final QueryKey queryKey;
    @JsonProperty
    private final DestroyReason destroyReason;

    @JsonCreator
    public DestroyStoreRequest(
            @JsonProperty("queryKey") final QueryKey queryKey,
            @JsonProperty("destroyReason") final DestroyReason destroyReason) {
        this.queryKey = queryKey;
        this.destroyReason = destroyReason;
    }

    public QueryKey getQueryKey() {
        return queryKey;
    }

    public DestroyReason getDestroyReason() {
        return destroyReason;
    }

    @Override
    public String toString() {
        return "DestroyStoreRequest{" +
                "queryKey=" + queryKey +
                ", destroyReason=" + destroyReason +
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
        private DestroyReason destroyReason;

        private Builder() {
        }

        private Builder(final DestroyStoreRequest searchRequest) {
            this.queryKey = searchRequest.queryKey;
            this.destroyReason = searchRequest.destroyReason;
        }

        public Builder queryKey(final QueryKey queryKey) {
            this.queryKey = queryKey;
            return this;
        }

        public Builder destroyReason(final DestroyReason destroyReason) {
            this.destroyReason = destroyReason;
            return this;
        }

        public DestroyStoreRequest build() {
            return new DestroyStoreRequest(
                    queryKey,
                    destroyReason);
        }
    }
}
