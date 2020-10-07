package stroom.security.identity.token;

import org.jose4j.jwk.PublicJsonWebKey;

import java.util.List;

public interface JwkDao {
    List<PublicJsonWebKey> readJwk();
}
