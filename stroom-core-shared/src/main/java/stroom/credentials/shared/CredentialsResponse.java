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

import java.util.Objects;

/**
 * Class to wrap up the return values from the Credentials Resource.
 */
@JsonInclude(Include.NON_NULL)
public class CredentialsResponse {

    /** Did the method call work? */
    public enum Status {
        /** Yes the call worked */
        OK,
        /** No the call didn't work */
        GENERAL_ERR
    }

    @JsonProperty
    private final Status status;

    @JsonProperty
    private final String message;

    @JsonProperty
    private final CredentialsWithPerms credentialsWithPerms;

    @JsonProperty
    private final CredentialsSecret secret;

    /**
     * Constructor that just takes the status. Default values for other parameters.
     * @param status If the API call worked. Must not be null.
     */
    public CredentialsResponse(final CredentialsResponse.Status status) {
        Objects.requireNonNull(status);
        this.status = status;
        this.message = "";
        this.credentialsWithPerms = null;
        this.secret = null;
    }

    /**
     * Constructor that just takes the status. Default values for other parameters.
     * @param status If the API call worked. Must not be null.
     */
    public CredentialsResponse(final CredentialsResponse.Status status,
                               final String message) {
        Objects.requireNonNull(status);
        Objects.requireNonNull(message);
        this.status = status;
        this.message = message;
        this.credentialsWithPerms = null;
        this.secret = null;
    }

    /**
     * Constructor for successful getting of object.
     * @param cwp Optional credentials - can be null.
     */
    public CredentialsResponse(final CredentialsWithPerms cwp) {
        this.status = Status.OK;
        this.message = "";
        this.credentialsWithPerms = cwp;
        this.secret = null;
    }

    /**
     * Constructor for successful getting of object.
     * @param secret Optional secret - can be null.
     */
    public CredentialsResponse(final CredentialsSecret secret) {
        this.status = Status.OK;
        this.message = "";
        this.credentialsWithPerms = null;
        this.secret = secret;
    }

    /**
     * Constructor for deserialisation.
     */
    @SuppressWarnings("unused")
    @JsonCreator
    public CredentialsResponse(@JsonProperty("status") final Status status,
                               @JsonProperty("message") final String message,
                               @JsonProperty("credentialsWithPerms") final CredentialsWithPerms cwp,
                               @JsonProperty("secret") final CredentialsSecret secret) {
        this.status = status;
        this.message = message;
        this.credentialsWithPerms = cwp;
        this.secret = secret;
    }

    /**
     * @return The status of the operation - whether it worked or something went wrong.
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @return Any message that might be useful to the user.
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return The credentials and permissions, if any.
     * Will return null if no credentials present.
     */
    public CredentialsWithPerms getCredentialsWithPerms() {
        return credentialsWithPerms;
    }

    /**
     * @return The secret, if any. Will return null if no secret present.
     */
    public CredentialsSecret getSecret() {
        return secret;
    }
}
