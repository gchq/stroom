/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.authentication.token;

import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;
import stroom.authentication.api.OIDC;

import java.time.Instant;
import java.util.Optional;

public class TokenBuilder {
    private Instant expiryDate;
    private String issuer;
    private String algorithm = AlgorithmIdentifiers.RSA_USING_SHA256;

    private String subject;
    private Optional<String> nonce = Optional.empty();
    private Optional<String> state = Optional.empty();
    private PublicJsonWebKey publicJsonWebKey;
    private String clientId;

    public TokenBuilder subject(String subject) {
        this.subject = subject;
        return this;
    }

    public TokenBuilder clientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public TokenBuilder issuer(String issuer) {
        this.issuer = issuer;
        return this;
    }

    public TokenBuilder privateVerificationKey(PublicJsonWebKey publicJsonWebKey) {
        this.publicJsonWebKey = publicJsonWebKey;
        return this;
    }

    public TokenBuilder nonce(String nonce) {
        this.nonce = Optional.of(nonce);
        return this;
    }

    public TokenBuilder state(String state) {
        this.state = Optional.of(state);
        return this;
    }

    public TokenBuilder algorithm(String algorithm) {
        this.algorithm = algorithm;
        return this;
    }

    public TokenBuilder expiryDate(Instant expiryDate) {
        this.expiryDate = expiryDate;
        return this;
    }

    public Instant getExpiryDate() {
        return this.expiryDate;
    }

    public String build() {
        JwtClaims claims = new JwtClaims();
        if (expiryDate != null) {
            claims.setExpirationTime(NumericDate.fromSeconds(expiryDate.getEpochSecond()));
        }
        claims.setSubject(subject);
        claims.setIssuer(issuer);
        claims.setAudience(clientId);

        nonce.ifPresent(nonce -> claims.setClaim(OIDC.NONCE, nonce));
        state.ifPresent(state -> claims.setClaim(OIDC.STATE, state));

        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setAlgorithmHeaderValue(this.algorithm);
        jws.setKey(this.publicJsonWebKey.getPrivateKey());
        jws.setDoKeyValidation(false);

        // Should we set the key ID in the headers? If so, we
//        jws.setKeyIdHeaderValue("123");

        try {
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new RuntimeException(e);
        }
    }

}
