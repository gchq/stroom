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

import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Class to wrap Credentials with enough permission info
 * to help the UI.
 */
@JsonPropertyOrder({
        "credentials",
        "edit",
        "delete"
})
@JsonInclude(Include.NON_NULL)
public class CredentialsWithPerms {

    @JsonProperty
    private final Credentials credentials;

    /** Edit permission */
    @JsonProperty
    private final boolean edit;

    /** Delete permission */
    @JsonProperty
    private final boolean delete;

    /**
     * Used when we want credentials with all permissions.
     * @param credentials The credentials to store. Must not be null.
     */
    public CredentialsWithPerms(final Credentials credentials) {
        this(credentials, true, true);
    }

    /**
     * Constructor.
     * @param credentials Must not be null.
     * @param edit If the user has edit permission.
     * @param delete If the user has delete permission.
     */
    @JsonCreator
    public CredentialsWithPerms(
            @JsonProperty("credentials") final Credentials credentials,
            @JsonProperty("edit") final boolean edit,
            @JsonProperty("delete") final boolean delete) {
        Objects.requireNonNull(credentials);
        this.credentials = credentials;
        this.edit = edit;
        this.delete = delete;
    }

    /**
     * For test purposes. Not for general use.
     */
    @SuppressWarnings("unused")
    @SerialisationTestConstructor
    public CredentialsWithPerms() {
        this(new Credentials(),
                false,
                false);
    }

    /**
     * @return Never returns null.
     */
    public Credentials getCredentials() {
        return credentials;
    }

    public boolean isEdit() {
        return edit;
    }

    public boolean isDelete() {
        return delete;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CredentialsWithPerms that = (CredentialsWithPerms) o;
        return edit == that.edit && delete == that.delete && Objects.equals(credentials, that.credentials);
    }

    @Override
    public int hashCode() {
        return Objects.hash(credentials, edit, delete);
    }

    @Override
    public String toString() {
        return "CredentialsWithPerms{" +
               "credentials=" + credentials +
               ", edit=" + edit +
               ", delete=" + delete +
               '}';
    }
}
