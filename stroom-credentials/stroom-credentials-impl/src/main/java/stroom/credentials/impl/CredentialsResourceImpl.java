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
import stroom.credentials.shared.CredentialsCreateRequest;
import stroom.credentials.shared.CredentialsResource;
import stroom.credentials.shared.CredentialsResponse;
import stroom.credentials.shared.CredentialsResponse.Status;
import stroom.credentials.shared.CredentialsSecret;
import stroom.credentials.shared.CredentialsWithPerms;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@AutoLogged
public class CredentialsResourceImpl implements CredentialsResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CredentialsResourceImpl.class);

    /** Service to talk to the DB */
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
    public ResultPage<CredentialsWithPerms> listCredentials(final PageRequest pageRequest) {
        LOGGER.debug("Request for list of credentials");
        try {
            final List<CredentialsWithPerms> fullList = credentialsServiceProvider.get().listCredentials();

            final int start = Math.max(pageRequest.getOffset(), 0);
            final int end = Math.min(start + pageRequest.getLength(), fullList.size());
            return new ResultPage<>(fullList.subList(start, end),
                    new PageResponse(start, end - start, (long) (end - start), true));
        } catch (final IOException e) {
            LOGGER.error("Error retrieving the list of credentials: {}", e.getMessage(), e);
            return new ResultPage<>(Collections.emptyList());
        }
    }

    @Override
    public CredentialsResponse createCredentials(final CredentialsCreateRequest request) {
        LOGGER.debug("Creating credentials '{}'", request.getCredentials());
        CredentialsResponse response;
        try {
            final CredentialsWithPerms dbCwp =
                    credentialsServiceProvider.get().createCredentials(request.getCredentials(), request.getSecret());
            response = new CredentialsResponse(dbCwp);
        } catch (final IOException e) {
            LOGGER.error("Error creating credentials: {}", e.getMessage(), e);
            response = new CredentialsResponse(Status.GENERAL_ERR, e.getMessage());
        }

        return response;
    }

    @Override
    public CredentialsResponse storeCredentials(final Credentials credentials) {
        LOGGER.debug("Storing credentials '{}'", credentials);
        CredentialsResponse response;
        try {
            credentialsServiceProvider.get().storeCredentials(credentials);

            response = new CredentialsResponse(Status.OK);

        } catch (final IOException e) {
            response = new CredentialsResponse(Status.GENERAL_ERR, e.getMessage());
        }
        return response;
    }

    @Override
    public CredentialsResponse getCredentials(final String uuid) {
        LOGGER.debug("Getting credentials for '{}'", uuid);
        final CredentialsWithPerms cwp;
        try {
            cwp = credentialsServiceProvider.get().getCredentials(uuid);
            return new CredentialsResponse(cwp);
        } catch (final IOException e) {
            return new CredentialsResponse(Status.GENERAL_ERR,
                    "Error getting credentials: " + e.getMessage());
        }
    }

    @Override
    public CredentialsResponse deleteCredentials(final String uuid) {
        LOGGER.debug("Deleting credentials for '{}'", uuid);
        try {
            credentialsServiceProvider.get().deleteCredentialsAndSecret(uuid);
            return new CredentialsResponse(Status.OK);
        } catch (final IOException e) {
            return new CredentialsResponse(Status.GENERAL_ERR,
                    "Error deleting credentials: "
                    + e.getMessage());
        }
    }

    @Override
    public CredentialsResponse storeSecret(final CredentialsSecret secret) {
        LOGGER.debug("Storing credentials secret for '{}'", secret.getUuid());
        try {
            credentialsServiceProvider.get().storeSecret(secret);
            return new CredentialsResponse(Status.OK);
        } catch (final IOException e) {
            return new CredentialsResponse(Status.GENERAL_ERR,
                    "Error storing secret: "
                    + e.getMessage());
        }
    }

    @Override
    public CredentialsResponse getSecret(final String credentialsId) {
        LOGGER.debug("Getting credentials secret for '{}'", credentialsId);
        try {
            final CredentialsSecret secret = credentialsServiceProvider.get().getSecret(credentialsId);
            return new CredentialsResponse(secret);
        } catch (final IOException e) {
            return new CredentialsResponse(Status.GENERAL_ERR,
                    "Error getting secret: "
                    + e.getMessage());
        }
    }
}
