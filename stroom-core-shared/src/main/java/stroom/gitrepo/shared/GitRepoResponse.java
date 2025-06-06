package stroom.gitrepo.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Object to return from GitRepo REST API calls with whether the call worked
 * and any messages about what happened.
 */
@JsonInclude(Include.NON_NULL)
public class GitRepoResponse {

    @JsonProperty
    private final boolean ok;

    @JsonProperty
    private final String message;

    /**
     * Constructor.
     * @param ok      If the API call worked
     * @param message Any message. Must not be null.
     */
    @JsonCreator
    public GitRepoResponse(@JsonProperty("ok") final boolean ok,
                           @JsonProperty("message") final String message) {
        Objects.requireNonNull(message);
        this.ok = ok;
        this.message = message;
    }

    /**
     * @return true if call worked, false if there was an error.
     */
    public boolean isOk() {
        return ok;
    }

    /**
     * @return Any message associated with the response. Never returns null.
     */
    public String getMessage() {
        return message;
    }

}
