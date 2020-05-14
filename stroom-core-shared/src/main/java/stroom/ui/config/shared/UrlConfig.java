package stroom.ui.config.shared;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;
import java.util.Objects;

@Singleton
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class UrlConfig extends AbstractConfig {

    @JsonProperty
    @JsonPropertyDescription("The path to the Users screen.")
    private String users = "../../../s/users";

    @JsonProperty
    @JsonPropertyDescription("The path to the API Keys screen.")
    private String apiKeys = "../../../s/apiKeys";

    @JsonProperty
    @JsonPropertyDescription("The path to the Change Password screen.")
    private String changepassword = "../../../s/changepassword";

    public UrlConfig() {
        setDefaults();
    }

    @JsonCreator
    public UrlConfig(@JsonProperty("users") final String users,
                     @JsonProperty("apiKeys") final String apiKeys,
                     @JsonProperty("changepassword") final String changepassword) {
        this.users = users;
        this.apiKeys = apiKeys;
        this.changepassword = changepassword;

        setDefaults();
    }

    private void setDefaults() {
        if (users == null) {
            users = "../../../s/users";
        }
        if (apiKeys == null) {
            apiKeys = "../../../s/apiKeys";
        }
        if (changepassword == null) {
            changepassword = "../../../s/changepassword";
        }
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
                ", users='" + users + '\'' +
                ", apiKeys='" + apiKeys + '\'' +
                ", changepassword='" + changepassword + '\'' +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final UrlConfig urlConfig = (UrlConfig) o;
        return Objects.equals(users, urlConfig.users) &&
                Objects.equals(apiKeys, urlConfig.apiKeys) &&
                Objects.equals(changepassword, urlConfig.changepassword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(users, apiKeys, changepassword);
    }
}
