package stroom.authentication;

import org.jose4j.jwk.PublicJsonWebKey;

public interface JwkDao {
    PublicJsonWebKey readJwk();
}
