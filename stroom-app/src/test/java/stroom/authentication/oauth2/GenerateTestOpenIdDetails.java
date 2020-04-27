package stroom.authentication.oauth2;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.jose4j.jwk.PublicJsonWebKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.account.Account;
import stroom.authentication.account.AccountService;
import stroom.authentication.account.CreateAccountRequest;
import stroom.authentication.api.JsonWebKeyFactory;
import stroom.authentication.token.CreateTokenRequest;
import stroom.authentication.token.Token;
import stroom.authentication.token.TokenService;
import stroom.security.impl.OAuth2Client;
import stroom.test.CoreTestModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.ConsoleColour;
import stroom.util.authentication.DefaultOpenIdCredentials;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * Used for generating a set of OpenId Connect credentials and a corresponding
 * API key so that stroom-proxy can connect to stroom on first boot.
 * ONLY intended for test/demo purposes.
 */
public class GenerateTestOpenIdDetails {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateTestOpenIdDetails.class);

    private static final String CLIENT_NAME = "Stroom Client Internal (TEST ONLY)";
    private static final String API_KEY_USER_EMAIL = "default-test-only-api-key-user";

    private final JsonWebKeyFactory jsonWebKeyFactory;
    private final TokenService tokenService;
    private final AccountService accountService;

    @Inject
    public GenerateTestOpenIdDetails(final JsonWebKeyFactory jsonWebKeyFactory,
                                     final TokenService tokenService,
                                     final AccountService accountService) {
        this.jsonWebKeyFactory = jsonWebKeyFactory;
        this.tokenService = tokenService;
        this.accountService = accountService;
    }


    public static void main(String[] args) {
        Injector injector = Guice.createInjector(
                new DbTestModule(),
                new CoreTestModule());

        injector.getInstance(GenerateTestOpenIdDetails.class).run();
    }

    private void run() {

        // Create a public key
        final PublicJsonWebKey publicJsonWebKey = jsonWebKeyFactory.createPublicKey();
        final String publicKeyAsJsonStr = jsonWebKeyFactory.asJson(publicJsonWebKey);

        final OAuth2Client oAuth2Client = OpenIdClientDetailsFactoryImpl.createRandomisedOAuth2Client(CLIENT_NAME);

        // Create an account to satisfy the create token call lower down
        final Account account = accountService.read(API_KEY_USER_EMAIL)
                .orElseGet(() -> {
                    CreateAccountRequest createAccountRequest = new CreateAccountRequest(
                            "-",
                            "-",
                            API_KEY_USER_EMAIL,
                            null,
                            "password",
                            false,
                            true);
                    return accountService.create(createAccountRequest);
                });


        final CreateTokenRequest tokenRequest = new CreateTokenRequest(
            oAuth2Client.getClientId(),
                API_KEY_USER_EMAIL,
                "api",
                true,
                null,
                Date.from(
                        LocalDate.of(3001, 12, 12).atStartOfDay(ZoneId.systemDefault())
                                .toInstant()));

        final Token token = tokenService.create(tokenRequest);

        final String escapedPublicKeyAsJsonStr = publicKeyAsJsonStr.replace("\"", "\\\"");

        final String msg = "\n" +
                "\n" +
                "\nCopy/paste the following lines into " + DefaultOpenIdCredentials.class.getName() +
                "\n";

        final String txt = "\n" +
                "\n// The values between the lines were generated using " + this.getClass().getName() + "\"" +
                "\n// ------------------------------------------------------------------------------------------------" +
                "\nprivate static final String OAUTH2_CLIENT_ID = \"" + oAuth2Client.getClientId() + "\";" +
                "\nprivate static final String OAUTH2_CLIENT_NAME = \"" + oAuth2Client.getName() + "\";" +
                "\nprivate static final String OAUTH2_CLIENT_SECRET = \"" + oAuth2Client.getClientSecret() + "\";" +
                "\nprivate static final String OAUTH2_CLIENT_URI_PATTERN = \"" + oAuth2Client.getUriPattern() + "\";" +

                "\nprivate static final String PUBLIC_KEY_ID = \"" + publicJsonWebKey.getKeyId() + "\";" +
                "\nprivate static final String PUBLIC_KEY_JSON = \"" + escapedPublicKeyAsJsonStr + "\";" +

                "\nprivate static final String API_KEY_USER_EMAIL = \"" + API_KEY_USER_EMAIL + "\";" +
                "\nprivate static final String API_KEY = \"" + token.getData() + "\";" +
                "\n// ------------------------------------------------------------------------------------------------";

                LOGGER.info(ConsoleColour.red(msg) + ConsoleColour.green(txt));

        // Delete the DB records created.
        tokenService.delete(token.getId());
        accountService.delete(account.getId());
    }

}
