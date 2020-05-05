package stroom.authentication.oauth2;

import stroom.authentication.api.OAuth2Client;
import stroom.authentication.api.OpenIdClientDetailsFactory;
import stroom.authentication.config.AuthenticationConfig;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LogUtil;

import org.apache.commons.codec.binary.Hex;

import javax.inject.Inject;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

public class OpenIdClientDetailsFactoryImpl implements OpenIdClientDetailsFactory {
    private static final String INTERNAL_STROOM_CLIENT = "Stroom Client Internal";
    private static final String CLIENT_ID_SUFFIX = ".client-id.apps.stroom-idp";
    private static final String CLIENT_SECRET_SUFFIX = ".client-secret.apps.stroom-idp";

    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final OAuth2Client oAuth2Client;

    @Inject
    public OpenIdClientDetailsFactoryImpl(final OAuth2ClientDao dao,
                                          final AuthenticationConfig authenticationConfig,
                                          final DefaultOpenIdCredentials defaultOpenIdCredentials) {
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;

        // TODO The way this is implemented means we are limited to a single client when using our
        //   internal auth provider.  Not sure this is what we want when we have stroom-stats in the
        //   mix. However to manage multiple client IDs we would probably need UI pages to do the CRUD on them.

        final OAuth2Client oAuth2Client;
        if (authenticationConfig.isUseDefaultOpenIdCredentials()) {
            oAuth2Client = createDefaultOAuthClient();
        } else {
            oAuth2Client = dao.getClientByName(INTERNAL_STROOM_CLIENT)
                    .or(() -> {
                        // Generate new randomised client details and persist them
                        final OAuth2Client newOAuth2Client = createRandomisedOAuth2Client(INTERNAL_STROOM_CLIENT);
                        dao.create(newOAuth2Client);
                        return dao.getClientByName(INTERNAL_STROOM_CLIENT);
                    })
                    .orElseThrow(() ->
                            new NullPointerException("Unable to get or create internal client details"));
        }
        this.oAuth2Client = oAuth2Client;
    }

    public OAuth2Client getOAuth2Client() {
        return oAuth2Client;
    }

    public OAuth2Client getOAuth2Client(final String clientId) {
        // TODO currently only support one client ID so just have to throw if the client id is wrong
        if (!Objects.requireNonNull(clientId).equals(oAuth2Client.getClientId())) {
            throw new RuntimeException(LogUtil.message(
                    "Unexpected client ID: {}, expecting {}", clientId, oAuth2Client.getClientId()));
        }
        return oAuth2Client;
    }

    private OAuth2Client createDefaultOAuthClient() {
        return new OAuth2Client(
                defaultOpenIdCredentials.getOauth2ClientName(),
                defaultOpenIdCredentials.getOauth2ClientId(),
                defaultOpenIdCredentials.getOauth2ClientSecret(),
                defaultOpenIdCredentials.getOauth2ClientUriPattern());
    }

    static OAuth2Client createRandomisedOAuth2Client(final String name) {
        return new OAuth2Client(
                name,
                createRandomCode(40) + CLIENT_ID_SUFFIX,
                createRandomCode(20) + CLIENT_SECRET_SUFFIX,
                ".*");
    }

    private static String createRandomCode(final int length) {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    private static String createRandomHexCode(final int length) {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return Hex.encodeHexString(bytes, true);
    }
}
