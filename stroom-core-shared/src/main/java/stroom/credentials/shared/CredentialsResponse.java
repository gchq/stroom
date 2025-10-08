package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Class to wrap up the return values from the Credentials Resource.
 */
@JsonInclude(Include.NON_NULL)
public class CredentialsResponse {

    /** Did the method call work? */
    public enum Status {
        /** Yes the call worked */
        OK,
        /** No the call didn't work */
        GENERAL_ERR
    }

    @JsonProperty
    private final Status status;

    @JsonProperty
    private final String message;

    @JsonProperty
    private final Credentials credentials;

    @JsonProperty
    private final CredentialsSecret secret;

    /**
     * Constructor that just takes the status. Default values for other parameters.
     * @param status If the API call worked. Must not be null.
     */
    public CredentialsResponse(final CredentialsResponse.Status status) {
        Objects.requireNonNull(status);
        this.status = status;
        this.message = "";
        this.credentials = null;
        this.secret = null;
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
        this.secret = null;
    }

    /**
     * Constructor for successful getting of object.
     * @param credentials Optional credentials - can be null.
     */
    public CredentialsResponse(final Credentials credentials) {
        this.status = Status.OK;
        this.message = "";
        this.credentials = credentials;
        this.secret = null;
    }

    /**
     * Constructor for successful getting of object.
     * @param secret Optional secret - can be null.
     */
    public CredentialsResponse(final CredentialsSecret secret) {
        this.status = Status.OK;
        this.message = "";
        this.credentials = null;
        this.secret = secret;
    }

    /**
     * Constructor for deserialisation.
     */
    @SuppressWarnings("unused")
    @JsonCreator
    public CredentialsResponse(@JsonProperty("status") final Status status,
                               @JsonProperty("message") final String message,
                               @JsonProperty("credentials") final Credentials credentials,
                               @JsonProperty("secret") final CredentialsSecret secret) {
        this.status = status;
        this.message = message;
        this.credentials = credentials;
        this.secret = secret;
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

    /**
     * @return The secret, if any. Will return null if no secret present.
     */
    public CredentialsSecret getSecret() {
        return secret;
    }
}
