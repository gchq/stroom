package stroom.credentials.impl;

import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsResource;
import stroom.credentials.shared.CredentialsResponse;
import stroom.credentials.shared.CredentialsResponse.Status;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class CredentialsResourceImpl implements CredentialsResource {

    /** DAO to talk to the DB */
    private final CredentialsDao credentialsDao;

    /** Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialsResourceImpl.class);

    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    public CredentialsResourceImpl(final CredentialsDao credentialsDao) {
        this.credentialsDao = credentialsDao;
    }

    /**
     * Return the list of credentials.
     * Note that this isn't an efficient implementation when there are lots of credentials.
     * However, this isn't considered a big problem at this stage.
     */
    @Override
    public ResultPage<Credentials> list(final PageRequest pageRequest) {
        LOGGER.info("Request for list of credentials");
        try {
            final List<Credentials> fullList = credentialsDao.list();

            final int start = Math.max(pageRequest.getOffset(), 0);
            final int end = Math.min(start + pageRequest.getLength(), fullList.size());
            return new ResultPage<>(fullList.subList(start, end),
                    new PageResponse(start, end - start, (long)(end - start), true));
        } catch (final IOException e) {
            LOGGER.error("Error retrieving the list of credentials: {}", e.getMessage(), e);
            return new ResultPage<>(Collections.emptyList());
        }
    }

    @Override
    public CredentialsResponse store(final Credentials credentials) {
        LOGGER.info("Storing credentials '{}'", credentials);
        CredentialsResponse response;
        try {
            credentialsDao.store(credentials);
            response = new CredentialsResponse(Status.OK, "");
        } catch (final IOException e) {
            response = new CredentialsResponse(Status.GENERAL_ERR, e.getMessage());
        }
        return response;
    }

    @Override
    public Credentials get(final String uuid) throws IOException {
        LOGGER.info("Getting credentials for '{}'", uuid);
        return credentialsDao.get(uuid);
    }

    @Override
    public CredentialsResponse delete(final String uuid) {
        LOGGER.info("Deleting credentials for '{}'", uuid);
        try {
            credentialsDao.delete(uuid);
            return new CredentialsResponse(Status.OK, "");
        } catch (final IOException e) {
            return new CredentialsResponse(Status.GENERAL_ERR,
                    "Error deleting credentials: "
                    + e.getMessage());
        }
    }
}
