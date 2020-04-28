package stroom.authentication.token;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jwk.Use;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.api.JsonWebKeyFactory;
import stroom.util.logging.LogUtil;

import java.util.UUID;

public class JsonWebKeyFactoryImpl implements JsonWebKeyFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonWebKeyFactoryImpl.class);

    private static final int BITS = 2048;

    @Override
    public PublicJsonWebKey createPublicKey() {
        // We need to set up the jwkId so we know which JWTs were signed by which JWKs.
        String jwkId = UUID.randomUUID().toString();
        try {
            RsaJsonWebKey jwk = RsaJwkGenerator.generateJwk(BITS);
            LOGGER.info("Generating RSA key pair for JSON Web Tokens with ID: {}", jwkId);

            jwk.setKeyId(jwkId);
            jwk.setUse(Use.SIGNATURE);
            jwk.setAlgorithm(AlgorithmIdentifiers.RSA_USING_SHA256);
            LOGGER.info("keyId: {}", jwk.getKeyId());
            return jwk;
        } catch (JoseException e) {
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
        } catch (JoseException e) {
            LOGGER.error("Unable to create RsaJsonWebKey from json:\n{}", json, e);
            throw new RuntimeException(e);
        }
    }
}
