package stroom.auth.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

public class AuthorisationServiceConfig {

    @NotNull
    @JsonProperty
    private String url;

    @NotNull
    @JsonProperty
    private String canManageUsersPermission;


    public String getUrl() {
        return url;
    }

    public String getCanManageUsersPermission() {
        return canManageUsersPermission;
    }
}
