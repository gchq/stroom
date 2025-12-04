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

package stroom.security.impl;

import stroom.security.common.impl.OpenIdPublicKeysSupplier;
import stroom.security.common.impl.StandardJwtContextFactory;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.jersey.JerseyClientFactory;

import org.jose4j.base64url.Base64;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestJWTService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestJWTService.class);

    @Mock
    private OpenIdPublicKeysSupplier openIdPublicKeysSupplier;
    @Mock
    private OpenIdConfiguration openIdConfiguration;
    @Mock
    private JerseyClientFactory mockJerseyClientFactory;


    private DefaultOpenIdCredentials defaultOpenIdCredentials = new DefaultOpenIdCredentials();

    /**
     * Make sure the hard coded token in {@link DefaultOpenIdCredentials} can be verified
     */
    @Test
    void verifyDefaultApiToken() throws JoseException {
        // Verify the hard coded default token

        final StandardJwtContextFactory jwtService = new StandardJwtContextFactory(
                () -> openIdConfiguration,
                openIdPublicKeysSupplier,
                defaultOpenIdCredentials,
                mockJerseyClientFactory);

        final String apiKey = defaultOpenIdCredentials.getApiKey();

        Mockito.when(openIdPublicKeysSupplier.get())
                .thenReturn(getPublicKeys());
        Mockito.when(openIdConfiguration.getIdentityProviderType())
                .thenReturn(IdpType.TEST_CREDENTIALS);

        final JwtClaims jwtClaims = jwtService
                .getJwtContext(apiKey)
                .map(JwtContext::getJwtClaims)
                .orElseThrow(() -> new RuntimeException("Token failed verification"));
        try {
            LOGGER.info("Claims: \n{}", jwtClaims.toJson());

            assertThat(jwtClaims.getSubject())
                    .isEqualTo(defaultOpenIdCredentials.getApiKeyUserEmail());
            assertThat(jwtClaims.getAudience())
                    .contains(defaultOpenIdCredentials.getOauth2ClientId());
            final LocalDateTime expiryTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(jwtClaims.getExpirationTime().getValueInMillis()),
                    ZoneOffset.UTC);

            // Can't be sure when the token was created so just ensure there is a year left on it.
            assertThat(Period.between(
                    LocalDateTime.now(ZoneOffset.UTC).toLocalDate(),
                    expiryTime.toLocalDate()).getYears())
                    .isGreaterThan(1);
        } catch (final MalformedClaimException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testPublicKey() throws JoseException {
        LOGGER.info("JsonWebToken (API key): \n\n{}\n", defaultOpenIdCredentials.getApiKey());

        final PublicJsonWebKey publicJsonWebKey = RsaJsonWebKey.Factory
                .newPublicJwk(defaultOpenIdCredentials.getPublicKeyJson());

        LOGGER.info("Public key: {}", publicJsonWebKey.getPublicKey().toString());

        LOGGER.info("Public key Base64: \n\n{}\n", Base64.encode(publicJsonWebKey.getPublicKey().getEncoded()));

        LOGGER.info("private key: {}", publicJsonWebKey.getPrivateKey().toString());

        LOGGER.info("Private key Base64: \n\n{}\n", Base64.encode(publicJsonWebKey.getPrivateKey().getEncoded()));
    }

    /**
     * Copied from https://bitbucket.org/b_c/jose4j/wiki/JWT Examples
     * <p>
     * Not really a test, just a handy reference for how to create a token and verify it in jose4j
     */
    @Test
    void testJose4jExample() throws InvalidJwtException, JoseException {

        //
        // JSON Web Token is a compact URL-safe means of representing claims/attributes to be transferred
        // between two parties. This example demonstrates producing and consuming a signed JWT
        //

        // Generate an RSA key pair, which will be used for signing and verification of the JWT, wrapped in a JWK
        final RsaJsonWebKey rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);

        // Give the JWK a Key ID (kid), which is just the polite thing to do
        rsaJsonWebKey.setKeyId("k1");

        // Create the Claims, which will be the content of the JWT
        final JwtClaims claims = new JwtClaims();
        claims.setIssuer("Issuer");  // who creates the token and signs it
        claims.setAudience("Audience"); // to whom the token is intended to be sent
        claims.setExpirationTimeMinutesInTheFuture(10); // time when the token will expire (10 minutes from now)
        claims.setGeneratedJwtId(); // a unique identifier for the token
        claims.setIssuedAtToNow();  // when the token was issued/created (now)
        claims.setNotBeforeMinutesInThePast(2); // time before which the token is not yet valid (2 minutes ago)
        claims.setSubject("subject"); // the subject/principal is whom the token is about
        // additional claims/attributes about the subject can be added
        claims.setClaim("email", "mail@example.com");
        final List<String> groups = Arrays.asList("group-one", "other-group", "group-three");
        // multi-valued claims work too and will end up as a JSON array
        claims.setStringListClaim("groups", groups);

        // A JWT is a JWS and/or a JWE with JSON claims as the payload.
        // In this example it is a JWS so we create a JsonWebSignature object.
        final JsonWebSignature jws = new JsonWebSignature();

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
        final String jwt = jws.getCompactSerialization();


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
        final JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setRequireExpirationTime() // the JWT must have an expiration time
                .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims to
                //                                   account for clock skew
                .setRequireSubject() // the JWT must have a subject claim
                .setExpectedIssuer("Issuer") // whom the JWT needs to have been issued by
                .setExpectedAudience("Audience") // to whom the JWT is intended for
                .setVerificationKey(rsaJsonWebKey.getKey()) // verify the signature with the public key
                .setJwsAlgorithmConstraints(// only allow the expected signature algorithm(s) in the given context
                        new AlgorithmConstraints(
                                AlgorithmConstraints.ConstraintType.WHITELIST,
                                AlgorithmIdentifiers.RSA_USING_SHA256)) // which is only RS256 here
                .build(); // create the JwtConsumer instance

        LOGGER.info("Public key: {}", rsaJsonWebKey.getPublicKey().toString());

        LOGGER.info("Base64: \n{}\n", Base64.encode(rsaJsonWebKey.getPublicKey().getEncoded()));

        LOGGER.info("private key: {}", rsaJsonWebKey.getPrivateKey().toString());

        LOGGER.info("Base64: \n{}\n", Base64.encode(rsaJsonWebKey.getPrivateKey().getEncoded()));


        //  Validate the JWT and process it to the Claims
        final JwtClaims jwtClaims = jwtConsumer.processToClaims(jwt);
        System.out.println("JWT validation succeeded! " + jwtClaims);

    }

    private JsonWebKeySet getPublicKeys() throws JoseException {
        final PublicJsonWebKey publicJsonWebKey = RsaJsonWebKey.Factory
                .newPublicJwk(defaultOpenIdCredentials.getPublicKeyJson());

        final List<PublicJsonWebKey> publicJsonWebKeys = Collections.singletonList(publicJsonWebKey);

        return new JsonWebKeySet(publicJsonWebKeys);
    }
}
