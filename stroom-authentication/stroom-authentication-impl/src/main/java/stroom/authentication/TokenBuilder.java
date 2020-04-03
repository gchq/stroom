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

package stroom.authentication;

import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;
import stroom.authentication.resources.token.v1.Token.TokenType;
import stroom.authentication.service.api.OIDC;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Optional;

public class TokenBuilder {
    private TokenType tokenType;
    private Instant expiryDate;
    private String issuer;
    private byte[] secret;
    private String algorithm = "RS256";

    private String subject;
    private Optional<String> nonce = Optional.empty();
    private Optional<String> state = Optional.empty();
    private PrivateKey privateVerificationKey;
    private String authSessionId;
    private String clientId;

    public TokenBuilder subject(String subject) {
        this.subject = subject;
        return this;
    }

    public TokenBuilder clientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public TokenBuilder tokenType(TokenType tokenType) {
        this.tokenType = tokenType;
        return this;
    }

    public TokenBuilder issuer(String issuer) {
        this.issuer = issuer;
        return this;
    }

    public TokenBuilder secret(byte[] secret) {
        this.secret = secret;
        return this;
    }

    public TokenBuilder privateVerificationKey(PrivateKey privateVerificationKey) {
        this.privateVerificationKey = privateVerificationKey;
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

    /**
     * The authSessionId is needed for federated logout. It allows a back-channel (API) logout request from an RP
     * to this Identity Provider to contain the authSessionId. The IP then requests logout from all known RPs,
     * passing this on. If the other RPs have a map of authSessionIds to it's own sessionIds then they will be
     * able to log the user out of their own sessions. See below for a sequence diagram of this flow.
     * <p>
     * The logout requests below all include the authSessionId.
     * <p>
     * RP1                      IP                       RP2                     RP3
     * |----logout------------>|                        |                        |
     * |                       |---logout-------------->|                        |
     * |                       |    Uses authSessionId to logout its own session |
     * |                       |---logout--------------------------------------->|
     * |                       |                        |       Uses authSessionId to logout its own session
     * |                       |                        |                        |
     */
    public TokenBuilder authSessionId(String authSessionId) {
        this.authSessionId = authSessionId;
        return this;
    }

    public String build() {
        JwtClaims claims = new JwtClaims();
        if (expiryDate != null) {
            claims.setExpirationTime(NumericDate.fromSeconds(expiryDate.getEpochSecond()));
        }
        claims.setSubject(subject);
        claims.setIssuer(issuer);
        claims.setAudience(clientId);
        if (authSessionId != null) {
            claims.setStringClaim("sid", authSessionId);
        }
        nonce.ifPresent(nonce -> claims.setClaim(OIDC.NONCE, nonce));
        state.ifPresent(state -> claims.setClaim(OIDC.STATE, state));

        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setAlgorithmHeaderValue(this.algorithm);
        jws.setKey(this.privateVerificationKey);
        jws.setDoKeyValidation(false);

        try {
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new RuntimeException(e);
        }
    }

}
