package stroom.authentication.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.AbstractConfig;

import javax.validation.constraints.NotNull;

public class AuthorisationServiceConfig extends AbstractConfig {

    @NotNull
    @JsonProperty
    private String url = "http://localhost:8080/api/authorisation/v";

    @NotNull
    @JsonProperty
    private String canManageUsersPermission = "Manage Users";


    public String getUrl() {
        return url;
    }

    public String getCanManageUsersPermission() {
        return canManageUsersPermission;
    }
}
