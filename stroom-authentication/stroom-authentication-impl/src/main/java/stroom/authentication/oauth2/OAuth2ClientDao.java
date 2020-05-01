package stroom.authentication.oauth2;


import stroom.authentication.api.OAuth2Client;

import java.util.Optional;

public interface OAuth2ClientDao {
    void create(OAuth2Client client);

    Optional<OAuth2Client> getClientForClientId(String clientId);

    Optional<OAuth2Client> getClientByName(String clientId);
}
