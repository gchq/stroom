package stroom.security.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"users", "quickFilterInput"})
@JsonInclude(Include.NON_NULL)
public class FilterUsersRequest {

    @JsonProperty
    private final List<User> users;
    @JsonProperty
    private final String quickFilterInput;

    @JsonCreator
    public FilterUsersRequest(@JsonProperty("users") final List<User> users,
                              @JsonProperty("quickFilterInput") final String quickFilterInput) {
        this.users = users;
        this.quickFilterInput = quickFilterInput;
    }

    public List<User> getUsers() {
        return users;
    }

    public String getQuickFilterInput() {
        return quickFilterInput;
    }

    @Override
    public String toString() {
        return "FilterUsersRequest{" +
                "users=" + users +
                ", quickFilterInput='" + quickFilterInput + '\'' +
                '}';
    }
}
