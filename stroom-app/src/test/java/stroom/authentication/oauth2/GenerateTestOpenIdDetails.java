package stroom.authentication.oauth2;

import stroom.authentication.api.JsonWebKeyFactory;
import stroom.authentication.api.OAuth2Client;
import stroom.authentication.config.AuthenticationConfig;
import stroom.authentication.token.JsonWebKeyFactoryImpl;
import stroom.authentication.token.JwkCache;
import stroom.authentication.token.Token;
import stroom.authentication.token.TokenBuilder;
import stroom.authentication.token.TokenBuilderFactory;
import stroom.util.ConsoleColour;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LogUtil;

import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.lang.JoseException;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
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
    private final JwkCache jwkCache;

    private PublicJsonWebKey publicJsonWebKey;

    public GenerateTestOpenIdDetails() {
        jwkCache = Mockito.mock(JwkCache.class);
        Mockito.when(jwkCache.get())
                .thenAnswer(invocation -> {
                    return Collections.singletonList(publicJsonWebKey);
                });
        jsonWebKeyFactory = new JsonWebKeyFactoryImpl();
    }

    public static void main(String[] args) throws JoseException {
        new GenerateTestOpenIdDetails().run();
    }

    private void run() throws JoseException {
        generateCode();
        System.exit(0);
    }

    private void generateCode() throws JoseException {

        // Create a public key
        publicJsonWebKey = jsonWebKeyFactory.createPublicKey();

        final String publicKeyAsJsonStr = jsonWebKeyFactory.asJson(publicJsonWebKey);

        final OAuth2Client oAuth2Client = OpenIdClientDetailsFactoryImpl.createRandomisedOAuth2Client(CLIENT_NAME);

        final TokenBuilderFactory tokenBuilderFactory = new TokenBuilderFactory(
                new AuthenticationConfig(),
                jwkCache);

        final TokenBuilder tokenBuilder = tokenBuilderFactory
                .expiryDateForApiKeys(ZonedDateTime.now(ZoneId.from(ZoneOffset.UTC)).plus(20, ChronoUnit.YEARS)
                        .toInstant())
                .newBuilder(Token.TokenType.API)
                .clientId(oAuth2Client.getClientId())
                .subject(API_KEY_USER_EMAIL);

        final String apiKey = tokenBuilder.build();


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
                "\n  The following lines have been substituted into " + DefaultOpenIdCredentials.class.getName() +
                "\n";

        final String generatedCode = "" +
                "    // ==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--" +
                "\n    // ALL the content between these dashed lines was generated inserted using" +
                "\n    // " + this.getClass().getName() + " at " + Instant.now().toString() +
                "\n    // The dashed lines are important, don't remove them!" +
                "\n    private static final String OAUTH2_CLIENT_ID = \"" + oAuth2Client.getClientId() + "\";" +
                "\n    private static final String OAUTH2_CLIENT_NAME = \"" + oAuth2Client.getName() + "\";" +
                "\n    private static final String OAUTH2_CLIENT_SECRET = \"" + oAuth2Client.getClientSecret() + "\";" +
                "\n    private static final String OAUTH2_CLIENT_URI_PATTERN = \"" + oAuth2Client.getUriPattern() + "\";" +

                "\n    private static final String PUBLIC_KEY_ID = \"" + publicJsonWebKey.getKeyId() + "\";" +
                "\n    private static final String PUBLIC_KEY_JSON = \"" + escapedPublicKeyAsJsonStr + "\";" +

                "\n    private static final String API_KEY_USER_EMAIL = \"" + API_KEY_USER_EMAIL + "\";" +
                "\n    private static final String API_KEY = \"" + apiKey + "\";" +
                "\n    // ==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--";

        LOGGER.info(ConsoleColour.red(msg) + ConsoleColour.green(generatedCode));

        // write the generated code to the class
        updateFile(generatedCode);
    }

    private void updateFile(final String generatedCode) {
        Path pwd = Paths.get(".").toAbsolutePath().normalize();

        LOGGER.debug("PWD: {}", pwd.toString());

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

            //   // ==--==--==--==--==--==--==--==--==--==--==--
            //   thisIsSomeCode......
            //   // ==--==--==--==--==--==--==--==--==--==--==--

            final Pattern generatedBlockPattern = Pattern.compile(
                    "[ ]*//[ ]*(==--)+.+?[ ]*//[ ]*(==--)+",
                    Pattern.DOTALL);
//            final Pattern generatedBlockPattern = Pattern.compile(
//                    "[ ]*//[ ]*---+.+?[ ]*//[ ]*---+",
//                    Pattern.DOTALL);

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
