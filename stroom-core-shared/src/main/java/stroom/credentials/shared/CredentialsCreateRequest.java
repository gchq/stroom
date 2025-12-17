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

package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Request to create new credentials and associated secrets in the DB.
 */
@JsonPropertyOrder({
        "credentials",
        "secret"
})
@JsonInclude(Include.NON_NULL)
public class CredentialsCreateRequest {

    @JsonProperty
    private final Credentials credentials;

    @JsonProperty
    private final CredentialsSecret secret;

    @JsonCreator
    public CredentialsCreateRequest(
            @JsonProperty("credentials") final Credentials credentials,
            @JsonProperty("secret") final CredentialsSecret secret) {
        this.credentials = credentials;
        this.secret = secret;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    public CredentialsSecret getSecret() {
        return secret;
    }

}
