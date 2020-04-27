package stroom.authentication.oauth2;

import com.google.inject.AbstractModule;
import io.dropwizard.testing.junit5.DropwizardClientExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import stroom.authentication.account.AccountService;
import stroom.authentication.api.JsonWebKeyFactory;
import stroom.authentication.config.AuthenticationConfig;
import stroom.authentication.token.TokenService;
import stroom.config.common.NodeUriConfig;
import stroom.security.impl.JWTService;
import stroom.security.impl.ResolvedOpenIdConfig;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.authentication.DefaultOpenIdCredentials;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

/**
 * Used for generating a set of OpenId Connect credentials and a corresponding
 * API key so that stroom-proxy can connect to stroom on first boot.
 * ONLY intended for test/demo purposes.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
@ExtendWith(MockitoExtension.class)
public class TestDefaultApiTokenVerification extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDefaultApiTokenVerification.class);

    private static final String CLIENT_NAME = "Stroom Client Internal (TEST ONLY)";
    private static final String API_KEY_USER_EMAIL = "default-test-only-api-key-user";

    // Need to add resources as suppliers so they can be fully mocked by mocktio before being used
//    @Rule
//    private final ResourceExtension resources = ResourceExtension.builder()
//            .addResource(this::getOAuth2Resource)
//            .build();

    @Inject
    private JsonWebKeyFactory jsonWebKeyFactory;
    @Inject
    private TokenService tokenService;
    @Inject
    private AccountService accountService;
    @Inject
    private DefaultOpenIdCredentials defaultOpenIdCredentials;
    @Inject
    private AuthenticationConfig authenticationConfig;
    @Inject
    private JWTService jwtService;
    @Inject
    private OAuth2ResourceImpl oAuth2Resource;
    @Inject
    private NodeUriConfig nodeUriConfig;

    @Mock
    private ResolvedOpenIdConfig resolvedOpenIdConfig;

    @IncludeModule(MyModule.class)

    Object getOAuth2Resource() {
        return oAuth2Resource;
    }

    @Test
    void test() throws Throwable {


        final DropwizardClientExtension dropwizard = new DropwizardClientExtension(oAuth2Resource);
        dropwizard.before();

        LOGGER.info("Base URI: " + dropwizard.baseUri().toString());

        nodeUriConfig.setHostname(dropwizard.baseUri().getHost());
        nodeUriConfig.setPort(dropwizard.baseUri().getPort());
        nodeUriConfig.setScheme("http");
        nodeUriConfig.setPathPrefix(dropwizard.baseUri().getPath());

        authenticationConfig.setUseDefaultOpenIdCredentials(true);

        String jwksUri = dropwizard.baseUri().toString() + ResolvedOpenIdConfig.INTERNAL_JWKS_URI.replace("/api", "");

        LOGGER.info("jwks uri: {}", jwksUri);

        Mockito.when(resolvedOpenIdConfig.getJwksUri()).thenReturn(jwksUri);

        String apiKey = defaultOpenIdCredentials.getApiKey();

        jwtService.verifyToken(apiKey);
    }

    /**
     * Copied from https://bitbucket.org/b_c/jose4j/wiki/JWT Examples
     *
     * Not really a test
     */
    @Test
    void testJose4jExample() throws InvalidJwtException, JoseException {

        //
        // JSON Web Token is a compact URL-safe means of representing claims/attributes to be transferred between two parties.
        // This example demonstrates producing and consuming a signed JWT
        //

        // Generate an RSA key pair, which will be used for signing and verification of the JWT, wrapped in a JWK
        RsaJsonWebKey rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);

        // Give the JWK a Key ID (kid), which is just the polite thing to do
        rsaJsonWebKey.setKeyId("k1");

        // Create the Claims, which will be the content of the JWT
        JwtClaims claims = new JwtClaims();
        claims.setIssuer("Issuer");  // who creates the token and signs it
        claims.setAudience("Audience"); // to whom the token is intended to be sent
        claims.setExpirationTimeMinutesInTheFuture(10); // time when the token will expire (10 minutes from now)
        claims.setGeneratedJwtId(); // a unique identifier for the token
        claims.setIssuedAtToNow();  // when the token was issued/created (now)
        claims.setNotBeforeMinutesInThePast(2); // time before which the token is not yet valid (2 minutes ago)
        claims.setSubject("subject"); // the subject/principal is whom the token is about
        claims.setClaim("email","mail@example.com"); // additional claims/attributes about the subject can be added
        List<String> groups = Arrays.asList("group-one", "other-group", "group-three");
        claims.setStringListClaim("groups", groups); // multi-valued claims work too and will end up as a JSON array

        // A JWT is a JWS and/or a JWE with JSON claims as the payload.
        // In this example it is a JWS so we create a JsonWebSignature object.
        JsonWebSignature jws = new JsonWebSignature();

        // The payload of the JWS is JSON content of the JWT Claims
        jws.setPayload(claims.toJson());

        // The JWT is signed using the private key
        jws.setKey(rsaJsonWebKey.getPrivateKey());

        // Set the Key ID (kid) header because it's just the polite thing to do.
        // We only have one key in this example but a using a Key ID helps
        // facilitate a smooth key rollover process
        jws.setKeyIdHeaderValue(rsaJsonWebKey.getKeyId());

        // Set the signature algorithm on the JWT/JWS that will integrity protect the claims
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);

        // Sign the JWS and produce the compact serialization or the complete JWT/JWS
        // representation, which is a string consisting of three dot ('.') separated
        // base64url-encoded parts in the form Header.Payload.Signature
        // If you wanted to encrypt it, you can simply set this jwt as the payload
        // of a JsonWebEncryption object and set the cty (Content Type) header to "jwt".
        String jwt = jws.getCompactSerialization();


        // Now you can do something with the JWT. Like send it to some other party
        // over the clouds and through the interwebs.
        System.out.println("JWT: " + jwt);

        // Use JwtConsumerBuilder to construct an appropriate JwtConsumer, which will
        // be used to validate and process the JWT.
        // The specific validation requirements for a JWT are context dependent, however,
        // it typically advisable to require a (reasonable) expiration time, a trusted issuer, and
        // and audience that identifies your system as the intended recipient.
        // If the JWT is encrypted too, you need only provide a decryption key or
        // decryption key resolver to the builder.
        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setRequireExpirationTime() // the JWT must have an expiration time
                .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims to account for clock skew
                .setRequireSubject() // the JWT must have a subject claim
                .setExpectedIssuer("Issuer") // whom the JWT needs to have been issued by
                .setExpectedAudience("Audience") // to whom the JWT is intended for
                .setVerificationKey(rsaJsonWebKey.getKey()) // verify the signature with the public key
                .setJwsAlgorithmConstraints( // only allow the expected signature algorithm(s) in the given context
                        new AlgorithmConstraints(
                                AlgorithmConstraints.ConstraintType.WHITELIST, AlgorithmIdentifiers.RSA_USING_SHA256)) // which is only RS256 here
                .build(); // create the JwtConsumer instance

        //  Validate the JWT and process it to the Claims
        JwtClaims jwtClaims = jwtConsumer.processToClaims(jwt);
        System.out.println("JWT validation succeeded! " + jwtClaims);
        
    }

    class MyModule extends AbstractModule {

        @Override
        protected void configure() {
            LOGGER.info("binding");
            bind(ResolvedOpenIdConfig.class).toInstance(resolvedOpenIdConfig);
        }
    }
}
