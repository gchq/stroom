package stroom.contentstore.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Object to return from ContentStore REST API calls with whether the call worked,
 * a value and any messages about what happened.
 */
@JsonInclude(Include.NON_NULL)
public class ContentStoreValueResponse<T> {

    @JsonProperty
    private final Boolean ok;

    @JsonProperty
    private final T value;

    @JsonProperty
    private final String message;

    /**
     * Constructor.
     * @param ok Whether the call worked or failed. Can be null in which case
     *           Boolean.FALSE will be returned.
     * @param value A value returned by this call. Can be null.
     * @param message A message about what happened to show the user.
     *                Can be null in which case the empty string will be
     *                returned.
     */
    public @JsonCreator ContentStoreValueResponse(@JsonProperty("ok") final Boolean ok,
                                           @JsonProperty("value") final T value,
                                           @JsonProperty("message") final String message) {
        this.ok = ok == null ? Boolean.FALSE : ok;
        this.value = value;
        this.message = message == null ? "" : message;
    }

    /**
     * @return Whether the call worked (true) or failed (false). Never
     * returns null.
     */
    public Boolean isOk() {
        return ok;
    }

    /**
     * @return The value encapsulated by this response.
     * Might be null.
     */
    public T getValue() {
        return value;
    }

    /**
     * @return The message to give to the user. Never returns null.
     */
    public String getMessage() {
        return message;
    }

}
