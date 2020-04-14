package stroom.authentication.oauth2;

import stroom.security.impl.OpenIdClientDetails;

import javax.inject.Inject;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

public class OpenIdClientDetailsImpl implements OpenIdClientDetails {
    private static final String INTERNAL_STROOM_CLIENT = "Stroom Client Internal";

    private final String clientId;
    private final String clientSecret;

    @Inject
    public OpenIdClientDetailsImpl(final OAuth2ClientDao dao) {
        Optional<OAuth2Client> optionalClient = dao.getClientByName(INTERNAL_STROOM_CLIENT);
        if (optionalClient.isEmpty()) {
            final OAuth2Client client = new OAuth2Client(INTERNAL_STROOM_CLIENT, createRandomCode(40), createRandomCode(20), ".*");
            dao.create(client);
            optionalClient = dao.getClientByName(INTERNAL_STROOM_CLIENT);
        }

        if (optionalClient.isEmpty()) {
            throw new NullPointerException("Unable to get or create internal client details");
        }

        clientId = optionalClient.get().getClientId();
        clientSecret = optionalClient.get().getClientSecret();
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public String getClientSecret() {
        return clientSecret;
    }

    private static String createRandomCode(final int length) {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }
}
