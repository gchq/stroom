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

package stroom.security.common.impl;

import stroom.security.api.exception.AuthenticationException;
import stroom.security.common.impl.StandardJwtContextFactory.JwsParts;
import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.test.common.TestUtil;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.string.TemplateUtil;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import jakarta.servlet.http.HttpServletRequest;
import org.assertj.core.api.Assertions;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.keys.HmacKey;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

class TestStandardJwtContextFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestStandardJwtContextFactory.class);

    private static final String ISSUER = "https://issuer.example.com";
    private static final String USER_ID = "user1";
    private static final String CLIENT_ID = "stroom-client";

    private static RsaJsonWebKey jwk;

    @BeforeAll
    static void generateKey() throws JoseException {
        jwk = RsaJwkGenerator.generateJwk(2048);
        jwk.setKeyId("test-key");
    }

    @TestFactory
    Stream<DynamicTest> testAwsPublicKeyUriFromSigner() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<String, Set<String>>>() {
                })
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final String signer = testCase.getInput()._1;
                    final Set<String> expectedSignerPrefixes = testCase.getInput()._2;
                    final String json = LogUtil.message("""
                            {
                              "signer": "{}",
                              "kid": "999"
                            }""", signer);

                    final String header = Base64.getEncoder()
                            .encodeToString(json.getBytes(StandardCharsets.UTF_8));

                    final JwsParts jwsParts = new JwsParts(
                            null,
                            header,
                            null,
                            null);

                    return getAwsPublicKeyUri(jwsParts, expectedSignerPrefixes);
                })
                .withSimpleEqualityAssertion()

                .addNamedCase("Single, full",
                        Tuple.of("arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678",
                                Set.of("arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678")),
                        "https://public-keys.auth.elb.region-x.amazonaws.com/999")

                .addNamedCase("Single, partial",
                        Tuple.of("arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678",
                                Set.of("arn:aws:elasticloadbalancing:region-x:1234:")),
                        "https://public-keys.auth.elb.region-x.amazonaws.com/999")

                .addNamedCase("Multiple, full",
                        Tuple.of("arn:aws:elasticloadbalancing:region-y:1234:loadbalancer/app/MyApp/5678",
                                Set.of(
                                        "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678",
                                        "arn:aws:elasticloadbalancing:region-y:1234:loadbalancer/app/MyApp/5678")),
                        "https://public-keys.auth.elb.region-y.amazonaws.com/999")

                .addNamedCase("Multiple, partial",
                        Tuple.of("arn:aws:elasticloadbalancing:region-y:1234:loadbalancer/app/MyApp/5678",
                                Set.of(
                                        "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp",
                                        "arn:aws:elasticloadbalancing:region-y:1234:")),
                        "https://public-keys.auth.elb.region-y.amazonaws.com/999")

                .build();
    }

    private String getAwsPublicKeyUri(final JwsParts jwsParts, final Set<String> expectedSignerPrefixes) {
        return StandardJwtContextFactory.getAwsPublicKeyUri(
                jwsParts,
                expectedSignerPrefixes,
                TemplateUtil.parseTemplate(AbstractOpenIdConfig.DEFAULT_AWS_PUBLIC_KEY_URI_TEMPLATE));
    }

    @Test
    void getAwsPublicKeyUriFromSigner_blankSigner() {
        final String signer2 = "arn:aws:elasticloadbalancing:region-y:1234:loadbalancer/app/MyApp/5678";

        final String json = """
                {
                  "signer": "",
                  "kid": "999"
                }""";

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        final JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        Assertions.assertThatThrownBy(
                        () -> {
                            getAwsPublicKeyUri(jwsParts, Set.of(signer2));
                        })
                .hasMessageContaining("does not match")
                .hasMessageContaining(AbstractOpenIdConfig.PROP_NAME_EXPECTED_SIGNER_PREFIXES)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getAwsPublicKeyUriFromSigner_badSigner() {
        final String signer1 = "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678";
        final String signer2 = "arn:aws:elasticloadbalancing:region-y:1234:loadbalancer/app/MyApp/5678";

        final String json = LogUtil.message("""
                {
                  "signer": "{}",
                  "kid": "999"
                }""", signer1);

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        final JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        Assertions.assertThatThrownBy(
                        () -> {
                            getAwsPublicKeyUri(jwsParts, Set.of(signer2));
                        })
                .hasMessageContaining("does not match")
                .hasMessageContaining(AbstractOpenIdConfig.PROP_NAME_EXPECTED_SIGNER_PREFIXES)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getAwsPublicKeyUriFromSigner_badKeyId() {
        // A valid signer, but a kid that tries to inject path segments into the public-key URL is rejected.
        final String signer = "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678";

        final String json = LogUtil.message("""
                {
                  "signer": "{}",
                  "kid": "../../etc/passwd"
                }""", signer);

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        final JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        Assertions.assertThatThrownBy(
                        () -> getAwsPublicKeyUri(jwsParts, Set.of(signer)))
                .hasMessageContaining("does not match the expected format")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getAwsPublicKeyUriFromSigner_badRegionInSigner() {
        final String signer1 = "arn:aws:elasticloadbalancing:region-x/foo:1234:loadbalancer/app/MyApp/5678";
        final String signer2 = "arn:aws:elasticloadbalancing:region-y:1234:loadbalancer/app/MyApp/5678";

        final String json = LogUtil.message("""
                {
                  "signer": "{}",
                  "kid": "999"
                }""", signer1);

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        final JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        Assertions.assertThatThrownBy(
                        () -> {
                            getAwsPublicKeyUri(jwsParts, Set.of(signer1, signer2));
                        })
                .hasMessageContaining("AWS region")
                .hasMessageContaining("does not match pattern")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getAwsPublicKeyUriFromSigner_noRegionInSigner() {
        final String signer1 = "arn:aws:elasticloadbalancing::1234:loadbalancer/app/MyApp/5678";

        final String json = LogUtil.message("""
                {
                  "signer": "{}",
                  "kid": "999"
                }""", signer1);

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        final JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        Assertions.assertThatThrownBy(
                        () -> {
                            getAwsPublicKeyUri(jwsParts, Set.of(signer1));
                        })
                .hasMessageContaining("AWS region")
                .hasMessageContaining("does not match pattern")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getAwsPublicKeyUriFromSigner_nullExpectedSigners() {
        final String signer1 = "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678";

        final String json = LogUtil.message("""
                {
                  "signer": "{}",
                  "kid": "999"
                }""", signer1);

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        final JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        Assertions.assertThatThrownBy(
                        () -> {
                            getAwsPublicKeyUri(jwsParts, null);
                        })
                .hasMessageContaining("does not match")
                .hasMessageContaining(AbstractOpenIdConfig.PROP_NAME_EXPECTED_SIGNER_PREFIXES)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getAwsPublicKeyUriFromSigner_signerNotFound() {
        final String signer = "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678";

        final String json = """
                {
                  "kid": "999"
                }""";

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        final JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        Assertions.assertThatThrownBy(
                        () -> {
                            getAwsPublicKeyUri(jwsParts, Set.of(signer));
                        })
                .hasMessageContaining("Missing")
                .hasMessageContaining(StandardJwtContextFactory.SIGNER_HEADER_KEY)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void audienceDefaultsToClientIdWhenNoAllowedAudiencesConfigured() {
        // With no allowedAudiences configured, the audience must default to our clientId so that a token
        // minted for another application at the same provider cannot be replayed against stroom.
        final StandardJwtContextFactory factory = newFactory(true, Collections.emptySet(), false, CLIENT_ID);

        assertAccepted(factory, buildToken(CLIENT_ID));
        assertRejected(factory, buildToken("another-app"));
        // The claim is not required, so a token with no aud is still accepted.
        assertAccepted(factory, buildToken(null));
    }

    @Test
    void audienceIsMandatoryWhenAudienceClaimRequired() {
        // With audienceClaimRequired=true (the shipped default) a token that omits aud is rejected, so the
        // audience check cannot be dodged by simply leaving the claim off.
        final StandardJwtContextFactory factory = newFactory(true, Collections.emptySet(), true, CLIENT_ID);

        assertAccepted(factory, buildToken(CLIENT_ID));
        assertRejected(factory, buildToken("another-app"));
        assertRejected(factory, buildToken(null));
    }

    @Test
    void audienceValidationCanBeDisabled() {
        // The escape hatch: an operator may turn audience validation off entirely.
        final StandardJwtContextFactory factory = newFactory(false, Collections.emptySet(), false, CLIENT_ID);

        assertAccepted(factory, buildToken("another-app"));
        assertAccepted(factory, buildToken(null));
    }

    @Test
    void explicitAllowedAudiencesTakePrecedenceOverClientId() {
        // When allowedAudiences is configured it is used verbatim - the clientId fallback does not apply.
        final StandardJwtContextFactory factory = newFactory(
                true, Set.of("api://resource"), false, CLIENT_ID);

        assertAccepted(factory, buildToken("api://resource"));
        assertRejected(factory, buildToken(CLIENT_ID));
    }

    @Test
    void algConfusionTokensAreRejected() {
        // The signature algorithm is pinned to the asymmetric families, so the two alg-confusion vectors are
        // rejected before verification: an unsigned 'none' token, and an HMAC token that abuses the known
        // RSA public key as the shared secret.
        final StandardJwtContextFactory factory = newFactory(true, Collections.emptySet(), false, CLIENT_ID);

        assertRejected(factory, buildNoneToken());
        assertRejected(factory, buildHmacTokenUsingPublicKeyAsSecret());
    }

    @Test
    void requiredAccessTokenTypeRejectsAWrongTypeOnTheBearerPath() {
        // With requiredAccessTokenType='at+jwt', only a token carrying that JOSE typ is accepted as a bearer
        // credential; an id_token-shaped token (typ 'JWT') or one with no typ is rejected.
        final StandardJwtContextFactory factory = factoryWithRequiredAccessTokenType("at+jwt");

        Assertions.assertThat(factory.getJwtContext(requestWithBearerToken(buildTokenWithType("at+jwt"))))
                .isPresent();
        Assertions.assertThat(factory.getJwtContext(requestWithBearerToken(buildTokenWithType("JWT"))))
                .isEmpty();
        Assertions.assertThat(factory.getJwtContext(requestWithBearerToken(buildTokenWithType(null))))
                .isEmpty();
    }

    @Test
    void unsetRequiredAccessTokenTypeAcceptsAnyType() {
        // Default (unset): any token type is accepted, for identity providers that do not set a distinct typ.
        final StandardJwtContextFactory factory = factoryWithRequiredAccessTokenType(null);

        Assertions.assertThat(factory.getJwtContext(requestWithBearerToken(buildTokenWithType("JWT"))))
                .isPresent();
        Assertions.assertThat(factory.getJwtContext(requestWithBearerToken(buildTokenWithType(null))))
                .isPresent();
    }

    private StandardJwtContextFactory factoryWithRequiredAccessTokenType(final String requiredType) {
        final OpenIdConfiguration openIdConfiguration = Mockito.mock(OpenIdConfiguration.class);
        Mockito.when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.EXTERNAL_IDP);
        Mockito.when(openIdConfiguration.getIssuer()).thenReturn(ISSUER);
        Mockito.when(openIdConfiguration.getValidIssuers()).thenReturn(Collections.emptySet());
        Mockito.when(openIdConfiguration.getAllowedAudiences()).thenReturn(Collections.emptySet());
        Mockito.when(openIdConfiguration.isAudienceClaimRequired()).thenReturn(false);
        Mockito.when(openIdConfiguration.isValidateAudience()).thenReturn(true);
        Mockito.when(openIdConfiguration.getClientId()).thenReturn(CLIENT_ID);
        Mockito.when(openIdConfiguration.getRequiredAccessTokenType()).thenReturn(requiredType);

        final OpenIdPublicKeysSupplier keysSupplier = Mockito.mock(OpenIdPublicKeysSupplier.class);
        Mockito.when(keysSupplier.get()).thenReturn(new JsonWebKeySet(jwk));

        return new StandardJwtContextFactory(
                () -> openIdConfiguration,
                keysSupplier,
                Mockito.mock(JerseyClientFactory.class));
    }

    private HttpServletRequest requestWithBearerToken(final String jws) {
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer " + jws);
        return request;
    }

    private String buildTokenWithType(final String type) {
        final JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(audienceClaims().toJson());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setKey(jwk.getPrivateKey());
        jws.setKeyIdHeaderValue(jwk.getKeyId());
        if (type != null) {
            jws.getHeaders().setStringHeaderValue("typ", type);
        }
        try {
            return jws.getCompactSerialization();
        } catch (final JoseException e) {
            throw new RuntimeException(e);
        }
    }

    private StandardJwtContextFactory newFactory(final boolean validateAudience,
                                                 final Set<String> allowedAudiences,
                                                 final boolean audienceClaimRequired,
                                                 final String clientId) {
        final OpenIdConfiguration openIdConfiguration = Mockito.mock(OpenIdConfiguration.class);
        Mockito.when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.EXTERNAL_IDP);
        Mockito.when(openIdConfiguration.getIssuer()).thenReturn(ISSUER);
        Mockito.when(openIdConfiguration.getValidIssuers()).thenReturn(Collections.emptySet());
        Mockito.when(openIdConfiguration.getAllowedAudiences()).thenReturn(allowedAudiences);
        Mockito.when(openIdConfiguration.isAudienceClaimRequired()).thenReturn(audienceClaimRequired);
        Mockito.when(openIdConfiguration.isValidateAudience()).thenReturn(validateAudience);
        Mockito.when(openIdConfiguration.getClientId()).thenReturn(clientId);

        final OpenIdPublicKeysSupplier keysSupplier = Mockito.mock(OpenIdPublicKeysSupplier.class);
        Mockito.when(keysSupplier.get()).thenReturn(new JsonWebKeySet(jwk));

        return new StandardJwtContextFactory(
                () -> openIdConfiguration,
                keysSupplier,
                Mockito.mock(JerseyClientFactory.class));
    }

    private void assertAccepted(final StandardJwtContextFactory factory, final String token) {
        Assertions.assertThat(factory.getJwtContext(token)).isPresent();
    }

    private void assertRejected(final StandardJwtContextFactory factory, final String token) {
        Assertions.assertThatThrownBy(() -> factory.getJwtContext(token))
                .isInstanceOf(AuthenticationException.class);
    }

    private String buildToken(final String audience) {
        final JwtClaims claims = new JwtClaims();
        claims.setSubject(USER_ID);
        claims.setIssuer(ISSUER);
        if (audience != null) {
            claims.setAudience(audience);
        }
        claims.setExpirationTime(NumericDate.fromSeconds(
                Instant.now().plusSeconds(600).getEpochSecond()));

        final JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setKey(jwk.getPrivateKey());
        jws.setKeyIdHeaderValue(jwk.getKeyId());
        jws.setDoKeyValidation(true);
        try {
            return jws.getCompactSerialization();
        } catch (final JoseException e) {
            throw new RuntimeException(e);
        }
    }

    private JwtClaims audienceClaims() {
        final JwtClaims claims = new JwtClaims();
        claims.setSubject(USER_ID);
        claims.setIssuer(ISSUER);
        claims.setAudience(CLIENT_ID);
        claims.setExpirationTime(NumericDate.fromSeconds(
                Instant.now().plusSeconds(600).getEpochSecond()));
        return claims;
    }

    private String buildNoneToken() {
        final JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(audienceClaims().toJson());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.NONE);
        jws.setAlgorithmConstraints(AlgorithmConstraints.ALLOW_ONLY_NONE);
        try {
            return jws.getCompactSerialization();
        } catch (final JoseException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildHmacTokenUsingPublicKeyAsSecret() {
        final JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(audienceClaims().toJson());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.HMAC_SHA256);
        jws.setKey(new HmacKey(jwk.getPublicKey().getEncoded()));
        jws.setKeyIdHeaderValue(jwk.getKeyId());
        try {
            return jws.getCompactSerialization();
        } catch (final JoseException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void getAwsPublicKeyUriFromSigner_noKeyId() {
        final String signer = "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678";

        final String json = LogUtil.message("""
                {
                  "signer": "{}"
                }""", signer);

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        final JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        Assertions.assertThatThrownBy(
                        () -> {
                            getAwsPublicKeyUri(jwsParts, Set.of(signer));
                        })
                .hasMessageContaining("Missing")
                .hasMessageContaining(OpenId.KEY_ID)
                .isInstanceOf(RuntimeException.class);
    }
}
