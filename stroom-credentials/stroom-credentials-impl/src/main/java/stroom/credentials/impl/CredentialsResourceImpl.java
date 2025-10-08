package stroom.credentials.impl;

import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsResource;
import stroom.credentials.shared.CredentialsResponse;
import stroom.credentials.shared.CredentialsResponse.Status;
import stroom.credentials.shared.CredentialsSecret;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@AutoLogged
public class CredentialsResourceImpl implements CredentialsResource {

    /** DAO to talk to the DB */
    private final Provider<CredentialsDao> credentialsDao;

    /** Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialsResourceImpl.class);

    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    public CredentialsResourceImpl(final Provider<CredentialsDao> credentialsDao) {
        this.credentialsDao = credentialsDao;
    }

    /**
     * Return the list of credentials.
     * Note that this isn't an efficient implementation when there are lots of credentials.
     * However, this isn't considered a big problem at this stage.
     */
    @Override
    public ResultPage<Credentials> listCredentials(final PageRequest pageRequest) {
        LOGGER.info("Request for list of credentials");
        try {
            final List<Credentials> fullList = credentialsDao.get().listCredentials();

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
    public CredentialsResponse storeCredentials(final Credentials credentials) {
        LOGGER.info("Storing credentials '{}'", credentials);
        CredentialsResponse response;
        try {
            credentialsDao.get().storeCredentials(credentials);

            response = new CredentialsResponse(Status.OK);

        } catch (final IOException e) {
            response = new CredentialsResponse(Status.GENERAL_ERR, e.getMessage());
        }
        return response;
    }

    @Override
    public CredentialsResponse getCredentials(final String uuid) {
        LOGGER.info("Getting credentials for '{}'", uuid);
        final Credentials credentials;
        try {
            credentials = credentialsDao.get().getCredentials(uuid);
            return new CredentialsResponse(credentials);
        } catch (final IOException e) {
            return new CredentialsResponse(Status.GENERAL_ERR,
                    "Error getting credentials: " + e.getMessage());
        }
    }

    @Override
    public CredentialsResponse deleteCredentials(final String uuid) {
        LOGGER.info("Deleting credentials for '{}'", uuid);
        try {
            credentialsDao.get().deleteCredentialsAndSecret(uuid);
            return new CredentialsResponse(Status.OK);
        } catch (final IOException e) {
            return new CredentialsResponse(Status.GENERAL_ERR,
                    "Error deleting credentials: "
                    + e.getMessage());
        }
    }

    @Override
    public CredentialsResponse storeSecret(final CredentialsSecret secret) {
        LOGGER.info("Storing credentials secret for '{}'", secret.getUuid());
        try {
            credentialsDao.get().storeSecret(secret);
            return new CredentialsResponse(Status.OK);
        } catch (final IOException e) {
            return new CredentialsResponse(Status.GENERAL_ERR,
                    "Error storing secret: "
                    + e.getMessage());
        }
    }

    @Override
    public CredentialsResponse getSecret(final String credentialsId) {
        LOGGER.info("Getting credentials secret for '{}'", credentialsId);
        try {
            final CredentialsSecret secret = credentialsDao.get().getSecret(credentialsId);
            return new CredentialsResponse(secret);
        } catch (final IOException e) {
            return new CredentialsResponse(Status.GENERAL_ERR,
                    "Error getting secret: "
                    + e.getMessage());
        }
    }
}
