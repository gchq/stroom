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

import stroom.security.openid.api.JsonWebKeyFactory;
import stroom.util.logging.LogUtil;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jwk.Use;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class JwkFactoryImpl implements JsonWebKeyFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwkFactoryImpl.class);

    private static final int BITS = 2048;

    @Override
    public PublicJsonWebKey createPublicKey() {
        // We need to set up the jwkId so we know which JWTs were signed by which JWKs.
        final String jwkId = UUID.randomUUID().toString();
        try {
            final RsaJsonWebKey jwk = RsaJwkGenerator.generateJwk(BITS);
            LOGGER.info("Generating RSA key pair for JSON Web Tokens with ID: {}", jwkId);

            jwk.setKeyId(jwkId);
            jwk.setUse(Use.SIGNATURE);
            jwk.setAlgorithm(AlgorithmIdentifiers.RSA_USING_SHA256);
            LOGGER.info("keyId: {}", jwk.getKeyId());
            return jwk;
        } catch (final JoseException e) {
            throw new RuntimeException(LogUtil.message("Error generating JWK of {} bits", BITS), e);
        }
    }

    @Override
    public String asJson(final PublicJsonWebKey publicJsonWebKey) {
        return publicJsonWebKey.toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE);
    }

    @Override
    public PublicJsonWebKey fromJson(final String json) {
        try {
            return RsaJsonWebKey.Factory.newPublicJwk(json);
        } catch (final JoseException e) {
            LOGGER.error("Unable to create RsaJsonWebKey from json:\n{}", json, e);
            throw new RuntimeException(e);
        }
    }
}
