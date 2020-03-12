package stroom.authentication.clients;

import stroom.authentication.config.AuthenticationConfig;
import stroom.authentication.config.AuthorisationServiceConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

@Singleton
public class AuthorisationService {
    private final AuthorisationServiceConfig config;
    private AuthorisationServiceClient authorisationServiceClient;

    @Inject
    public AuthorisationService(@NotNull AuthorisationServiceClient authorisationServiceClient, @NotNull AuthenticationConfig config) {
        super();
        this.authorisationServiceClient = authorisationServiceClient;
        this.config = config.getAuthorisationServiceConfig();
    }

    public boolean canManageUsers(String userName, String idToken) {
        return authorisationServiceClient.hasPermission(userName, idToken, config.getCanManageUsersPermission());
    }
}
