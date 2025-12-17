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

package stroom.security.identity.token;

import stroom.security.openid.api.OpenId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;

import java.time.Instant;

public class TokenBuilder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TokenBuilder.class);

    private Instant expirationTime;
    private String issuer;
    private String algorithm = AlgorithmIdentifiers.RSA_USING_SHA256;

    private String subject;
    private String nonce;
    private String state;
    private PublicJsonWebKey publicJsonWebKey;
    private String clientId;

    public TokenBuilder subject(final String subject) {
        this.subject = subject;
        return this;
    }

    public TokenBuilder clientId(final String clientId) {
        this.clientId = clientId;
        return this;
    }

    public TokenBuilder issuer(final String issuer) {
        this.issuer = issuer;
        return this;
    }

    public TokenBuilder privateVerificationKey(final PublicJsonWebKey publicJsonWebKey) {
        this.publicJsonWebKey = publicJsonWebKey;
        return this;
    }

    public TokenBuilder nonce(final String nonce) {
        this.nonce = nonce;
        return this;
    }

    public TokenBuilder state(final String state) {
        this.state = state;
        return this;
    }

    public TokenBuilder algorithm(final String algorithm) {
        this.algorithm = algorithm;
        return this;
    }

    public TokenBuilder expirationTime(final Instant expirationTime) {
        this.expirationTime = expirationTime;
        return this;
    }

    public Instant getExpirationTime() {
        return this.expirationTime;
    }

    public String build() {
        final JwtClaims claims = new JwtClaims();
        if (expirationTime != null) {
            claims.setExpirationTime(NumericDate.fromSeconds(expirationTime.getEpochSecond()));
        }
        claims.setSubject(subject);
        claims.setIssuer(issuer);
        claims.setAudience(clientId);
        if (nonce != null) {
            claims.setClaim(OpenId.NONCE, nonce);
        }
        if (state != null) {
            claims.setClaim(OpenId.STATE, state);
        }

        final JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setAlgorithmHeaderValue(this.algorithm);
        jws.setKey(this.publicJsonWebKey.getPrivateKey());
        jws.setDoKeyValidation(true);

        // TODO need to pass this in as it may not be the default one
        if (publicJsonWebKey.getKeyId() != null && !publicJsonWebKey.getKeyId().isEmpty()) {
            LOGGER.debug(() -> "Setting KeyIdHeaderValue to " + publicJsonWebKey.getKeyId());
            jws.setKeyIdHeaderValue(publicJsonWebKey.getKeyId());
        }

        try {
            return jws.getCompactSerialization();
        } catch (final JoseException e) {
            throw new RuntimeException(e);
        }
    }

}
