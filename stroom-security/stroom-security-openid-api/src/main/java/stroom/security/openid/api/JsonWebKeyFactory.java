package stroom.security.openid.api;

import org.jose4j.jwk.PublicJsonWebKey;

public interface JsonWebKeyFactory {
    PublicJsonWebKey createPublicKey();

    String asJson(final PublicJsonWebKey publicJsonWebKey);

    PublicJsonWebKey fromJson(final String json);
}
