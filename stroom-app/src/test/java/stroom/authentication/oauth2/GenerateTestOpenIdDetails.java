package stroom.authentication.oauth2;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.lang.JoseException;
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
import stroom.test.CommonTestControl;
import stroom.test.CoreTestModule;
import stroom.test.IntegrationTestSetupUtil;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.ConsoleColour;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LogUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final CommonTestControl commonTestControl;
    private final IntegrationTestSetupUtil integrationTestSetupUtil;

    @Inject
    public GenerateTestOpenIdDetails(final JsonWebKeyFactory jsonWebKeyFactory,
                                     final TokenService tokenService,
                                     final AccountService accountService,
                                     final CommonTestControl commonTestControl,
                                     final IntegrationTestSetupUtil integrationTestSetupUtil) {
        this.jsonWebKeyFactory = jsonWebKeyFactory;
        this.tokenService = tokenService;
        this.accountService = accountService;
        this.commonTestControl = commonTestControl;
        this.integrationTestSetupUtil = integrationTestSetupUtil;
    }


    public static void main(String[] args) throws JoseException {
        Injector injector = Guice.createInjector(
                new DbTestModule(),
                new CoreTestModule());

        injector.getInstance(GenerateTestOpenIdDetails.class).run();
    }

    private void run() throws JoseException {

        try {
            integrationTestSetupUtil.clean(true);
            generateCode();
        } finally {
            //
        }
    }

    private void generateCode() throws JoseException {

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

        // Check we can build a JsonWebKey from the public key

        final PublicJsonWebKey publicJsonWebKey2 = RsaJsonWebKey.Factory.newPublicJwk(publicKeyAsJsonStr);

        if (!Arrays.equals(publicJsonWebKey.getPublicKey().getEncoded(), publicJsonWebKey2.getPublicKey().getEncoded())) {
            throw new RuntimeException("Public keys do not match");
        }

        if (!Arrays.equals(publicJsonWebKey.getPrivateKey().getEncoded(), publicJsonWebKey2.getPrivateKey().getEncoded())) {
            throw new RuntimeException("Private keys do not match");
        }

        final String msg = "\n" +
                "\n" +
                "\nThe following lines have been substituted into " + DefaultOpenIdCredentials.class.getName() +
                "\n";

        final String generatedCode = "\n" +
                "\n// The values between the lines were generated using " + this.getClass().getName() + " on " + Instant.now().toString() +
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

        LOGGER.info(ConsoleColour.red(msg) + ConsoleColour.green(generatedCode));

        // write the generated code to the class
        updateFile(generatedCode);


        // Delete the DB records created.
//        tokenService.delete(token.getId());
//        accountService.delete(account.getId());
    }

    private void updateFile(final String generatedCode) {
        Path pwd = Paths.get(".").toAbsolutePath().normalize();

        LOGGER.info("PWD: {}", pwd.toString());

        Path defaultCredsFile = pwd.resolve("stroom-util/src/main/java")
                .resolve(DefaultOpenIdCredentials.class.getName().replace(".", File.separator) + ".java")
                .normalize();

        LOGGER.info("DefaultOpenIdCredentials: {}", defaultCredsFile.toString());

        if (!Files.isRegularFile(defaultCredsFile)) {
            throw new RuntimeException("Can't find " + defaultCredsFile.toString());
        }

        if (!Files.isWritable(defaultCredsFile)) {
            throw new RuntimeException("File is not writable" + defaultCredsFile.toString());
        }

        try {
            String fileContent = Files.readString(defaultCredsFile);

            // match some thing like:

            //   // ----------------------
            //   thisIsSomeCode......
            //   // ----------------------

            final Pattern generatedBlockPattern = Pattern.compile("[ ]*//[ ]*---+.+?[ ]*//[ ]*---+", Pattern.DOTALL);

            LOGGER.debug("\n{}", fileContent);
            Matcher matcher = generatedBlockPattern.matcher(fileContent);

            boolean foundMatch = matcher.find();
            if (foundMatch) {
                final String newFileContent = matcher.replaceAll(matchResult -> {
                    LOGGER.debug("match \n{}", matchResult.group());

                    return generatedCode.replace("\"", "\\\"");
                });

                Files.writeString(defaultCredsFile, newFileContent, Charset.defaultCharset());

                LOGGER.debug("\n{}", Files.readString(defaultCredsFile));
            } else {
                throw new RuntimeException(LogUtil.message(
                        "Expecting to find one block matching [{}]", generatedBlockPattern));
            }

        } catch (IOException e) {
            throw new RuntimeException("Error reading content of " + defaultCredsFile.toString());
        }

    }

}
