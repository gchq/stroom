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

import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.CredentialsResource;
import stroom.credentials.shared.FindCredentialRequest;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.UUID;

@AutoLogged
public class CredentialsResourceImpl implements CredentialsResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CredentialsResourceImpl.class);

    /**
     * Service to talk to the DB
     */
    private final Provider<CredentialsService> credentialsServiceProvider;

    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    public CredentialsResourceImpl(final Provider<CredentialsService> credentialsServiceProvider) {
        this.credentialsServiceProvider = credentialsServiceProvider;
    }

    /**
     * Return the list of credentials.
     * Note that this isn't an efficient implementation when there are lots of credentials.
     * However, this isn't considered a big problem at this stage.
     */
    @Override
    public ResultPage<CredentialWithPerms> findCredentialsWithPermissions(final FindCredentialRequest request) {
        LOGGER.debug("Request for list of credentials");
        return credentialsServiceProvider.get().findCredentialsWithPermissions(request);
    }

    /**
     * Return the list of credentials.
     * Note that this isn't an efficient implementation when there are lots of credentials.
     * However, this isn't considered a big problem at this stage.
     */
    @Override
    public ResultPage<Credential> findCredentials(final FindCredentialRequest request) {
        LOGGER.debug("Request for list of credentials");
        return credentialsServiceProvider.get().findCredentials(request);
    }

    @Override
    public DocRef createDocRef() {
        return new DocRef(Credential.TYPE, UUID.randomUUID().toString());
    }

    @Override
    public Credential storeCredential(final PutCredentialRequest request) {
        LOGGER.debug("Storing credentials '{}'", request);
        credentialsServiceProvider.get().storeCredentials(request);
        return request.getCredential();
    }

    @Override
    public Credential getCredentialByUuid(final String uuid) {
        LOGGER.debug("Getting credential for '{}'", uuid);
        return credentialsServiceProvider.get().getCredentialByUuid(uuid);
    }

    @Override
    public Credential getCredentialByName(final String name) {
        LOGGER.debug("Getting credential for '{}'", name);
        return credentialsServiceProvider.get().getCredentialByName(name);
    }

    @Override
    public Boolean deleteCredentials(final String uuid) {
        LOGGER.debug("Deleting credentials for '{}'", uuid);
        credentialsServiceProvider.get().deleteCredentialsAndSecret(uuid);
        return true;
    }
}
