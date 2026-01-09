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

import stroom.credentials.api.StoredSecret;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.FindCredentialRequest;
import stroom.util.shared.ResultPage;

import java.util.function.Function;
import java.util.function.Predicate;

public interface CredentialsDao {

    /**
     * Returns a list of all the credentials in the database meeting the specified criteria and decorated with
     * permissions.
     */
    @SuppressWarnings("checkstyle:LineLength")
    ResultPage<CredentialWithPerms> findCredentialsWithPermissions(FindCredentialRequest request,
                                                                   Function<Credential, CredentialWithPerms> permissionDecorator);

    /**
     * Returns a list of all the credentials in the database meeting the specified criteria.
     */
    ResultPage<Credential> findCredentials(FindCredentialRequest request,
                                           Predicate<Credential> permissionFilter);

    /**
     * Returns the credential matching the UUID.
     *
     * @return The credential matching the UUID.
     */
    Credential getCredentialByUuid(String uuid);

    /**
     * Returns the credential matching the name.
     *
     * @return The credential matching the name.
     */
    Credential getCredentialByName(String name);

    /**
     * Deletes the credentials and secrets matching the UUID.
     */
    void deleteCredentialsAndSecret(String uuid);

    /**
     * Returns the credential matching the name.
     *
     * @return The credential and secret matching the name.
     */
    StoredSecret getStoredSecretByName(String name);

    /**
     * Stores the given secret to the database.
     *
     * @param secret The secret to store.
     */
    void putStoredSecret(StoredSecret secret, boolean update);
}
