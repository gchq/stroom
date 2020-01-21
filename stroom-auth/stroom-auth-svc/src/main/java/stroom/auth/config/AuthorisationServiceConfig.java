package stroom.auth.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

public class AuthorisationServiceConfig {

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
