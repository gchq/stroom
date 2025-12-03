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

package stroom.security.identity;

import stroom.security.identity.token.TokenBuilder;

import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.fail;

@Disabled
public class KeyGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyGenerator.class);

    RsaJsonWebKey rsaJwk = RsaJwkGenerator.generateJwk(2048);

    @SuppressWarnings("checkstyle:linelength")
    private static final String JWK = "{" +
            "\"kty\":\"RSA\"," +
            "\"kid\":\"1ec7a983-317d-46ce-ae93-ce42bc217e52\"," +
            "\"n\":\"72G4eRyG91OlsKOs-s2vBsbvXV5IKDOUpqDkFKYR_pCJwSBgD4BZYZ_qnIXx5L6cIK1Hsk_nMchHnL9lK7-nIt_bO41AyBes2IF2TTPuPKwqGY-aKCcuRh_BkDrCPZBR7-iyuYigL8MwgiP-yX6ieNpXzrfy3C8IYwLrKjLt39fb0fBod6_nrBPZeBUlH5m5pswNDfbOJII3bEGq5uQw5SZbaGBEEVbvkLNTROByK1PuOezfrpAyTWHCsG-zEsQ0HuIFyVo07sFJhV1AypPL-s71vKi6sDY46Mc5I2KV4CHbdvIhHT4xTaAhE9VimZgQW6sVvpRalV5XL8czjWVjtw\"," +
            "\"e\":\"AQAB\"," +
            "\"d\":\"HnwZXAMQBQs3_Ii7jK0I7xoCfad2FPiMo7O1mBOWEw8hG-EdmpvDxjTxUcGVDoZfp6GpkcGvNZ3F0OZm4e1kQYK0jp7scw7gyimigS5t1nguXFb3UMm8kN2WbuGsvt5UMPM3X31QuQRodwpSdiKUWkOkDwVJ_lRXAxTqEdOui2TY2WzKijvBEahyA2pqUGvrajF2GKFbZzXO1zagq6VhGCQohvCkaRWB-tVzc4-Ns6v3jqCKRpYYPrxD4C0RZ1K6InkxtXg1qYrcGMAPzCbqdjLoN41k-ZLNrp8rUSbRf2JAv9A8Ca2r6GDa0PcsySIbUWfduXOgGSuSfW6Hze1nMQ\"," +
            "\"p\":\"__6DyUpzhXxh6JfRghv2K371tCeV4l1Q45S92Ea1IX3Y6iBweK4W6ToQYTM7l_0bA5eaJ3kywvmpFqzpEcmtsOJOyg_rPQe3GeBDhUJLHqEPDT4t2yc8eQnrFOdWLEYg2nYWLoOkFPS36UBgJtaxIpyj16qidk-DYSEXpeDcqkM\"," +
            "\"q\":\"72McA2LIVj3hjlt_JsLpMFpcMm7E6knS-YITQUlwIFmVcW5tNQkC871o5x5AJL2Zj7MwxGHEx1WLzwd8B6lGkFP4BTpjCEoo_kXS-6IWyI3p6v7EmIb1O5Av8W37CA3VkPtuM-_dTFGlEW2vF0_ZetvTL49FlRx01Ekt48pvK30\"," +
            "\"dp\":\"xEAYQ_6hpVn_rVKGORq6lAnWz2_xhgJH-tCS4fUC81QJMSQBVWMRCWeMGxgtvY06Ynycn1pYwgSnzkxsuUhFse8su9eMXdNGWb4FxWlXMXoDkgFzIiloQNqLsBDRjUuN8CzLQImHBtG9FEJX9C5uybwQF0wnFFBMxe-as345bQU\"," +
            "\"dq\":\"1z03vNue4dw16Dfgdcueu7kjWL08FKRYK7uG8JbFWHDz68-sJZl6rAlMPzJ13hMT9Z7aZFi8A7apRHaoUIMlTTQStzCuRo_Xl_jUISi2b5EaGA8GWVZPPUUBtoR6x90Yf4lypwQu6CYo0yjZ244SL2Nj2Ulq-Q1jBlTeDAjCOEk\"," +
            "\"qi\":\"a6D1TYa8dgJDqKfZ926SqPxzA3FiJPWAfo8BTyIahZFqqaPAaXEfKUTTCZKmIqsWwNntGcdmDhnNGZ11E2X8MPYkRpnIQj3BywtDzRbPGJ8AFcvy6Zq_vYl874LFrkvGPILe6NxabRCyrFgDKOQ-gIjkL83t0wyS_vVlrGLtCrk\"}";

    public KeyGenerator() throws JoseException {
    }

    @Test
    void tokenBuilder() throws JoseException {
        final TokenBuilder builder = new TokenBuilder();
        final PublicJsonWebKey jwk = RsaJsonWebKey.Factory.newPublicJwk(JWK);
        final String key = builder
                .subject("admin")
                .issuer("stroom")
                .clientId("PZnJr8kHRKqnlJRQThSI")
                .algorithm("RS256")
                .privateVerificationKey(jwk)
                .build();
        LOGGER.info(key);
    }

    @Test
    void generateRsaKeys() throws JoseException {
        final String jwt = sign();

        verify(jwt);

        LOGGER.info(getPrivate());
        LOGGER.info(getPublic());
    }

    private void verify(final String jwt) throws JoseException {
        final PublicJsonWebKey publicJwk = PublicJsonWebKey.Factory.newPublicJwk(getPublic());
        final JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setRequireExpirationTime() // the JWT must have an expiration time
                .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims
                // to account for clock skew
                .setRequireSubject() // the JWT must have a subject claim
                .setExpectedIssuer("stroom") // whom the JWT needs to have been issued by
                .setExpectedAudience("PZnJr8kHRKqnlJRQThSI") // to whom the JWT is intended for
                .setVerificationKey(publicJwk.getPublicKey()) // verify the signature with the public key
                .setJwsAlgorithmConstraints(// only allow the expected signature algorithm(s) in the given context
                        new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.WHITELIST,
                                // which is only RS256 here
                                AlgorithmIdentifiers.RSA_USING_SHA256))
                .build(); // create the JwtConsumer instance
        try {
            //  Validate the JWT and process it to the Claims
            final JwtClaims jwtClaims = jwtConsumer.processToClaims(jwt);
            System.out.println("JWT validation succeeded! " + jwtClaims);
        } catch (final InvalidJwtException e) {
            System.out.println("Invalid JWT! " + e);
            fail("Invalid JWT! " + e);
        }
    }

    private String sign() throws JoseException {
        final JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(getSomeClaimsForTesting().toJson());
        final PublicJsonWebKey privateJwk = PublicJsonWebKey.Factory.newPublicJwk(getPrivate());
        jws.setKey(privateJwk.getPrivateKey());
        jws.setKeyIdHeaderValue(privateJwk.getKeyId());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        final String jwt = jws.getCompactSerialization();

        return jwt;
    }

    private String getPrivate() {
        return rsaJwk.toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE);
    }

    private String getPublic() {
        return rsaJwk.toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY);
    }

    private JwtClaims getSomeClaimsForTesting() {
        // Create the Claims, which will be the content of the JWT
        final JwtClaims claims = new JwtClaims();
        claims.setIssuer("stroom");  // who creates the token and signs it
        claims.setAudience("PZnJr8kHRKqnlJRQThSI"); // to whom the token is intended to be sent
        claims.setExpirationTimeMinutesInTheFuture(10); // time when the token will expire (10 minutes from now)
        claims.setGeneratedJwtId(); // a unique identifier for the token
        claims.setIssuedAtToNow();  // when the token was issued/created (now)
        claims.setNotBeforeMinutesInThePast(2); // time before which the token is not yet valid (2 minutes ago)
        claims.setSubject("subject"); // the subject/principal is whom the token is about
        claims.setClaim("email", "mail@example.com"); // additional claims/attributes about the subject can be added
        final List<String> groups = Arrays.asList("group-one", "other-group", "group-three");
        claims.setStringListClaim("groups", groups); // multi-valued claims work too and will end up as a JSON array
        return claims;
    }
}
