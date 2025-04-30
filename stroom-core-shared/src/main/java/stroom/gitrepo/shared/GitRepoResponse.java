package stroom.gitrepo.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class GitRepoResponse {

    @JsonProperty
    private final boolean ok;
    @JsonProperty
    private final String message;

    @JsonCreator
    public GitRepoResponse(@JsonProperty("ok") final boolean ok,
                           @JsonProperty("message") final String message) {
        this.ok = ok;
        this.message = message;
    }

    public boolean isOk() {
        return ok;
    }

    public String getMessage() {
        return message;
    }

}