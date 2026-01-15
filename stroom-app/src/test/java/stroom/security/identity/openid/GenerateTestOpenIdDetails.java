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

package stroom.security.identity.openid;

import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.token.JwkFactoryImpl;
import stroom.security.identity.token.TokenBuilder;
import stroom.security.identity.token.TokenBuilderFactory;
import stroom.security.impl.StroomOpenIdConfig;
import stroom.security.openid.api.JsonWebKeyFactory;
import stroom.security.openid.api.OpenIdClient;
import stroom.security.openid.api.PublicJsonWebKeyProvider;
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
 * <p>
 * !!!!!!!!!! WARNING !!!!!!!!!!
 * <p>
 * If you run this and commit the new creds it will break any existing api keys
 * in any existing test environments that are upgrading and use these default creds.
 * If that happens you will need to delete any user API keys in the UI and recreate them.
 * There is an API key for INTERNAL_PROCESSING_USER which is system generated. You will also
 * need to delete that and reboot so stroom can recreated it.
 * <p>
 * !!!!!!!!!! WARNING !!!!!!!!!!
 */

public class GenerateTestOpenIdDetails {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateTestOpenIdDetails.class);

    private static final String ISSUER = "default-test-only-issuer";
    private static final String CLIENT_NAME = "Stroom Client Internal (TEST ONLY)";
    private static final String API_KEY_USER_EMAIL = "default-test-only-api-key-user";

    private final JsonWebKeyFactory jsonWebKeyFactory;
    private final PublicJsonWebKeyProvider publicJsonWebKeyProvider;

    private PublicJsonWebKey publicJsonWebKey;

    public GenerateTestOpenIdDetails() {
        publicJsonWebKeyProvider = Mockito.mock(PublicJsonWebKeyProvider.class);
        Mockito.when(publicJsonWebKeyProvider.list())
                .thenAnswer(invocation -> Collections.singletonList(publicJsonWebKey));
        Mockito.when(publicJsonWebKeyProvider.getFirst())
                .thenAnswer(invocation -> publicJsonWebKey);
        jsonWebKeyFactory = new JwkFactoryImpl();
    }

    public static void main(final String[] args) throws JoseException {
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

        final OpenIdClient oAuth2Client = OpenIdClientDetailsFactoryImpl.createRandomisedOAuth2Client(CLIENT_NAME);

        final TokenBuilderFactory tokenBuilderFactory = new TokenBuilderFactory(
                IdentityConfig::new,
                publicJsonWebKeyProvider,
                StroomOpenIdConfig::new);

        final TokenBuilder tokenBuilder = tokenBuilderFactory
                .builder()
                .expirationTime(ZonedDateTime.now(ZoneId.from(ZoneOffset.UTC)).plus(20, ChronoUnit.YEARS)
                        .toInstant())
                .clientId(oAuth2Client.getClientId())
                .subject(API_KEY_USER_EMAIL)
                .issuer(ISSUER);

        final String apiKey = tokenBuilder.build();

        final String escapedPublicKeyAsJsonStr = publicKeyAsJsonStr.replace("\"", "\\\"");

        // Check we can build a JsonWebKey from the public key

        final PublicJsonWebKey publicJsonWebKey2 = RsaJsonWebKey.Factory.newPublicJwk(publicKeyAsJsonStr);

        if (!Arrays.equals(publicJsonWebKey.getPublicKey().getEncoded(),
                publicJsonWebKey2.getPublicKey().getEncoded())) {
            throw new RuntimeException("Public keys do not match");
        }

        if (!Arrays.equals(publicJsonWebKey.getPrivateKey().getEncoded(),
                publicJsonWebKey2.getPrivateKey().getEncoded())) {
            throw new RuntimeException("Private keys do not match");
        }

        final String msg = "\n" +
                "\n" +
                "\n  The following lines have been substituted into " + DefaultOpenIdCredentials.class.getName() +
                "\n";

        final String generatedCode = "" +
                "    // ==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--==--" +
                "\n    // ALL the content between these dashed lines was generated and inserted using" +
                "\n    // " + this.getClass().getName() + " at " + Instant.now().toString() +
                "\n    // The dashed lines are important, don't remove them!" +
                "\n    private static final String OAUTH2_ISSUER = \"" +
                ISSUER + "\";" +
                "\n    private static final String OAUTH2_CLIENT_ID = \"" +
                oAuth2Client.getClientId() + "\";" +
                "\n    private static final String OAUTH2_CLIENT_NAME = \"" +
                oAuth2Client.getName() + "\";" +
                "\n    private static final String OAUTH2_CLIENT_SECRET = \"" +
                oAuth2Client.getClientSecret() + "\";" +
                "\n    private static final String OAUTH2_CLIENT_URI_PATTERN = \"" +
                oAuth2Client.getUriPattern() + "\";" +
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
        final Path pwd = Paths.get(".").toAbsolutePath().normalize();

        LOGGER.debug("PWD: {}", pwd.toString());

        final Path defaultCredsFile = pwd.resolve("stroom-util/src/main/java")
                .resolve(DefaultOpenIdCredentials.class.getName().replace(".", File.separator) + ".java")
                .normalize();

        LOGGER.info("DefaultOpenIdCredentials: {}", defaultCredsFile.toString());

        if (!Files.isRegularFile(defaultCredsFile)) {
            throw new RuntimeException("Can't find " + defaultCredsFile);
        }

        if (!Files.isWritable(defaultCredsFile)) {
            throw new RuntimeException("File is not writable" + defaultCredsFile);
        }

        try {
            final String fileContent = Files.readString(defaultCredsFile);

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
            final Matcher matcher = generatedBlockPattern.matcher(fileContent);

            final boolean foundMatch = matcher.find();
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

        } catch (final IOException e) {
            throw new RuntimeException("Error reading content of " + defaultCredsFile);
        }

    }

}
