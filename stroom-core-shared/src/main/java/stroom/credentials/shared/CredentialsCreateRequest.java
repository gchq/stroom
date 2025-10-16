package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Request to create new credentials and associated secrets in the DB.
 */
@JsonPropertyOrder({
        "credentials",
        "secret"
})
@JsonInclude(Include.NON_NULL)
public class CredentialsCreateRequest {

    @JsonProperty
    private final Credentials credentials;

    @JsonProperty
    private final CredentialsSecret secret;

    @JsonCreator
    public CredentialsCreateRequest(
            @JsonProperty("credentials") final Credentials credentials,
            @JsonProperty("secret") final CredentialsSecret secret) {
        this.credentials = credentials;
        this.secret = secret;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    public CredentialsSecret getSecret() {
        return secret;
    }

}
