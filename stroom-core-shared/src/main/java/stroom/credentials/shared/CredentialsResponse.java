package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class CredentialsResponse {
    public enum Status {
        OK,
        GENERAL_ERR
    }

    @JsonProperty
    private final Status status;

    @JsonProperty
    private final String message;

    @JsonProperty
    private final Credentials credentials;

    /**
     * Constructor that just takes the status. Default values for other parameters.
     * @param status If the API call worked. Must not be null.
     */
    public CredentialsResponse(final CredentialsResponse.Status status) {
        Objects.requireNonNull(status);
        this.status = status;
        this.message = "";
        this.credentials = null;
    }

    /**
     * Constructor that just takes the status. Default values for other parameters.
     * @param status If the API call worked. Must not be null.
     */
    public CredentialsResponse(final CredentialsResponse.Status status,
                               final String message) {
        Objects.requireNonNull(status);
        Objects.requireNonNull(message);
        this.status = status;
        this.message = message;
        this.credentials = null;
    }

    /**
     * Constructor.
     * @param status If the API call worked. Must not be null.
     * @param message Any message. Must not be null.
     * @param credentials Optional credentials - can be null.
     */
    @JsonCreator
    public CredentialsResponse(@JsonProperty("status") final CredentialsResponse.Status status,
                               @JsonProperty("message") final String message,
                               @JsonProperty("credentials") final Credentials credentials) {
        Objects.requireNonNull(status);
        Objects.requireNonNull(message);
        this.status = status;
        this.message = message;
        this.credentials = credentials;
    }

    /**
     * @return The status of the operation - whether it worked or something went wrong.
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @return Any message that might be useful to the user.
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return The credentials, if any. Will return null if no credentials present.
     */
    public Credentials getCredentials() {
        return credentials;
    }
}
