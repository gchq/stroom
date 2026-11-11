/*
 * Copyright 2025 Crown Copyright
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
import stroom.credentials.shared.ReplaceSecretRequest;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
public class CredentialsResourceImpl implements CredentialsResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CredentialsResourceImpl.class);

    private final Provider<CredentialsService> credentialsServiceProvider;

    @SuppressWarnings("unused")
    @Inject
    public CredentialsResourceImpl(final Provider<CredentialsService> credentialsServiceProvider) {
        this.credentialsServiceProvider = credentialsServiceProvider;
    }

    /// Return the list of credentials.
    /// Note that this isn't an efficient implementation when there are lots of credentials.
    /// However, this isn't considered a big problem at this stage.
    @Override
    public ResultPage<CredentialWithPerms> findCredentialsWithPermissions(final FindCredentialRequest request) {
        final ResultPage<CredentialWithPerms> result = credentialsServiceProvider.get()
                .findCredentialsWithPermissions(request);
        LOGGER.debug("findCredentialsWithPermissions() - request: {}, result: {}", request, result);
        return result;
    }

    /// Return the list of credentials.
    /// Note that this isn't an efficient implementation when there are lots of credentials.
    /// However, this isn't considered a big problem at this stage.
    @Override
    public ResultPage<Credential> findCredentials(final FindCredentialRequest request) {
        final ResultPage<Credential> result = credentialsServiceProvider.get()
                .findCredentials(request);
        LOGGER.debug("findCredentials() - request: {}, result: {}", request, result);
        return result;
    }

    @Override
    public Credential storeCredential(final PutCredentialRequest request) {
        credentialsServiceProvider.get().storeCredentials(request);
        final Credential result = request.getCredential();
        LOGGER.debug("storeCredential() - request: {}, result: {}", request, result);
        return result;
    }

    // Logged as an update of the credential. The request (holding the secret) is not logged.
    @AutoLogged(OperationType.UPDATE)
    @Override
    public Credential replaceSecret(final ReplaceSecretRequest request) {
        // Never log the request itself as it holds the secret
        LOGGER.debug("Replacing secret for credential '{}'", request.getUuid());
        return credentialsServiceProvider.get().replaceSecret(request);
    }

    @Override
    public Credential getCredentialByUuid(final String uuid) {
        final Credential result = credentialsServiceProvider.get().getCredentialByUuid(uuid);
        LOGGER.debug("getCredentialByUuid() - uuid: {}, result: {}", uuid, result);
        return result;
    }

    @Override
    public Credential getCredentialByName(final String name) {
        final Credential result = credentialsServiceProvider.get().getCredentialByName(name);
        LOGGER.debug("getCredentialByName() - name: {}, result: {}", name, result);
        return result;
    }

    @Override
    public Boolean deleteCredentials(final String uuid) {
        credentialsServiceProvider.get().deleteCredentialsAndSecret(uuid);
        final Boolean result = true;
        LOGGER.debug("deleteCredentials() - uuid: {}, result: {}", uuid, result);
        return result;
    }
}
