package stroom.authentication.dao;

import org.jose4j.jwk.PublicJsonWebKey;

public interface JwkDao {
    PublicJsonWebKey readJwk();
}
