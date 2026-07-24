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
    private PublicJsonWebKey publicJsonWebKey;
    private String clientId;
    private String type;
    private Long authTime;
    private String scope;

    public TokenBuilder subject(final String subject) {
        this.subject = subject;
        return this;
    }

    /**
     * The JOSE {@code typ} header value. Set {@link OpenId#TOKEN_TYPE__ACCESS} to mark the token as an
     * access token that may authenticate requests; leave unset for tokens (id, refresh) that may not.
     */
    public TokenBuilder type(final String type) {
        this.type = type;
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

    /**
     * The time the end-user authenticated, as seconds since the epoch, see {@link OpenId#CLAIM__AUTH_TIME}.
     * An id token claim.
     */
    public TokenBuilder authTime(final Long authTime) {
        this.authTime = authTime;
        return this;
    }

    /**
     * The scope granted to an access token, see {@link OpenId#SCOPE}.
     */
    public TokenBuilder scope(final String scope) {
        this.scope = scope;
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
        // A unique id per token, giving each token a distinct identity for logging, correlation and
        // future refresh token reuse detection.
        claims.setGeneratedJwtId();
        if (clientId != null) {
            // The authorized party - the client the token was issued to. Providers such as Keycloak
            // set this on both id and access tokens.
            claims.setClaim(OpenId.CLAIM__AUTHORIZED_PARTY, clientId);
        }
        if (OpenId.TOKEN_TYPE__ACCESS.equals(type) && clientId != null) {
            // RFC 9068 identifies the client of an access token with the client_id claim.
            claims.setClaim(OpenId.CLIENT_ID, clientId);
        }
        if (nonce != null) {
            claims.setClaim(OpenId.NONCE, nonce);
        }
        if (authTime != null) {
            claims.setClaim(OpenId.CLAIM__AUTH_TIME, authTime);
        }
        if (scope != null) {
            claims.setClaim(OpenId.SCOPE, scope);
        }

        final JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setAlgorithmHeaderValue(this.algorithm);
        jws.setKey(this.publicJsonWebKey.getPrivateKey());
        jws.setDoKeyValidation(true);
        if (type != null) {
            jws.setHeader("typ", type);
        }

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
