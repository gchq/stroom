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
public class CredentialResponse {

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
    private final CredentialWithPerms credentialWithPerms;

    /**
     * Constructor that just takes the status. Default values for other parameters.
     * @param status If the API call worked. Must not be null.
     */
    public CredentialResponse(final CredentialResponse.Status status) {
        Objects.requireNonNull(status);
        this.status = status;
        this.message = "";
        this.credentialWithPerms = null;
    }

    /**
     * Constructor that just takes the status. Default values for other parameters.
     * @param status If the API call worked. Must not be null.
     */
    public CredentialResponse(final CredentialResponse.Status status,
                              final String message) {
        Objects.requireNonNull(status);
        Objects.requireNonNull(message);
        this.status = status;
        this.message = message;
        this.credentialWithPerms = null;
    }

    /**
     * Constructor for successful getting of object.
     * @param cwp Optional credentials - can be null.
     */
    public CredentialResponse(final CredentialWithPerms cwp) {
        this.status = Status.OK;
        this.message = "";
        this.credentialWithPerms = cwp;
    }
//
//    /**
//     * Constructor for successful getting of object.
//     * @param secret Optional secret - can be null.
//     */
//    public CredentialsResponse(final CredentialsSecret secret) {
//        this.status = Status.OK;
//        this.message = "";
//        this.credentialWithPerms = null;
//    }

    /**
     * Constructor for deserialisation.
     */
    @SuppressWarnings("unused")
    @JsonCreator
    public CredentialResponse(@JsonProperty("status") final Status status,
                              @JsonProperty("message") final String message,
                              @JsonProperty("credentialWithPerms") final CredentialWithPerms cwp) {
        this.status = status;
        this.message = message;
        this.credentialWithPerms = cwp;
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
    public CredentialWithPerms getCredentialsWithPerms() {
        return credentialWithPerms;
    }
}
