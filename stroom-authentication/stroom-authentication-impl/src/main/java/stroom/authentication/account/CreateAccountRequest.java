package stroom.authentication.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class CreateAccountRequest {
    @JsonProperty
    private final String firstName;
    @JsonProperty
    private final String lastName;
    @JsonProperty
    private final String email;
    @JsonProperty
    private final String comments;
    @JsonProperty
    private final String password;
    @JsonProperty
    private final boolean forcePasswordChange;
    @JsonProperty
    private final boolean neverExpires;

    @JsonCreator
    public CreateAccountRequest(@JsonProperty("firstName") final String firstName,
                                @JsonProperty("lastName") final String lastName,
                                @JsonProperty("email") final String email,
                                @JsonProperty("comments") final String comments,
                                @JsonProperty("password") final String password,
                                @JsonProperty("forcePasswordChange") final boolean forcePasswordChange,
                                @JsonProperty("neverExpires") final boolean neverExpires) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.comments = comments;
        this.password = password;
        this.forcePasswordChange = forcePasswordChange;
        this.neverExpires = neverExpires;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getComments() {
        return comments;
    }

    public String getPassword() {
        return password;
    }

    public boolean isForcePasswordChange() {
        return forcePasswordChange;
    }

    public boolean isNeverExpires() {
        return neverExpires;
    }
}
