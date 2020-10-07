package stroom.security.identity.openid;


import stroom.security.openid.api.OpenIdClient;

import java.util.Optional;

public interface OpenIdClientDao {
    void create(OpenIdClient client);

    Optional<OpenIdClient> getClientForClientId(String clientId);

    Optional<OpenIdClient> getClientByName(String clientId);
}
