package stroom.contentstore.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Object to return from ContentStore REST API calls with whether the call worked
 * and any messages about what happened.
 */
@JsonInclude(Include.NON_NULL)
public class ContentStoreResponse {

    /**
     * Return status; used to flag different error statuses.
     */
    public enum Status {
            OK,
        GENERAL_ERR,
        ALREADY_EXISTS
    }

    @JsonProperty
    private final Status status;

    @JsonProperty
    private final String message;

    /**
     * Constructor.
     * @param status  If the API call worked. Must not be null.
     * @param message Any message. Must not be null.
     */
    @JsonCreator
    public ContentStoreResponse(@JsonProperty("status") final ContentStoreResponse.Status status,
                                @JsonProperty("message") final String message) {
        Objects.requireNonNull(message);
        this.status = status;
        this.message = message;
    }

    /**
     * @return Returns the status of the operation.
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @return Any message associated with the response. Never returns null.
     */
    public String getMessage() {
        return message;
    }

}
