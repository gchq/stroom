package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.AbstractConfig;

@JsonPropertyOrder({"ui", "authenticationService", "users", "apiKeys", "changepassword"})
@JsonInclude(Include.NON_NULL)
public class UrlConfig extends AbstractConfig {
    @JsonProperty
    @JsonPropertyDescription("The URL of Stroom as provided to the browser")
    private String ui;
    @JsonProperty
    @JsonPropertyDescription("The URL of the authentication service")
    private String authenticationService;
    @JsonProperty
    private String users;
    @JsonProperty
    private String apiKeys;
    @JsonProperty
    private String changepassword;

    public UrlConfig() {
        setDefaults();
    }

    @JsonCreator
    public UrlConfig(@JsonProperty("ui") final String ui,
                     @JsonProperty("authenticationService") final String authenticationService,
                     @JsonProperty("users") final String users,
                     @JsonProperty("apiKeys") final String apiKeys,
                     @JsonProperty("changepassword") final String changepassword) {
        this.ui = ui;
        this.authenticationService = authenticationService;
        this.users = users;
        this.apiKeys = apiKeys;
        this.changepassword = changepassword;

        setDefaults();
    }

    private void setDefaults() {
        if (ui == null) {
            ui = "http://IP_ADDRESS";
        }
        if (authenticationService == null) {
            authenticationService = "http://auth-service:8099/authentication/v1";
        }
        if (users == null) {
            users = "http://IP_ADDRESS/users";
        }
        if (apiKeys == null) {
            apiKeys = "http://IP_ADDRESS/tokens";
        }
        if (changepassword == null) {
            changepassword = "http://IP_ADDRESS/changepassword";
        }
    }

    public String getUi() {
        return ui;
    }

    public void setUi(final String ui) {
        this.ui = ui;
    }

    public String getAuthenticationService() {
        return authenticationService;
    }

    public void setAuthenticationService(final String authenticationService) {
        this.authenticationService = authenticationService;
    }

    public String getUsers() {
        return users;
    }

    public void setUsers(final String users) {
        this.users = users;
    }

    public String getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(final String apiKeys) {
        this.apiKeys = apiKeys;
    }

    public String getChangepassword() {
        return changepassword;
    }

    public void setChangepassword(final String changepassword) {
        this.changepassword = changepassword;
    }

    @Override
    public String toString() {
        return "UrlConfig{" +
                "ui='" + ui + '\'' +
                ", authenticationService='" + authenticationService + '\'' +
                ", users='" + users + '\'' +
                ", apiKeys='" + apiKeys + '\'' +
                ", changepassword='" + changepassword + '\'' +
                '}';
    }
}
