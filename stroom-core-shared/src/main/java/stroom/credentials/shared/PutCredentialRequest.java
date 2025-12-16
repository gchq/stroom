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
        "credential",
        "secret"
})
@JsonInclude(Include.NON_NULL)
public class PutCredentialRequest {

    @JsonProperty
    private final Credential credential;

    @JsonProperty
    private final Secret secret;

    @JsonCreator
    public PutCredentialRequest(
            @JsonProperty("credential") final Credential credential,
            @JsonProperty("secret") final Secret secret) {
        this.credential = credential;
        this.secret = secret;
    }

    public Credential getCredential() {
        return credential;
    }

    public Secret getSecret() {
        return secret;
    }

}
