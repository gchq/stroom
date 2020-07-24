package stroom.authentication.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class UpdateAccountRequest {
    @JsonProperty
    private final Account account;
    @JsonProperty
    private final String password;
    @JsonProperty
    private final String confirmPassword;

    @JsonCreator
    public UpdateAccountRequest(@JsonProperty("account") final Account account,
                                @JsonProperty("password") final String password,
                                @JsonProperty("confirmPassword") final String confirmPassword) {
        this.account = account;
        this.password = password;
        this.confirmPassword = confirmPassword;
    }

    public Account getAccount() {
        return account;
    }

    public String getPassword() {
        return password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }
}
