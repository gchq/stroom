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

package stroom.credentials.impl;

import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsSecret;
import stroom.credentials.shared.CredentialsType;

import java.io.IOException;
import java.util.List;

public interface CredentialsDao {

    /**
     * Returns a list of all the credentials in the database.
     * @throws IOException if something goes wrong.
     */
    List<Credentials> listCredentials() throws IOException;

    /**
     * Returns a list of all the credentials of a given type.
     * @param type The type of credentials to select, or all credentials if null.
     * @return The credentials that match the type.
     * @throws IOException if something goes wrong.
     */
    List<Credentials> listCredentials(CredentialsType type) throws IOException;

    /**
     * Creates new credentials within the database.
     * @param clientCredentials The credentials from the client to create.
     * @return The created credentials. These WILL NOT have the same UUID as the clientCredentials
     * parameter for security reasons.
     * @throws IOException if something goes wrong.
     */
    Credentials createCredentials(final Credentials clientCredentials) throws IOException;

    /**
     * Stores the given credential to the database.
     * @param credentials The credentials to store in the database.
     * @throws IOException if something goes wrong.
     */
    void storeCredentials(Credentials credentials) throws IOException;

    /**
     * Returns the credential matching the UUID.
     * @return The credential matching the UUID.
     */
    Credentials getCredentials(String uuid) throws IOException;

    /**
     * Deletes the credentials and secrets matching the UUID.
     */
    void deleteCredentialsAndSecret(String uuid) throws IOException;

    /**
     * Stores the given secret to the database.
     * @param secret The secret to store.
     * @throws IOException if something goes wrong.
     */
    void storeSecret(CredentialsSecret secret) throws IOException;

    /**
     * Returns the secret for the given UUID.
     * @param uuid The UUID of the corresponding Credentials object.
     * @return The secrets for the credentials.
     * @throws IOException if something goes wrong.
     */
    CredentialsSecret getSecret(String uuid) throws IOException;

}
