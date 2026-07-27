/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.security.openid.api.ClusterToken;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.PublicJsonWebKeyProvider;

import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestClusterTokenVerifier {

    // The cluster's internal signing key - the sole trust anchor. The verifier only ever holds this key.
    private static RsaJsonWebKey internalKey;
    // A second key that is NOT in the verifier's key set, standing in for an external IdP / attacker.
    private static RsaJsonWebKey foreignKey;

    @BeforeAll
    static void generateKeys() throws JoseException {
        internalKey = RsaJwkGenerator.generateJwk(2048);
        internalKey.setKeyId("internal-key");
        foreignKey = RsaJwkGenerator.generateJwk(2048);
        foreignKey.setKeyId("foreign-key");
    }

    @Test
    void acceptsAWellFormedClusterToken() {
        final String token = buildToken(internalKey,
                ClusterToken.CLUSTER_ISSUER,
                ClusterToken.CLUSTER_AUDIENCE,
                ClusterToken.PROCESSING_USER_SUBJECT,
                OpenId.TOKEN_TYPE__ACCESS,
                validExpiry());
        assertThat(verifier().verify(token)).isPresent();
    }

    @Test
    void rejectsAForeignKeyEvenWithPerfectClusterClaims() {
        // A token signed by a foreign key but carrying every cluster claim (including
        // sub=INTERNAL_PROCESSING_USER) must NOT be accepted - the internal signing key is the discriminator.
        final String token = buildToken(foreignKey,
                ClusterToken.CLUSTER_ISSUER,
                ClusterToken.CLUSTER_AUDIENCE,
                ClusterToken.PROCESSING_USER_SUBJECT,
                OpenId.TOKEN_TYPE__ACCESS,
                validExpiry());
        assertThat(verifier().verify(token)).isEmpty();
    }

    @Test
    void rejectsAWrongIssuer() {
        final String token = buildToken(internalKey,
                "https://some-external-idp",
                ClusterToken.CLUSTER_AUDIENCE,
                ClusterToken.PROCESSING_USER_SUBJECT,
                OpenId.TOKEN_TYPE__ACCESS,
                validExpiry());
        assertThat(verifier().verify(token)).isEmpty();
    }

    @Test
    void rejectsAWrongAudience() {
        final String token = buildToken(internalKey,
                ClusterToken.CLUSTER_ISSUER,
                "some-other-app",
                ClusterToken.PROCESSING_USER_SUBJECT,
                OpenId.TOKEN_TYPE__ACCESS,
                validExpiry());
        assertThat(verifier().verify(token)).isEmpty();
    }

    @Test
    void rejectsAMissingAudience() {
        final String token = buildToken(internalKey,
                ClusterToken.CLUSTER_ISSUER,
                null,
                ClusterToken.PROCESSING_USER_SUBJECT,
                OpenId.TOKEN_TYPE__ACCESS,
                validExpiry());
        assertThat(verifier().verify(token)).isEmpty();
    }

    @Test
    void rejectsAWrongSubject() {
        final String token = buildToken(internalKey,
                ClusterToken.CLUSTER_ISSUER,
                ClusterToken.CLUSTER_AUDIENCE,
                "some-human-user",
                OpenId.TOKEN_TYPE__ACCESS,
                validExpiry());
        assertThat(verifier().verify(token)).isEmpty();
    }

    @Test
    void rejectsAWrongType() {
        // An id token (no at+jwt typ) signed with the internal key and carrying every cluster claim.
        final String token = buildToken(internalKey,
                ClusterToken.CLUSTER_ISSUER,
                ClusterToken.CLUSTER_AUDIENCE,
                ClusterToken.PROCESSING_USER_SUBJECT,
                null,
                validExpiry());
        assertThat(verifier().verify(token)).isEmpty();
    }

    @Test
    void rejectsAnExpiredToken() {
        final String token = buildToken(internalKey,
                ClusterToken.CLUSTER_ISSUER,
                ClusterToken.CLUSTER_AUDIENCE,
                ClusterToken.PROCESSING_USER_SUBJECT,
                OpenId.TOKEN_TYPE__ACCESS,
                NumericDate.fromSeconds(Instant.now().minusSeconds(600).getEpochSecond()));
        assertThat(verifier().verify(token)).isEmpty();
    }

    @Test
    void rejectsNull() {
        assertThat(verifier().verify((String) null)).isEmpty();
    }

    private ClusterTokenVerifier verifier() {
        final PublicJsonWebKeyProvider provider = Mockito.mock(PublicJsonWebKeyProvider.class);
        Mockito.when(provider.list()).thenReturn(List.<PublicJsonWebKey>of(internalKey));
        return new ClusterTokenVerifier(provider);
    }

    private NumericDate validExpiry() {
        return NumericDate.fromSeconds(Instant.now().plusSeconds(600).getEpochSecond());
    }

    private String buildToken(final RsaJsonWebKey signingKey,
                              final String issuer,
                              final String audience,
                              final String subject,
                              final String type,
                              final NumericDate expiry) {
        final JwtClaims claims = new JwtClaims();
        if (issuer != null) {
            claims.setIssuer(issuer);
        }
        if (audience != null) {
            claims.setAudience(audience);
        }
        if (subject != null) {
            claims.setSubject(subject);
        }
        claims.setExpirationTime(expiry);

        final JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setKey(signingKey.getPrivateKey());
        jws.setKeyIdHeaderValue(signingKey.getKeyId());
        jws.setDoKeyValidation(true);
        if (type != null) {
            jws.setHeader("typ", type);
        }
        try {
            return jws.getCompactSerialization();
        } catch (final JoseException e) {
            throw new RuntimeException(e);
        }
    }
}
