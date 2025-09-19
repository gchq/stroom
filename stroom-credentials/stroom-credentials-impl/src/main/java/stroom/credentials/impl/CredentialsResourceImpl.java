package stroom.credentials.impl;

import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsResource;
import stroom.credentials.shared.CredentialsType;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CredentialsResourceImpl implements CredentialsResource {

    /** Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialsResourceImpl.class);

    /**
     * Return the list of credentials.
     * Mock for now.
     */
    @Override
    public ResultPage<Credentials> list(final PageRequest pageRequest) {
        LOGGER.info("Request for list of credentials");

        final List<Credentials> creds = new ArrayList<>();
        for (int i = 0; i < 10; ++i) {
            final Credentials cred = new Credentials("Cred " + i,
                    UUID.randomUUID().toString(),
                    CredentialsType.USERNAME_PASSWORD,
                    "username " + i,
                    "password " + i,
                    null,
                    null);
            creds.add(cred);
        }

        return new ResultPage<>(creds);
    }
}
