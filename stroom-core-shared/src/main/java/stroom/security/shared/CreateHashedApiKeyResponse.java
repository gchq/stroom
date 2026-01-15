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

package stroom.security.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class CreateHashedApiKeyResponse {

    // The actual api key
    @JsonProperty
    private final String apiKey;

    // The DB mapped object containing the hash of the api key
    @JsonProperty
    private final HashedApiKey hashedApiKey;

    @JsonCreator
    public CreateHashedApiKeyResponse(@JsonProperty("apiKey") final String apiKey,
                                      @JsonProperty("hashedApiKey") final HashedApiKey hashedApiKey) {
        this.apiKey = apiKey;
        this.hashedApiKey = hashedApiKey;
    }

    public HashedApiKey getHashedApiKey() {
        return hashedApiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final CreateHashedApiKeyResponse that = (CreateHashedApiKeyResponse) object;
        return Objects.equals(apiKey, that.apiKey) && Objects.equals(hashedApiKey, that.hashedApiKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiKey, hashedApiKey);
    }

    @Override
    public String toString() {
        return "CreateApiKeyResponse{" +
                "apiKey='" + apiKey + '\'' +
                ", hashedApiKey=" + hashedApiKey +
                '}';
    }
}
