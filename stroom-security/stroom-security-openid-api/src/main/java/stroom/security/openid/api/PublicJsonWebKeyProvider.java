package stroom.security.openid.api;

import org.jose4j.jwk.PublicJsonWebKey;

import java.util.List;

public interface PublicJsonWebKeyProvider {

    List<PublicJsonWebKey> list();

    PublicJsonWebKey getFirst();
}
